package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.CompoundTag;

public class MoveAction implements MacroAction {
    public enum Direction {
        FORWARD, BACKWARD, LEFT, RIGHT;

        public String displayName() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }

        public Direction next() {
            Direction[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    public Direction direction = Direction.FORWARD;
    public int durationTicks = 20;

    public boolean nonBlocking = false;
    private boolean enabled = true;

    public MoveAction() {}

    public MoveAction(Direction direction, int durationTicks) {
        this.direction = direction;
        this.durationTicks = durationTicks;
    }

    public KeyMapping getKey(Minecraft mc) {
        if (mc.options == null) return null;
        return switch (direction) {
            case FORWARD -> mc.options.keyUp;
            case BACKWARD -> mc.options.keyDown;
            case LEFT -> mc.options.keyLeft;
            case RIGHT -> mc.options.keyRight;
        };
    }

    @Override
    public void execute(Minecraft mc) {
        KeyMapping key = getKey(mc);
        if (key != null) key.setDown(true);
    }

    public void release(Minecraft mc) {
        KeyMapping key = getKey(mc);
        if (key != null) key.setDown(false);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("direction", direction.name());
        tag.putInt("durationTicks", durationTicks);
        tag.putBoolean("nonBlocking", nonBlocking);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("direction")) {
            try { direction = Direction.valueOf(tag.getStringOr("direction", "FORWARD")); }
            catch (Exception e) { direction = Direction.FORWARD; }
        }
        if (tag.contains("durationTicks")) durationTicks = tag.getIntOr("durationTicks", 20);
        if (tag.contains("nonBlocking")) nonBlocking = tag.getBooleanOr("nonBlocking", false);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.MOVE;
    }

    @Override
    public String getDisplayName() {
        return "Move " + direction.displayName() + " (" + durationTicks + "t)" + (nonBlocking ? " [BG]" : "");
    }

    @Override
    public String getIcon() {
        return "MOV";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
