package io.quarkus.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import io.quarkus.runtime.annotations.Recorder;

/**
 *
 */
@Recorder
public class ExecutorRecorder {

    private static final Logger log = Logger.getLogger("io.quarkus.thread-pool");

    public ExecutorRecorder() {
    }

    /**
     * In dev mode for now we need the executor to last for the life of the app, as it is used by Undertow. This will likely
     * change
     */
    static volatile CleanableExecutor devModeExecutor;

    private static volatile Executor current;

    public ExecutorService setupRunTime(ShutdownContext shutdownContext, ThreadPoolConfig threadPoolConfig,
            LaunchMode launchMode) {
        if (devModeExecutor != null) {
            current = devModeExecutor;
            return devModeExecutor;
        }
        final ExecutorService underlying = createExecutor(threadPoolConfig);
        ExecutorService executor;
        Runnable shutdownTask = createShutdownTask(threadPoolConfig, underlying);
        if (launchMode == LaunchMode.DEVELOPMENT) {
            devModeExecutor = new CleanableExecutor(underlying);
            shutdownContext.addShutdownTask(new Runnable() {
                @Override
                public void run() {
                    devModeExecutor.clean();
                }
            });
            executor = devModeExecutor;
            Runtime.getRuntime().addShutdownHook(new Thread(shutdownTask, "Executor shutdown thread"));
        } else {
            shutdownContext.addShutdownTask(shutdownTask);
            executor = underlying;
        }

        current = executor;
        return executor;
    }

    public static ExecutorService createDevModeExecutorForFailedStart(ThreadPoolConfig config) {
        ExecutorService underlying = createExecutor(config);
        Runnable task = createShutdownTask(config, underlying);
        devModeExecutor = new CleanableExecutor(underlying);
        Runtime.getRuntime().addShutdownHook(new Thread(task, "Executor shutdown thread"));
        current = devModeExecutor;
        return devModeExecutor;
    }

    static void shutdownDevMode() {
        devModeExecutor.shutdown();
    }

    private static Runnable createShutdownTask(ThreadPoolConfig threadPoolConfig, ExecutorService executor) {
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
                        if (!executor.awaitTermination(Math.min(remaining, intervalRemaining), TimeUnit.MILLISECONDS)) {
                            long elapsed = System.nanoTime() - start;
                            intervalRemaining -= elapsed;
                            remaining -= elapsed;
                            interruptRemaining -= elapsed;
                            if (interruptRemaining <= 0) {
                                executor.shutdown();
                            }
                            if (remaining <= 0) {
                                // done waiting
                                final List<Runnable> runnables = executor.shutdownNow();
                                //                                if (!runnables.isEmpty()) {
                                //                                    log.warnf("Thread pool shutdown failed: discarding %d tasks, %d threads still running",
                                //                                            runnables.size(), executor.getActiveCount());
                                //                                } else {
                                //                                    log.warnf("Thread pool shutdown failed: %d threads still running",
                                //                                            executor.getActiveCount());
                                //                                }
                                break;
                            }
                            if (intervalRemaining <= 0) {
                                //                                intervalRemaining = interval;
                                // do some probing
                                //                                final int queueSize = executor.getQueueSize();
                                //                                final Thread[] runningThreads = executor.getRunningThreads();
                                //                                log.infof("Awaiting thread pool shutdown; %d thread(s) running with %d task(s) waiting",
                                //                                        runningThreads.length, queueSize);
                                // make sure no threads are stuck in {@code exit()}
                                //                                int realWaiting = runningThreads.length;
                                //                                for (Thread thr : runningThreads) {
                                //                                    final StackTraceElement[] stackTrace = thr.getStackTrace();
                                //                                    for (int i = 0; i < stackTrace.length && i < 8; i++) {
                                //                                        if (stackTrace[i].getClassName().equals("java.lang.System")
                                //                                                && stackTrace[i].getMethodName().equals("exit")) {
                                //                                            final Throwable t = new Throwable();
                                //                                            t.setStackTrace(stackTrace);
                                //                                            log.errorf(t, "Thread %s is blocked in System.exit(); pooled (Executor) threads "
                                //                                                    + "should never call this method because it never returns, thus preventing "
                                //                                                    + "the thread pool from shutting down in a timely manner.  This is the "
                                //                                                    + "stack trace of the call", thr.getName());
                                //                                            // don't bother waiting for exit() to return
                                //                                            realWaiting--;
                                //                                            break;
                                //                                        }
                                //                                    }
                                //                                }
                                //                                if (realWaiting == 0 && queueSize == 0) {
                                //                                    // just exit
                                //                                    break;
                                //                                }
                                executor.shutdownNow();
                            }
                        }
                        return;
                    } catch (InterruptedException ignored) {
                    }
            }
        };
    }

    private static ExecutorService createExecutor(ThreadPoolConfig threadPoolConfig) {

        return Executors.newUnboundedVirtualThreadExecutor();

    }

    public static Executor getCurrent() {
        return current;
    }
}
