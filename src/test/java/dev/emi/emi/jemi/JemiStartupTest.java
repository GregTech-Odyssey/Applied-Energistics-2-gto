package dev.emi.emi.jemi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JemiStartupTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final JemiStartup startup = new JemiStartup(executor);

    @AfterEach
    void shutdown() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void startsWhenTheLifecycleCallbackArrivesAfterEmiHasRequestedLoading() throws Exception {
        var calls = new AtomicInteger();
        var ready = startup.startAsync();
        assertFalse(ready.isDone());

        assertTrue(startup.deferStart(() -> {
            assertFalse(startup.deferStart(calls::incrementAndGet));
            calls.incrementAndGet();
        }));

        ready.get(5, TimeUnit.SECONDS);
        assertSame(ready, startup.startAsync());
        assertEquals(1, calls.get());
    }

    @Test
    void waitsForCallbacksAfterRuntimePublication() throws Exception {
        var published = new CountDownLatch(1);
        var finishCallbacks = new CountDownLatch(1);
        var callbacksFinished = new AtomicBoolean();
        startup.deferStart(() -> {
            startup.runtimeAvailable();
            published.countDown();
            await(finishCallbacks);
            callbacksFinished.set(true);
        });

        var ready = startup.startAsync();
        try {
            assertTrue(published.await(5, TimeUnit.SECONDS));
            assertFalse(ready.isDone());
        } finally {
            finishCallbacks.countDown();
        }
        ready.get(5, TimeUnit.SECONDS);
        assertTrue(callbacksFinished.get());
    }

    @Test
    void propagatesStartupFailuresAndDoesNotLaunchTheSameStartupTwice() {
        var error = new IllegalStateException("plugin callback failed");
        var calls = new AtomicInteger();
        startup.deferStart(() -> {
            calls.incrementAndGet();
            throw error;
        });

        var ready = startup.startAsync();
        var failure = assertThrows(ExecutionException.class, () -> ready.get(5, TimeUnit.SECONDS));
        assertSame(error, failure.getCause());
        assertSame(ready, startup.startAsync());
        assertEquals(1, calls.get());
    }

    @Test
    void finishesStartupThenStopsBeforeStartingTheNextConnection() throws Exception {
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var stopped = new AtomicBoolean();
        startup.deferStart(() -> {
            started.countDown();
            await(finish);
        });
        var previous = startup.startAsync();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(startup.deferStop(() -> {
                assertFalse(startup.deferStop(() -> fail("stop must not be deferred recursively")));
                startup.runtimeUnavailable();
                stopped.set(true);
            }));
            startup.deferStart(() -> assertTrue(stopped.get()));
            var next = startup.startAsync();
            assertFalse(next.isDone());
            finish.countDown();
            previous.get(5, TimeUnit.SECONDS);
            next.get(5, TimeUnit.SECONDS);
        } finally {
            finish.countDown();
        }
    }

    @Test
    void disconnectUnblocksALoadThatHasNotReceivedItsStarter() throws Exception {
        var previous = startup.startAsync();
        startup.deferStop(() -> {});
        assertThrows(CancellationException.class, () -> previous.get(5, TimeUnit.SECONDS));

        var calls = new AtomicInteger();
        startup.deferStart(calls::incrementAndGet);
        startup.startAsync().get(5, TimeUnit.SECONDS);
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotRunAQueuedStarterAfterItsConnectionHasStopped() throws Exception {
        var releaseExecutor = new CountDownLatch(1);
        executor.execute(() -> await(releaseExecutor));
        var calls = new AtomicInteger();
        startup.deferStart(calls::incrementAndGet);
        var previous = startup.startAsync();
        startup.deferStop(() -> {});
        releaseExecutor.countDown();

        assertThrows(CancellationException.class, () -> previous.get(5, TimeUnit.SECONDS));
        assertEquals(0, calls.get());
    }

    @Test
    void supportsJeisNormalLifecycleWithoutTheGtoInterceptor() throws Exception {
        startup.runtimeAvailable();
        startup.startAsync().get(5, TimeUnit.SECONDS);
        startup.runtimeUnavailable();
        assertFalse(startup.startAsync().isDone());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
