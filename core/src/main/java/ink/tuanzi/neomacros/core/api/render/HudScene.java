package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.core.api.render.components.Draw2DElement;
import ink.tuanzi.neomacros.core.api.render.components.ElementSpec;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.port.model.DrawContext2d;
import ink.tuanzi.neomacros.port.model.WindowData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The retained scene graph behind one HUD layer: an id-keyed element table
 * whose immutable specs are re-projected by the engine every frame. Scripts
 * mutate it from any thread (volatile spec cells + a structure generation
 * counter); the render thread only reads the cached ordered snapshot and
 * replays neutral {@link DrawContext2d} primitives. No script callback ever
 * runs on the render path.
 *
 * <p>Draw order within the layer is {@code element z ascending, then
 * insertion order} (stable for equal z). Element ids are unique per layer;
 * re-upserting the same id with the same kind updates the spec in place and
 * keeps the handle and insertion slot, while a kind change replaces the
 * cell and invalidates the previous handle.</p>
 *
 * <p>The element handle reaches its cell through {@link Draw2DElement.Backend}:
 * fluent setters republish an {@link ElementSpec} snapshot (a z change
 * bumps the generation so the order rebuilds), and getters read the live
 * volatile field — the G-001 §2.2 write-then-publish path.</p>
 *
 * @zh 单个 HUD 图层背后的保留式场景图：以 id 为键的元素表，其不可变 spec 由引擎每帧重新投影。脚本可从任意线程修改（volatile spec 单元 + 结构代数计数器）；渲染线程只读取缓存的有序快照并重放中立 {@link DrawContext2d} 原语。渲染路径上永不进入脚本回调。
 * @zh 绘制序为「元素 z 升序 → 插入序」（同 z 稳定）。元素 id 在层内唯一；同 id 同类型重复 upsert 原地更新 spec 并保留句柄与插入位，类型不同则替换单元并使旧句柄失效。
 * @zh 元素句柄经 {@link Draw2DElement.Backend} 抵达其单元：fluent setter 重新发布 {@link ElementSpec} 快照（z 变更推进代数使序重建），getter 读取 volatile 现值——即 G-001 §2.2 的写入即发布路径。
 */
@ApiInternal
final class HudScene {

    /** One element cell: identity, insertion slot, and the published spec. */
    static final class Cell {
        final String id;
        final long seq;
        final ElementSpec.Kind kind;
        volatile ElementSpec spec;
        volatile boolean removed;
        volatile Draw2DElement handle;

        Cell(String id, long seq, ElementSpec.Kind kind, ElementSpec spec) {
            this.id = id;
            this.seq = seq;
            this.kind = kind;
            this.spec = spec;
        }
    }

    private final HudLayer owner;
    private final Supplier<WindowData> window;
    /** Live cells keyed by element id; guarded by {@code this}. */
    private final Map<String, Cell> cells = new LinkedHashMap<>();
    private long nextSeq;
    /** Structure generation: bumped on every add/update/remove. */
    private volatile long generation;
    private volatile Cell[] ordered = new Cell[0];
    private volatile long orderedGeneration = -1;
    private volatile boolean visible = true;
    private volatile boolean disposed;

    HudScene(HudLayer owner, Supplier<WindowData> window) {
        this.owner = owner;
        this.window = window;
    }

    /**
     * Inserts or updates the element under {@code id}. Same kind keeps the
     * cell, handle, and insertion slot; a different kind replaces the cell
     * and disposes the previous handle.
     *
     * @return the live element handle
     */
    synchronized Draw2DElement upsert(String id, ElementSpec.Kind kind,
            ElementSpec spec) {
        Cell existing = cells.get(id);
        if (existing != null && !existing.removed && existing.kind == kind) {
            existing.spec = spec;
            generation++;
            return existing.handle;
        }
        if (existing != null) {
            existing.removed = true;
            cells.remove(id);
        }
        Cell fresh = new Cell(id, nextSeq++, kind, spec);
        fresh.handle = new Draw2DElement(id, owner, backendFor(fresh));
        cells.put(id, fresh);
        generation++;
        return fresh.handle;
    }

    /**
     * The scene-side bridge handed to one handle: reads the cell's live
     * spec, republishes replacements (bumping the generation only when the
     * z-order changed, so the ordering cache stays valid), and exposes the
     * viewport for percentage geometry.
     */
    private Draw2DElement.Backend backendFor(Cell cell) {
        return new Draw2DElement.Backend() {
            @Override
            public ElementSpec spec() {
                return cell.spec;
            }

            @Override
            public void publish(ElementSpec next) {
                publishCell(cell, next);
            }

            @Override
            public int viewportWidth() {
                return width();
            }

            @Override
            public int viewportHeight() {
                return height();
            }

            @Override
            public boolean disposed() {
                return cell.removed || disposed;
            }
        };
    }

