package autismclient.gui;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiTextRenderer;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AutismThemeApplyOverlay extends Overlay {
    private static final Object LOCK = new Object();
    private static final long MIN_VISIBLE_MS = 300L;
    private static final long MAX_VISIBLE_MS = 10_000L;

    private static int totalJobs;
    private static int doneJobs;
    private static String currentTitle = "Applying Theme";
    private static String currentLabel = "";
    private static boolean shown;

    private static Runnable deferredAction;

    private final UiTextRenderer text = new UiTextRenderer(Minecraft.getInstance().font);
    private long shownAtMs = -1L;
    private float easedProgress;
    private int framesRendered;

    private AutismThemeApplyOverlay() {}

    public static Runnable beginJob(String label) {
        return beginJob(null, label);
    }

    public static Runnable beginJob(String title, String label) {
        synchronized (LOCK) {
            if (doneJobs >= totalJobs) {
                totalJobs = 0;
                doneJobs = 0;
                currentTitle = (title == null || title.isBlank()) ? "Applying Theme" : title;
            }
            totalJobs++;
            currentLabel = label == null ? "" : label;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (shown || mc.gui.overlay() != null) return;
            shown = true;
            mc.gui.setOverlay(new AutismThemeApplyOverlay());
        });
        AtomicBoolean completed = new AtomicBoolean();
        return () -> {
            if (!completed.compareAndSet(false, true)) return;
            synchronized (LOCK) {
                doneJobs = Math.min(totalJobs, doneJobs + 1);
            }
        };
    }

    public static void runAfterShown(Runnable action) {
        synchronized (LOCK) {
            deferredAction = action;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (shownAtMs < 0) shownAtMs = now;

        Screen screen = mc.gui.screen();
        if (screen != null) {
            try {
                screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, delta);
            } catch (Exception ignored) {  }
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.nextStratum();
        UiRenderer.rect(graphics, UiBounds.of(0, 0, width, height), 0xA8000000);

        float target;
        String title;
        String label;
        boolean finished;
        synchronized (LOCK) {
            target = totalJobs == 0 ? 1.0f : doneJobs / (float) totalJobs;
            title = currentTitle;
            label = currentLabel;
            finished = doneJobs >= totalJobs;
        }
        easedProgress = Mth.clamp(easedProgress + (target - easedProgress) * (finished ? 0.35f : 0.15f), 0.0f, 1.0f);
        if (finished && easedProgress > 0.98f) easedProgress = 1.0f;

        int panelW = Math.min(260, width - 40);
        int panelH = 62;
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;
        int border = AutismTheme.recolor(0xFFB32B2B, Channel.OUTLINE);
        int textColor = AutismTheme.recolor(0xFFF3ECE7, Channel.TEXT);
        UiRenderer.frame(graphics, UiBounds.of(px, py, panelW, panelH), 0xF5120B0D, border);

        text.drawCentered(graphics, title, UiBounds.of(px, py + 10, panelW, 9), textColor);
        if (!label.isBlank()) {
            text.drawCentered(graphics, label, UiBounds.of(px, py + 24, panelW, 9), 0xFF9A8F8A);
        }

        int barX = px + 16;
        int barW = panelW - 32;
        int barY = py + panelH - 20;
        int barH = 8;

        autismclient.gui.vanillaui.components.ProgressBar.render(
            autismclient.gui.vanillaui.UiContexts.overlay(graphics, mc.font, mouseX, mouseY),
            UiBounds.of(barX, barY, barW, barH), easedProgress);

        boolean minElapsed = now - shownAtMs >= MIN_VISIBLE_MS;
        boolean timedOut = now - shownAtMs >= MAX_VISIBLE_MS;
        if ((finished && minElapsed && easedProgress >= 1.0f) || timedOut) {
            shown = false;
            if (mc.gui.overlay() == this) mc.gui.setOverlay(null);
        }

        framesRendered++;
        if (framesRendered >= 2) {
            Runnable action;
            synchronized (LOCK) {
                action = deferredAction;
                deferredAction = null;
            }
            if (action != null) {
                try { action.run(); } catch (Throwable ignored) { }
            }
        }
    }

    @Override
    public boolean isPausing() {
        return false;
    }
}
