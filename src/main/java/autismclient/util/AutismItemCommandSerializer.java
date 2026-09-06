package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AutismItemCommandSerializer {
    private static final Minecraft MC = Minecraft.getInstance();

    private AutismItemCommandSerializer() {
    }

    public static String giveCommand(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String components = componentPatch(stack);
        return "/give @p " + id + components + " " + Math.max(1, Math.min(99, stack.getCount()));
    }

    public static String itemStackSnbt(ItemStack stack) {
        if (stack == null || stack.isEmpty() || MC.player == null) return "{}";
        try {
            Tag tag = ItemStack.CODEC.encodeStart(
                MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack
            ).getOrThrow();
            return tagToSnbt(tag);
        } catch (Throwable t) {
            return "{error:\"" + snbtString(t.getClass().getSimpleName()) + "\"}";
        }
    }

    public static ItemStack itemStackFromSnbt(String snbt) {
        if (snbt == null || snbt.isBlank() || MC.player == null) return ItemStack.EMPTY;
        try {
            CompoundTag tag = TagParser.parseCompoundFully(snbt.trim());
            return ItemStack.CODEC.parse(
                MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag
            ).getOrThrow();
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static String validationError(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "item stack is empty";
        return ItemStack.validateStrict(stack).error().map(Object::toString).orElse("");
    }

    public static String componentPatch(ItemStack stack) {
        if (stack == null || stack.isEmpty() || MC.player == null) return "";
        try {
            Tag encoded = DataComponentPatch.CODEC.encodeStart(
                MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack.getComponentsPatch()
            ).getOrThrow();
            if (!(encoded instanceof CompoundTag compound) || compound.isEmpty()) return "";

            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Tag> entry : compound.entrySet()) {
                String key = componentCommandKey(entry.getKey());
                Tag value = entry.getValue();
                if (key.isBlank() || value == null) continue;
                if (key.charAt(0) == '!') {
                    parts.add(key);
                } else {
                    parts.add(key + "=" + tagToSnbt(value));
                }
            }
            return parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]";
        } catch (Throwable t) {
            return "";
        }
    }

    public static String tagToSnbt(Tag tag) {
        return tag == null ? "" : tag.toString();
    }

    public static String snbtString(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String componentCommandKey(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String key = raw.trim();
        boolean removed = key.startsWith("!");
        if (removed) key = key.substring(1);
        if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
        return removed ? "!" + key : key;
    }
}
