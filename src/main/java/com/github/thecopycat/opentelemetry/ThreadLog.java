package com.github.thecopycat.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class ThreadLog implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ThreadLog.class);

    private Tracer tracer = GlobalOpenTelemetry.getTracer("aspect-exploring", "0.1");

    Thread thread;
    String className;
    String methodName;
    Method method;
    Span parentSpan;
    private boolean running = false;

    public ThreadLog(Thread thread, StackTraceElement element, Span parentSpan) {
        this.thread = thread;
        this.className = element.getClassName();
        this.methodName = element.getMethodName();
        this.parentSpan = parentSpan;
        this.method = new Method(className + "." + methodName);
    }

    @Override
    public synchronized void run() {
        synchronized (this) {
            if (logger.isDebugEnabled()) {
                logger.debug("logging inside " + className + " :: " + methodName);
            }
            long now = nowNanos();
            Thread.State state = thread.getState();

            StackTraceElement[] elements = thread.getStackTrace();
            int i = 0;
            for (StackTraceElement element : elements) {
                if (logger.isDebugEnabled()) {
                    logger.debug(element.getClassName() + " :: " + element.getMethodName());
                }
                if (element.getClassName().equals(className) && element.getMethodName().equals(methodName)) {
                    break;
                }
                i++;
            }
            method.parse(elements, i, now, state);
            if (logger.isDebugEnabled()) {
                long end = nowNanos();
                logger.debug("Thread dumping took "+(end - now)+" ns");
            }
        }

        running = false;
    }

    public synchronized void buildSpans() {
        synchronized (this) {
            Long now = nowNanos();
            method.buildSpan(tracer, parentSpan, now);
            if (logger.isInfoEnabled()) {
                long end = nowNanos();
                logger.info("Attaching span "+className+"."+methodName+" to parent span : "+parentSpan.getSpanContext().getSpanId());
                logger.info("span building took "+(end - now)+" ns");
            }
        }
    }

    public void restart() {
        this.method = new Method(className+"."+methodName);
    }

    public boolean isRunning() {
        return running;
    }

    private long nowNanos() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.NANOS);
        return (now.getEpochSecond()*1000_000_000)+(now.getNano());
    }

    public Map<Thread.State,Long> getThreadStates() {
        if (method == null) {
            return new HashMap<>();
        }
        return method.getThreadMap();
    }
}