    /**
     * Republishes one cell's state from a fluent setter. Runs synchronized
     * so a z change bumps the structure generation atomically with the
     * write; a cell that died between the setter's alive-check and here
     * rejects the write (the handle turned disposed in between).
     */
    private synchronized void publishCell(Cell cell, ElementSpec next) {
        if (cell.removed || disposed) {
            throw new IllegalStateException("element no longer valid");
        }
        boolean zChanged = cell.spec.zIndex() != next.zIndex();
        cell.spec = next;
        if (zChanged) {
            generation++;
        }
    }

    /** Removes one element; its handle becomes disposed. Unknown ids are ignored. */
    synchronized void remove(String id) {
        Cell cell = cells.remove(id);
        if (cell != null) {
            cell.removed = true;
            generation++;
        }
    }

    /** Removes every element; all outstanding handles become disposed. */
    synchronized void clear() {
        if (cells.isEmpty()) {
            return;
        }
        for (Cell cell : cells.values()) {
            cell.removed = true;
        }
        cells.clear();
        generation++;
    }

    /** Marks the whole scene (and every handle) disposed; later paints are no-ops. */
    synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        clear();
    }

    /** Whether {@link #dispose()} has run. */
    boolean isDisposed() {
        return disposed;
    }

    /** Layer visibility flag: invisible layers keep their elements but paint nothing. */
    boolean isVisible() {
        return visible;
    }

    /** Sets the visibility flag without touching the port. */
    void setVisible(boolean value) {
        visible = value;
    }

    /** Current viewport width in scaled pixels ({@code 0} before the window exists). */
    int width() {
        WindowData data = window.get();
        return data == null ? 0 : data.scaledWidth();
    }

    /** Current viewport height in scaled pixels ({@code 0} before the window exists). */
    int height() {
        WindowData data = window.get();
        return data == null ? 0 : data.scaledHeight();
    }

    /** The live handle for {@code id}, or null when absent. */
    synchronized Draw2DElement element(String id) {
        Cell cell = cells.get(id);
        return cell == null || cell.removed ? null : cell.handle;
    }

    /** Handles in draw order (element z ascending, insertion order for ties). */
    List<Draw2DElement> elements() {
        List<Draw2DElement> out = new ArrayList<>();
        for (Cell cell : orderedSnapshot()) {
            out.add(cell.handle);
        }
        return List.copyOf(out);
    }

    /** Element ids in the same order as {@link #elements()}. */
    List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Cell cell : orderedSnapshot()) {
            out.add(cell.id);
        }
        return List.copyOf(out);
    }

    /**
     * Replays every visible element into {@code ctx}. Runs on the render
     * thread; reads only volatile state and the cached ordering snapshot.
     *
     * @param tick current tick count (reserved for future animation faces)
     * @param ctx  neutral draw context for this frame
     */
    void paint(int tick, DrawContext2d ctx) {
        if (disposed || !visible) {
            return;
        }
        int viewportWidth = width();
        int viewportHeight = height();
        for (Cell cell : orderedSnapshot()) {
            ElementSpec spec = cell.spec;
            if (spec == null || !spec.visible()) {
                continue;
            }
            emit(spec, viewportWidth, viewportHeight, ctx);
        }
    }

    private static void emit(ElementSpec spec, int viewportWidth, int viewportHeight,
            DrawContext2d ctx) {
        int x = spec.x().resolve(viewportWidth);
        int y = spec.y().resolve(viewportHeight);
        switch (spec.kind()) {
            case TEXT -> ctx.text(x, y, spec.text(), spec.color());
            case RECT -> ctx.rect(x, y,
                    spec.w().resolve(viewportWidth),
                    spec.h().resolve(viewportHeight),
                    spec.color());
        }
    }

    /**
     * The cached draw-order snapshot, rebuilt only when the structure
     * generation moved; a frame with no script writes allocates nothing.
     */
    private Cell[] orderedSnapshot() {
        long current = generation;
        if (orderedGeneration == current) {
            return ordered;
        }
        synchronized (this) {
            current = generation;
            if (orderedGeneration == current) {
                return ordered;
            }
            List<Cell> sorted = new ArrayList<>(cells.values());
            sorted.sort(Comparator.comparingInt((Cell cell) -> cell.spec.zIndex())
                    .thenComparingLong(cell -> cell.seq));
            Cell[] snapshot = sorted.toArray(new Cell[0]);
            ordered = snapshot;
            orderedGeneration = current;
            return snapshot;
        }
    }
}
