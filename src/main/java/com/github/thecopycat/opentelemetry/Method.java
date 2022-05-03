package com.github.thecopycat.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Method {
    private static final Logger LOGGER = LoggerFactory.getLogger(Method.class);

    private String methodName;
    private long startTimestamp = 0L;
    private long endTimestamp = 0L;
    private Map<Thread.State,Long> stateMap;
    private boolean finished = false;
    List<Method> finishedChild = new ArrayList<>();
    Method currentChild;
    boolean traceSpan = true;

    public Method(String methodName) {
        this(methodName, shouldTrace(methodName,0));
    }
    public Method(String methodName, boolean traceSpan) {
        this.methodName = methodName;
        this.traceSpan = traceSpan;
        this.stateMap = new HashMap<>();
        this.startTimestamp = nowNanos();
    }

    public void parse(StackTraceElement[] elements, int index, long timestamp, Thread.State state) {

        if (index > 0) {
            String childMethodName = elements[index-1].getClassName()+"."+elements[index-1].getMethodName();

            if (currentChild == null) {
                currentChild = new Method(childMethodName,shouldTrace(childMethodName,index));
            }
            if (!currentChild.methodName.equals(childMethodName)) {
                currentChild.finish(timestamp, state);
                finishedChild.add(currentChild);
                currentChild = new Method(childMethodName,shouldTrace(childMethodName,index));
            }
            currentChild.parse(elements,index -1, timestamp, state);
        } else {
            if (currentChild != null) {
                currentChild.finish(timestamp, state);
                finishedChild.add(currentChild);
                currentChild = null;
            }
        }
        endTimestamp = timestamp;
        stateMap.put(state,stateMap.getOrDefault(state,0L)+1);

    }

    public void finish(long timestamp, Thread.State state) {
        this.endTimestamp = timestamp;
        this.finished = true;
        this.stateMap.put(state,stateMap.getOrDefault(state,0L)+1);
        if (currentChild != null) {
            this.currentChild.finish(timestamp, state);
            this.finishedChild.add(currentChild);
            this.currentChild = null;
        }
    }



    public void buildSpan(Tracer tracer, Span parentSpan, Long now) {
        if (!this.finished) {
            this.finished = true;
            this.endTimestamp = now;
        }
        if (this.endTimestamp-this.startTimestamp < 5_000_000L) { // less than 5ms we do not log.
            return;
        }
        if (traceSpan) {
            Span span = tracer.spanBuilder(
                            this.methodName)
                    .setParent(Context.current().with(parentSpan))
                    .setStartTimestamp(this.startTimestamp, TimeUnit.NANOSECONDS)
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
            long total = stateMap.values().stream().reduce(0L, (l1, l2) -> l1 + l2);
            for (Map.Entry<Thread.State, Long> entry : stateMap.entrySet()) {
                span.setAttribute("state." + entry.getKey().toString(), (entry.getValue() * 100L / total) + "%");
            }
            for (Method method : this.finishedChild) {
                method.buildSpan(tracer, span, now);
            }
            if (this.currentChild != null) {
                this.currentChild.buildSpan(tracer, span, now);
            }
            if (!this.finished) {
                this.finished = true;
                span.end(now, TimeUnit.NANOSECONDS);
            } else {
                span.end(this.endTimestamp, TimeUnit.NANOSECONDS);
            }
        } else {
            for (Method method : this.finishedChild) {
                method.buildSpan(tracer, parentSpan, now);
            }
            if (this.currentChild != null) {
                this.currentChild.buildSpan(tracer, parentSpan, now);
            }
        }
    }

    private static boolean shouldTrace(String methodName, int index) {
        for (String filters: ParallelTracerManager.TRACING_FILTER) {
            if (methodName.contains(filters)) {
                return false;
            }
        }
        for (String whitelisted : ParallelTracerManager.ALWAYS_LOG) {
            if (methodName.contains(whitelisted)) {
                return true;
            }
        }
        if (ParallelTracerManager.LAST_ONLY && index > ParallelTracerManager.MAX_SHORT_STACK_SIZE) {
            return false;
        }
        return true;
    }
    private long nowNanos() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.NANOS);
        return (now.getEpochSecond()*1000_000_000)+(now.getNano());
    }

    public Map<Thread.State,Long> getThreadMap() {
        return stateMap;
    }
}
