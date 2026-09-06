package autismclient.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.CommonPacketTypes;
import net.minecraft.network.protocol.configuration.ConfigurationPacketTypes;
import net.minecraft.network.protocol.cookie.CookiePacketTypes;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.network.protocol.handshake.HandshakePacketTypes;
import net.minecraft.network.protocol.login.LoginPacketTypes;
import net.minecraft.network.protocol.ping.PingPacketTypes;
import net.minecraft.network.protocol.status.StatusPacketTypes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AutismPacketRegistry {
    private static final Class<?>[] PACKET_TYPE_HOLDERS = {
        HandshakePacketTypes.class,
        StatusPacketTypes.class,
        LoginPacketTypes.class,
        ConfigurationPacketTypes.class,
        CommonPacketTypes.class,
        CookiePacketTypes.class,
        PingPacketTypes.class,
        GamePacketTypes.class
    };

    private static final Map<Class<? extends Packet<?>>, String> S2C_PACKETS;
    private static final Map<Class<? extends Packet<?>>, String> C2S_PACKETS;
    private static final Map<String, Class<? extends Packet<?>>> PACKETS_BY_NAME;

    public static final Set<Class<? extends Packet<?>>> PACKETS;

    static {
        LinkedHashMap<Class<? extends Packet<?>>, String> s2cPackets = new LinkedHashMap<>();
        LinkedHashMap<Class<? extends Packet<?>>, String> c2sPackets = new LinkedHashMap<>();
        LinkedHashMap<String, Class<? extends Packet<?>>> packetsByName = new LinkedHashMap<>();
        LinkedHashSet<String> ambiguousAliases = new LinkedHashSet<>();

        for (Class<?> holder : PACKET_TYPE_HOLDERS) {
            registerHolder(holder, s2cPackets, c2sPackets, packetsByName, ambiguousAliases);
        }

        LinkedHashSet<Class<? extends Packet<?>>> packets = new LinkedHashSet<>();
        packets.addAll(c2sPackets.keySet());
        packets.addAll(s2cPackets.keySet());

        S2C_PACKETS = Collections.unmodifiableMap(s2cPackets);
        C2S_PACKETS = Collections.unmodifiableMap(c2sPackets);
        PACKETS_BY_NAME = Collections.unmodifiableMap(packetsByName);
        PACKETS = Collections.unmodifiableSet(packets);
    }

    private AutismPacketRegistry() {
    }

    private static void registerHolder(
        Class<?> holder,
        Map<Class<? extends Packet<?>>, String> s2cPackets,
        Map<Class<? extends Packet<?>>, String> c2sPackets,
        Map<String, Class<? extends Packet<?>>> packetsByName,
        Set<String> ambiguousAliases
    ) {
        for (Field field : holder.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !PacketType.class.isAssignableFrom(field.getType())) {
                continue;
            }

            Class<? extends Packet<?>> packetClass = extractPacketClass(field);
            if (packetClass == null) {
                continue;
            }

            String fieldName = field.getName();
            Map<Class<? extends Packet<?>>, String> target;
            if (fieldName.startsWith("CLIENTBOUND_")) {
                target = s2cPackets;
            } else if (fieldName.startsWith("SERVERBOUND_")) {
                target = c2sPackets;
            } else {
                continue;
            }

            String primaryName = displayName(packetClass);
            target.putIfAbsent(packetClass, primaryName);
            registerAliases(
                packetsByName,
                ambiguousAliases,
                packetClass,
                primaryName,
                packetClass.getSimpleName(),
                packetClass.getName(),
                fieldName,
                packetIdAlias(fieldName),
                "minecraft:" + packetIdAlias(fieldName)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Packet<?>> extractPacketClass(Field field) {
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        Type[] args = parameterizedType.getActualTypeArguments();
        if (args.length != 1 || !(args[0] instanceof Class<?> packetClass) || !Packet.class.isAssignableFrom(packetClass)) {
            return null;
        }
        return (Class<? extends Packet<?>>) packetClass;
    }

    private static String displayName(Class<?> packetClass) {
        Class<?> enclosing = packetClass.getEnclosingClass();
        if (enclosing == null || packetClass.getPackageName() == null || !packetClass.getPackageName().equals(enclosing.getPackageName())) {
            return packetClass.getSimpleName();
        }
        return displayName(enclosing) + "." + packetClass.getSimpleName();
    }

    private static String packetIdAlias(String fieldName) {
        String name = fieldName;
        if (name.startsWith("CLIENTBOUND_")) {
            name = name.substring("CLIENTBOUND_".length());
        } else if (name.startsWith("SERVERBOUND_")) {
            name = name.substring("SERVERBOUND_".length());
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static void registerAliases(
        Map<String, Class<? extends Packet<?>>> target,
        Set<String> ambiguousAliases,
        Class<? extends Packet<?>> packetClass,
        String... aliases
    ) {
        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }
            String trimmed = alias.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            putAlias(target, ambiguousAliases, trimmed, packetClass);
            putAlias(target, ambiguousAliases, normalize(trimmed), packetClass);
            int namespaceSeparator = trimmed.indexOf(':');
            if (namespaceSeparator >= 0 && namespaceSeparator + 1 < trimmed.length()) {
                String pathOnly = trimmed.substring(namespaceSeparator + 1);
                putAlias(target, ambiguousAliases, pathOnly, packetClass);
                putAlias(target, ambiguousAliases, normalize(pathOnly), packetClass);
            }
        }
    }

    private static void putAlias(
        Map<String, Class<? extends Packet<?>>> target,
        Set<String> ambiguousAliases,
        String alias,
        Class<? extends Packet<?>> packetClass
    ) {
        if (alias.isEmpty() || ambiguousAliases.contains(alias)) {
            return;
        }
        Class<? extends Packet<?>> existing = target.get(alias);
        if (existing == null || existing == packetClass) {
            target.put(alias, packetClass);
            return;
        }
        target.remove(alias);
        ambiguousAliases.add(alias);
    }

    private static String normalize(String value) {
        return value.trim()
            .replace('$', '.')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('/', '.')
            .toLowerCase(Locale.ROOT);
    }

    public static String getName(Class<? extends Packet<?>> packetClass) {
        String name = S2C_PACKETS.get(packetClass);
        if (name != null) return name;
        return C2S_PACKETS.get(packetClass);
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Packet<?>> getPacket(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        Class<? extends Packet<?>> packet = PACKETS_BY_NAME.get(trimmed);
        if (packet != null) {
            return packet;
        }
        packet = PACKETS_BY_NAME.get(normalize(trimmed));
        if (packet != null) {
            return packet;
        }

        try {
            Class<?> direct = Class.forName(trimmed);
            if (Packet.class.isAssignableFrom(direct)) {
                return (Class<? extends Packet<?>>) direct;
            }
        } catch (ClassNotFoundException ignored) {  }
        return null;
    }

    public static Set<Class<? extends Packet<?>>> getS2CPackets() {
        return S2C_PACKETS.keySet();
    }

    public static Set<Class<? extends Packet<?>>> getC2SPackets() {
        return C2S_PACKETS.keySet();
    }
}
