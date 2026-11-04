package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.java.JavaInterop;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The {@code neo.command} facade: client command registration and dispatch.
 * Commands register declaratively with typed argument slots built through
 * {@code command.arg} ({@code int() float() bool() word() string() greedy()
 * player() pos()} plus {@code optional()} and {@code default(v)}); the
 * handler receives a context whose {@code args} carry parsed values and
 * whose {@code raw} keeps the remainder string.
 *
 * @zh {@code neo.command} 门面：客户端命令注册与派发。命令以声明式注册，经 {@code command.arg} 构建带类型的参数槽（{@code int() float() bool() word() string() greedy() player() pos()}，另有 {@code optional()} 与 {@code default(v)}）；处理器收到的上下文 {@code args} 携带解析值，{@code raw} 保留剩余字符串。
 */
@ApiModule(name = "command", category = "界面")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class CommandApi {

    private final CommandRegistry registry;
    private final CommandArgFactory argFactory = new CommandArgFactory();

    /** Creates the facade over the command registry (bootstrap-provided). */
    public CommandApi(CommandRegistry registry) {
        this.registry = registry;
    }

    /** The typed argument-slot factory ({@code command.arg}).
     * @zh 带类型的参数槽工厂（{@code command.arg}）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public CommandArgFactory getArg() {
        return argFactory;
    }

    /**
     * The dynamic slot factory: any slot-kind member name builds a fresh
     * slot ({@code int() float() bool() word() string() greedy() player()
     * pos()}), so script-facing names never collide with Java keywords. The
     * engine renders it as a dynamic member proxy.
     */
    public static final class CommandArgFactory implements JavaInterop {

        @Override
        public String className() {
            return CommandArgFactory.class.getName();
        }

        @Override
        public boolean hasMember(String name) {
            return CommandArg.KIND_NAMES.contains(name);
        }

        @Override
        public java.util.function.Function<Object[], Object> readCallable(String name) {
            if (!hasMember(name)) {
                return null;
            }
            return args -> JavaInterop.wrap(CommandArg.of(name));
        }

        @Override
        public Object readValue(String name) {
            return null;
        }

        @Override
        public Object invoke(String name, Object[] args) {
            return JavaInterop.wrap(CommandArg.of(name));
        }

        @Override
        public void write(String name, Object value) {
            throw new IllegalStateException("command.arg is read-only");
        }

        @Override
        public boolean instanceOf(String className) {
            return className.equals(CommandArgFactory.class.getName());
        }

        @Override
        public Object rawValue() {
            return this;
        }
    }

    /**
     * Registers a client command. The spec map carries {@code name},
     * {@code description}, optional {@code args} slots and the
     * {@code handler} receiving the parsed context.
     *
     * @return an opaque registration handle (unregister via {@link #unregister})
     * @zh 注册一个客户端命令；spec 携带 name/description/args 槽位与 handler（收到解析后的上下文）。
     * @zh-return 注销用的不透明句柄
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiPermission("command")
    public Object register(Map<String, Object> spec) {
        Map<String, Object> prepared = new LinkedHashMap<>(spec);
        Map<String, CommandArg> slots = CommandArg.slotsFrom(spec.get("args"));
        if (spec.get("handler") instanceof Consumer<?> consumer) {
            Consumer<Object[]> original = (Consumer<Object[]>) consumer;
            prepared.put("handler", (Consumer<Object[]>) args -> {
                String remainder = args != null && args.length > 0
                        && args[0] instanceof String text ? text : "";
                Map<String, Object> parsed = CommandArg.parseAll(slots, remainder);
                original.accept(new Object[]{new CommandContext(parsed, remainder)});
            });
        }
        return registry.register(prepared);
    }

    /**
     * Unregisters by the handle returned from {@link #register}, or by
     * command name when a string is given.
     *
     * @zh 按句柄或命令名注销。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<boolean>")
    @ApiPermission("command")
    public CompletableFuture<Boolean> execute(String commandLine) {
        return registry.execute(commandLine);
    }

    /** Kind names the slot factory answers to (kept for tests and docs).
     * @zh 参数槽工厂应答的全部槽位类型名（供测试与文档保留）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static List<String> argKindNames() {
        return new ArrayList<>(CommandArg.KIND_NAMES);
    }
}
