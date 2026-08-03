package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.util.macro.MacroAction;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.network.HashedStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

import java.io.*;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;

@SuppressWarnings({"removal", "deprecation"})
public class AutismClipboardHelper {
    private static final int CLIPBOARD_VERSION = 1;
    private static final String MACRO_CLIPBOARD_TYPE = "autism_macro";
    private static final String MACRO_STEPS_CLIPBOARD_TYPE = "autism_macro_steps";

    public static void copyToClipboard(List<AutismSharedState.QueuedPacket> queue) {
        try {
            CompoundTag rootTag = new CompoundTag();
            ListTag packetList = new ListTag();

            for (AutismSharedState.QueuedPacket qp : queue) {
                CompoundTag packetTag = serializeQueuedPacket(qp);
                if (packetTag != null) {
                    packetList.add(packetTag);
                }
            }

            rootTag.put("packets", packetList);
            rootTag.putInt("version", 1);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            NbtIo.writeCompressed(rootTag, outputStream);
            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            Minecraft.getInstance().keyboardHandler.setClipboard(base64);
            AutismClientAddon.LOG.info("[Autism] Copied {} packets to clipboard", queue.size());

        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to copy packets to clipboard", e);
        }
    }

    public static boolean copyMacroToClipboard(AutismMacro macro) {
        if (macro == null) return false;

        try {
            CompoundTag rootTag = new CompoundTag();
            rootTag.putInt("version", CLIPBOARD_VERSION);
            rootTag.putString("type", MACRO_CLIPBOARD_TYPE);
            rootTag.put("macro", macro.toShareableTag());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            NbtIo.writeCompressed(rootTag, outputStream);
            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            Minecraft.getInstance().keyboardHandler.setClipboard(base64);
            AutismClientAddon.LOG.info("[Autism] Copied macro '{}' to clipboard", macro.name);
            return true;
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to copy macro to clipboard", e);
            return false;
        }
    }

    private static CompoundTag readClipboardRoot() {
        try {
            String base64 = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (base64 == null || base64.trim().isEmpty()) return null;
            byte[] data = Base64.getDecoder().decode(base64.trim());
            CompoundTag rootTag = NbtIo.readCompressed(new ByteArrayInputStream(data), safeNbtAccounter());
            int version = rootTag.getIntOr("version", 0);
            if (version != CLIPBOARD_VERSION) {
                AutismClientAddon.LOG.warn("[Autism] Unsupported clipboard version: {}", version);
                return null;
            }
            return rootTag;
        } catch (Exception e) {

            return null;
        }
    }

    private static List<MacroAction> deserializeActionList(ListTag actionList) {
        List<MacroAction> actions = new ArrayList<>();
        if (actionList == null) return actions;
        int dropped = 0;
        for (int i = 0; i < actionList.size(); i++) {
            if (!(actionList.get(i) instanceof CompoundTag actionTag)) continue;
            if (!AutismMacro.isBuiltInActionType(actionTag.getStringOr("type", ""))) { dropped++; continue; }
            MacroAction action = AutismMacro.createActionFromTag(actionTag);
            if (action != null) {
                action.sanitizeForSharing();
                actions.add(action);
            }
        }
        notifyDroppedAddonActions(dropped);
        return actions;
    }

    private static AutismMacro buildUntrustedMacro(CompoundTag macroTag) {
        int dropped = AutismMacro.stripToBuiltInActions(macroTag);
        AutismMacro macro = new AutismMacro().fromTag(macroTag).sanitizeForSharing();
        macro.keyCode = -1;
        notifyDroppedAddonActions(dropped);
        return macro;
    }

    private static void notifyDroppedAddonActions(int dropped) {
        if (dropped <= 0) return;
        AutismNotifications.show(
            "Removed " + dropped + " addon action" + (dropped == 1 ? "" : "s") + " from imported macro for safety",
            0xFFE0A53A);
    }

    public static AutismMacro pasteMacroFromClipboard() {
        CompoundTag rootTag = readClipboardRoot();
        if (rootTag == null) return null;

        String type = rootTag.getStringOr("type", "");
        if (MACRO_CLIPBOARD_TYPE.equals(type)) {
            CompoundTag macroTag = rootTag.getCompound("macro").orElse(new CompoundTag());
            if (macroTag.isEmpty()) {
                AutismClientAddon.LOG.warn("[Autism] Macro clipboard payload was empty");
                return null;
            }
            return buildUntrustedMacro(macroTag);
        }
        if (MACRO_STEPS_CLIPBOARD_TYPE.equals(type)) {
            List<MacroAction> actions = deserializeActionList(rootTag.getList("actions").orElse(new ListTag()));
            if (actions.isEmpty()) return null;
            AutismMacro macro = new AutismMacro();
            macro.actions = actions;
            return macro;
        }

        AutismClientAddon.LOG.warn("[Autism] Clipboard data is not a Autism macro payload");
        return null;
    }

