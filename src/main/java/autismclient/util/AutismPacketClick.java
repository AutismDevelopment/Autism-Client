package autismclient.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;

public final class AutismPacketClick {
    private AutismPacketClick() {}

    public enum Mode {
        LEFT_CLICK("Left Click", "Left", 0, ContainerInput.PICKUP),
        RIGHT_CLICK("Right Click", "Right", 1, ContainerInput.PICKUP),
        QUICK_MOVE("Quick Move", "QMove", 0, ContainerInput.QUICK_MOVE);

        public final String displayName;
        public final String shortName;
        public final int button;
        public final ContainerInput input;

        Mode(String displayName, String shortName, int button, ContainerInput input) {
            this.displayName = displayName;
            this.shortName = shortName;
            this.button = button;
            this.input = input;
        }

        public static Mode byIndex(int index) {
            Mode[] values = values();
            if (index < 0 || index >= values.length) return LEFT_CLICK;
            return values[index];
        }

        public static Mode fromName(String name) {
            if (name != null) {
                for (Mode mode : values()) {
                    if (mode.name().equalsIgnoreCase(name)
                        || mode.displayName.equalsIgnoreCase(name)
                        || mode.shortName.equalsIgnoreCase(name)) {
                        return mode;
                    }
                }
            }
            return LEFT_CLICK;
        }
    }

    public record Target(
        int containerId,
        int stateId,
        int handlerSlot,
        int visibleSlot,
        String screenTitle,
        String menuClass,
        String itemSummary,
        Mode mode,
        long capturedAtMs
    ) {
        public ServerboundContainerClickPacket buildPacket() {
            Mode effectiveMode = mode == null ? Mode.LEFT_CLICK : mode;
            return new ServerboundContainerClickPacket(
                containerId,
                stateId,
                (short) handlerSlot,
                (byte) effectiveMode.button,
                effectiveMode.input,
                new Int2ObjectArrayMap<>(),
                HashedStack.EMPTY
            );
        }

        public Target withMode(Mode newMode) {
            return new Target(
                containerId,
                stateId,
                handlerSlot,
                visibleSlot,
                screenTitle,
                menuClass,
                itemSummary,
                newMode == null ? Mode.LEFT_CLICK : newMode,
                capturedAtMs
            );
        }

        public String summary() {
            String modeName = (mode == null ? Mode.LEFT_CLICK : mode).displayName;
            String item = itemSummary == null || itemSummary.isBlank() ? "empty" : itemSummary;
            return modeName + " slot " + visibleSlot + " [" + item + "]"
                + " menu " + containerId + ":" + stateId;
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("containerId", containerId);
            tag.putInt("stateId", stateId);
            tag.putInt("handlerSlot", handlerSlot);
            tag.putInt("visibleSlot", visibleSlot);
            tag.putString("screenTitle", screenTitle == null ? "" : screenTitle);
            tag.putString("menuClass", menuClass == null ? "" : menuClass);
            tag.putString("itemSummary", itemSummary == null ? "" : itemSummary);
            tag.putString("mode", (mode == null ? Mode.LEFT_CLICK : mode).name());
            tag.putLong("capturedAtMs", capturedAtMs);
            return tag;
        }

        public static Target fromTag(CompoundTag tag) {
            if (tag == null) return null;
            int containerId = tag.getIntOr("containerId", -1);
            int stateId = tag.getIntOr("stateId", 0);
            int handlerSlot = tag.getIntOr("handlerSlot", -1);
            int visibleSlot = tag.getIntOr("visibleSlot", handlerSlot);
            if (containerId < 0 || handlerSlot < 0) return null;
            return new Target(
                containerId,
                stateId,
                handlerSlot,
                visibleSlot,
                tag.getStringOr("screenTitle", ""),
                tag.getStringOr("menuClass", ""),
                tag.getStringOr("itemSummary", ""),
                Mode.fromName(tag.getStringOr("mode", Mode.LEFT_CLICK.name())),
                tag.getLongOr("capturedAtMs", 0L)
            );
        }
    }
}
