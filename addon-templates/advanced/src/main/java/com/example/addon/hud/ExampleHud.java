package com.example.addon.hud;

import com.example.addon.ExampleAddon;
import autismclient.api.hud.HudElementProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// A HUD element. Report your size via width()/height(); draw in render() with the GuiGraphicsExtractor
// (ctx.text / ctx.fill). The defaultX/Y/anchor/enabled are used until the user moves it in the HUD editor.
public final class ExampleHud implements HudElementProvider {
    private static final int MIN_WIDTH = 94;
    private static final int TEXT_PAD = 2;

    @Override public String id() { return ExampleAddon.ID + ":clock"; }
    @Override public String label() { return "Example Clock"; }
    @Override public String description() { return "Shows the in-game tick."; }

    @Override
    public int width() {
        Font font = Minecraft.getInstance().font;
        String text = text();
        int textWidth = font != null ? font.width(text) : text.length() * 6;
        return Math.max(MIN_WIDTH, textWidth + TEXT_PAD * 2);
    }

    @Override public int height() { return 10; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Font font, int x, int y, float alpha) {
        ctx.text(font, text(), x + TEXT_PAD, y, 0xFFFFD7FF);
    }

    private static String text() {
        long tick = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        return "Addon tick: " + tick;
    }

    @Override public boolean defaultEnabled() { return false; }
    @Override public String defaultAnchor() { return "TOP_LEFT"; }
    @Override public int defaultX() { return 4; }
    @Override public int defaultY() { return 120; }
}
