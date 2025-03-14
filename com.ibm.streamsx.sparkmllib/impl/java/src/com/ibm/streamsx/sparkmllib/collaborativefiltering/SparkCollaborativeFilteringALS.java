/*******************************************************************************
 * Copyright (C) 2015 International Business Machines Corporation
 * All Rights Reserved
 *******************************************************************************/
package com.ibm.streamsx.sparkmllib.collaborativefiltering;

import java.util.ArrayList;
import java.util.logging.Logger;

import org.apache.spark.SparkContext;
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel;
import org.apache.spark.mllib.recommendation.Rating;

import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.logging.LogLevel;
import com.ibm.streams.operator.logging.LoggerNames;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streamsx.sparkmllib.AbstractSparkMLlibOperator;

//@PrimitiveOperator(description="This operator provides support for analysis of incoming tuple data against Apache Spark's collaborative filtering machine learning library.")
@InputPorts({@InputPortSet(cardinality=1,description="This input port is required. The operator expects 2 attributes of type int32 that will be used as input to the collaborative filtering algorithm."),
			 @InputPortSet(cardinality=1,optional=true,controlPort=true,description="This input control port is optional. The port expects a single attribute of type rstring and the value must be a string in JSON format. For example, to reload the spark model, the attribute value must be set to '{\\\"reloadModel\\\":true}'.")})
@OutputPortSet(cardinality=1,description="This output port is required. The operator passes through all attributes on the input port as-is to the output port. In addition, it expects an attribute called 'analysisResult' of type list<float64> or float64 depending on the 'analysisType' parameter.")
public class SparkCollaborativeFilteringALS extends AbstractSparkMLlibOperator<MatrixFactorizationModel> {

	
	private static final String CLASS_NAME =  "com.ibm.streamsx.sparkmllib.collaborativefiltering.SparkCollaborativeFilteringALS";
	/**
	 * Create a {@code Logger} specific to this class that will write to the SPL
	 * log facility as a child of the {@link LoggerNames#LOG_FACILITY}
	 * {@code Logger}. The {@code Logger} uses a
	 */
	private static Logger log = Logger.getLogger(LoggerNames.LOG_FACILITY + "." + CLASS_NAME, "com.ibm.streamsx.sparkmllib.Messages");
	private TupleAttribute<Tuple, Integer> attr1;
	private TupleAttribute<Tuple, Integer> attr2;
	private AnalysisType analysisType;
	
	public SparkCollaborativeFilteringALS() {
	}

	@Override
	protected MatrixFactorizationModel loadModel(SparkContext sc,
			String modelPath) {
		return MatrixFactorizationModel.load(sc, modelPath);
	}
	
	@Parameter(description="The attribute to be used to provide the first input value to the analytics model.",optional=false)
	public void setAttr1(TupleAttribute<Tuple, Integer> attr1) {
		this.attr1 = attr1;
	}
	
	@Parameter(description="The attribute to be used to provide the second input value to the analytics model.",optional=false)
	public void setAttr2(TupleAttribute<Tuple, Integer> attr2) {
		this.attr2 = attr2;
	}
	
	@Parameter(description="The type of analysis to perform using the collaborative filtering algorithm.",optional=false)
	public void setAnalysisType(AnalysisType type) {
		this.analysisType = type;
	}
	
	@ContextCheck(compile=false)
	public static void checkOutputAttributeRuntime(OperatorContextChecker checker) {
		
		OperatorContext context = checker.getOperatorContext();
		StreamSchema schema = context.getStreamingOutputs().get(0).getStreamSchema();
		Attribute resultAttribute = schema.getAttribute(ANALYSISRESULT_ATTRIBUTE);
		
		//make sure that the output attribute is the right type based on the type of analysis
		String type = context.getParameterValues("analysisType").get(0);
		
		if(type.equals(AnalysisType.Prediction.name())) {
			if( resultAttribute.getType().getMetaType() != MetaType.FLOAT64) {
				log.log(LogLevel.ERROR, "WRONG_TYPE_ALS", new Object[]{"Prediction", "float64", resultAttribute.getType()});
				checker.setInvalidContext();
			}
		}
		else if(!isList(resultAttribute, Integer.class)) {
			log.log(LogLevel.ERROR, "WRONG_TYPE_ALS", new Object[]{type, "list<int32>", resultAttribute.getType()});

			checker.setInvalidContext();
		}
	}

	@Override
	public void processTuple(StreamingInput<Tuple> stream, Tuple tuple)
			throws Exception {
		int val1 = attr1.getValue(tuple);
		int val2 = attr2.getValue(tuple);
		
		OutputTuple out = getOutput(0).newTuple();
		out.assign(tuple);
		try {
			switch (analysisType) {
			case Prediction:
				double result = getModel().predict(val1, val2);		
				out.setDouble(ANALYSISRESULT_ATTRIBUTE, result);
				break;
			case RecommendProducts: {
				Rating[] ratings = getModel().recommendProducts(val1, val2);
				ArrayList<Integer> products = new ArrayList<Integer>();
				for(Rating r: ratings) {
					products.add(r.product());
				}
				out.setList(ANALYSISRESULT_ATTRIBUTE, products);
				break;
			}
			case RecommendUsers:
				Rating[] ratings = getModel().recommendUsers(val1, val2);
				ArrayList<Integer> users = new ArrayList<Integer>();
				for(Rating r: ratings) {
					users.add(r.product());
				}
				out.setList(ANALYSISRESULT_ATTRIBUTE, users);
				break;
			}

			getOutput(0).submit(out);
	} catch (Exception e){
		log.log(LogLevel.ERROR, "PROCESS_TUPLE", new Object[]{e.getClass().getName(),e.getMessage()});
	}
}
	
}
