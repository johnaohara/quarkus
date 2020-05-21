package io.quarkus.runtime;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    public ExecutorService setupRunTime(ShutdownContext shutdownContext,
            LaunchMode launchMode) {
        if (devModeExecutor != null) {
            current = devModeExecutor;
            return devModeExecutor;
        }
        final ExecutorService underlying = createExecutor();
        ExecutorService executor;
        Runnable shutdownTask = createShutdownTask(underlying);
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

    public static ExecutorService createDevModeExecutorForFailedStart() {
        ExecutorService underlying = createExecutor();
        Runnable task = createShutdownTask(underlying);
        devModeExecutor = new CleanableExecutor(underlying);
        Runtime.getRuntime().addShutdownHook(new Thread(task, "Executor shutdown thread"));
        current = devModeExecutor;
        return devModeExecutor;
    }

    static void shutdownDevMode() {
        devModeExecutor.shutdown();
    }

    private static Runnable createShutdownTask(ExecutorService executor) {
        return new Runnable() {
            @Override
            public void run() {
                executor.shutdown();
            }
        }

        ;
    }

    private static ExecutorService createExecutor() {

        return Executors.newUnboundedVirtualThreadExecutor();

    }

    public static Executor getCurrent() {
        return current;
    }
}
