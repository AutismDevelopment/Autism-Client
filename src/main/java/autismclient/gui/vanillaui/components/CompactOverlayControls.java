package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.components.UiTone;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class CompactOverlayControls {
    private CompactOverlayControls() {
    }

    public static void action(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                              String label, CompactOverlayButton.Variant variant, boolean enabled, int mouseX, int mouseY) {
        action(graphics, font, x, y, width, height, label, variant, enabled, UiTone.BODY, mouseX, mouseY);
    }

    public static void action(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                              String label, CompactOverlayButton.Variant variant, boolean enabled, UiTone tone,
                              int mouseX, int mouseY) {
        CompactOverlayButton button = CompactOverlayButton.create(x, y, width, height, Component.literal(label), ignored -> {});
        button.setWidth(width);
        button.setVariant(variant);
        button.setTone(tone);
        button.active = enabled;
        CompactOverlayButton.renderStyled(graphics, font, button, mouseX, mouseY);
    }

    public static void toggle(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                              String label, boolean state, String animationKey, int mouseX, int mouseY) {
        toggle(graphics, font, x, y, width, height, label, state, animationKey, true, mouseX, mouseY);
    }

    public static void toggle(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                              String label, boolean state, String animationKey, boolean enabled, int mouseX, int mouseY) {
        CompactOverlayButton button = CompactOverlayButton.create(x, y, width, height, Component.literal(label), ignored -> {});
        button.setWidth(width);
        button.setVariant(state ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER);
        button.setToggleState(state).setAnimationKey(animationKey);
        button.active = enabled;
        CompactOverlayButton.renderStyled(graphics, font, button, mouseX, mouseY);
    }

    public static void tab(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                           String label, boolean selected, int mouseX, int mouseY) {
        action(graphics, font, x, y, width, height, label,
            selected ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.GHOST,
            true, mouseX, mouseY);
    }
}
