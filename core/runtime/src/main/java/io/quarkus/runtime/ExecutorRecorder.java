package io.quarkus.runtime;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.jboss.logging.Logger;

import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.executor.ExecutorServiceContext;
import io.quarkus.runtime.executor.ExecutorServiceFactory;
import io.quarkus.runtime.executor.ManagedExecutorService;

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
    static volatile ManagedExecutorService devModeExecutor;

    private static volatile Executor current;

    public ExecutorService setupRunTime(ShutdownContext shutdownContext, ThreadPoolConfig threadPoolConfig,
            LaunchMode launchMode) {
        if (devModeExecutor != null) {
            current = devModeExecutor;
            return devModeExecutor;
        }

        final ExecutorServiceContext executorServiceContext = ExecutorServiceFactory.buildExecutorContext(threadPoolConfig);
        ExecutorService executor;

        if (launchMode == LaunchMode.DEVELOPMENT) {
            devModeExecutor = executorServiceContext.getManagedExecutorService();
            shutdownContext.addShutdownTask(new Runnable() {
                @Override
                public void run() {
                    devModeExecutor.clean();
                }
            });
            executor = devModeExecutor;
            Runtime.getRuntime()
                    .addShutdownHook(new Thread(executorServiceContext.getShutdownTask(), "Executor shutdown thread"));
        } else {
            shutdownContext.addShutdownTask(executorServiceContext.getShutdownTask());
            executor = executorServiceContext.getManagedExecutorService();
        }
        if (threadPoolConfig.prefill) {
            executorServiceContext.getManagedExecutorService().prefillThreads();
        }
        current = executor;
        return executor;
    }

    public static ExecutorService createDevModeExecutorForFailedStart(ThreadPoolConfig threadPoolConfig) {
        ExecutorServiceContext executorServiceContext = ExecutorServiceFactory.buildExecutorContext(threadPoolConfig);
        devModeExecutor = executorServiceContext.getManagedExecutorService();
        Runtime.getRuntime().addShutdownHook(new Thread(executorServiceContext.getShutdownTask(), "Executor shutdown thread"));
        current = devModeExecutor;
        return devModeExecutor;
    }

    static void shutdownDevMode() {
        if (devModeExecutor != null) {
            devModeExecutor.shutdown();
        }
    }

    public static Executor getCurrent() {
        return current;
    }
}
