package com.example.addon.macro;

import com.example.addon.ExampleAddon;
import autismclient.api.macro.ActionSchema;
import autismclient.api.macro.AddonContextAction;
import autismclient.api.macro.MacroExecutionContext;
import net.minecraft.nbt.CompoundTag;

// A macro condition: a step that waits. Extend AddonContextAction and block in run(), e.g. with
// ctx.awaitCondition(...). Register it with .condition(...) so it lands in the condition picker.
public final class ExampleHeightCondition extends AddonContextAction {
    public static final String TYPE_ID = ExampleAddon.ID + ":wait-height";

    public double minY = 64.0;

    public ExampleHeightCondition() {
        super(TYPE_ID);
    }

    @Override
    public void run(MacroExecutionContext ctx) throws InterruptedException {
        ctx.setStatus("Waiting until Y >= " + minY);
        ctx.awaitCondition(new ExampleCondition(minY));
    }

    @Override
    protected void save(CompoundTag tag) {
        putDouble(tag, "minY", minY);
    }

    @Override
    protected void load(CompoundTag tag) {
        minY = getDouble(tag, "minY", minY);
    }

    @Override
    protected ActionSchema schema() {
        return ActionSchema.builder().decimal("minY", "Min Y").decRange(-64, 320).build();
    }

    @Override
    public String getDisplayName() {
        return "Wait until Y >= " + minY;
    }

    @Override
    public String getIcon() {
        return "Y";
    }
}
