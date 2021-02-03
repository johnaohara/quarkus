package io.quarkus.runtime.executor;

public class ExecutorServiceContext {
    private ManagedExecutorService managedExecutorService;
    private Runnable shutdownTask;

    public ExecutorServiceContext(ManagedExecutorService cleanableExecutor, Runnable shutdownTask) {
        this.managedExecutorService = cleanableExecutor;
        this.shutdownTask = shutdownTask;
    }

    public Runnable getShutdownTask() {
        return shutdownTask;
    }

    public ManagedExecutorService getManagedExecutorService() {
        return managedExecutorService;
    }

}
