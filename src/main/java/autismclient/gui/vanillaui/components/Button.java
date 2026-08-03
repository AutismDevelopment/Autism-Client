package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismThemeTextures;
import net.minecraft.resources.Identifier;

public final class Button {
    private Button() {
    }

    public enum Tone {
        NORMAL,
        SECONDARY,
        PRIMARY,
        SUCCESS,
        DANGER
    }

    public static void render(UiContext context, UiBounds bounds, String label, Tone tone, boolean hovered, boolean active) {
        render(context, bounds, label, null, tone, hovered, active,
            autismclient.gui.vanillaui.StateFades.get(autismclient.gui.vanillaui.StateFades.key(bounds), active));
    }

    public static void renderIcon(UiContext context, UiBounds bounds, String label, Identifier icon, Tone tone, boolean hovered, boolean active, float activeProgress) {
        render(context, bounds, label, icon, tone, hovered, active, activeProgress);
    }

    private static void render(UiContext context, UiBounds bounds, String label, Identifier icon, Tone tone, boolean hovered, boolean active, float activeProgress) {
        var colors = context.theme().colors();
        float progress = Math.min(1.0f, Math.max(0.0f, activeProgress));
        boolean coloredTone = tone == Tone.SUCCESS || tone == Tone.DANGER || tone == Tone.PRIMARY;

        UiRenderer.frame(context.graphics(), bounds, 0xC01E242D, colors.buttonBorder);
        if (progress > 0.001f) {
            if (coloredTone) {
                int toneFill = switch (tone) {
                    case PRIMARY -> colors.accentDark;
                    case SUCCESS -> AutismTheme.recolor(0xD41F5233, Channel.SUCCESS);
                    case DANGER -> AutismTheme.recolor(0xD4531B20, Channel.DANGER);
                    default -> 0xCC282E39;
                };
                int onBorder = switch (tone) {
                    case SUCCESS -> colors.success;
                    case DANGER -> colors.bad;
                    case PRIMARY -> colors.accent;
                    default -> colors.borderSoft;
                };
                UiRenderer.rect(context.graphics(), bounds.inset(1), UiRenderer.applyAlpha(toneFill, progress));
                UiRenderer.outline(context.graphics(), bounds, UiRenderer.applyAlpha(onBorder, progress));
            } else {

                UiRenderer.rect(context.graphics(), bounds.inset(1), UiRenderer.applyAlpha(0xCC282E39, progress));
            }
        }

        float hoverT = autismclient.gui.vanillaui.HoverFades.get(autismclient.gui.vanillaui.HoverFades.key(bounds), hovered);
        if (hoverT > 0.001f) {
            UiRenderer.rect(context.graphics(), bounds.inset(1),
                (((int) Math.round(0x14 * hoverT)) << 24) | 0x00FFFFFF);
        }
        if (icon == null) {
            context.text().drawCentered(context.graphics(), label, bounds, colors.text);
            return;
        }

        int iconSize = Math.min(12, Math.max(8, bounds.height() - 5));
        int textW = context.text().width(label);
        int groupW = iconSize + 4 + textW;
        int iconX = bounds.x() + Math.max(3, (bounds.width() - groupW) / 2);
        int iconY = bounds.y() + Math.max(1, (bounds.height() - iconSize) / 2);
        context.graphics().blit(AutismThemeTextures.recolored(icon, Channel.ACCENT), iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
        context.text().drawFitted(context.graphics(), label, iconX + iconSize + 4, context.text().centeredY(bounds),
            Math.max(1, bounds.right() - (iconX + iconSize + 4) - 4), colors.text);
    }
}
