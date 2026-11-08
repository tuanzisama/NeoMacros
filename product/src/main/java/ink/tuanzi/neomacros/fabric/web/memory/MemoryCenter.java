package ink.tuanzi.neomacros.fabric.web.memory;

import ink.tuanzi.neomacros.core.config.CoreOptions;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.task.TaskRegistry;
import ink.tuanzi.neomacros.fabric.web.v1.JvmMemory;
import ink.tuanzi.neomacros.fabric.web.v1.MemorySample;
import ink.tuanzi.neomacros.fabric.web.v1.ScriptMemory;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The memory sampler behind the panel's memory surfaces: on a fixed tick it
 * reads the JVM heap from the {@code MemoryMXBean} and, per loaded script,
 * the JDK per-thread allocation counter of the script's event-loop thread
 * (every GraalJS operation of a script runs on that thread, so its counter
 * is the per-script allocation signal). Values are pushed into the replay
 * ring and mirrored to the panel via the {@code memory:sample} SSE event;
 * active tasks get their last-known value for the task-manager column.
 *
 * <p>The sampler runs only while a panel is connected (the {@code auto}
 * mode); {@code always} and {@code off} override that per {@code core.web}.
 * memorySampleIntervalMs. Sampling is observability only — nothing here
 * enforces limits.</p>
 */
public final class MemoryCenter implements AutoCloseable {

    /** Minimum tick the scheduler runs at; the configured interval gates sampling. */
    private static final long TICK_MILLIS = 250;

    /** The pseudo script name/category of the shared DevTools session. */
    private static final String DEVTOOLS = "devtools";

    private final ModuleManager modules;
    private final TaskRegistry tasks;
    private final CoreOptions options;
    private ScheduledExecutorService executor;
    private com.sun.management.ThreadMXBean threadBean;
    private boolean threadBeanResolved;
    private volatile SampleSink sink;
    private volatile BooleanSupplier online = () -> false;
    private long lastSampleMillis;

    /** The sample sink: the replay ring's append (which assigns the seq). */
    public interface SampleSink {
        void accept(long ts, JvmMemory jvm, long graaljsAllocated, List<ScriptMemory> scripts);
    }

    /** Creates the sampler over the live modules, task registry, and options. */
    public MemoryCenter(ModuleManager modules, TaskRegistry tasks, CoreOptions options) {
        this.modules = modules;
        this.tasks = tasks;
        this.options = options;
    }

    /**
     * Starts sampling into the given sink (the replay ring, whose listener
     * mirrors to SSE) while the online gate reports a connected panel. Each
     * service start gets a fresh scheduler thread.
     */
    public synchronized void start(SampleSink sink, BooleanSupplier online) {
        close();
        this.sink = sink;
        this.online = online;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "neomacros-memory");
                    thread.setDaemon(true);
                    return thread;
                });
        this.executor = scheduler;
        scheduler.scheduleWithFixedDelay(this::tick, TICK_MILLIS, TICK_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    /** Stops sampling; the sink and gate detach. */
    @Override
    public synchronized void close() {
        ScheduledExecutorService scheduler = executor;
        executor = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        sink = null;
        online = () -> false;
    }

    /** One sampler tick: gate, read, push. */
    private void tick() {
        try {
            SampleSink current = sink;
            if (current == null) {
                return;
            }
            String mode = options.web.memorySampling == null
                    ? "auto" : options.web.memorySampling.toLowerCase(java.util.Locale.ROOT);
            if (mode.equals("off")) {
                return;
            }
            if (mode.equals("auto") && !online.getAsBoolean()) {
                return;
            }
            long interval = clampInterval(options.web.memorySampleIntervalMs);
            long now = System.currentTimeMillis();
            if (now - lastSampleMillis < interval) {
                return;
            }
            lastSampleMillis = now;
            sample(now, current);
        } catch (Throwable ignored) {
            // A failing tick must never take the sampler thread down.
        }
    }

    /** Reads the heap and every live script thread into one sample and pushes it. */
    private void sample(long now, SampleSink sink) {
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        JvmMemory jvm = new JvmMemory(heap.getUsed(), heap.getCommitted(), heap.getMax());

        List<ScriptMemory> scripts = new ArrayList<>();
        for (LoadedModule module : modules.loaded()) {
            long bytes = threadAllocatedBytes(module.context());
            if (bytes < 0) {
                continue;
            }
            String category = module.entry().category().dirName();
            scripts.add(new ScriptMemory(module.taskId(), module.meta().name(),
                    category, bytes, now));
            if (tasks != null) {
                tasks.updateTaskMemory(module.taskId(), bytes);
            }
        }
        // The shared DevTools session surfaces as a pseudo script row.
        modules.devToolsContext().ifPresent(context -> {
            long bytes = threadAllocatedBytes(context);
            if (bytes >= 0) {
                scripts.add(new ScriptMemory(0, DEVTOOLS, DEVTOOLS, bytes, now));
            }
        });
        scripts.sort(Comparator.comparingLong(ScriptMemory::taskId));

        long total = 0;
        for (ScriptMemory script : scripts) {
            total += script.bytes();
        }
        sink.accept(now, jvm, total, scripts);
    }

    /** The thread's cumulative allocated bytes; -1 when unsupported or gone. */
    private long threadAllocatedBytes(ScriptContext context) {
        com.sun.management.ThreadMXBean bean = threadBean();
        if (bean == null) {
            return -1;
        }
        long threadId = context.threadId();
        if (threadId <= 0) {
            return -1;
        }
        try {
            return bean.getThreadAllocatedBytes(threadId);
        } catch (RuntimeException e) {
            // a dead thread or an unsupported reading: report unknown
            return -1;
        }
    }

    /** Resolves the HotSpot per-thread allocation interface once. */
    private synchronized com.sun.management.ThreadMXBean threadBean() {
        if (!threadBeanResolved) {
            threadBeanResolved = true;
            java.lang.management.ThreadMXBean plain = ManagementFactory.getThreadMXBean();
            if (plain instanceof com.sun.management.ThreadMXBean managed
                    && managed.isThreadAllocatedMemorySupported()) {
                managed.setThreadAllocatedMemoryEnabled(true);
                threadBean = managed;
            }
        }
        return threadBean;
    }

    /** The configured sampling interval, clamped to sane bounds. */
    private static long clampInterval(int configured) {
        if (configured < 250) {
            return 250;
        }
        return Math.min(configured, 60_000);
    }
}
