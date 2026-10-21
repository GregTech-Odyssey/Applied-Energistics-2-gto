package dev.emi.emi.jemi;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Coordinates JEI's lifecycle with EMI without publishing a partially initialized runtime. */
final class JemiStartup {
    private final Executor executor;
    private final ThreadLocal<Boolean> invoking = ThreadLocal.withInitial(() -> false);
    private Load current = new Load();

    JemiStartup(Executor executor) {
        this.executor = executor;
    }

    synchronized boolean deferStart(Runnable starter) {
        if (invoking.get())
            return false;
        if (!current.scheduled) {
            current.starter = starter;
            if (current.requested)
                launch(current);
        }
        return true;
    }

    synchronized CompletableFuture<Void> startAsync() {
        current.requested = true;
        if (!current.scheduled && current.starter != null)
            launch(current);
        return current.ready;
    }

    private void launch(Load load) {
        Runnable starter = load.starter;
        load.starter = null;
        load.scheduled = true;
        executor.execute(() -> {
            try {
                if (load.stopped)
                    throw new CancellationException("JEI startup was stopped");
                invoke(starter);
                load.ready.complete(null);
            } catch (Throwable error) {
                load.ready.completeExceptionally(error);
            }
        });
    }

    synchronized void runtimeAvailable() {
        // Without the GTO startup interceptor, JEI retains its normal lifecycle.
        if (!invoking.get())
            current.ready.complete(null);
    }

    synchronized boolean deferStop(Runnable stopper) {
        if (invoking.get())
            return false;
        retire();
        // The same executor finishes startup before stopping JEI, including during disconnect.
        executor.execute(() -> invoke(stopper));
        return true;
    }

    synchronized void runtimeUnavailable() {
        if (!invoking.get())
            retire();
    }

    private void retire() {
        current.stopped = true;
        current.starter = null;
        if (!current.scheduled)
            current.ready.completeExceptionally(new CancellationException("JEI startup was stopped"));
        current = new Load();
    }

    private void invoke(Runnable action) {
        invoking.set(true);
        try {
            action.run();
        } finally {
            invoking.remove();
        }
    }

    private static final class Load {
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private Runnable starter;
        private boolean requested;
        private boolean scheduled;
        private volatile boolean stopped;
    }
}
