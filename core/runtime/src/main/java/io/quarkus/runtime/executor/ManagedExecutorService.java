package io.quarkus.runtime.executor;

import java.util.concurrent.ExecutorService;

public interface ManagedExecutorService<T extends ExecutorService> extends ExecutorService {
    void clean();

    void prefillThreads();

    T getExecutorService();
}
