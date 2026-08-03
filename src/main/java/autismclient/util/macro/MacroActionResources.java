package autismclient.util.macro;

import java.util.EnumSet;
import java.util.List;

public final class MacroActionResources {
    public enum SharedClientResource { INVENTORY, GUI, INPUT, ROTATION, BARITONE, NETWORK, PACKET_DELAY, WORLD_ACTION }

    private MacroActionResources() {
    }

    public static EnumSet<SharedClientResource> resourcesForAction(MacroAction action) {
        EnumSet<SharedClientResource> resources = EnumSet.noneOf(SharedClientResource.class);
        if (action instanceof ClickAction
            || action instanceof InventoryAction
            || action instanceof ItemAction
            || action instanceof DropAction
            || action instanceof CraftAction
            || action instanceof StoreItemAction
            || action instanceof InventoryAuditAction
            || action instanceof XCarryAction
            || action instanceof SwapSlotsAction
            || action instanceof SelectSlotAction) {
            resources.add(SharedClientResource.INVENTORY);
            resources.add(SharedClientResource.GUI);
        }
        if (action instanceof WaitDurabilityAction durability && durability.useNext) {
            resources.add(SharedClientResource.INVENTORY);
            resources.add(SharedClientResource.GUI);
        }
        if (action instanceof OpenContainerAction
            || action instanceof InteractEntityAction
            || action instanceof CloseGuiAction
            || action instanceof SaveGuiAction
            || action instanceof RestoreGuiAction
            || action instanceof DesyncAction
            || action instanceof NbtBookAction
            || action instanceof CustomMenuAction) {
            resources.add(SharedClientResource.GUI);
            resources.add(SharedClientResource.NETWORK);
        }
        if (action instanceof SendPacketAction
            || action instanceof PacketAction
            || action instanceof PacketClickAction
            || action instanceof PayloadAction
            || action instanceof PayAction
            || action instanceof SendChatAction) {
            resources.add(SharedClientResource.NETWORK);
        }
        if (action instanceof DelayPacketsAction) {
            resources.add(SharedClientResource.NETWORK);
            resources.add(SharedClientResource.PACKET_DELAY);
        }
        if (action instanceof UseItemAction) {
            resources.add(SharedClientResource.INPUT);
            resources.add(SharedClientResource.NETWORK);
        }
        if (action instanceof MoveAction
            || action instanceof SneakAction
            || action instanceof SprintAction
            || action instanceof JumpAction) {
            resources.add(SharedClientResource.INPUT);
        }
        if (action instanceof RotateAction || action instanceof LookAtBlockAction) {
            resources.add(SharedClientResource.ROTATION);
        }
        if (action instanceof GoToAction || action instanceof MineAction || action instanceof InstaBreakAction
            || action instanceof BreakAction) {
            resources.add(SharedClientResource.BARITONE);
            resources.add(SharedClientResource.INPUT);
            resources.add(SharedClientResource.ROTATION);
            resources.add(SharedClientResource.NETWORK);
        }
        if (action instanceof ToggleModuleAction) {
            resources.add(SharedClientResource.WORLD_ACTION);
        }
        return resources;
    }

    public static EnumSet<SharedClientResource> resourcesForActions(List<MacroAction> actions) {
        EnumSet<SharedClientResource> resources = EnumSet.noneOf(SharedClientResource.class);
        if (actions == null) return resources;
        for (MacroAction action : actions) {
            if (action != null && action.isEnabled() && RaceAction.isBodyAction(action)) {
                resources.addAll(resourcesForAction(action));
            }
        }
        return resources;
    }
}
