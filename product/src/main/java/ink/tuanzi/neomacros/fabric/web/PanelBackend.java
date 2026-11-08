package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.core.runtime.EvalOutcome;
import ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse;
import ink.tuanzi.neomacros.fabric.web.v1.FilesResponse;
import ink.tuanzi.neomacros.fabric.web.v1.MacroView;
import ink.tuanzi.neomacros.fabric.web.v1.Meta;
import ink.tuanzi.neomacros.fabric.web.v1.StateSnapshot;
import ink.tuanzi.neomacros.fabric.web.v1.TasksResponse;

/**
 * The mod-side capabilities the web panel exposes, implemented by the
 * fabric layer over the module manager and config. Game-state methods run
 * on the game main thread (the controller dispatches them there and maps a
 * timeout to 504); everything else is plain thread-safe state.
 */
public interface PanelBackend {

    /** One DevTools console evaluation: the owning macro name plus the outcome. */
    record EvalExecution(String macro, EvalOutcome outcome) {
    }

    /** The version-negotiation probe payload. */
    Meta meta();

    /**
     * The full state snapshot. Must be called on the game main thread; may
     * reject actions with an {@code ApiError}.
     */
    StateSnapshot snapshot();

    /**
     * Runs one macro lifecycle action ({@code enable/disable/start/stop/
     * reload}). Must be called on the game main thread.
     */
    MacroView macroAction(String id, String action);

    /**
     * The task-manager snapshot: active tasks plus the bounded history.
     * Plain registry state, safe on any thread.
     */
    TasksResponse tasks();

    /**
     * Stops one task by id (unloads its macro). Must be called on the game
     * main thread; unknown ids are a 404, stopped tasks a 409.
     */
    void stopTask(long taskId);

    /**
     * Reads one file under the macros root. Pure file IO (no game state),
     * safe on any thread; rejects paths that escape the root with 403 and
     * missing files with 404.
     */
    FileContent readFile(String path);

    /**
     * Lists one macro folder's files as folder-relative POSIX paths (the
     * manifest first, then lexicographic). Pure file IO and discovery, safe
     * on any thread; unknown ids are a 404 and a folder outside the macros
     * root is a 403.
     */
    FilesResponse listFiles(String id);

    /**
     * Writes one file with optimistic concurrency: a non-matching
     * {@code If-Match} yields the conflict shape instead of a save. A
     * successful save hot-reloads the owning macro and reports its
     * structured error. Must be called on the game main thread.
     */
    FileWriteResult writeFile(String path, byte[] content, String ifMatch);

    /**
     * Deletes one file or directory tree under the macros root. Confined to
     * macro folders; any macro's manifest and the macro folder itself are
     * protected. Must be called on the game main thread (hot-reloads the
     * owner).
     */
    void deleteFile(String path);

    /**
     * Moves or renames one file or directory within a single macro folder.
     * The target must not exist; a file target must pass the write
     * whitelist. Must be called on the game main thread (hot-reloads the
     * owner).
     */
    void moveFile(String from, String to);

    /**
     * Creates a directory (with parents) inside one macro folder. Pure file
     * IO, safe on any thread; an existing target is a conflict.
     */
    void makeDirectory(String path);

    /**
     * Reveals one existing path under the macros root in the system file
     * manager of the game machine. Pure IO and process spawn, safe on any
     * thread; malformed paths are 422, escapes 403, missing targets 404.
     */
    void reveal(String path);

    /**
     * Creates a macro scaffold (manifest + entry template) in a category.
     * Must be called on the game main thread.
     */
    MacroView createMacro(String category, String name, String description);

    /**
     * Moves one macro folder to another category (identity, persisted
     * state, and key bindings follow the manifest name). Must be called on
     * the game main thread.
     */
    MacroView moveMacro(String from, String to);

    /**
     * Deletes one macro (folder, loaded instance, and persisted state).
     * Must be called on the game main thread.
     */
    void deleteMacro(String id);

    /**
     * Evaluates one DevTools console (REPL) expression. {@code target}
     * {@code devtools} runs in the shared devtools session; any other value
     * names a loaded macro whose context (and bindings) the expression
     * runs in. Guest-level failures (the expression threw) come back inside
     * the outcome; unknown targets are a 404 and loop timeouts surface as a
     * 504 {@code eval_timeout} {@code ApiError}. Plain thread-safe: the
     * engine funnels the work onto the target context loop itself.
     */
    EvalExecution evaluateConsole(String target, String expression);

    /**
     * The subscribable event catalog; plain thread-safe (catalog reads only).
     */
    EventsCatalogResponse eventCatalog();
}
