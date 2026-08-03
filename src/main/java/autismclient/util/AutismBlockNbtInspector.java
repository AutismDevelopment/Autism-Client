package autismclient.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class AutismBlockNbtInspector {
    private static final int RAW_DISPLAY_CHAR_CAP = 40_000;

    private AutismBlockNbtInspector() {
    }

    public static BlockInspection inspect(CompoundTag source, List<ItemStack> containerItems, String note) {
        CompoundTag root = source == null ? new CompoundTag() : source.copy();
        List<ItemStack> items = copyItems(containerItems);
        CompoundTag state = root.getCompound("state").orElse(new CompoundTag());
        String blockId = state.getStringOr("Name", "minecraft:air");
        String raw = root.toString();

        List<AutismItemNbtInspector.InspectionLine> nice = new ArrayList<>();
        section(nice, "Identity", AutismColors.packetLightYellow());
        line(nice, "Block: " + blockId, AutismColors.packetWhite());
        line(nice, "State: " + root.getStringOr("block_state", blockId), AutismColors.textSecondary());
        line(nice, "Dimension: " + root.getStringOr("dimension", "<unknown>"), AutismColors.textSecondary());
        int[] pos = root.getIntArray("position").orElse(new int[0]);
        line(nice, "Position: " + position(pos), AutismColors.textSecondary());
        line(nice, "Source: " + root.getStringOr("source", "client"), AutismColors.textMuted());

        CompoundTag entity = root.getCompound("block_entity").orElse(null);
        if (entity != null) {
            blank(nice);
            section(nice, "Block Entity", AutismColors.packetCyan());
            line(nice, "Type: " + entity.getStringOr("id", "<unknown>"), AutismColors.packetWhite());
            line(nice, "Fields: " + entity.size(), AutismColors.textSecondary());
        }

        if (!items.isEmpty() || root.getBooleanOr("contents_available", false)) {
            blank(nice);
            section(nice, "Contents", AutismColors.packetGreen());
            line(nice, "Slots: " + root.getIntOr("container_slots", items.size()), AutismColors.textSecondary());
            int nonEmpty = 0;
            for (int slot = 0; slot < items.size(); slot++) {
                ItemStack stack = items.get(slot);
                if (stack == null || stack.isEmpty()) continue;
                nonEmpty++;
                String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                line(nice, "Slot " + slot + ": " + stack.getCount() + "x " + id
                    + " (" + stack.getHoverName().getString() + ")", AutismColors.packetWhite());
            }
            if (nonEmpty == 0) line(nice, "Container is empty.", AutismColors.textMuted());
        }

        if (note != null && !note.isBlank()) {
            blank(nice);
            section(nice, "Access", AutismColors.packetOrange());
            line(nice, note, AutismColors.textSecondary());
        }

        List<AutismItemNbtInspector.InspectionLine> rawLines = new ArrayList<>();
        section(rawLines, "Raw Block SNBT", AutismColors.packetLightYellow());
        boolean truncated = raw.length() > RAW_DISPLAY_CHAR_CAP;
        String shown = truncated ? raw.substring(0, RAW_DISPLAY_CHAR_CAP) : raw;
        for (String rawLine : AutismItemNbtInspector.prettySnbtLines(shown)) {
            rawLines.add(new AutismItemNbtInspector.InspectionLine(rawLine, AutismColors.packetWhite(),
                AutismItemNbtInspector.tokenizeStructuredText(rawLine, AutismColors.packetWhite())));
        }
        if (truncated) {
            line(rawLines, "... (display truncated; Copy Raw keeps everything)", AutismColors.textMuted());
        }

        StringBuilder pretty = new StringBuilder("Block NBT - ").append(blockId);
        for (AutismItemNbtInspector.InspectionLine inspectionLine : nice) {
            pretty.append('\n').append(inspectionLine.text());
        }
        return new BlockInspection(blockId, List.copyOf(nice), List.copyOf(rawLines), pretty.toString(), raw);
    }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<ItemStack> copy = new ArrayList<>(source.size());
        for (ItemStack stack : source) copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        return List.copyOf(copy);
    }

    private static String position(int[] pos) {
        return pos.length >= 3 ? pos[0] + ", " + pos[1] + ", " + pos[2] : "<unknown>";
    }

    private static void section(List<AutismItemNbtInspector.InspectionLine> lines, String title, int color) {
        line(lines, "[" + title + "]", color);
    }

    private static void line(List<AutismItemNbtInspector.InspectionLine> lines, String text, int color) {
        lines.add(new AutismItemNbtInspector.InspectionLine(text == null ? "" : text, color));
    }

    private static void blank(List<AutismItemNbtInspector.InspectionLine> lines) {
        line(lines, "", AutismColors.textMuted());
    }

    public record BlockInspection(String title, List<AutismItemNbtInspector.InspectionLine> niceLines,
                                  List<AutismItemNbtInspector.InspectionLine> rawLines,
                                  String prettyCopyText, String rawCopyText)
        implements AutismItemNbtInspector.Inspection {

        @Override
        public String windowTitle() {
            return "Block NBT - " + title;
        }

        @Override
        public String subject() {
            return "block";
        }

        @Override
        public ItemStack stack() {
            return ItemStack.EMPTY;
        }
    }
}
