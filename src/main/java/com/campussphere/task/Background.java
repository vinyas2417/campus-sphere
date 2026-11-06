package com.campussphere.task;

import javafx.concurrent.Task;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** JDBC and exports run off the JavaFX application thread; callbacks run on it. */
public final class Background {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "campussphere-worker"); thread.setDaemon(true); return thread;
    });
    private Background() { }
    public static <T> Task<T> run(Callable<T> action, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> failure.accept(task.getException()));
        EXECUTOR.submit(task); return task;
    }
    public static void shutdown() { EXECUTOR.shutdownNow(); }
}
