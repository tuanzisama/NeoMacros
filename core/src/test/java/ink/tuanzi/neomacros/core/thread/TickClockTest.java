package ink.tuanzi.neomacros.core.thread;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tick clock semantics: waiters fire at their boundary, counting is exact,
 * and tick(n) resolves through the context loop.
 */
class TickClockTest {

    private ScriptThreadPool pool;
    private ContextEventLoop loop;
    private TickClock clock;

    @BeforeEach
    void setUp() {
        pool = new ScriptThreadPool(1);
        loop = pool.openLoop("ticktest");
        clock = new TickClock();
    }

    @Test
    void tickCountingIsExact() {
        assertEquals(0, clock.currentTick());
        clock.tick();
        clock.tick();
        assertEquals(2, clock.currentTick());
    }

    @Test
    void ticksCompletesAfterBoundariesOnLoopThread() throws Exception {
        CompletableFuture<Void> done = clock.ticks(loop, 3);
        clock.tick();
        clock.tick();
        assertTrue(!done.isDone(), "not yet due");
        clock.tick();
        done.get(2, TimeUnit.SECONDS);
        // Completion ran on the loop thread.
        CompletableFuture<String> threadName = new CompletableFuture<>();
        loop.execute(() -> threadName.complete(Thread.currentThread().getName()));
        assertEquals(loop.threadName(), threadName.get(2, TimeUnit.SECONDS));
    }

    @Test
    void nextTickResolvesAtNextBoundary() throws Exception {
        CompletableFuture<Void> done = clock.nextTick(loop);
        clock.tick();
        done.get(2, TimeUnit.SECONDS);
    }

    @Test
    void multipleWaitersSameBoundaryAllFire() throws Exception {
        CompletableFuture<Void> a = clock.ticks(loop, 1);
        CompletableFuture<Void> b = clock.ticks(loop, 1);
        clock.tick();
        CompletableFuture.allOf(a, b).get(2, TimeUnit.SECONDS);
        assertTrue(a.isDone());
        assertTrue(b.isDone());
    }

    @Test
    void nonPositiveTicksCompletesImmediately() throws Exception {
        CompletableFuture<Void> done = clock.ticks(loop, 0);
        done.get(2, TimeUnit.SECONDS);
    }

    @Test
    void atTickWithPastBoundaryRunsInline() {
        clock.tick();
        AtomicInteger ran = new AtomicInteger();
        clock.atTick(clock.currentTick(), ran::incrementAndGet);
        assertEquals(1, ran.get());
    }
}
