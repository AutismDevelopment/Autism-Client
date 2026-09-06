package autismclient.security;

import autismclient.AutismClientAddon;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public final class AutismProtectorChannelFilter {

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

        if (!AutismProtector.shouldFilterChannels()) {

            noteIfRegister(customPayload);
            return PASS;
        }

        if (AutismProtector.isUserBypass(packet)) {
            noteIfRegister(customPayload);
            return PASS;
        }
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
            Verdict verdict = filterRegister(payload);
            if (REGISTER.equals(path) && verdict.kind != Verdict.Kind.DROP) {
                AutismFabricRegisterMimicry.noteOutboundRegister();
            }
            return verdict;
        }

        if (MINECRAFT.equals(namespace) && MCO.equals(path)) return DROP;

        if (AutismProtectorTracker.isWhitelistedChannel(id)) return PASS;

        return DROP;
    }

    private static void noteIfRegister(ServerboundCustomPayloadPacket packet) {
        CustomPacketPayload payload;
        try {
            payload = packet.payload();
        } catch (Throwable t) {
            return;
        }
        if (payload == null) return;
        Identifier id = payloadId(payload);
        if (id != null && MINECRAFT.equals(id.getNamespace()) && REGISTER.equals(id.getPath())) {
            AutismFabricRegisterMimicry.noteOutboundRegister();
        }
    }

    private static Identifier payloadId(CustomPacketPayload payload) {
        try {
            CustomPacketPayload.Type<?> type = payload.type();
            if (type != null) return type.id();
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Verdict filterRegister(CustomPacketPayload payload) {
        if (payload instanceof RegistrationPayload registrationPayload) {
            List<Identifier> kept = keepWhitelisted(registrationPayload.channels(), AutismProtectorTracker::isWhitelistedChannel);
            if (kept.size() == registrationPayload.channels().size()) return PASS;
            if (kept.isEmpty()) return DROP;
            return new Verdict(Verdict.Kind.REPLACE,
                new ServerboundCustomPayloadPacket(new RegistrationPayload(registrationPayload.type(), kept)));
        }

        List<Identifier> channels = extractChannels(payload);
        if (channels == null) {
            logFallbackOnce("uninspectable " + payload.getClass().getName() + " on " + payloadId(payload));
            return PASS;
        }
        for (Identifier channel : channels) {
            if (!AutismProtectorTracker.isWhitelistedChannel(channel)) {
                logFallbackOnce("dropping non-whitelisted register channel " + channel);
                return DROP;
            }
        }
        return PASS;
    }

    private static List<Identifier> extractChannels(CustomPacketPayload payload) {
        try {
            Method method = payload.getClass().getMethod("channels");
            Object value = method.invoke(payload);
            if (!(value instanceof List<?> list)) return null;
            List<Identifier> channels = new ArrayList<>(list.size());
            for (Object entry : list) {
                if (!(entry instanceof Identifier identifier)) return null;
                channels.add(identifier);
            }
            return channels;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void logFallbackOnce(String detail) {
        if (!AutismFabricRegisterMimicry.claimFallbackLog()) return;
        AutismClientAddon.LOG.warn("[AutismProtector] Register fallback engaged ({}); passing by whitelist verdict only.", detail);
    }

    static List<Identifier> keepWhitelisted(Collection<Identifier> channels, Predicate<Identifier> whitelist) {
        List<Identifier> kept = new ArrayList<>(channels.size());
        for (Identifier channel : channels) {
            if (whitelist.test(channel)) kept.add(channel);
        }
        return kept;
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
