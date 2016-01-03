/*
 * Copyright (c) 2015 Villu Ruusmann
 */
package org.jpmml.model.visitors;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldUsageType;
import org.dmg.pmml.LocalTransformations;
import org.dmg.pmml.MiningField;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.MultipleModelMethodType;
import org.dmg.pmml.Output;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.Segment;
import org.dmg.pmml.Segmentation;
import org.dmg.pmml.Visitable;
import org.jpmml.model.FieldUtil;

/**
 * <p>
 * A Visitor that removes redundant {@link MiningField mining fields} from the {@link MiningSchema mining schema}.
 * </p>
 */
public class MiningSchemaCleaner extends FieldResolver {

	private FieldDependencyResolver fieldDependencyResolver = null;


	@Override
	public void applyTo(Visitable visitable){
		FieldDependencyResolver fieldDependencyResolver = new FieldDependencyResolver();
		fieldDependencyResolver.applyTo(visitable);

		setFieldDependencyResolver(fieldDependencyResolver);

		super.applyTo(visitable);
	}

	@Override
	public PMMLObject popParent(){
		PMMLObject parent = super.popParent();

		if(parent instanceof MiningModel){
			MiningModel miningModel = (MiningModel)parent;

			Set<FieldName> activeFieldNames = processMiningModel(miningModel);

			clean(miningModel, activeFieldNames);
		} else

		if(parent instanceof Model){
			Model model = (Model)parent;

			Set<FieldName> activeFieldNames = processModel(model);

			clean(model, activeFieldNames);
		}

		return parent;
	}

	private Set<FieldName> processMiningModel(MiningModel miningModel){
		Set<FieldName> activeFieldNames = new LinkedHashSet<>();

		Segmentation segmentation = miningModel.getSegmentation();

		List<Segment> segments = segmentation.getSegments();
		for(Segment segment : segments){
			Predicate predicate = segment.getPredicate();
			if(predicate != null){
				FieldReferenceFinder fieldReferenceFinder = new FieldReferenceFinder();
				fieldReferenceFinder.applyTo(predicate);

				activeFieldNames.addAll(fieldReferenceFinder.getFieldNames());
			}

			Model model = segment.getModel();
			if(model != null){
				MiningSchema miningSchema = model.getMiningSchema();

				List<MiningField> miningFields = miningSchema.getMiningFields();
				for(MiningField miningField : miningFields){
					FieldName name = miningField.getName();

					FieldUsageType fieldUsage = miningField.getUsageType();
					switch(fieldUsage){
						case ACTIVE:
							activeFieldNames.add(name);
							break;
						default:
							break;
					}
				}
			}
		}

		Set<Field> modelFields;

		MultipleModelMethodType multipleModelMethod = segmentation.getMultipleModelMethod();
		switch(multipleModelMethod){
			case MODEL_CHAIN:
				modelFields = getFields(miningModel, segmentation);
				break;
			default:
				modelFields = getModelFields(miningModel);
				break;
		}

		Set<Field> activeFields = FieldUtil.selectAll(modelFields, activeFieldNames);

		return processModel(miningModel, activeFields);
	}

	private Set<FieldName> processModel(Model model){
		FieldReferenceFinder fieldReferenceFinder = new FieldReferenceFinder();
		fieldReferenceFinder.applyTo(model);

		Set<Field> modelFields = getModelFields(model);

		Set<Field> activeFields = FieldUtil.selectAll(modelFields, fieldReferenceFinder.getFieldNames());

		return processModel(model, activeFields);
	}

	private Set<Field> getModelFields(Model model){
		Output output = model.getOutput();

		if(output != null){
			return getFields(model, output);
		}

		return getFields(model);
	}

	private Set<FieldName> processModel(Model model, Set<Field> activeFields){
		FieldDependencyResolver fieldDependencyResolver = getFieldDependencyResolver();
		fieldDependencyResolver.expand(activeFields);

		LocalTransformations localTransformations = model.getLocalTransformations();
		if(localTransformations != null && localTransformations.hasDerivedFields()){
			activeFields.removeAll(localTransformations.getDerivedFields());
		}

		Output output = model.getOutput();
		if(output != null && output.hasOutputFields()){
			activeFields.removeAll(output.getOutputFields());
		}

		return FieldUtil.nameSet(activeFields);
	}

	private void clean(Model model, Set<FieldName> activeFieldNames){
		MiningSchema miningSchema = model.getMiningSchema();

		if(miningSchema.hasMiningFields()){
			clean(miningSchema.getMiningFields(), activeFieldNames);
		}
	}

	private void clean(List<MiningField> miningFields, Set<FieldName> activeFieldNames){

		for(Iterator<MiningField> it = miningFields.iterator(); it.hasNext(); ){
			MiningField miningField = it.next();

			FieldName name = miningField.getName();

			FieldUsageType fieldUsage = miningField.getUsageType();
			switch(fieldUsage){
				case ACTIVE:
					if(!(activeFieldNames).contains(name)){
						it.remove();
					}
					break;
				default:
					break;
			}
		}
	}

	private FieldDependencyResolver getFieldDependencyResolver(){
		return this.fieldDependencyResolver;
	}

	private void setFieldDependencyResolver(FieldDependencyResolver fieldDependencyResolver){
		this.fieldDependencyResolver = fieldDependencyResolver;
	}
}