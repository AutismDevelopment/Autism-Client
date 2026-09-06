package autismclient.util;

import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PacketListCodec {
    private PacketListCodec() {
    }

    public static String normalizeSeparators(String value) {
        return value == null ? "" : value.replace(',', '|');
    }

    public static boolean isC2SOption(String optionId) {
        return optionId != null && optionId.toLowerCase(Locale.ROOT).contains("c2s");
    }

    public static Set<Class<? extends Packet<?>>> resolvePackets(String value, boolean c2s) {
        Set<Class<? extends Packet<?>>> packets = new LinkedHashSet<>();
        Set<Class<? extends Packet<?>>> pool = c2s ? AutismPacketRegistry.getC2SPackets() : AutismPacketRegistry.getS2CPackets();
        for (String token : tokens(value)) {
            Class<? extends Packet<?>> packetClass = resolvePacket(token, pool);
            if (packetClass != null) packets.add(packetClass);
        }
        return packets;
    }

    public static List<String> invalidTokens(String value, boolean c2s) {
        List<String> invalid = new ArrayList<>();
        Set<Class<? extends Packet<?>>> pool = c2s ? AutismPacketRegistry.getC2SPackets() : AutismPacketRegistry.getS2CPackets();
        for (String token : tokens(value)) {
            if (resolvePacket(token, pool) == null) invalid.add(token);
        }
        return invalid;
    }

    public static String encodePackets(Collection<Class<? extends Packet<?>>> packets) {
        if (packets == null || packets.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (Class<? extends Packet<?>> packetClass : packets) {
            String name = AutismPacketRegistry.getName(packetClass);
            names.add(name == null ? packetClass.getName() : name);
        }
        return String.join("|", names);
    }

    public static String canonicalize(String value, boolean c2s) {
        return encodePackets(resolvePackets(value, c2s));
    }

    private static List<String> tokens(String value) {
        List<String> out = new ArrayList<>();
        String normalized = normalizeSeparators(value);
        if (normalized.isBlank()) return out;
        for (String raw : normalized.split("\\|")) {
            String token = raw.trim();
            if (!token.isEmpty()) out.add(token);
        }
        return out;
    }

    private static Class<? extends Packet<?>> resolvePacket(String token, Set<Class<? extends Packet<?>>> pool) {
        Class<? extends Packet<?>> packetClass = AutismPacketRegistry.getPacket(token);
        if (packetClass != null && pool.contains(packetClass)) return packetClass;

        String friendly = AutismPacketNamer.getFriendlyName(token);
        packetClass = AutismPacketRegistry.getPacket(friendly);
        if (packetClass != null && pool.contains(packetClass)) return packetClass;

        for (Class<? extends Packet<?>> candidate : pool) {
            String registryName = AutismPacketRegistry.getName(candidate);
            if (matches(token, registryName, candidate)) return candidate;
        }
        return null;
    }

    private static boolean matches(String token, String registryName, Class<? extends Packet<?>> candidate) {
        String needle = normalize(token);
        if (needle.isEmpty()) return false;
        String registry = normalize(registryName);
        String registryNoSuffix = registry.endsWith("packet") ? registry.substring(0, registry.length() - 6) : registry;
        String simple = normalize(candidate.getSimpleName());
        String full = normalize(candidate.getName());
        String friendly = normalize(AutismPacketNamer.getFriendlyName(candidate));
        return needle.equals(registry)
            || needle.equals(registryNoSuffix)
            || needle.equals(simple)
            || needle.equals(full)
            || needle.equals(friendly)
            || full.endsWith(needle);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }
}
