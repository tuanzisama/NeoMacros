package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionError;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.core.security.PermissionRequests;
import ink.tuanzi.neomacros.core.security.TrustManager;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code neo.permissions} module: the script-facing permission meta API.
 * {@code checkPermission} reports whether the trust gate currently allows a
 * capability; {@code requestPermission} asks the player to grant one of the
 * script's declared capabilities at runtime, resolving true when granted
 * (the grant persists trust for the whole script and applies to the running
 * script immediately) and false when denied or dismissed. Requesting a
 * capability the manifest does not declare rejects the promise — the
 * declaration is the ceiling, never bypassed.
 *
 * @zh {@code neo.permissions}：脚本侧权限元 API。checkPermission 查询信任门当前是否放行某个能力；requestPermission 在运行时请玩家授予 manifest 已声明的能力之一——授予成功解析 true（持久信任整个脚本并立即对运行中脚本生效），拒绝或关闭解析 false。请求未声明的能力会 reject：声明即上限，永不绕过。
 */
@ApiModule(name = "permissions", category = "系统")
@ApiSince("0.6.0")
@ApiMC("*")
@ApiLoader("*")
public final class PermissionsApi {

    private final PermissionRequests requests;

    /** Creates the facade over the runtime request orchestrator. */
    public PermissionsApi(PermissionRequests requests) {
        this.requests = requests;
    }

    /**
     * Returns whether the trust gate currently allows the capability for
     * this script. Unknown capability names return false. A script that
     * declares nothing runs under an all-allowing view, so every known
     * capability reports true.
     *
     * @zh 返回信任门当前是否为本脚本放行该能力。未知能力名返回 false。未声明任何权限的脚本运行在全放行视图下，所有已知能力都返回 true。
     * @zh-param capability 能力名（fs / net / command / java / packet）
     */
    @ApiSince("0.6.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean checkPermission(String capability) {
        String cap = requireCapability(capability);
        if (!TrustManager.CAPABILITIES.contains(cap)) {
            return false;
        }
        // Outside a script context (the DevTools console) the binding tree
        // was built on an all-allowing view, so report what the gate does.
        LoadedModule owner = LoadTracker.current();
        PermissionView view = owner != null ? owner.permissionView() : PermissionView.ALLOW_ALL;
        return view != null && view.allows(cap);
    }

    /**
     * Asks the player to grant one declared capability at runtime. Already
     * granted capabilities resolve immediately without a prompt. An
     * ungranted request surfaces a confirmation dialog (a clickable chat
     * line on older versions); allowing it persists trust for the whole
     * script (exactly the trust command's decision) and applies to the
     * running script without a reload. Resolves true when granted, false
     * when denied, dismissed, or the script unloaded while pending.
     * Rejects with a permission error when the manifest does not declare
     * the capability or the name is unknown.
     *
     * @zh 运行时请玩家授予一个已声明的能力。已授予的能力立即解析（不弹窗）。未授予的请求会弹出确认对话框（旧版本回退为聊天栏可点击行）；同意即持久信任整个脚本（与 trust 命令同一决定）并立即对运行中脚本生效（不重启）。授予解析 true；拒绝、关闭或脚本卸载解析 false。请求未声明或未知的能力时 reject。
     * @zh-param capability 能力名（fs / net / command / java / packet）
     */
    @ApiSince("0.6.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CompletableFuture<Boolean> requestPermission(String capability) {
        String cap = requireCapability(capability);
        LoadedModule owner = LoadTracker.current();
        if (owner == null) {
            throw new IllegalStateException(
                    "requestPermission requires a script context (not available in the console)");
        }
        if (!TrustManager.CAPABILITIES.contains(cap)) {
            throw new PermissionError("unknown permission '" + cap + "'");
        }
        List<String> declared = owner.entry().manifest().permissions();
        if (declared == null || !declared.contains(cap)) {
            throw new PermissionError("permission '" + cap
                    + "' is not declared in this script's meta.permissions");
        }
        PermissionView view = owner.permissionView();
        if (view != null && view.allows(cap)) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        String hash = owner.contentHash();
        if (hash == null) {
            throw new PermissionError(
                    "content hash unavailable; cannot grant permission '" + cap + "'");
        }
        return requests.submit(new PermissionRequests.PermissionRequest(
                UUID.randomUUID().toString(), owner.entry().name(), hash, cap, declared, true,
                owner.entry().manifest().description()));
    }

    /** Normalizes one capability argument (trimmed, lowercased, non-empty). */
    private static String requireCapability(String capability) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("capability is required");
        }
        return capability.trim().toLowerCase(Locale.ROOT);
    }
}
