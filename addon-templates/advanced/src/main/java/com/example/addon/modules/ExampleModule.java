package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.EnumSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.util.AutismClientMessaging;

import java.util.Locale;

// A toggleable module built on the typed setting API. Declare each setting as a final field with
// add(new XSetting(...)) and read it at runtime with field.get() — no string ids, no casting. An EnumSetting
// is backed by a real enum, so reads are compiler-checked (style.get() == Style.LOUD). Visibility is a plain
// predicate over other settings. With no category, the module auto-lands under a menu column named after your addon.
public final class ExampleModule extends Module {
    public enum Style { FRIENDLY, LOUD }

    private final BoolSetting greet = add(new BoolSetting("greet", "Greet on enable", true)
        .group("General"));
    private final IntSetting amount = add(new IntSetting("amount", "Amount", 3, 1, 10, 1)
        .group("General"));
    private final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.FRIENDLY, Style.values())
        .group("Display"));
    private final BoolSetting shout = add(new BoolSetting("shout", "Shout in caps", false)
        .group("Display")
        .visibleWhen(() -> style.get() == Style.LOUD));

    public ExampleModule() {
        super(ExampleAddon.ID + ":example", "Example", "Demonstrates an AUTISM addon module.");
    }

    @Override
    public void onEnable() {
        if (greet.get()) {
            String msg = "[Example] enabled - amount " + amount.get() + ", style " + style.get();
            if (shout.get()) msg = msg.toUpperCase(Locale.ROOT);
            AutismClientMessaging.sendPrefixed("§b" + msg);
        }
    }
}
