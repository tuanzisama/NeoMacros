package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code neo.command} facade: client command registration and dispatch.
 * Commands register declaratively; the handler receives the parsed argument
 * string after the command name. Argument DSL (typed parsing) lands with the
 * command-subsystem increment; handlers receive {@code args} as the raw
 * remainder string meanwhile.
 */
@ApiModule(name = "command", category = "界面")
public final class CommandApi {

    private final CommandRegistry registry;

    /** Creates the facade over the command registry (bootstrap-provided). */
    public CommandApi(CommandRegistry registry) {
        this.registry = registry;
    }

    /**
     * Registers a client command. The spec map carries {@code name},
     * {@code description} and {@code handler} (args as the raw remainder
     * string; nullable).
     *
     * @return an opaque registration handle (unregister via {@link #unregister})
     * @zh 注册一个客户端命令；spec 携带 name/description/handler。
     * @zh-return 注销用的不透明句柄
     */
    @ApiSince("0.1.0")
    @ApiPermission("command")
    public Object register(Map<String, Object> spec) {
        return registry.register(spec);
    }

    /**
     * Unregisters by the handle returned from {@link #register}, or by
     * command name when a string is given.
     *
     * @zh 按句柄或命令名注销。
     */
    @ApiSince("0.1.0")
    public void unregister(Object handleOrName) {
        if (handleOrName instanceof String name) {
            registry.unregister(name);
        }
    }

    /**
     * Executes a client command as if typed by the player; dispatches through
     * the vanilla dispatcher on the main thread. The promise settles with
     * whether the command was found and executed.
     *
     * @zh 以玩家身份执行客户端命令（走原版派发器，主线程执行）。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<boolean>")
    @ApiPermission("command")
    public CompletableFuture<Boolean> execute(String commandLine) {
        return registry.execute(commandLine);
    }

}