    private static CompoundTag decodeRoot(String base64) {
        try {
            if (base64 == null || base64.trim().isEmpty()) return null;
            byte[] data = Base64.getDecoder().decode(base64.trim());
            CompoundTag rootTag = NbtIo.readCompressed(new ByteArrayInputStream(data), safeNbtAccounter());
            return rootTag.getIntOr("version", 0) == CLIPBOARD_VERSION ? rootTag : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String serializeMacroToBase64(AutismMacro macro) {
        if (macro == null) return null;
        try {
            CompoundTag rootTag = new CompoundTag();
            rootTag.putInt("version", CLIPBOARD_VERSION);
            rootTag.putString("type", MACRO_CLIPBOARD_TYPE);
            rootTag.put("macro", macro.toShareableTag());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(rootTag, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to serialize macro to Base64", e);
            return null;
        }
    }

    public static AutismMacro deserializeMacroFromBase64(String base64) {
        CompoundTag rootTag = decodeRoot(base64);
        if (rootTag == null) return null;
        String type = rootTag.getStringOr("type", "");
        if (MACRO_CLIPBOARD_TYPE.equals(type)) {
            CompoundTag macroTag = rootTag.getCompound("macro").orElse(new CompoundTag());
            if (macroTag.isEmpty()) return null;
            return buildUntrustedMacro(macroTag);
        }
        if (MACRO_STEPS_CLIPBOARD_TYPE.equals(type)) {
            List<MacroAction> actions = deserializeActionList(rootTag.getList("actions").orElse(new ListTag()));
            if (actions.isEmpty()) return null;
            AutismMacro macro = new AutismMacro();
            macro.actions = actions;
            return macro;
        }
        return null;
    }

    public static String detectShareType(String base64) {
        CompoundTag rootTag = decodeRoot(base64);
        if (rootTag == null) return null;
        String type = rootTag.getStringOr("type", "");
        if (MACRO_CLIPBOARD_TYPE.equals(type) || MACRO_STEPS_CLIPBOARD_TYPE.equals(type)) return type;
        if (rootTag.getList("packets").isPresent() || rootTag.contains("packet")) return "packets";
        return null;
    }

    public static boolean copyMacroStepsToClipboard(List<MacroAction> actions) {
        if (actions == null || actions.isEmpty()) return false;

        try {
            CompoundTag rootTag = new CompoundTag();
            rootTag.putInt("version", CLIPBOARD_VERSION);
            rootTag.putString("type", MACRO_STEPS_CLIPBOARD_TYPE);
            ListTag actionList = new ListTag();
            for (MacroAction action : actions) {
                if (action == null) continue;

                MacroAction copy = AutismMacro.createActionFromTag(action.toTag());
                if (copy == null) continue;
                copy.sanitizeForSharing();
                actionList.add(copy.toTag());
            }
            if (actionList.isEmpty()) return false;
            rootTag.put("actions", actionList);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            NbtIo.writeCompressed(rootTag, outputStream);
            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            Minecraft.getInstance().keyboardHandler.setClipboard(base64);
            AutismClientAddon.LOG.info("[Autism] Copied {} macro steps to clipboard", actionList.size());
            return true;
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to copy macro steps to clipboard", e);
            return false;
        }
    }

    public static List<MacroAction> pasteMacroStepsFromClipboard() {
        CompoundTag rootTag = readClipboardRoot();
        if (rootTag == null) return null;

        String type = rootTag.getStringOr("type", "");
        if (MACRO_STEPS_CLIPBOARD_TYPE.equals(type)) {
            List<MacroAction> actions = deserializeActionList(rootTag.getList("actions").orElse(new ListTag()));
            return actions.isEmpty() ? null : actions;
        }
        if (MACRO_CLIPBOARD_TYPE.equals(type)) {
            CompoundTag macroTag = rootTag.getCompound("macro").orElse(new CompoundTag());
            List<MacroAction> actions = deserializeActionList(macroTag.getList("actions").orElse(new ListTag()));
            return actions.isEmpty() ? null : actions;
        }
        return null;
    }

    public static List<AutismSharedState.QueuedPacket> pasteFromClipboard() {
        try {
            String base64 = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (base64 == null || base64.trim().isEmpty()) {
                AutismClientAddon.LOG.warn("[Autism] Clipboard is empty");
                return null;
            }

            byte[] data = Base64.getDecoder().decode(base64.trim());
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            CompoundTag rootTag = NbtIo.readCompressed(inputStream, safeNbtAccounter());

            int version = rootTag.getIntOr("version", 0);
            if (version != 1) {
                AutismClientAddon.LOG.error("[Autism] Unsupported clipboard format version: {}", version);
                return null;
            }

            ListTag packetList = (ListTag) rootTag.get("packets");
            List<AutismSharedState.QueuedPacket> queue = new ArrayList<>();

            for (int i = 0; i < packetList.size(); i++) {
                CompoundTag packetTag = (CompoundTag) packetList.get(i);
                AutismSharedState.QueuedPacket qp = deserializeQueuedPacket(packetTag);
                if (qp != null) {
                    queue.add(qp);
                }
            }

            AutismClientAddon.LOG.info("[Autism] Pasted {} packets from clipboard", queue.size());
            return queue.isEmpty() ? null : queue;

        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to paste packets from clipboard", e);
            return null;
        }
    }

    public static Packet<?> deserializePacketFromBase64(String base64) {
        try {
            if (base64 == null || base64.trim().isEmpty()) return null;

            byte[] data = Base64.getDecoder().decode(base64.trim());
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            CompoundTag tag = NbtIo.readCompressed(inputStream, safeNbtAccounter());

            if (tag.contains("packets")) {

                ListTag list = (ListTag) tag.get("packets");
                if (list != null && !list.isEmpty()) {
                    CompoundTag packetTag = (CompoundTag) list.get(0);
                    AutismSharedState.QueuedPacket qp = deserializeQueuedPacket(packetTag);
                    return qp != null ? qp.packet : null;
                }
            } else if (tag.contains("packet")) {

                AutismSharedState.QueuedPacket qp = deserializeQueuedPacket(tag);
                return qp != null ? qp.packet : null;
            } else {

                return deserializePacket(tag);
            }

            return null;
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to deserialize packet from Base64", e);
            return null;
        }
    }

    public static CompoundTag serializeQueuedPacket(AutismSharedState.QueuedPacket qp) {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putInt("delay", qp.getDelay());
            tag.putInt("id", qp.getId());
            tag.putString("replayMode", qp.getReplayMode().name());

            CompoundTag packetData = serializePacket(qp.packet);
            if (packetData != null) {
                tag.put("packet", packetData);
                return tag;
            }

            return null;
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to serialize queued packet", e);
            return null;
        }
    }

    public static AutismSharedState.QueuedPacket deserializeQueuedPacket(CompoundTag tag) {
        try {
            int delay = tag.getIntOr("delay", 0);
            int id = tag.getIntOr("id", 0);
            AutismSharedState.ReplayMode replayMode = AutismSharedState.ReplayMode.REGENERATE;
            if (tag.contains("replayMode")) {
                try {
                    replayMode = AutismSharedState.ReplayMode.valueOf(tag.getStringOr("replayMode", "REGENERATE"));
                } catch (IllegalArgumentException ignored) {
                    replayMode = AutismSharedState.ReplayMode.REGENERATE;
                }
            }
            CompoundTag packetData = tag.getCompound("packet").orElse(new CompoundTag());

            Packet<?> packet = deserializePacket(packetData);
            if (packet != null) {
                return id > 0
                    ? new AutismSharedState.QueuedPacket(packet, delay, id, replayMode)
                    : new AutismSharedState.QueuedPacket(packet, delay, replayMode);
            }

            return null;
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to deserialize queued packet", e);
            return null;
        }
    }

    private static CompoundTag serializePacket(Packet<?> packet) {
        CompoundTag tag = new CompoundTag();

        String className = packet.getClass().getName();
        tag.putString("class", className);

        @SuppressWarnings("unchecked")
        String friendlyName = AutismPacketRegistry.getName((Class<? extends Packet<?>>) packet.getClass());
        if (friendlyName != null) {
            tag.putString("friendlyName", friendlyName);
        }

        if (packet instanceof ServerboundCustomPayloadPacket) {
            tag.putBoolean("customPayloadPacket", true);
            AutismPayloadSupport.PayloadSnapshot snapshot = AutismPayloadSupport.snapshot(packet, "C2S");
            if (snapshot != null) {
                tag.putString("payloadChannel", snapshot.channel());
                tag.putByteArray("payloadBytes", snapshot.rawBytes());
                return tag;
            }
        }

        CompoundTag fieldsTag = new CompoundTag();
        serializeFields(packet, fieldsTag);
        tag.put("fields", fieldsTag);

        return tag;
    }

    private static Packet<?> deserializePacket(CompoundTag tag) {
        try {
            if (tag.getBooleanOr("customPayloadPacket", false)) {
                String channel = tag.getStringOr("payloadChannel", "");
                byte[] bytes = tag.getByteArray("payloadBytes").orElse(new byte[0]);
                if (!channel.isBlank()) {
                    return AutismPayloadSupport.createC2SPacket(channel, bytes);
                }
            }

            String className = tag.getStringOr("class", "");

            Class<?> packetClass = null;
            if (isKnownPacketClass(className)) {
                try { packetClass = Class.forName(className); } catch (ClassNotFoundException ignored) {  }
            }
            if (packetClass == null && tag.contains("friendlyName")) {
                packetClass = AutismPacketRegistry.getPacket(tag.getStringOr("friendlyName", ""));
            }

            if (packetClass == null) {
                AutismClientAddon.LOG.warn("[Autism] Rejected unknown/untrusted packet class: {}", className);
                return null;
            }

            CompoundTag fieldsTag = tag.getCompound("fields").orElse(new CompoundTag());

            if (packetClass.isRecord()) {
                return (Packet<?>) deserializeRecord(packetClass, fieldsTag);
            }

            Packet<?> packet = (Packet<?>) getUnsafe().allocateInstance(packetClass);

            deserializeFields(packet, fieldsTag);

            if (!AutismPacketRegistry.getC2SPackets().contains(packetClass)) {
                AutismClientAddon.LOG.warn("[Autism] Deserialized packet {} is NOT a registered C2S packet! It may be skipped when sending.", className);
            }

            return packet;

        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to deserialize packet", e);
            return null;
        }
    }

    private static void serializeFields(Object obj, CompoundTag fieldsTag) {
        Class<?> clazz = obj.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    serializeField(fieldsTag, field.getName(), value, field.getType());
                } catch (Exception e) {
                    AutismClientAddon.LOG.warn("[Autism] Failed to serialize field: {}", field.getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static void serializeField(CompoundTag tag, String name, Object value, Class<?> type) {
        if (value == null) {
            tag.putBoolean(name + "_null", true);
            return;
        }

        tag.putString(name + "_type", value.getClass().getName());

        if (value instanceof ItemStack) {
            ItemStack stack = (ItemStack) value;
            if (!stack.isEmpty()) {
                CompoundTag stackNbt = new CompoundTag();

                try {
                    Tag encoded = ItemStack.CODEC.encodeStart(
                        getRegistryManager().createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
                    tag.put(name, encoded);
                } catch (Exception e) {

                    stackNbt.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    stackNbt.putInt("count", stack.getCount());
                    tag.put(name, stackNbt);
                }
            } else {
                tag.putBoolean(name + "_empty", true);
            }
        }
        else if (value instanceof CompoundTag) {
            tag.put(name, (CompoundTag) value);
        }
        else if (value instanceof Tag) {
            tag.put(name, (Tag) value);
        }
        else if (value instanceof BlockPos) {
            tag.putLong(name, ((BlockPos) value).asLong());
        }
        else if (value instanceof Identifier) {
            tag.putString(name, value.toString());
        }
        else if (value instanceof Component) {
            tag.putString(name, ((Component) value).getString());
        }
        else if (value instanceof String) {
            tag.putString(name, (String) value);
        }
        else if (value instanceof Integer) {
            tag.putInt(name, (Integer) value);
        }
        else if (value instanceof Boolean) {
            tag.putBoolean(name, (Boolean) value);
        }
        else if (value instanceof Long) {
            tag.putLong(name, (Long) value);
        }
        else if (value instanceof Float) {
            tag.putFloat(name, (Float) value);
        }
        else if (value instanceof Double) {
            tag.putDouble(name, (Double) value);
        }
        else if (value instanceof Byte) {
            tag.putByte(name, (Byte) value);
        }
        else if (value instanceof Short) {
            tag.putShort(name, (Short) value);
        }
        else if (value instanceof byte[]) {
            tag.putByteArray(name, (byte[]) value);
        }
        else if (value instanceof int[]) {
            tag.putIntArray(name, (int[]) value);
        }
        else if (value instanceof long[]) {
            tag.putLongArray(name, (long[]) value);
        }
        else if (value instanceof Enum) {
            tag.putString(name, ((Enum<?>) value).name());
            tag.putString(name + "_enumClass", value.getClass().getName());
        }
        else if (value instanceof Instant) {
            tag.putLong(name, ((Instant) value).toEpochMilli());
        }
        else if (value instanceof UUID) {
            UUID uuid = (UUID) value;
            tag.putLong(name + "_mostSig", uuid.getMostSignificantBits());
            tag.putLong(name + "_leastSig", uuid.getLeastSignificantBits());
        }
        else if (value instanceof Optional) {
            Optional<?> opt = (Optional<?>) value;
            if (opt.isPresent()) {
                serializeField(tag, name + "_value", opt.get(), opt.get().getClass());
                tag.putBoolean(name + "_present", true);
            } else {
                tag.putBoolean(name + "_present", false);
            }
        }
        else if (value instanceof java.util.BitSet) {
            java.util.BitSet bitSet = (java.util.BitSet) value;
            tag.putLongArray(name, bitSet.toLongArray());
        }
        else if (value instanceof Vec3) {
            Vec3 vec = (Vec3) value;
            tag.putDouble(name + "_x", vec.x);
            tag.putDouble(name + "_y", vec.y);
            tag.putDouble(name + "_z", vec.z);
        }
        else if (value instanceof BlockHitResult) {
            BlockHitResult hit = (BlockHitResult) value;
            serializeField(tag, name + "_pos", hit.getLocation(), Vec3.class);
            tag.putString(name + "_side", hit.getDirection().name());
            serializeField(tag, name + "_blockPos", hit.getBlockPos(), BlockPos.class);
            tag.putBoolean(name + "_insideBlock", hit.isInside());
        }
        else if (value instanceof Int2ObjectMap) {
            serializeInt2ObjectMap(tag, name, (Int2ObjectMap<?>) value);
        }
        else if (value instanceof Map) {
            serializeMap(tag, name, (Map<?, ?>) value);
        }
        else if (value instanceof List) {
            serializeList(tag, name, (List<?>) value);
        }
        else {

            try {
                tag.putString(name + "_toString", value.toString());
            } catch (Exception e) {

            }
        }
    }

    private static void serializeInt2ObjectMap(CompoundTag tag, String name, Int2ObjectMap<?> map) {
        CompoundTag mapTag = new CompoundTag();
        int index = 0;
        for (Int2ObjectMap.Entry<?> entry : map.int2ObjectEntrySet()) {
            String entryName = "entry_" + index++;
            CompoundTag entryTag = new CompoundTag();

            int key = entry.getIntKey();
            Object val = entry.getValue();

            entryTag.putInt("key", key);
            if (val != null) {
                serializeField(entryTag, "value", val, val.getClass());
            }

            mapTag.put(entryName, entryTag);
        }
        mapTag.putInt("size", map.size());
        tag.put(name, mapTag);
    }

    private static void serializeMap(CompoundTag tag, String name, Map<?, ?> map) {
        CompoundTag mapTag = new CompoundTag();
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String entryName = "entry_" + index++;
            CompoundTag entryTag = new CompoundTag();

            Object key = entry.getKey();
            Object val = entry.getValue();

            if (key != null) {
                serializeField(entryTag, "key", key, key.getClass());
            }
            if (val != null) {
                serializeField(entryTag, "value", val, val.getClass());
            }

            mapTag.put(entryName, entryTag);
        }
        mapTag.putInt("size", map.size());
        tag.put(name, mapTag);
    }

    private static void serializeList(CompoundTag tag, String name, List<?> list) {
        ListTag listTag = new ListTag();
        for (Object item : list) {
            if (item != null) {
                CompoundTag itemTag = new CompoundTag();
                serializeField(itemTag, "value", item, item.getClass());
                listTag.add(itemTag);
            }
        }
        tag.put(name, listTag);
        tag.putInt(name + "_size", list.size());
    }

    private static void deserializeFields(Object obj, CompoundTag fieldsTag) {
        Class<?> clazz = obj.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                String fieldName = field.getName();

                if (fieldsTag.contains(fieldName + "_null")) {
                    continue;
                }

                try {
                    Object value = deserializeField(fieldsTag, fieldName, field.getType());
                    if (value != null) {

                        @SuppressWarnings("deprecation")
                        long offset = getUnsafe().objectFieldOffset(field);
                        Class<?> type = field.getType();

                        if (type == int.class) {
                            getUnsafe().putInt(obj, offset, (Integer) value);
                        } else if (type == boolean.class) {
                            getUnsafe().putBoolean(obj, offset, (Boolean) value);
                        } else if (type == byte.class) {
                            getUnsafe().putByte(obj, offset, (Byte) value);
                        } else if (type == short.class) {
                            getUnsafe().putShort(obj, offset, (Short) value);
                        } else if (type == long.class) {
                            getUnsafe().putLong(obj, offset, (Long) value);
                        } else if (type == float.class) {
                            getUnsafe().putFloat(obj, offset, (Float) value);
                        } else if (type == double.class) {
                            getUnsafe().putDouble(obj, offset, (Double) value);
                        } else if (type == char.class) {
                            getUnsafe().putChar(obj, offset, (Character) value);
                        } else {
                            getUnsafe().putObject(obj, offset, value);
                        }

                    } else {

                        Object defaultValue = getSafeDefault(field.getType(), fieldName);
                        if (defaultValue != null) {
                            @SuppressWarnings("deprecation")
                            long offset = getUnsafe().objectFieldOffset(field);
                            getUnsafe().putObject(obj, offset, defaultValue);
                        }
                    }
                } catch (Exception e) {
                    AutismClientAddon.LOG.warn("[Autism] Failed to deserialize field: {}", fieldName, e);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static Object deserializeField(CompoundTag tag, String name, Class<?> targetType) {
        if (!tag.contains(name + "_type") && !tag.contains(name)) {
            return null;
        }

        if (tag.contains(name + "_empty") && tag.getBooleanOr(name + "_empty", false)) {
            return ItemStack.EMPTY;
        }

        String typeName = tag.contains(name + "_type") ? tag.getStringOr(name + "_type", "") : null;

        if (targetType == ItemStack.class || (typeName != null && typeName.contains("ItemStack"))) {
            if (tag.contains(name)) {
                try {
                    Tag stackNbt = tag.get(name);
                    if (stackNbt == null) return ItemStack.EMPTY;
                    return ItemStack.CODEC.parse(
                        getRegistryManager().createSerializationContext(NbtOps.INSTANCE), stackNbt).result().orElse(ItemStack.EMPTY);
                } catch (Exception e) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }
        else if (targetType == CompoundTag.class) {
            return tag.getCompound(name).orElse(new CompoundTag());
        }
        else if (targetType == Tag.class) {
            return tag.get(name);
        }
        else if (targetType == BlockPos.class) {
            return BlockPos.of(tag.getLongOr(name, 0L));
        }
        else if (targetType == Identifier.class) {
            return Identifier.parse(tag.getStringOr(name, ""));
        }
        else if (targetType == Component.class) {
            return Component.literal(tag.getStringOr(name, ""));
        }
        else if (targetType == String.class) {
            return tag.getStringOr(name, "");
        }
        else if (targetType == Integer.class || targetType == int.class) {
            return tag.getIntOr(name, 0);
        }
        else if (targetType == Boolean.class || targetType == boolean.class) {
            return tag.getBooleanOr(name, false);
        }
        else if (targetType == Long.class || targetType == long.class) {
            return tag.getLongOr(name, 0L);
        }
        else if (targetType == Float.class || targetType == float.class) {
            return tag.getFloatOr(name, 0.0f);
        }
        else if (targetType == Double.class || targetType == double.class) {
            return tag.getDoubleOr(name, 0.0);
        }
        else if (targetType == Byte.class || targetType == byte.class) {
            return tag.getByteOr(name, (byte) 0);
        }
        else if (targetType == Short.class || targetType == short.class) {
            return tag.getShortOr(name, (short) 0);
        }
        else if (targetType == byte[].class) {
            return tag.getByteArray(name).orElse(new byte[0]);
        }
        else if (targetType == int[].class) {
            return tag.getIntArray(name).orElse(new int[0]);
        }
        else if (targetType == long[].class) {
            return tag.getLongArray(name).orElse(new long[0]);
        }
        else if (targetType.isEnum()) {
            String enumName = tag.getStringOr(name, "");
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Enum<?> enumValue = Enum.valueOf((Class<Enum>) targetType, enumName);
                return enumValue;
            } catch (Exception e) {
                return null;
            }
        }
        else if (targetType == Instant.class) {
            long epochMilli = tag.getLongOr(name, System.currentTimeMillis());
            return Instant.ofEpochMilli(epochMilli);
        }
        else if (targetType == UUID.class) {
            if (tag.contains(name + "_mostSig")) {
                long mostSig = tag.getLongOr(name + "_mostSig", 0L);
                long leastSig = tag.getLongOr(name + "_leastSig", 0L);
                return new UUID(mostSig, leastSig);
            }
            return new UUID(0, 0);
        }
        else if (targetType == Optional.class || Optional.class.isAssignableFrom(targetType)) {
            if (tag.getBooleanOr(name + "_present", false)) {
                Object value = deserializeField(tag, name + "_value", Object.class);
                return Optional.ofNullable(value);
            }
            return Optional.empty();
        }
        else if (targetType == java.util.BitSet.class) {
            long[] longs = tag.getLongArray(name).orElse(new long[0]);
            return java.util.BitSet.valueOf(longs);
        }
        else if (targetType == Vec3.class) {
            double x = tag.getDoubleOr(name + "_x", 0.0);
            double y = tag.getDoubleOr(name + "_y", 0.0);
            double z = tag.getDoubleOr(name + "_z", 0.0);
            return new Vec3(x, y, z);
        }
        else if (targetType == BlockHitResult.class) {
            Vec3 pos = (Vec3) deserializeField(tag, name + "_pos", Vec3.class);
            if (pos == null) pos = Vec3.ZERO;

            String sideName = tag.getStringOr(name + "_side", "UP");
            Direction side;
            try {
                side = Direction.valueOf(sideName);
            } catch (IllegalArgumentException e) {
                side = Direction.UP;
            }

            BlockPos blockPos = (BlockPos) deserializeField(tag, name + "_blockPos", BlockPos.class);
            if (blockPos == null) blockPos = BlockPos.ZERO;

            boolean insideBlock = tag.getBooleanOr(name + "_insideBlock", false);
            return new BlockHitResult(pos, side, blockPos, insideBlock);
        }
        else if (targetType == HashedStack.class) {
            return HashedStack.EMPTY;
        }
        else if (Int2ObjectMap.class.isAssignableFrom(targetType)) {
            return deserializeInt2ObjectMap(tag.getCompound(name).orElse(new CompoundTag()));
        }
        else if (Map.class.isAssignableFrom(targetType)) {
            return deserializeMap(tag.getCompound(name).orElse(new CompoundTag()));
        }
        else if (List.class.isAssignableFrom(targetType)) {
            return deserializeList((ListTag) tag.get(name));
        }
        else if (tag.contains(name + "_toString")) {

            return null;
        }

        return null;
    }

    private static Int2ObjectMap<Object> deserializeInt2ObjectMap(CompoundTag mapTag) {
        Int2ObjectMap<Object> map = new Int2ObjectArrayMap<>();
        int size = mapTag.getIntOr("size", 0);

        for (int i = 0; i < size; i++) {
            String entryName = "entry_" + i;
            if (mapTag.contains(entryName)) {
                CompoundTag entryTag = mapTag.getCompound(entryName).orElse(new CompoundTag());
                int key = entryTag.getIntOr("key", 0);
                Object value = deserializeField(entryTag, "value", Object.class);
                if (value != null) {
                    map.put(key, value);
                }
            }
        }

        return map;
    }

    private static Map<Object, Object> deserializeMap(CompoundTag mapTag) {
        Map<Object, Object> map = new HashMap<>();
        int size = mapTag.getIntOr("size", 0);

        for (int i = 0; i < size; i++) {
            String entryName = "entry_" + i;
            if (mapTag.contains(entryName)) {
                CompoundTag entryTag = mapTag.getCompound(entryName).orElse(new CompoundTag());
                Object key = deserializeField(entryTag, "key", Object.class);
                Object value = deserializeField(entryTag, "value", Object.class);
                if (key != null) {
                    map.put(key, value);
                }
            }
        }

        return map;
    }

    private static List<Object> deserializeList(ListTag listTag) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag itemTag = (CompoundTag) listTag.get(i);
            Object value = deserializeField(itemTag, "value", Object.class);
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    private static net.minecraft.core.HolderLookup.Provider getRegistryManager() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.registryAccess();
        }
        if (Minecraft.getInstance().getConnection() != null) {
            return Minecraft.getInstance().getConnection().registryAccess();
        }
        return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    }

    public static net.minecraft.core.HolderLookup.Provider registries() {
        return getRegistryManager();
    }

    private static final long SAFE_NBT_LIMIT = 8L * 1024 * 1024;
    public static NbtAccounter safeNbtAccounter() { return NbtAccounter.create(SAFE_NBT_LIMIT); }

    private static volatile java.util.Set<String> knownPacketClassNames;
    private static boolean isKnownPacketClass(String className) {
        if (className == null || className.isBlank()) return false;
        java.util.Set<String> set = knownPacketClassNames;
        if (set == null) {
            java.util.Set<String> built = new java.util.HashSet<>();
            try {
                for (Class<?> c : AutismPacketRegistry.getC2SPackets()) built.add(c.getName());
                for (Class<?> c : AutismPacketRegistry.getS2CPackets()) built.add(c.getName());
            } catch (Throwable ignored) {  }
            knownPacketClassNames = set = built;
        }
        return set.contains(className);
    }

    private static UnsafeBridge getUnsafe() {
        return UnsafeBridge.INSTANCE;
    }

    private static final class UnsafeBridge {
        private static final UnsafeBridge INSTANCE = create();

        private final Object unsafe;
        private final Method objectFieldOffset;
        private final Method putInt;
        private final Method putBoolean;
        private final Method putByte;
        private final Method putShort;
        private final Method putLong;
        private final Method putFloat;
        private final Method putDouble;
        private final Method putChar;
        private final Method putObject;
        private final Method allocateInstance;

        private UnsafeBridge(Object unsafe, Class<?> type) throws NoSuchMethodException {
            this.unsafe = unsafe;
            this.objectFieldOffset = type.getMethod("objectFieldOffset", Field.class);
            this.putInt = type.getMethod("putInt", Object.class, long.class, int.class);
            this.putBoolean = type.getMethod("putBoolean", Object.class, long.class, boolean.class);
            this.putByte = type.getMethod("putByte", Object.class, long.class, byte.class);
            this.putShort = type.getMethod("putShort", Object.class, long.class, short.class);
            this.putLong = type.getMethod("putLong", Object.class, long.class, long.class);
            this.putFloat = type.getMethod("putFloat", Object.class, long.class, float.class);
            this.putDouble = type.getMethod("putDouble", Object.class, long.class, double.class);
            this.putChar = type.getMethod("putChar", Object.class, long.class, char.class);
            this.putObject = type.getMethod("putObject", Object.class, long.class, Object.class);
            this.allocateInstance = type.getMethod("allocateInstance", Class.class);
        }

        private static UnsafeBridge create() {
            try {
                Class<?> type = Class.forName("sun.misc.Unsafe");
                Field f = type.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return new UnsafeBridge(f.get(null), type);
            } catch (Exception e) {
                throw new RuntimeException("Cannot access Unsafe", e);
            }
        }

        long objectFieldOffset(Field field) {
            return invokeLong(objectFieldOffset, field);
        }

        void putInt(Object object, long offset, int value) { invokeVoid(putInt, object, offset, value); }
        void putBoolean(Object object, long offset, boolean value) { invokeVoid(putBoolean, object, offset, value); }
        void putByte(Object object, long offset, byte value) { invokeVoid(putByte, object, offset, value); }
        void putShort(Object object, long offset, short value) { invokeVoid(putShort, object, offset, value); }
        void putLong(Object object, long offset, long value) { invokeVoid(putLong, object, offset, value); }
        void putFloat(Object object, long offset, float value) { invokeVoid(putFloat, object, offset, value); }
        void putDouble(Object object, long offset, double value) { invokeVoid(putDouble, object, offset, value); }
        void putChar(Object object, long offset, char value) { invokeVoid(putChar, object, offset, value); }
        void putObject(Object object, long offset, Object value) { invokeVoid(putObject, object, offset, value); }
        Object allocateInstance(Class<?> type) { return invokeObject(allocateInstance, type); }

        private long invokeLong(Method method, Object... args) {
            try {
                return (Long) method.invoke(unsafe, args);
            } catch (Exception e) {
                throw new RuntimeException("Unsafe call failed", e);
            }
        }

        private void invokeVoid(Method method, Object... args) {
            try {
                method.invoke(unsafe, args);
            } catch (Exception e) {
                throw new RuntimeException("Unsafe call failed", e);
            }
        }

        private Object invokeObject(Method method, Object... args) {
            try {
                return method.invoke(unsafe, args);
            } catch (Exception e) {
                throw new RuntimeException("Unsafe call failed", e);
            }
        }
    }

    private static Object deserializeRecord(Class<?> recordClass, CompoundTag fieldsTag) throws Exception {
        RecordComponent[] components = recordClass.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            paramTypes[i] = component.getType();
            String name = component.getName();

            Object value = deserializeField(fieldsTag, name, component.getType());

            if (value == null) {

                if (component.getType() == int.class) value = 0;
                else if (component.getType() == boolean.class) value = false;
                else if (component.getType() == byte.class) value = (byte) 0;
                else if (component.getType() == short.class) value = (short) 0;
                else if (component.getType() == long.class) value = 0L;
                else if (component.getType() == float.class) value = 0.0f;
                else if (component.getType() == double.class) value = 0.0;
                else if (component.getType() == char.class) value = '\0';

                else {
                    value = getSafeDefault(component.getType(), name);
                }
            }

            args[i] = value;
        }

        Constructor<?> constructor = recordClass.getDeclaredConstructor(paramTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static Object getSafeDefault(Class<?> type, String fieldName) {

        if (type == CustomPacketPayload.class || CustomPacketPayload.class.isAssignableFrom(type)) {
            return new AutismPayloadSupport.RawCustomPacketPayload(Identifier.fromNamespaceAndPath("minecraft", "empty"), new byte[0]);
        }

        try {
            try {
                Field emptyField = type.getDeclaredField("EMPTY");
                if (Modifier.isStatic(emptyField.getModifiers())) {
                    emptyField.setAccessible(true);
                    Object empty = emptyField.get(null);
                    if (empty != null) {
                        AutismClientAddon.LOG.warn("[Autism] Using {}.EMPTY for unsupported field: {}",
                            type.getSimpleName(), fieldName);
                        return empty;
                    }
                }
            } catch (NoSuchFieldException ignored) {  }

            try {
                Field defaultField = type.getDeclaredField("DEFAULT");
                if (Modifier.isStatic(defaultField.getModifiers())) {
                    defaultField.setAccessible(true);
                    Object defaultValue = defaultField.get(null);
                    if (defaultValue != null) {
                        AutismClientAddon.LOG.warn("[Autism] Using {}.DEFAULT for unsupported field: {}",
                            type.getSimpleName(), fieldName);
                        return defaultValue;
                    }
                }
            } catch (NoSuchFieldException ignored) {  }

            if (type.isRecord()) {
                RecordComponent[] components = type.getRecordComponents();
                Class<?>[] paramTypes = new Class<?>[components.length];
                Object[] args = new Object[components.length];

                for (int i = 0; i < components.length; i++) {
                    paramTypes[i] = components[i].getType();

                    if (paramTypes[i] == byte[].class) {
                        args[i] = new byte[0];
                    } else if (paramTypes[i] == java.util.BitSet.class) {
                        args[i] = new java.util.BitSet();
                    } else if (paramTypes[i] == Vec3.class) {
                        args[i] = Vec3.ZERO;
                    } else if (paramTypes[i] == BlockHitResult.class) {
                        args[i] = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
                    } else if (paramTypes[i].isPrimitive()) {
                        args[i] = getPrimitiveDefault(paramTypes[i]);
                    } else if (paramTypes[i].isRecord()) {

                        try {
                            args[i] = getSafeDefault(paramTypes[i], components[i].getName());
                        } catch (Exception e) {
                            AutismClientAddon.LOG.warn("[Autism] Failed to create nested Record {}: {}",
                                paramTypes[i].getSimpleName(), e.getMessage());
                            args[i] = null;
                        }
                    } else {
                        args[i] = null;
                    }
                }

                Constructor<?> constructor = type.getDeclaredConstructor(paramTypes);
                constructor.setAccessible(true);
                Object instance = constructor.newInstance(args);
                AutismClientAddon.LOG.warn("[Autism] Created default {} for unsupported field: {}",
                    type.getSimpleName(), fieldName);
                return instance;
            }

        } catch (Exception e) {
            AutismClientAddon.LOG.warn("[Autism] Could not create safe default for {}: {}",
                type.getSimpleName(), e.getMessage());
        }

        AutismClientAddon.LOG.error("[Autism] No safe default found for unsupported type: {} field: {}",
            type.getSimpleName(), fieldName);
        return null;
    }

    private static Object getPrimitiveDefault(Class<?> type) {
        if (type == int.class) return 0;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }

    public static String serializeQueueToBase64(List<AutismSharedState.QueuedPacket> queue) {
        try {
            CompoundTag rootTag = new CompoundTag();
            ListTag packetList = new ListTag();

            for (AutismSharedState.QueuedPacket qp : queue) {
                CompoundTag packetTag = serializeQueuedPacket(qp);
                if (packetTag != null) {
                    packetList.add(packetTag);
                }
            }

            rootTag.put("packets", packetList);
            rootTag.putInt("version", 1);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            NbtIo.writeCompressed(rootTag, outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to serialize queue to Base64", e);
            return null;
        }
    }

    public static List<AutismSharedState.QueuedPacket> deserializeQueueFromBase64(String base64) {
        try {
            byte[] compressed = Base64.getDecoder().decode(base64);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(compressed);
            CompoundTag rootTag = NbtIo.readCompressed(inputStream, safeNbtAccounter());

            ListTag packetList = rootTag.getList("packets").orElse(new ListTag());
            List<AutismSharedState.QueuedPacket> queue = new ArrayList<>();

            for (int i = 0; i < packetList.size(); i++) {
                CompoundTag packetTag = (CompoundTag) packetList.get(i);
                AutismSharedState.QueuedPacket qp = deserializeQueuedPacket(packetTag);
                if (qp != null) {
                    queue.add(qp);
                }
            }

            AutismClientAddon.LOG.info("[Autism] Deserialized {} packets from Base64", queue.size());
            return queue.isEmpty() ? null : queue;
        } catch (Exception e) {
            AutismClientAddon.LOG.error("[Autism] Failed to deserialize queue from Base64", e);
            return null;
        }
    }
}
