package io.quarkus.runtime.executor;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.JBossExecutors;
import org.jboss.threads.JBossThreadFactory;
import org.wildfly.common.cpu.ProcessorInfo;

import io.quarkus.runtime.ThreadPoolConfig;

public class ExecutorServiceFactory {

    private static final Logger log = Logger.getLogger("io.quarkus.thread-pool");
    private static final Method virtualThreadMethod;

    static {
        Method foundVirtualThreadMethod = null;
        try {
            foundVirtualThreadMethod = Executors.class.getDeclaredMethod("newVirtualThreadExecutor");
        } catch (ReflectiveOperationException ignored) {
        }
        virtualThreadMethod = foundVirtualThreadMethod;

    }

    public static ExecutorServiceContext buildExecutorContext(ThreadPoolConfig threadPoolConfig) {
        ExecutorServiceBuilder executorServiceBuilder = null;
        ManagedExecutorService managedExecutor = null;

        if (threadPoolConfig.enableVirtualThreads && virtualThreadMethod != null) {
            log.info("Tech Preview - Enabling Virtual Threads");

            executorServiceBuilder = new VirtualThreadExecutorBuilder();
            try {
                managedExecutor = executorServiceBuilder.buildExecutor(threadPoolConfig);
            } catch (IllegalStateException ignore) {
                log.warn(
                        "Configuration option `enable-virtual-threads` was set, but Virtual Threads are not available, falling back to default Thread Pool");
                managedExecutor = null;
            }
        } else if (threadPoolConfig.enableVirtualThreads) {
            log.warn(
                    "Configuration option `enable-virtual-threads` was set, but Virtual Threads are not available, falling back to default Thread Pool");
        }

        if (managedExecutor == null) {
            executorServiceBuilder = new EnhancedQueueExecutorBuilder();
            managedExecutor = executorServiceBuilder.buildExecutor(threadPoolConfig);
        }

        if (executorServiceBuilder != null && managedExecutor != null) {
            return new ExecutorServiceContext(
                    managedExecutor,
                    executorServiceBuilder.createShutdownTask(threadPoolConfig, managedExecutor.getExecutorService()));
        } else {
            log.warn("Enable to build Executor");
            throw new IllegalStateException("Enable to build Executor");
        }
    }

    private interface ExecutorServiceBuilder<T> {
        ManagedExecutorService buildExecutor(ThreadPoolConfig threadPoolConfig);

        Runnable createShutdownTask(ThreadPoolConfig threadPoolConfig, T executor);
    }

    private static class EnhancedQueueExecutorBuilder implements ExecutorServiceBuilder<EnhancedQueueExecutor> {

        @Override
        public Runnable createShutdownTask(ThreadPoolConfig threadPoolConfig, EnhancedQueueExecutor executor) {

            return new Runnable() {
                @Override
                public void run() {
                    executor.shutdown();
                    final Duration shutdownTimeout = threadPoolConfig.shutdownTimeout;
                    final Optional<Duration> optionalInterval = threadPoolConfig.shutdownCheckInterval;
                    long remaining = shutdownTimeout.toNanos();
                    final long interval = optionalInterval.orElse(Duration.ofNanos(Long.MAX_VALUE)).toNanos();
                    long intervalRemaining = interval;
                    long interruptRemaining = threadPoolConfig.shutdownInterrupt.toNanos();

                    long start = System.nanoTime();
                    for (;;)
                        try {
                            if (!executor.awaitTermination(Math.min(remaining, intervalRemaining),
                                    TimeUnit.MILLISECONDS)) {
                                long elapsed = System.nanoTime() - start;
                                intervalRemaining -= elapsed;
                                remaining -= elapsed;
                                interruptRemaining -= elapsed;
                                if (interruptRemaining <= 0) {
                                    executor.shutdown(true);
                                }
                                if (remaining <= 0) {
                                    // done waiting
                                    final List<Runnable> runnables = executor.shutdownNow();
                                    if (!runnables.isEmpty()) {
                                        log.warnf("Thread pool shutdown failed: discarding %d tasks, %d threads still running",
                                                runnables.size(), executor.getActiveCount());
                                    } else {
                                        log.warnf("Thread pool shutdown failed: %d threads still running",
                                                executor.getActiveCount());
                                    }
                                    break;
                                }
                                if (intervalRemaining <= 0) {
                                    intervalRemaining = interval;
                                    // do some probing
                                    final int queueSize = executor.getQueueSize();
                                    final Thread[] runningThreads = executor.getRunningThreads();
                                    log.infof("Awaiting thread pool shutdown; %d thread(s) running with %d task(s) waiting",
                                            runningThreads.length, queueSize);
                                    // make sure no threads are stuck in {@code exit()}
                                    int realWaiting = runningThreads.length;
                                    for (Thread thr : runningThreads) {
                                        final StackTraceElement[] stackTrace = thr.getStackTrace();
                                        for (int i = 0; i < stackTrace.length && i < 8; i++) {
                                            if (stackTrace[i].getClassName().equals("java.lang.System")
                                                    && stackTrace[i].getMethodName().equals("exit")) {
                                                final Throwable t = new Throwable();
                                                t.setStackTrace(stackTrace);
                                                log.errorf(t,
                                                        "Thread %s is blocked in System.exit(); pooled (Executor) threads "
                                                                + "should never call this method because it never returns, thus preventing "
                                                                + "the thread pool from shutting down in a timely manner.  This is the "
                                                                + "stack trace of the call",
                                                        thr.getName());
                                                // don't bother waiting for exit() to return
                                                realWaiting--;
                                                break;
                                            }
                                        }
                                    }
                                    if (realWaiting == 0 && queueSize == 0) {
                                        // just exit
                                        executor.shutdownNow();
                                        break;
                                    }
                                }
                            }
                            return;
                        } catch (InterruptedException ignored) {
                        }
                }
            };
        }

