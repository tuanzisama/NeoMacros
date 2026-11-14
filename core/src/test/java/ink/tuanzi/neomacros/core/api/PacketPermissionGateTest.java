package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.ModuleBinding;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionError;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.api.client.ClientApi;
import ink.tuanzi.neomacros.core.api.types.PacketBuffer;
import ink.tuanzi.neomacros.core.security.TrustManager;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packet trust gate (G-002 §5 方案甲): {@code client.sendPacket} stays
 * behind {@code java} at the binding layer, {@code buffer.send} declares
 * {@code packet}, the capability joins {@link TrustManager#CAPABILITIES},
 * and the ungated builder/ids members stay callable even under a
 * denying view.
 */
class PacketPermissionGateTest {

    private ClientApi api;

    @BeforeEach
    void install() {
        ScriptApiEnvironment.install(null,
                new ClientGapExpansionsTest.SinglePortAdapter(
                        new ClientGapExpansionsTest.CannedClient()));
        api = new ClientApi();
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void sendPacketIsJavaGatedAtTheBindingLayer() throws Throwable {
        BindingRegistry registry = new BindingRegistry("26.3");
        registry.register(ClientApi.class);

        ModuleBinding denied = (ModuleBinding) registry
                .neoTree(PermissionView.DENY_ALL).get("client");
        ModuleBinding.DeniedMember deniedSend = (ModuleBinding.DeniedMember)
                denied.members().get("sendPacket");
        assertEquals("java", deniedSend.permission());
        assertThrows(PermissionError.class, deniedSend::invoke,
                "an undeclared java capability raises PermissionError");

        ModuleBinding granted = (ModuleBinding) registry
                .neoTree(PermissionView.of(Set.of("java"))).get("client");
        assertInstanceOf(ModuleBinding.FunctionMember.class,
                granted.members().get("sendPacket"));

        // The packet face members carry no permission of their own.
        ModuleBinding stillDenied = (ModuleBinding) registry
                .neoTree(PermissionView.DENY_ALL).get("client");
        assertInstanceOf(ModuleBinding.FunctionMember.class,
                stillDenied.members().get("createPacketBuffer"),
                "builder construction is ungated (pure data)");
        assertInstanceOf(ModuleBinding.PropertyMember.class,
                stillDenied.members().get("packetIds"),
                "the packet id catalog is ungated (read-only fact)");
    }

    @Test
    void bufferSendDeclaresThePacketCapability() throws Exception {
        Method send = PacketBuffer.class.getMethod("send", String.class);
        assertEquals("packet", send.getAnnotation(ApiPermission.class).value(),
                "buffer.send gates byte-on-wire behind the packet key");
        Method toPacket = PacketBuffer.class.getMethod("toPacket",
                String.class, String.class);
        assertEquals("java", toPacket.getAnnotation(ApiPermission.class).value(),
                "toPacket hands out raw objects behind the java key");
        Method create = ClientApi.class.getMethod("createPacketBuffer");
        assertNull(create.getAnnotation(ApiPermission.class));
        assertTrue(TrustManager.CAPABILITIES.contains("packet"),
                "the capability joins the directory (G-002 §5.2 方案甲)");
    }

    @Test
    void createPacketBufferBuildsAFreshWritableBuilder() {
        PacketBuffer buffer = api.createPacketBuffer();
        assertNotNull(buffer);
        assertEquals(0, buffer.getWriterIndex());
        buffer.writeInt(5);
        assertEquals(4, buffer.getWriterIndex());
    }
}
