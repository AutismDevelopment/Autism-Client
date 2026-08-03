package autismclient.security;

import autismclient.AutismClientAddon;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public final class AutismProtectorChannelFilter {

    private static final boolean DEBUG = Boolean.getBoolean("autism.protector.debug");
    private static final Verdict PASS = new Verdict(Verdict.Kind.PASS, null);
    private static final Verdict DROP = new Verdict(Verdict.Kind.DROP, null);

    private static final String MINECRAFT = "minecraft";
    private static final String REGISTER = "register";
    private static final String UNREGISTER = "unregister";
    private static final String MCO = "mco";

    private AutismProtectorChannelFilter() {
    }

    public static Verdict pass() {
        return PASS;
    }

    public static Verdict drop() {
        return DROP;
    }

    public static Verdict filter(Packet<?> packet) {
        if (packet == null) return PASS;
        if (!(packet instanceof ServerboundCustomPayloadPacket customPayload)) return PASS;
        if (!AutismProtector.shouldFilterChannels()) return PASS;

        if (AutismProtector.isUserBypass(packet)) return PASS;
        CustomPacketPayload payload = customPayload.payload();
        if (payload == null) return PASS;

        if (payload instanceof BrandPayload) return PASS;

        Identifier id = payloadId(payload);
        if (id == null) return PASS;

        if (AutismProtector.isVanillaMode()) {
            return DROP;
        }

        String namespace = id.getNamespace();
        String path = id.getPath();

        if (MINECRAFT.equals(namespace) && (REGISTER.equals(path) || UNREGISTER.equals(path))) {
            if (payload instanceof RegistrationPayload registrationPayload) {
                return rebuildRegister(registrationPayload);
            }
            return DROP;
        }

        if (MINECRAFT.equals(namespace) && MCO.equals(path)) return DROP;

        if (AutismProtectorTracker.isWhitelistedChannel(id)) return PASS;

        return DROP;
    }

    private static Identifier payloadId(CustomPacketPayload payload) {
        try {
            CustomPacketPayload.Type<?> type = payload.type();
            if (type != null) return type.id();
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Verdict rebuildRegister(RegistrationPayload original) {
        List<Identifier> kept = new ArrayList<>();
        try {
            for (Identifier channel : original.channels()) {
                if (AutismProtectorTracker.isWhitelistedChannel(channel)) {
                    kept.add(channel);
                }
            }
        } catch (Throwable t) {
            if (DEBUG) {
                AutismClientAddon.LOG.debug("[AutismProtector] register payload inspection failed: {}", t.getMessage());
            }
            return DROP;
        }

        if (kept.isEmpty()) return DROP;

        RegistrationPayload rebuilt = newRegistrationPayload(original, kept);
        if (rebuilt == null) return DROP;
        return new Verdict(Verdict.Kind.REPLACE, new ServerboundCustomPayloadPacket(rebuilt));
    }

    private static RegistrationPayload newRegistrationPayload(RegistrationPayload original, List<Identifier> channels) {

        for (Constructor<?> ctor : RegistrationPayload.class.getDeclaredConstructors()) {
            if (ctor.getParameterCount() != 2) continue;
            try {
                ctor.setAccessible(true);
            } catch (Throwable ignored) {
                continue;
            }
            try {
                return (RegistrationPayload) ctor.newInstance(original.type(), channels);
            } catch (Throwable ignored) {
                try {
                    return (RegistrationPayload) ctor.newInstance(channels, original.type());
                } catch (Throwable ignored2) {  }
            }
        }
        AutismClientAddon.LOG.warn("[AutismProtector] No compatible RegistrationPayload constructor; dropping packet.");
        return null;
    }

    public static final class Verdict {
        public enum Kind { PASS, DROP, REPLACE }

        public final Kind kind;
        public final Packet<?> replacement;

        private Verdict(Kind kind, Packet<?> replacement) {
            this.kind = kind;
            this.replacement = replacement;
        }
    }
}
