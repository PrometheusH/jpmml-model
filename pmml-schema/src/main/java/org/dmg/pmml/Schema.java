/*
 * Copyright (c) 2012 University of Tartu
 */
package org.dmg.pmml;

import java.lang.annotation.*;

@Retention (
	value = RetentionPolicy.RUNTIME
)
@Target (
	value = {ElementType.TYPE}
)
public @interface Schema {

	Version min() default Version.PMML_3_0;

	Version max() default Version.PMML_4_2;
}