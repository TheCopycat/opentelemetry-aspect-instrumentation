package com.github.thecopycat.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;


@Aspect
public abstract class TracingAspect {

	private static final Logger LOGGER = LoggerFactory.getLogger(TracingAspect.class);

	private Tracer tracer = GlobalOpenTelemetry.getTracer("aspect-instrumentation", "0.1");

	@Pointcut
	abstract void scope();


	@Around("scope()")
	public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
		Span parentSpan = Span.current();
		Span span = tracer.spanBuilder(
				joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName())
				.setParent(Context.current().with(parentSpan)).startSpan();
		CodeSignature signature = (CodeSignature) joinPoint.getSignature();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("The method " + joinPoint.getSignature().getName() + "() begins with "
					+ Arrays.toString(joinPoint.getArgs()));
		}

		Thread thread = Thread.currentThread();
		StackTraceElement element = Thread.currentThread().getStackTrace()[1];
		ParallelTracerManager.getInstance().startTracing(thread,span,element);
		for (int i = 0; i < signature.getParameterNames().length; i++) {
			span.setAttribute("parameter." + signature.getParameterNames()[i], String.valueOf(joinPoint.getArgs()[i]));
		}
		try (Scope scope = span.makeCurrent()) {
			Object result = joinPoint.proceed();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("The method " + joinPoint.getSignature().getName() + "() ends with " + result);
			}
			return result;
		}
		catch (IllegalArgumentException e) {
			LOGGER.error("Illegal argument " + Arrays.toString(joinPoint.getArgs()) + " in "
					+ joinPoint.getSignature().getName() + "()");
			span.setStatus(StatusCode.ERROR);
			throw e;
		}
		finally {
			ParallelTracerManager.getInstance().finishTracing(thread);
			span.end();

		}
	}

}
