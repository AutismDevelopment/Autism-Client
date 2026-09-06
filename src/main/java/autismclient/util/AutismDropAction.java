package autismclient.util;

public enum AutismDropAction {

    PICKUP("Pickup",         "Pick",    false),
    QUICK_MOVE("Quick Move", "QMove",   false),
    SWAP("Swap",             "Swap",    false),
    CLONE("Clone",           "Clone",   false),
    THROW("Throw (2x)",      "Throw",   false),
    QUICK_CRAFT("Quick Craft","QCraft", false),
    PICKUP_ALL("Pick Up All", "All", false),
    DROP_ITEM("Drop Item (1x)", "Drop1",  false),
    DROP_STACK("Drop Stack",    "DropStk",false);

    public final String displayName;
    public final String shortName;
    public final boolean isPlayerAction;

    AutismDropAction(String displayName, String shortName, boolean isPlayerAction) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.isPlayerAction = isPlayerAction;
    }

    public boolean isSlotAction() {
        return !isPlayerAction;
    }

    public boolean usesFixedButton() {
        return getButton() >= 0;
    }

    public int getButton() {
        return switch(this) {
            case QUICK_MOVE -> 0;
            case CLONE -> 2;
            case PICKUP_ALL -> 0;
            case DROP_ITEM -> 0;
            case DROP_STACK -> 1;
            default -> -1;
        };
    }

    public net.minecraft.world.inventory.ContainerInput toContainerInput() {
        if (isPlayerAction) {
            throw new IllegalStateException("Cannot convert PlayerAction to ContainerInput: " + this);
        }

        return switch(this) {
            case PICKUP -> net.minecraft.world.inventory.ContainerInput.PICKUP;
            case QUICK_MOVE -> net.minecraft.world.inventory.ContainerInput.QUICK_MOVE;
            case SWAP -> net.minecraft.world.inventory.ContainerInput.SWAP;
            case CLONE -> net.minecraft.world.inventory.ContainerInput.CLONE;
            case THROW, DROP_ITEM, DROP_STACK -> net.minecraft.world.inventory.ContainerInput.THROW;
            case QUICK_CRAFT -> net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT;
            case PICKUP_ALL -> net.minecraft.world.inventory.ContainerInput.PICKUP_ALL;
            default -> throw new IllegalStateException("Unknown action: " + this);
        };
    }
}
