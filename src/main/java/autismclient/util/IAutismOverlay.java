package autismclient.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface IAutismOverlay {
    enum OverlayScope {
        HOST_SCREEN,
        MODULE_MENU,
        CONTAINER_GUI,
        BACKGROUND_STATUS
    }

    void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta);

    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean mouseReleased(double mouseX, double mouseY, int button);

    boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY);

    boolean mouseScrolled(double mouseX, double mouseY, double amount);

    boolean keyPressed(int keyCode, int scanCode, int modifiers);

    boolean charTyped(char chr, int modifiers);

    boolean isVisible();

    void setVisible(boolean visible);

    boolean isMouseOver(double mouseX, double mouseY);

    boolean isOverDragBar(double mouseX, double mouseY);

    default int getZLevel() {
        return 0;
    }

    default boolean isCollapsed() {
        return false;
    }

    default void setCollapsed(boolean collapsed) {

    }

    default void toggleCollapsed() {
        setCollapsed(!isCollapsed());
    }

    default boolean hasTextFieldFocused() {
        return false;
    }

    default void clearTextFieldFocus() {
    }

    default boolean wantsKeyboardCapture() {
        return false;
    }

    default String getOverlayId() {
        return getClass().getSimpleName();
    }

    default OverlayScope getDefaultOverlayScope() {
        return OverlayScope.HOST_SCREEN;
    }

    default boolean persistsAcrossScreenClose() {
        return false;
    }

    default AutismWindowLayout getBounds() {
        return new AutismWindowLayout(0, 0, getMinWidth(), getMinHeight(), isVisible(), isCollapsed());
    }

    default void setBounds(AutismWindowLayout bounds) {
    }

    default int getMinWidth() {
        return 240;
    }

    default int getMinHeight() {
        return 140;
    }

    default boolean isOverResizeHandle(double mouseX, double mouseY) {
        return false;
    }

    default boolean usesSharedHeaderClickCollapse() {
        return false;
    }

    default void saveLayout() {
        AutismSharedState.get().setWindowLayout(getOverlayId(), getBounds());
    }

    default void restoreLayout() {
        AutismWindowLayout layout = AutismSharedState.get().getWindowLayout(getOverlayId());
        if (layout != null) {
            setBounds(layout);
        }
    }
}
