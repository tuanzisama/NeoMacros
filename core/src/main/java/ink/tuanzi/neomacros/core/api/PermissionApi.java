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
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code neo.permission} module: the script-facing permission meta API.
 * {@code checkPermission} reports whether the trust gate currently allows a
 * capability (or, for a list, every one of them); {@code requestPermission}
 * asks the player to grant declared capabilities at runtime, resolving true
 * when granted (the grant persists trust for the whole script and applies to
 * the running script immediately) and false when denied or dismissed.
 * Requesting a capability the manifest does not declare rejects the promise
 * — the declaration is the ceiling, never bypassed.
 *
 * @zh {@code neo.permission}：脚本侧权限元 API。checkPermission 查询信任门当前是否放行某个能力（传数组则要求全部放行）；requestPermission 在运行时请玩家授予 manifest 已声明的能力——授予成功解析 true（持久信任整个脚本并立即对运行中脚本生效），拒绝或忽略解析 false。请求未声明的能力会 reject：声明即上限，永不绕过。
 */
@ApiModule(name = "permission", category = "系统")
@ApiSince("0.6.0")
@ApiMC("*")
@ApiLoader("*")
public final class PermissionApi {

    private final PermissionRequests requests;

    /** Creates the facade over the runtime request orchestrator. */
    public PermissionApi(PermissionRequests requests) {
        this.requests = requests;
    }

    /**
     * Returns whether the trust gate currently allows the capability — or,
     * when a list is given, every one of them. Unknown capability names
     * return false. A script that declares nothing runs under an
     * all-allowing view, so every known capability reports true.
     *
     * @zh 返回信任门当前是否为本脚本放行该能力——传数组时要求全部放行。未知能力名返回 false。未声明任何权限的脚本运行在全放行视图下，所有已知能力都返回 true。
     * @zh-param capability 能力名或能力名数组（fs / net / command / java / packet）
     */
    @ApiSince("0.6.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean checkPermission(@ApiType("string | string[]") Object capability) {
        List<String> caps = normalizeCapabilities(capability);
        for (String cap : caps) {
            if (!TrustManager.CAPABILITIES.contains(cap)) {
                return false;
            }
            // Outside a script context (the DevTools console) the binding
            // tree was built on an all-allowing view, so report what the
            // gate does.
            LoadedModule owner = LoadTracker.current();
            PermissionView view = owner != null ? owner.permissionView() : PermissionView.ALLOW_ALL;
            if (view == null || !view.allows(cap)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Asks the player to grant declared capabilities at runtime — one name
     * or a list of them (the prompt then covers the whole list). Already
     * granted capabilities resolve immediately without a prompt. An
     * ungranted request surfaces a confirmation dialog (a clickable chat
     * line on older versions); allowing it persists trust for the whole
     * script (exactly the trust command's decision) and applies to the
     * running script without a reload. Resolves true when granted, false
     * when denied, ignored, or the script unloaded while pending. Rejects
     * with a permission error when the manifest does not declare one of the
     * capabilities or a name is unknown.
     *
     * @zh 运行时请玩家授予已声明的能力——单个名称或数组（弹窗覆盖整组）。已授予的能力立即解析（不弹窗）。未授予的请求会弹出确认对话框（旧版本回退为聊天栏可点击行）；同意即持久信任整个脚本（与 trust 命令同一决定）并立即对运行中脚本生效（不重启）。授予解析 true；拒绝、忽略或脚本卸载解析 false。请求未声明或未知的能力时 reject。
     * @zh-param capability 能力名或能力名数组（fs / net / command / java / packet）
     */
    @ApiSince("0.6.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CompletableFuture<Boolean> requestPermission(
            @ApiType("string | string[]") Object capability) {
        List<String> caps = normalizeCapabilities(capability);
        LoadedModule owner = LoadTracker.current();
        if (owner == null) {
            throw new IllegalStateException(
                    "requestPermission requires a script context (not available in the console)");
        }
        for (String cap : caps) {
            if (!TrustManager.CAPABILITIES.contains(cap)) {
                throw new PermissionError("unknown permission '" + cap + "'");
            }
        }
        List<String> declared = owner.entry().manifest().permissions();
        for (String cap : caps) {
            if (declared == null || !declared.contains(cap)) {
                throw new PermissionError("permission '" + cap
                        + "' is not declared in this script's meta.permissions");
            }
        }
        PermissionView view = owner.permissionView();
        boolean allAllowed = view != null
                && caps.stream().allMatch(view::allows);
        if (allAllowed) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        String hash = owner.contentHash();
        if (hash == null) {
            throw new PermissionError("content hash unavailable; cannot grant permission");
        }
        return requests.submit(new PermissionRequests.PermissionRequest(
                UUID.randomUUID().toString(), owner.entry().name(), hash, caps, declared,
                true, owner.entry().manifest().description()));
    }

    /**
     * Normalizes the capability argument: one name or a list of names
     * (trimmed, lowercased, de-duplicated in order). Blank or empty input is
     * a caller error.
     */
    private static List<String> normalizeCapabilities(Object capability) {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        if (capability instanceof List<?> list) {
            for (Object item : list) {
                addCapability(caps, item);
            }
        } else {
            addCapability(caps, capability);
        }
        if (caps.isEmpty()) {
            throw new IllegalArgumentException("capability list is empty");
        }
        return new ArrayList<>(caps);
    }

    private static void addCapability(LinkedHashSet<String> caps, Object item) {
        if (item == null || String.valueOf(item).isBlank()) {
            throw new IllegalArgumentException("capability is required");
        }
        caps.add(String.valueOf(item).trim().toLowerCase(Locale.ROOT));
    }
}
