package com.example.addon.macro;

import autismclient.util.macro.MacroAction;

import java.util.ArrayList;
import java.util.List;

// Presets insert a ready-made stack of steps. Return a fresh list each call; don't reuse instances.
public final class ExamplePresets {
    private ExamplePresets() {}

    // Wait until the player is high up, then announce it.
    public static List<MacroAction> reachHeightThenSay() {
        List<MacroAction> stack = new ArrayList<>();

        ExampleHeightCondition waitForHeight = new ExampleHeightCondition();
        waitForHeight.minY = 80.0;
        stack.add(waitForHeight);

        ExampleSayAction say = new ExampleSayAction();
        say.message = "Reached Y 80!";
        stack.add(say);

        return stack;
    }
}
