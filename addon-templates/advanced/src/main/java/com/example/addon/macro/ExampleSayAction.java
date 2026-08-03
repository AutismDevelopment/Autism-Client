package com.example.addon.macro;

import com.example.addon.ExampleAddon;
import autismclient.api.macro.ActionSchema;
import autismclient.api.macro.AddonAction;
import autismclient.util.AutismClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

// A macro action: runs once when the step is reached. Extend AddonAction and pass a namespaced id.
public final class ExampleSayAction extends AddonAction {
    public static final String TYPE_ID = ExampleAddon.ID + ":say";

    public String message = "Hello from an addon!";

    public ExampleSayAction() {
        super(TYPE_ID);
    }

    @Override
    protected void run(Minecraft mc) {
        AutismClientMessaging.sendPrefixed("\u00a7d[Example] " + message);
    }

    // Field keys here, in load(), and in the schema must match.
    @Override
    protected void save(CompoundTag tag) {
        putString(tag, "message", message);
    }

    @Override
    protected void load(CompoundTag tag) {
        message = getString(tag, "message", message);
    }

    @Override
    protected ActionSchema schema() {
        return ActionSchema.builder().text("message", "Say").build();
    }

    @Override
    public String getDisplayName() {
        return "Say: " + message;
    }

    @Override
    public String getIcon() {
        return "S";
    }
}
