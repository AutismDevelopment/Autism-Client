package com.example.addon.macro;

import autismclient.api.macro.AddonCondition;
import net.minecraft.client.Minecraft;

// A reusable predicate: implement check(). A waiting step blocks on it via ctx.awaitCondition(...).
public final class ExampleCondition extends AddonCondition {
    private final double minY;

    public ExampleCondition(double minY) {
        this.minY = minY;
    }

    @Override
    public boolean check(Minecraft mc) {
        return mc.player != null && mc.player.getY() >= minY;
    }
}
