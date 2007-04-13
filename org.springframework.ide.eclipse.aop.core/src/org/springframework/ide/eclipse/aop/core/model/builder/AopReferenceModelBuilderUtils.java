/*******************************************************************************
 * Copyright (c) 2005, 2007 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.aop.core.model.builder;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.aspectj.lang.reflect.PerClauseKind;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.springframework.aop.aspectj.AspectJAfterAdvice;
import org.springframework.aop.aspectj.AspectJAfterReturningAdvice;
import org.springframework.aop.aspectj.AspectJAfterThrowingAdvice;
import org.springframework.aop.aspectj.AspectJAroundAdvice;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.AspectJMethodBeforeAdvice;
import org.springframework.ide.eclipse.aop.core.model.IAspectDefinition;
import org.springframework.ide.eclipse.aop.core.model.IAopReference.ADVICE_TYPES;
import org.springframework.util.StringUtils;

/**
 * Helper class for {@link AopReferenceModelBuilder} and
 * {@link AspectDefinitionBuilder}.
 * @author Christian Dupuis
 * @since 2.0
 */
@SuppressWarnings("restriction")
public class AopReferenceModelBuilderUtils {

	private static final String AJC_MAGIC = "ajc$";

	/** The ".class" file suffix */
	private static final String CLASS_FILE_SUFFIX = ".class";

	public static boolean validateAspect(String className) throws Throwable {
		ClassLoader classLoader = Thread.currentThread()
				.getContextClassLoader();
		InputStream is = classLoader
				.getResourceAsStream(AopReferenceModelBuilderUtils
						.getClassFileName(className));

		// check if class exists on class path
		if (is == null) {
			return false;
		}

		ClassReader reader = new ClassReader(is);
		AspectAnnotationVisitor v = new AspectAnnotationVisitor();
		reader.accept(v, false);

		if (!v.getClassInfo().hasAspectAnnotation()) {
			return false;
		}
		else {
			// we know it's an aspect, but we don't know whether it is an
			// @AspectJ aspect or a code style aspect.
			// This is an *unclean* test whilst waiting for AspectJ to provide
			// us with something better
			for (String m : v.getClassInfo().getMethodNames()) {
				if (m.startsWith(AJC_MAGIC)) {
					// must be a code style aspect
					return false;
				}
			}
			// validate supported instantiation models
			if (v.getClassInfo().getAspectAnnotation().getValue() != null) {
				if (v.getClassInfo().getAspectAnnotation().getValue()
						.toUpperCase()
						.equals(PerClauseKind.PERCFLOW.toString())) {
					return false;
				}
				if (v.getClassInfo().getAspectAnnotation().getValue()
						.toUpperCase().toString().equals(
								PerClauseKind.PERCFLOWBELOW.toString())) {
					return false;
				}
			}

			// check if super class is Aspect as well and abstract
			if (v.getClassInfo().getSuperType() != null) {
				reader = new ClassReader(classLoader
						.getResourceAsStream(AopReferenceModelBuilderUtils
								.getClassFileName(v.getClassInfo()
										.getSuperType())));
				AspectAnnotationVisitor sv = new AspectAnnotationVisitor();
				reader.accept(sv, false);

				if (sv.getClassInfo().getAspectAnnotation() != null
						&& !((sv.getClassInfo().getModifier() & Opcodes.ACC_ABSTRACT) != 0)) {
					return false;
				}
			}
			return true;
		}
	}

	public static Object initAspectJExpressionPointcut(IAspectDefinition info)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException,
			NoSuchMethodException {
		Class<?> expressionPointcutClass = loadClass(AspectJExpressionPointcut.class
				.getName());
		Object pc = expressionPointcutClass.newInstance();
		for (Method m : expressionPointcutClass.getMethods()) {
			if (m.getName().equals("setExpression")) {
				m.invoke(pc, info.getPointcutExpression());
			}
		}
		Method setDeclarationScopeMethod = expressionPointcutClass.getMethod(
				"setPointcutDeclarationScope", Class.class);
		setDeclarationScopeMethod.invoke(pc, loadClass(info
				.getAspectClassName()));
		return pc;
	}

	public static Class<?> loadClass(String className)
			throws ClassNotFoundException {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		return loader.loadClass(className);
	}

	public static Class<?> getAspectJAdviceClass(IAspectDefinition info)
			throws ClassNotFoundException {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Class<?> aspectJAdviceClass = null;
		if (info.getType() == ADVICE_TYPES.AROUND) {
			aspectJAdviceClass = loader.loadClass(AspectJAroundAdvice.class
					.getName());
		}
		else if (info.getType() == ADVICE_TYPES.AFTER) {
			aspectJAdviceClass = loader.loadClass(AspectJAfterAdvice.class
					.getName());
		}
		else if (info.getType() == ADVICE_TYPES.AFTER_RETURNING) {
			aspectJAdviceClass = loader
					.loadClass(AspectJAfterReturningAdvice.class.getName());
		}
		else if (info.getType() == ADVICE_TYPES.AFTER_THROWING) {
			aspectJAdviceClass = loader
					.loadClass(AspectJAfterThrowingAdvice.class.getName());
		}
		else if (info.getType() == ADVICE_TYPES.BEFORE) {
			aspectJAdviceClass = loader
					.loadClass(AspectJMethodBeforeAdvice.class.getName());
		}
		return aspectJAdviceClass;
	}

	public static Object createAspectJPointcutExpression(IAspectDefinition info)
			throws Throwable {
		try {
			Object pc = info.getAspectJPointcutExpression();
			Class<?> aspectJAdviceClass = getAspectJAdviceClass(info);
			Constructor<?> ctor = aspectJAdviceClass.getConstructors()[0];
			Object aspectJAdvice = ctor.newInstance(new Object[] {
					info.getAdviceMethod(), pc, null });
			if (info.getType() == ADVICE_TYPES.AFTER_RETURNING) {
				if (info.getReturning() != null) {
					Method setReturningNameMethod = aspectJAdviceClass
							.getMethod("setReturningName", String.class);
					setReturningNameMethod.invoke(aspectJAdvice, info
							.getReturning());
				}
			}
			else if (info.getType() == ADVICE_TYPES.AFTER_THROWING) {
				if (info.getThrowing() != null) {
					Method setThrowingNameMethod = aspectJAdviceClass
							.getMethod("setThrowingName", String.class);
					setThrowingNameMethod.invoke(aspectJAdvice, info
							.getThrowing());
				}
			}

			if (info.getArgNames() != null && info.getArgNames().length > 0) {
				Method setArgumentNamesFromStringArrayMethod = aspectJAdviceClass
						.getMethod("setArgumentNamesFromStringArray",
								String[].class);
				setArgumentNamesFromStringArrayMethod.invoke(aspectJAdvice,
						new Object[] { info.getArgNames() });
			}
			
			Method getPointuctMethod = aspectJAdviceClass
					.getMethod("getPointcut", (Class[]) null);
			return getPointuctMethod.invoke(aspectJAdvice,
					(Object[]) null);
		}
		catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	/**
	 * Determine the name of the class file, relative to the containing package:
	 * e.g. "String.class"
	 * @param clazz the class
	 * @return the file name of the ".class" file
	 */
	public static String getClassFileName(String className) {
		className = StringUtils.replace(className, ".", "/");
		return className + CLASS_FILE_SUFFIX;
	}
}
