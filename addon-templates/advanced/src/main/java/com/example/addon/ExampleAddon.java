package com.example.addon;

import autismclient.api.AutismAddon;
import autismclient.api.AutismAddons;
import autismclient.api.ApiVersion;
import autismclient.api.macro.MacroActionEntry;

import com.example.addon.commands.ExampleCommand;
import com.example.addon.events.ExampleEvents;
import com.example.addon.hud.ExampleHud;
import com.example.addon.macro.ExampleHeightCondition;
import com.example.addon.macro.ExamplePresets;
import com.example.addon.macro.ExampleSayAction;
import com.example.addon.modules.ExampleModule;

// Addon entrypoint (the "autism" entrypoint in fabric.mod.json). Start small:
// register only ExampleModule first, launch the client, then add macro/actions/HUD/events as needed.
// Modules, actions, conditions and presets all auto-group under a category named after this addon.
public final class ExampleAddon extends AutismAddon {
    public static final String ID = "autism-advanced-addon-template";

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public void onInitialize() {
        AutismAddons.modules().register(new ExampleModule());

        // Action: appears in the "add action" picker.
        AutismAddons.macroActions().register(MacroActionEntry.local("say", ExampleSayAction::new)
            .picker("Say", "Send a chat message.")
            .build());

        // Condition: appears in the "add condition" picker (it waits).
        AutismAddons.macroActions().register(MacroActionEntry.local("wait-height", ExampleHeightCondition::new)
            .condition("Wait Height", "Wait until the player is at or above a Y level.")
            .build());

        AutismAddons.presets().register("Reach Height", "Wait for Y 80, then say.",
            ExamplePresets::reachHeightThenSay);

        AutismAddons.commands().register(new ExampleCommand());
        AutismAddons.hud().register(new ExampleHud());
        ExampleEvents.register();
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
