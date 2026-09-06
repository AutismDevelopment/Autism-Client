package autismclient.api.macro;

import autismclient.util.macro.MacroAction;
import net.minecraft.nbt.CompoundTag;

public abstract class AddonAction implements MacroAction {
    private final String typeId;

    protected AddonAction(String typeId) {
        this.typeId = typeId;
    }

    protected abstract void run(net.minecraft.client.Minecraft mc);

    protected void save(CompoundTag tag) {}

    protected void load(CompoundTag tag) {}

    protected ActionSchema schema() {
        return null;
    }

    @Override
    public final void execute(net.minecraft.client.Minecraft mc) {
        run(mc);
    }

    @Override
    public final String getTypeId() {
        return typeId;
    }

    @Override
    public final CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", typeId);
        save(tag);
        return tag;
    }

    @Override
    public final void fromTag(CompoundTag tag) {
        load(tag);
    }

    protected static void putString(CompoundTag tag, String key, String value) { tag.putString(key, value == null ? "" : value); }
    protected static void putInt(CompoundTag tag, String key, int value) { tag.putInt(key, value); }
    protected static void putDouble(CompoundTag tag, String key, double value) { tag.putDouble(key, value); }
    protected static void putBool(CompoundTag tag, String key, boolean value) { tag.putBoolean(key, value); }

    protected static String getString(CompoundTag tag, String key, String def) { return tag.getStringOr(key, def); }
    protected static int getInt(CompoundTag tag, String key, int def) { return tag.getIntOr(key, def); }
    protected static double getDouble(CompoundTag tag, String key, double def) { return tag.getDoubleOr(key, def); }
    protected static boolean getBool(CompoundTag tag, String key, boolean def) { return tag.getBooleanOr(key, def); }

    public static ActionSchema schemaOf(MacroAction action) {
        return action instanceof AddonAction a ? a.schema()
            : action instanceof AddonContextAction c ? c.schemaInternal()
            : null;
    }
}
