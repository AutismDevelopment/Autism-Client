package autismclient.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismBlockNbtInspectorTest {
    @Test
    void synchronizedContentsKeepSlotsAndNestedItemData() {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:shulker_box");
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:shulker_box");
        entity.putString("CustomName", "Plugin Chest");

        AutismBlockNbtCapture.Snapshot snapshot = new AutismBlockNbtCapture.Snapshot(
            "minecraft:overworld", new BlockPos(4, 70, -8), "minecraft:shulker_box", state);
        CompoundTag payload = AutismBlockNbtCapture.payload(snapshot, entity, "client");
        CompoundTag item = new CompoundTag();
        item.putByte("Slot", (byte) 5);
        item.putString("id", "minecraft:diamond");
        item.putInt("count", 3);
        CompoundTag components = new CompoundTag();
        CompoundTag custom = new CompoundTag();
        custom.putString("plugin:owner", "full-data");
        components.put("minecraft:custom_data", custom);
        item.put("components", components);
        ListTag items = new ListTag();
        items.add(item);

        CompoundTag complete = AutismBlockNbtCapture.attachEncodedContents(
            payload, items, 27, "Plugin Chest", "minecraft:generic_9x3");

        assertTrue(complete.getBooleanOr("contents_available", false));
        assertTrue(complete.getIntOr("container_slots", 0) == 27);
        assertTrue(complete.toString().contains("plugin:owner"));
        assertTrue(complete.toString().contains("full-data"));
        assertTrue(complete.toString().contains("Slot:5b"));
    }

    @Test
    void emptySynchronizedContainerIsStillReported() {
        CompoundTag payload = new CompoundTag();
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:chest");
        payload.put("state", state);
        payload.putBoolean("contents_available", true);
        payload.putInt("container_slots", 27);

        CompoundTag complete = AutismBlockNbtCapture.attachEncodedContents(
            payload, new ListTag(), 27, "Chest", "minecraft:generic_9x3");

        assertTrue(complete.getBooleanOr("contents_available", false));
        assertTrue(complete.getCompound("block_entity").orElseThrow().getList("Items").orElseThrow().isEmpty());
        assertFalse(complete.toString().contains("No item stack"));
    }
}
