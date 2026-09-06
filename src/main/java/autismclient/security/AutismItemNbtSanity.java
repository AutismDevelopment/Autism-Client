package autismclient.security;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;

public final class AutismItemNbtSanity {
    static final int MAX_TOOLTIP_LINES = 512;

    private AutismItemNbtSanity() {
    }

    public static boolean trimTooltipLines(List<Component> lines) {
        if (lines == null || lines.size() <= MAX_TOOLTIP_LINES) return false;
        int hidden = lines.size() - (MAX_TOOLTIP_LINES - 1);
        while (lines.size() > MAX_TOOLTIP_LINES - 1) {
            lines.remove(lines.size() - 1);
        }
        lines.add(Component.literal("... (huge NBT item: " + hidden + " tooltip lines hidden)")
            .withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    public static int scrubUnsafeTooltipLines(List<Component> lines) {
        if (lines == null || lines.isEmpty()) return 0;
        int replaced = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (!AutismComponentSanity.isSafe(lines.get(i))) {
                lines.set(i, Component.literal("[unsafe tooltip line removed]")
                    .withStyle(ChatFormatting.DARK_GRAY));
                replaced++;
            }
        }
        return replaced;
    }

    public static int encodedSizeBytes(ItemStack stack, RegistryAccess access) {
        if (stack == null || stack.isEmpty() || access == null) return -1;
        try {
            CompoundTag nbt = (CompoundTag) ItemStack.CODEC.encodeStart(
                access.createSerializationContext(NbtOps.INSTANCE), stack
            ).getOrThrow();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.write(nbt, new DataOutputStream(baos));
            return baos.size();
        } catch (Throwable t) {
            return -1;
        }
    }
}
