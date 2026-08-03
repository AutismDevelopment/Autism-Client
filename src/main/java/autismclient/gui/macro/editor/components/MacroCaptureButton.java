package autismclient.gui.macro.editor.components;

import autismclient.gui.macro.editor.CaptureMode;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class MacroCaptureButton {
    public static final int WIDTH = 52;
    public static final int HEIGHT = 14;

    private MacroCaptureButton() {
    }

    public static CompactOverlayButton render(
            GuiGraphicsExtractor graphics,
            Font font,
            UiBounds bounds,
            CaptureMode mode,
            boolean capturing,
            boolean active,
            int mouseX,
            int mouseY,
            Runnable action
    ) {
        return render(graphics, font, bounds, mode, capturing, active, mouseX, mouseY, null, null, action);
    }

    public static CompactOverlayButton render(
            GuiGraphicsExtractor graphics,
            Font font,
            UiBounds bounds,
            CaptureMode mode,
            boolean capturing,
            boolean active,
            int mouseX,
            int mouseY,
            String idleLabel,
            String capturingLabel,
            Runnable action
    ) {
        CaptureMode resolvedMode = mode == null ? CaptureMode.NONE : mode;
        Runnable resolvedAction = action == null ? () -> { } : action;
        CompactOverlayButton button = CompactOverlayButton.create(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                Component.literal(label(resolvedMode, capturing, idleLabel, capturingLabel)),
                ignored -> resolvedAction.run()
        );
        button.setVariant(capturing ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.PRIMARY);
        button.active = active;
        CompactOverlayButton.renderStyled(graphics, font, button, mouseX, mouseY);
        return button;
    }

    public static String label(CaptureMode mode, boolean capturing) {
        return label(mode, capturing, null, null);
    }

    public static String label(CaptureMode mode, boolean capturing, String idleLabel, String capturingLabel) {
        if (capturing && capturingLabel != null && !capturingLabel.isBlank()) return capturingLabel;
        if (!capturing && idleLabel != null && !idleLabel.isBlank()) return idleLabel;
        if (capturing) return "Done";
        return switch (mode == null ? CaptureMode.NONE : mode) {
            case ITEM_SLOT, PACKET_NAME, CUSTOM_MENU_INPUT, CUSTOM_MENU_BUTTON, CUSTOM_MENU_TITLE -> "Pick";
            case PACKET_CLICK_TARGET -> "Re-capture";
            case BLOCK_ID, ENTITY_ID, BLOCK_CATALOG, NONE -> "Capture";
        };
    }
}