        @Override
        public ManagedExecutorService buildExecutor(ThreadPoolConfig threadPoolConfig) {
            final JBossThreadFactory threadFactory = new JBossThreadFactory(new ThreadGroup("executor"), Boolean.TRUE, null,
                    "executor-thread-%t", JBossExecutors.loggingExceptionHandler("org.jboss.executor.uncaught"), null);
            final EnhancedQueueExecutor.Builder builder = new EnhancedQueueExecutor.Builder()
                    .setRegisterMBean(false)
                    .setHandoffExecutor(JBossExecutors.rejectingExecutor())
                    .setThreadFactory(JBossExecutors.resettingThreadFactory(threadFactory));
            final int cpus = ProcessorInfo.availableProcessors();
            // run time config variables
            builder.setCorePoolSize(threadPoolConfig.coreThreads);
            builder.setMaximumPoolSize(threadPoolConfig.maxThreads.orElse(Math.max(8 * cpus, 200)));
            if (threadPoolConfig.queueSize.isPresent()) {
                if (threadPoolConfig.queueSize.getAsInt() < 0) {
                    builder.setMaximumQueueSize(Integer.MAX_VALUE);
                } else {
                    builder.setMaximumQueueSize(threadPoolConfig.queueSize.getAsInt());
                }
            }
            builder.setGrowthResistance(threadPoolConfig.growthResistance);
            builder.setKeepAliveTime(threadPoolConfig.keepAliveTime);
            return new ManagedEnhancedQueueExecutor(builder.build());
        }
    }

    private static class VirtualThreadExecutorBuilder implements ExecutorServiceBuilder<ExecutorService> {

        @Override
        public Runnable createShutdownTask(ThreadPoolConfig threadPoolConfig, ExecutorService executor) {
            return new Runnable() {
                @Override
                public void run() {
                    //TODO:: what are the semantics wrt VirtualThreadExecutor.shutdown()?
                    executor.shutdown();
                }
            };
        }

        @Override
        public ManagedExecutorService buildExecutor(ThreadPoolConfig threadPoolConfig) {
            Method foundVirtualThreadMethod = null;
            try {
                foundVirtualThreadMethod = Executors.class.getDeclaredMethod("newVirtualThreadExecutor");
                if (foundVirtualThreadMethod == null) {
                    log.error("Unable to instantiate Virtual Thread Executor");
                    throw new IllegalStateException("Unable to instantiate Virtual Thread Executor");
                }
                return new ManagedVirtualExecutor((ExecutorService) virtualThreadMethod.invoke(null));
            } catch (final ReflectiveOperationException reflectiveOperationException) {
                throw new IllegalStateException(reflectiveOperationException.getMessage(), reflectiveOperationException);
            }
        }
    }

}
