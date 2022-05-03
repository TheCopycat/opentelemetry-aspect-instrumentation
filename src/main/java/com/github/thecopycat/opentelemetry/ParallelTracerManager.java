package com.github.thecopycat.opentelemetry;

import io.opentelemetry.api.trace.Span;

import java.util.*;
import java.util.concurrent.*;

public class ParallelTracerManager {

    public static final int TICK_MS = 50;
    public static final int INITIAL_DELAY_MS = 1;

    public static final int MAX_SHORT_STACK_SIZE = 3;

    public static final boolean LAST_ONLY = true;

    public static final String[] ALWAYS_LOG = new String[] {
            "com.worldline"
    };

    public static final String[] TRACING_FILTER = new String[] {
            "com.github.thecopycat.opentelemetry",
            "org.aspectj.runtime.reflect.JoinPointImpl.proceed",
            "$AjcClosure",
            "_aroundBody"
    };


    private static ParallelTracerManager _instance;

    public static synchronized ParallelTracerManager getInstance() {
        if (_instance == null) {
            _instance = new ParallelTracerManager();
        }
        return _instance;
    }

    private Map<Long, Deque<ThreadLog>> threads = new HashMap<>();
    private Map<Long, Future> taskMap = new HashMap<>();

    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public ParallelTracerManager() {
        // Empty default constructor
    }


    public void startTracing(Thread thread, Span parentSpan, StackTraceElement stackTraceElement) {
        Long threadId = thread.getId();
        if (threads.containsKey(threadId)) {

                taskMap.get(threadId).cancel(false);
                taskMap.remove(threadId);
                Deque<ThreadLog> threadDeque = threads.get(threadId);
                threadDeque.peekFirst().buildSpans();

        } else {
            threads.put(threadId, new ArrayDeque<>());
        }
        ThreadLog threadLog = new ThreadLog(Thread.currentThread(),stackTraceElement,parentSpan);
        Deque<ThreadLog> stack = threads.get(threadId);
        stack.addFirst(threadLog);
        ScheduledFuture future = executor.scheduleAtFixedRate(threadLog,INITIAL_DELAY_MS,TICK_MS, TimeUnit.MILLISECONDS);
        taskMap.put(threadId,future);

    }

    public void finishTracing(Thread thread) {
        long threadId = thread.getId();
        if (taskMap.containsKey(threadId)) {
            taskMap.get(threadId).cancel(false);
            taskMap.remove(threadId);
            Deque<ThreadLog> stack = threads.get(threadId);
            ThreadLog threadLog = stack.removeFirst();
            threadLog.buildSpans();
            if (stack.isEmpty()) {
                threads.remove(threadId);
            } else {
                stack.peekFirst().restart();
                ScheduledFuture future = executor.scheduleAtFixedRate(stack.peekFirst(),INITIAL_DELAY_MS,TICK_MS,TimeUnit.MILLISECONDS);
                taskMap.put(threadId,future);
            }
        }

    }

}
