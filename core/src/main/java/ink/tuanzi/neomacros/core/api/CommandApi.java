package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.java.JavaInterop;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The {@code neo.command} module: client command registration and dispatch.
 * Commands register declaratively with typed argument slots built through
 * {@code command.arg} ({@code int() long() float() bool() word() string()
 * greedy() player() pos() uuid() identifier() time() literal()} plus
 * {@code optional()}, {@code default(v)} and the numeric
 * {@code range(min, max)}); the handler receives a context whose
 * {@code args} carry parsed values and whose {@code raw} keeps the
 * remainder string. This declarative surface is the single home of command
 * building: JsMacros' chain-style {@code CommandBuilder} members map onto
 * these slots, so no parallel builder grows here.
 *
 * @zh {@code neo.command}：客户端命令注册与派发。命令以声明式注册，经 {@code command.arg} 构建带类型的参数槽（{@code int() long() float() bool() word() string() greedy() player() pos() uuid() identifier() time() literal()}，另有 {@code optional()}、{@code default(v)} 与数值 {@code range(min, max)}）；处理器收到的上下文 {@code args} 携带解析值，{@code raw} 保留剩余字符串。声明式面是命令构建的唯一家：JsMacros 链式 {@code CommandBuilder} 成员映射到这些槽位，不在此生长第二套 builder。
 */
@ApiModule(name = "command", category = "界面")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class CommandApi {

    private final CommandRegistry registry;
    private final CommandArgFactory argFactory = new CommandArgFactory();

    /** Creates the API over the command registry (bootstrap-provided). */
    public CommandApi(CommandRegistry registry) {
        this.registry = registry;
    }

    /** The typed argument-slot factory ({@code command.arg}).
     * @zh 带类型的参数槽工厂（{@code command.arg}）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    @ApiType("Record<\"int\" | \"long\" | \"float\" | \"bool\" | \"word\" | \"string\""
            + " | \"greedy\" | \"player\" | \"pos\" | \"uuid\" | \"identifier\""
            + " | \"time\" | \"literal\", () => CommandArg>")
    public CommandArgFactory getArg() {
        return argFactory;
    }

    /**
     * The dynamic slot factory: any slot-kind member name builds a fresh
     * slot ({@code int() long() float() bool() word() string() greedy()
     * player() pos() uuid() identifier() time() literal()}), so
     * script-facing names never collide with Java keywords. Extra factory
     * arguments reach the slot builder ({@code literal('set')} fixes the
     * required word). The engine renders it as a dynamic member proxy.
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
            // Factory arguments pass through: literal('set') fixes the word.
            return args -> JavaInterop.wrap(CommandArg.of(name, args));
        }

        @Override
        public Object readValue(String name) {
            return null;
        }

        @Override
        public Object invoke(String name, Object[] args) {
            return JavaInterop.wrap(CommandArg.of(name, args));
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
     * @return the command name (the key for {@link #unregister})
     * @zh 注册一个客户端命令；spec 携带 name/description/args 槽位与 handler（收到解析后的上下文）。
     * @zh-return 命令名（即 {@link #unregister} 的注销键）
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiPermission("command")
    @ApiType("string")
    public Object register(@ApiType("CommandSpec") Map<String, Object> spec) {
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
     * Unregisters by the command name returned from {@link #register}.
     *
     * @zh 按 {@link #register} 返回的命令名注销。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void unregister(@ApiType("string") Object handleOrName) {
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

    /**
     * The names of every command in the client-visible command tree: server
     * commands plus client-side registrations, read as a snapshot. Empty
     * before a session opens. This is the declarative home of JsMacros'
     * {@code CommandManager.getValidCommands}.
     *
     * @zh 客户端可见命令树中的全部命令名快照：服务端命令加客户端注册命令。会话建立前为空。对应 JsMacros 的 {@code CommandManager.getValidCommands}。
     * @zh-return 命令名数组
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    @ApiType("string[]")
    public List<String> getNames() {
        return registry.names();
    }

    /**
     * Completes one partial command line against the live command tree and
     * resolves with the suggestion texts a player would see on tab. The
     * leading slash is optional; without a connection the promise resolves
     * to an empty list. This is the declarative home of JsMacros'
     * {@code CommandManager.getArgumentAutocompleteOptions}.
     *
     * @zh 对活跃命令树补全部分命令行，以玩家 Tab 时可见的建议文本数组兑现。前导斜杠可省略；无连接时以空数组兑现。对应 JsMacros 的 {@code CommandManager.getArgumentAutocompleteOptions}。
     * @zh-param commandPart 待补全的部分命令行
     * @zh-return 建议文本数组
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<string[]>")
    public CompletableFuture<List<String>> suggestions(String commandPart) {
        return registry.suggestions(commandPart);
    }

    /** Kind names the slot factory answers to (kept for tests and docs).
     * @zh 参数槽工厂应答的全部槽位类型名（供测试与文档保留）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static List<String> argKindNames() {
        return new ArrayList<>(CommandArg.KIND_NAMES);
    }

    /**
     * One client command registration spec: the command name, an optional
     * description, optional typed argument slots, and the handler receiving
     * the parsed context.
     *
     * @zh 一条客户端命令注册规格：命令名、可选描述、可选类型化参数槽与接收解析上下文的处理器。
     */
    @ApiClass(name = "CommandSpec", kind = ApiClassKind.DATA)
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record CommandSpec(
            String name,
            @ApiOptional String description,
            @ApiOptional List<CommandArg> args,
            @ApiOptional @ApiType("(ctx: CommandContext) => void")
                    java.util.function.Consumer<Object[]> handler) {
    }
}
