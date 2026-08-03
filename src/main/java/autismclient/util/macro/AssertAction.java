package autismclient.util.macro;

import autismclient.util.AutismClientMessaging;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

public class AssertAction implements MacroAction {
    public enum CheckType { HELD_ITEM, INVENTORY_ITEM, GUI_TYPE, LOOKING_AT_ENTITY, LOOKING_AT_CONTAINER_ENTITY, MOUNTED_ENTITY, HAS_BUNDLE, BUNDLE_V2_READY, HAS_WRITABLE_BOOK, CONNECTION, LOOKING_AT_BLOCK }
    public enum FailureBehavior { STOP_MACRO, WARN_ONLY }

    public CheckType check = CheckType.CONNECTION;
    public FailureBehavior failureBehavior = FailureBehavior.STOP_MACRO;
    public String itemName = "";
    public String guiType = "ANY";
    public String entityId = "";
    public String message = "";

    @Override
    public void execute(Minecraft mc) {
        if (passes(mc)) return;
        String out;
        if (message == null || message.isBlank()) {
            out = "Macro assert failed: " + check;
        } else {
            MacroTemplate.Resolution resolved = MacroVariables.resolve(message, mc);
            if (!resolved.success()) return;
            out = resolved.value();
        }
        AutismClientMessaging.sendPrefixed("\u00a7c" + out);
        if (failureBehavior == FailureBehavior.STOP_MACRO) MacroExecutor.stopCurrentActionRun();
    }

    public boolean passes(Minecraft mc) {
        if (mc == null) return false;
        MacroTemplate.Resolution itemResolution = MacroVariables.resolve(itemName, mc);
        MacroTemplate.Resolution entityResolution = MacroVariables.resolve(entityId, mc);
        MacroTemplate.Resolution guiResolution = MacroVariables.resolve(guiType, mc);
        if (!itemResolution.success() || !entityResolution.success() || !guiResolution.success()) return false;
        return switch (check) {
            case CONNECTION -> mc.getConnection() != null;
            case GUI_TYPE -> MacroGuiMatcher.matches(mc.gui.screen(), guiResolution.value(), "");
            case LOOKING_AT_ENTITY -> mc.crosshairPickEntity != null && matchesEntity(mc.crosshairPickEntity, entityResolution.value());
            case LOOKING_AT_CONTAINER_ENTITY -> mc.crosshairPickEntity != null && matchesEntity(mc.crosshairPickEntity, entityResolution.value()) && isContainerEntity(mc.crosshairPickEntity);
            case MOUNTED_ENTITY -> mc.player != null && mc.player.getVehicle() != null && matchesEntity(mc.player.getVehicle(), entityResolution.value());
            case LOOKING_AT_BLOCK -> mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
            case HAS_BUNDLE -> WaitInventoryPredicateAction.hasInventoryItem(mc, ItemTarget.fromLegacyEntry("minecraft:bundle"));
            case BUNDLE_V2_READY -> isBundleV2Ready(mc);
            case HAS_WRITABLE_BOOK -> WaitInventoryPredicateAction.hasInventoryItem(mc, ItemTarget.fromLegacyEntry("minecraft:writable_book"));
            case HELD_ITEM -> mc.player != null && ItemTarget.fromLegacyEntry(itemResolution.value()).score(mc.player.getMainHandItem(), mc.player.getInventory().getSelectedSlot()) >= 0;
            case INVENTORY_ITEM -> WaitInventoryPredicateAction.hasInventoryItem(mc, ItemTarget.fromLegacyEntry(itemResolution.value()));
        };
    }

    private boolean isBundleV2Ready(Minecraft mc) {
        if (mc == null || mc.player == null || !(mc.player.containerMenu instanceof InventoryMenu)) return false;
        ItemStack stack = mc.player.getInventory().getItem(0);
        if (stack == null || stack.isEmpty() || !stack.is(Items.BUNDLE)) return false;
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        return contents != null && contents.size() == 1;
    }

    private boolean matchesEntity(net.minecraft.world.entity.Entity entity, String expected) {
        if (entity == null) return false;
        if (expected == null || expected.isBlank()) return true;
        String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return type.equalsIgnoreCase(expected) || entity.getStringUUID().equalsIgnoreCase(expected)
            || entity.getName().getString().equalsIgnoreCase(expected);
    }

    private boolean isContainerEntity(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return false;
        String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(java.util.Locale.ROOT);
        return type.contains("chest_boat")
            || type.contains("chest_minecart")
            || type.contains("hopper_minecart")
            || type.contains("furnace_minecart")
            || type.contains("llama")
            || type.contains("donkey")
            || type.contains("mule");
    }

    @Override public MacroActionType getType() { return MacroActionType.ASSERT; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "ASSERT");
        tag.putString("check", check.name());
        tag.putString("failureBehavior", failureBehavior.name());
        tag.putString("itemName", itemName);
        tag.putString("guiType", guiType);
        tag.putString("entityId", entityId);
        tag.putString("message", message);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        check = MacroStringList.enumValue(CheckType.class, tag.getStringOr("check", "CONNECTION"), CheckType.CONNECTION);
        failureBehavior = MacroStringList.enumValue(FailureBehavior.class, tag.getStringOr("failureBehavior", "STOP_MACRO"), FailureBehavior.STOP_MACRO);
        itemName = tag.getStringOr("itemName", "");
        guiType = tag.getStringOr("guiType", "ANY");
        entityId = tag.getStringOr("entityId", "");
        message = tag.getStringOr("message", "");
    }

    @Override public String getDisplayName() { return "Assert " + check; }
    @Override public String getIcon() { return "!"; }
}
