package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Fabric 入口（薄层）。T-000 骨架阶段职责：
 * <ul>
 *   <li>输出 bootstrap 标记（验收断言锚点：日志 grep "NeoMacros bootstrap"）；</li>
 *   <li>注册最小 {@code /neomacros} 命令（E2E-G 用例 T000-bootstrap 的探针）。</li>
 * </ul>
 * 真正的 Bootstrap 装配（端口递入、ScriptRuntime 构建）由 T-030 落地（架构文档 03 §1）。
 */
public final class NeoMacrosFabric implements ClientModInitializer {
    public static final String MOD_ID = "neomacros";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("NeoMacros bootstrap (T-000 skeleton)");
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("neomacros").executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal("NeoMacros skeleton (T-000) is loaded."));
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
