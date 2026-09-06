package autismclient.security;

import autismclient.util.AutismConfig;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AutismProtectorChannelFilterTest {

    @AfterEach
    void resetProtectorState() {

        AutismProtector.publishRuntimeState(null);
    }

    private static void publishProtector(boolean vanillaMode) {
        AutismConfig config = new AutismConfig();
        config.protectorEnabled = true;
        config.protectorFilterChannels = true;
        config.spoofClientVanilla = vanillaMode;
        AutismProtector.publishRuntimeState(config);
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static AutismProtectorChannelFilter.Verdict filterRegister(List<Identifier> channels) {
        RegistrationPayload payload = new RegistrationPayload(RegistrationPayload.REGISTER, channels);
        return AutismProtectorChannelFilter.filter(new ServerboundCustomPayloadPacket(payload));
    }

    @Test
    void mixedRegisterKeepsWhitelistedChannelsInOriginalOrder() {
        publishProtector(false);
        List<Identifier> channels = List.of(
            id("evilmod", "secret"),
            id("minecraft", "brand"),
            id("cheatclient", "ping"),
            id("c", "version")
        );
        AutismProtectorChannelFilter.Verdict verdict = filterRegister(channels);
        assertEquals(AutismProtectorChannelFilter.Verdict.Kind.REPLACE, verdict.kind);
        ServerboundCustomPayloadPacket replacement =
            assertInstanceOf(ServerboundCustomPayloadPacket.class, verdict.replacement);
        RegistrationPayload rebuilt = assertInstanceOf(RegistrationPayload.class, replacement.payload());
        assertSame(RegistrationPayload.REGISTER, rebuilt.type());
        assertEquals(List.of(id("minecraft", "brand"), id("c", "version")), rebuilt.channels());
    }

    @Test
    void fullyWhitelistedRegisterPassesUnchanged() {
        publishProtector(false);
        AutismProtectorChannelFilter.Verdict verdict =
            filterRegister(List.of(id("minecraft", "brand"), id("c", "version")));
        assertEquals(AutismProtectorChannelFilter.Verdict.Kind.PASS, verdict.kind);
    }

    @Test
    void fullyNonWhitelistedRegisterIsDropped() {
        publishProtector(false);
        AutismProtectorChannelFilter.Verdict verdict =
            filterRegister(List.of(id("evilmod", "secret"), id("cheatclient", "ping")));
        assertEquals(AutismProtectorChannelFilter.Verdict.Kind.DROP, verdict.kind);
    }

    @Test
    void vanillaModeStillDropsTheWholeRegister() {
        publishProtector(true);
        AutismProtectorChannelFilter.Verdict verdict =
            filterRegister(List.of(id("minecraft", "brand")));
        assertEquals(AutismProtectorChannelFilter.Verdict.Kind.DROP, verdict.kind);
    }

    public static final class CustomRegisterPayload implements CustomPacketPayload {
        public static final Type<CustomRegisterPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("minecraft", "register"));

        private final List<Identifier> channels;

        CustomRegisterPayload(List<Identifier> channels) {
            this.channels = channels;
        }

        public List<Identifier> channels() {
            return channels;
        }

        @Override
        public Type<CustomRegisterPayload> type() {
            return TYPE;
        }
    }

    public static final class OpaqueRegisterPayload implements CustomPacketPayload {
        public static final Type<OpaqueRegisterPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("minecraft", "register"));

        @Override
        public Type<OpaqueRegisterPayload> type() {
            return TYPE;
        }
    }

    @Test
    void customRegisterImplWithAllWhitelistedChannelsPasses() {
        publishProtector(false);
        AutismProtectorChannelFilter.Verdict verdict = AutismProtectorChannelFilter.filter(
            new ServerboundCustomPayloadPacket(
                new CustomRegisterPayload(List.of(id("minecraft", "brand"), id("c", "version")))));
        assertEquals(AutismProtectorChannelFilter.Verdict.Kind.PASS, verdict.kind);
    }

    @Test
    void customRegisterImplWithANonWhitelistedChannelIsDropped() {
        publishProtector(false);
        AutismProtectorChannelFilter.Verdict verdict = AutismProtectorChannelFilter.filter(
            new ServerboundCustomPayloadPacket(
                new CustomRegisterPayload(List.of(id("minecraft", "brand"), id("evilmod", "secret")))));
        assertEquals(AutismProtectorChannelFilter.Verdict.Kind.DROP, verdict.kind);
    }

    @Test
    void uninspectableRegisterImplFailsOpenRatherThanVanishing() {
        publishProtector(false);

        AutismProtectorChannelFilter.Verdict verdict = AutismProtectorChannelFilter.filter(
            new ServerboundCustomPayloadPacket(new OpaqueRegisterPayload()));
        assertEquals(AutismProtectorChannelFilter.Verdict.Kind.PASS, verdict.kind);
    }

    @Test
    void keepWhitelistedIsAnOrderPreservingSubsequence() {
        List<Identifier> channels = List.of(
            id("banned", "a"), id("minecraft", "one"), id("banned", "b"), id("c", "two"));
        List<Identifier> kept = AutismProtectorChannelFilter.keepWhitelisted(
            channels, channel -> !"banned".equals(channel.getNamespace()));
        assertEquals(List.of(id("minecraft", "one"), id("c", "two")), kept);
        assertTrue(AutismProtectorChannelFilter.keepWhitelisted(channels, c -> false).isEmpty());
    }
}
