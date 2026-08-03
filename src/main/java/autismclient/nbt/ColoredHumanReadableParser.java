package autismclient.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ColoredHumanReadableParser implements NbtTagParser {
    private static final int LINE_SPLIT_THRESHOLD = 30;

    private static final ChatFormatting LIST_INDEX = ChatFormatting.GREEN;
    private static final ChatFormatting STRING = ChatFormatting.LIGHT_PURPLE;
    private static final ChatFormatting STRUCTURE = ChatFormatting.GRAY;
    private static final ChatFormatting TAG_NAME = ChatFormatting.GOLD;

    @Override
    public void parseTagToList(List<Component> tooltip, @Nullable Tag tag, boolean splitLines) {
        if (tag == null) {
            tooltip.add(Component.literal("No NBT tag").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            unwrapTag(tooltip, tag, "", "", "  ", splitLines);
        }
    }

    private void unwrapTag(List<Component> tooltip, Tag base, String pad, String tagName, String padIncrement, boolean splitLongStrings) {
        if (base instanceof CompoundTag compound) {
            addCompoundToTooltip(tooltip, compound, pad, padIncrement, splitLongStrings);
        } else if (base instanceof ListTag list) {
            addListToTooltip(tooltip, list, pad, padIncrement, splitLongStrings);
        } else {
            addValueToTooltip(tooltip, base, Component.literal(tagName).withStyle(TAG_NAME), pad, splitLongStrings);
        }
    }

    private void addCompoundToTooltip(List<Component> tooltip, CompoundTag tag, String pad, String padIncrement, boolean splitLongStrings) {
        for (String key : tag.keySet()) {
            Tag value = tag.get(key);
            if (value == null) continue;

            boolean nested = value instanceof ListTag || value instanceof CompoundTag;
            if (nested) {
                Component subtreeName = Component.literal(key).withStyle(TAG_NAME);
                Component intro = Component.literal(pad).append(subtreeName).append(Component.literal(": {").withStyle(STRUCTURE));
                tooltip.add(intro);
                unwrapTag(tooltip, value, pad + padIncrement, key, padIncrement, splitLongStrings);
                tooltip.add(Component.literal(pad + "}").withStyle(STRUCTURE));
            } else {
                addValueToTooltip(tooltip, value, Component.literal(key).withStyle(TAG_NAME), pad, splitLongStrings);
            }
        }
    }

    private void addListToTooltip(List<Component> tooltip, ListTag list, String pad, String padIncrement, boolean splitLongStrings) {
        for (int index = 0; index < list.size(); index++) {
            Tag element = list.get(index);
            Component bracketedIndex = Component.literal("[").withStyle(STRUCTURE)
                .append(Component.literal(String.valueOf(index)).withStyle(LIST_INDEX))
                .append(Component.literal("]").withStyle(STRUCTURE));
            if (element instanceof ListTag || element instanceof CompoundTag) {
                tooltip.add(Component.literal(pad + " ").append(bracketedIndex).append(Component.literal(": {").withStyle(STRUCTURE)));
                unwrapTag(tooltip, element, pad + padIncrement, "", padIncrement, splitLongStrings);
                tooltip.add(Component.literal(pad + "}").withStyle(STRUCTURE));
            } else {
                addValueToTooltip(tooltip, element, bracketedIndex, pad, splitLongStrings);
            }
        }
    }

    private void addValueToTooltip(List<Component> tooltip, Tag nbt, Component name, String pad, boolean splitLongStrings) {
        String content = nbt.toString();
        if (!splitLongStrings || content.length() < LINE_SPLIT_THRESHOLD) {
            tooltip.add(Component.literal(pad).append(name).append(Component.literal(": ")).append(Component.literal(content).withStyle(STRING)));
        } else {
            tooltip.add(Component.literal(pad).append(name).append(Component.literal(":")));
            int index = 0;
            while (index < content.length()) {
                int nextLength = Math.min(LINE_SPLIT_THRESHOLD, content.length() - index);
                String chunk = content.substring(index, index + nextLength);
                tooltip.add(Component.literal(pad + "   |").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" " + chunk).withStyle(STRING)));
                index += nextLength;
            }
        }
    }
}
