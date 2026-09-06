package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.Button;
import autismclient.util.AutismItemNbtInspectOverlay;
import autismclient.util.AutismOverlayManager;
import autismclient.util.IAutismOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AutismOverlayHostScreen extends Screen {

    private static final int BACK_W = 200;
    private static final int BACK_H = 20;
    private static final int BACK_BOTTOM_MARGIN = 27;

    private final IAutismOverlay tiedOverlay;
    private final Screen returnScreen;

    private final boolean showBackButton;

    private final boolean dismissOnClose;

    public AutismOverlayHostScreen() {
        this(null, null, false);
    }

    public AutismOverlayHostScreen(IAutismOverlay tiedOverlay) {
        this(tiedOverlay, null, false);
    }

    public AutismOverlayHostScreen(IAutismOverlay tiedOverlay, Screen returnScreen) {
        this(tiedOverlay, returnScreen, false);
    }

    public AutismOverlayHostScreen(IAutismOverlay tiedOverlay, Screen returnScreen, boolean showBackButton) {
        this(tiedOverlay, returnScreen, showBackButton, false);
    }

    private boolean darkenBackground;

    private autismclient.util.AutismHostScreenOverlays overlaySet;

    public AutismOverlayHostScreen(IAutismOverlay tiedOverlay, Screen returnScreen, boolean showBackButton, boolean dismissOnClose) {
        super(Component.literal("Autism Overlays"));
        this.tiedOverlay = tiedOverlay;
        this.returnScreen = returnScreen;
        this.showBackButton = showBackButton;
        this.dismissOnClose = dismissOnClose;
    }

    public AutismOverlayHostScreen withDarkenedBackground() {
        this.darkenBackground = true;
        return this;
    }

    public boolean hostsOverlay(IAutismOverlay overlay) {
        return overlay != null && tiedOverlay == overlay;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        if (overlaySet == null && !showBackButton && minecraft != null && minecraft.level != null) {
            autismclient.modules.AutismModule module = autismclient.modules.AutismModule.get();
            if (module != null && module.isActive()) {
                try {
                    overlaySet = autismclient.util.AutismHostScreenOverlays.build(this.font);
                } catch (Throwable error) {
                    autismclient.AutismClientAddon.LOG.warn("Host screen window set failed to build", error);
                }

                if (tiedOverlay != null) {
                    AutismOverlayManager.get().register(tiedOverlay);
                    AutismOverlayManager.get().bringToFront(tiedOverlay);
                }
            }
        }
    }

    @Override
    public void removed() {
        if (overlaySet != null) {
            try {
                overlaySet.saveAndClear();
            } catch (Throwable error) {
                autismclient.AutismClientAddon.LOG.warn("Host screen window set failed to save", error);
            }
            overlaySet = null;
        }
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();

        if (tiedOverlay != null && !tiedOverlay.isVisible() && minecraft != null && minecraft.gui.screen() == this) {
            minecraft.gui.setScreen(returnScreen);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (darkenBackground) {

            autismclient.gui.vanillaui.UiRenderer.rect(graphics,
                autismclient.gui.vanillaui.UiBounds.of(0, 0, this.width, this.height), 0xC0101010);
        }
        AutismOverlayManager.get().renderAll(graphics, mouseX, mouseY, delta);

        if (showBackButton && this.font != null) {
            int bw = Math.min(BACK_W, Math.max(120, this.width - 40));
            int bx = (this.width - bw) / 2;
            int by = this.height - BACK_BOTTOM_MARGIN;
            boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BACK_H;
            Button.render(UiContexts.overlay(graphics, this.font, mouseX, mouseY),
                UiBounds.of(bx, by, bw, BACK_H), "Back", Button.Tone.NORMAL, hovered, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (showBackButton) {
            int bw = Math.min(BACK_W, Math.max(120, this.width - 40));
            int bx = (this.width - bw) / 2;
            int by = this.height - BACK_BOTTOM_MARGIN;
            if (event.x() >= bx && event.x() < bx + bw && event.y() >= by && event.y() < by + BACK_H) {
                goBack();
                return true;
            }
        }
        return AutismOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return AutismOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return AutismOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return AutismOverlayManager.get().handleMouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {

        boolean inventoryKey = minecraft != null && minecraft.options != null
            && minecraft.options.keyInventory != null
            && minecraft.options.keyInventory.matches(input);
        boolean closeKey = input.key() == GLFW.GLFW_KEY_ESCAPE || inventoryKey;

        if (closeKey && minecraft != null && !AutismOverlayManager.get().isAnyTextFieldFocused()) {
            goBack();
            return true;
        }

        if (AutismOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }
        if (closeKey && minecraft != null) {
            goBack();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return AutismOverlayManager.get().handleCharTyped((char) input.codepoint(), 0);
    }

    private void goBack() {

        if (tiedOverlay != null && (showBackButton || dismissOnClose)) tiedOverlay.setVisible(false);
        if (dismissOnClose) {
            if (tiedOverlay != null) AutismOverlayManager.get().unregister(tiedOverlay);

            AutismItemNbtInspectOverlay.dismissShared();
        }
        if (minecraft != null) minecraft.gui.setScreen(returnScreen);
    }
}
