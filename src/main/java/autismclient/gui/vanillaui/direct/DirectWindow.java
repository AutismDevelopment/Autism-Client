package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiColumn;
import autismclient.gui.vanillaui.direct.DirectUiContainer;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.CompactWindow;
import net.minecraft.resources.Identifier;

public class DirectWindow extends DirectUiContainer {
    private final DirectUiColumn content = new DirectUiColumn();
    private String title;
    private Identifier titleIcon;
    private boolean showBody = true;
    private boolean active = true;
    private boolean headerHovered = false;
    private boolean centerTitle = true;
    private boolean showCollapseControl = false;
    private boolean showCloseControl = false;
    private UiTone titleTone = UiTone.TITLE;
    private int titleLeftInset = 10;
    private int titleRightInset = 10;

    public DirectWindow(String title) {
        this.title = title == null ? "" : title;
        content.setGap(4).setPadding(DirectUiInsets.all(6));
        add(content);
    }

    public DirectUiColumn content() {
        return content;
    }

    public DirectWindow setTitle(String title) {
        String next = title == null ? "" : title;
        if (!this.title.equals(next)) {
            this.title = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectWindow setTitleIcon(Identifier titleIcon) {
        this.titleIcon = titleIcon;
        return this;
    }

    public DirectWindow setShowBody(boolean showBody) {
        if (this.showBody != showBody) {
            this.showBody = showBody;
            markLayoutDirty();
        }
        return this;
    }

    public DirectWindow restoreShowBody(boolean showBody) {
        if (this.showBody != showBody) {
            this.showBody = showBody;
            markLayoutDirty();
        }
        return this;
    }

    public DirectWindow syncShowBody(boolean showBody) {
        return restoreShowBody(showBody);
    }

    public DirectWindow setActive(boolean active) {
        this.active = active;
        return this;
    }

    public DirectWindow setHeaderHovered(boolean headerHovered) {
        this.headerHovered = headerHovered;
        return this;
    }

    public DirectWindow setCenterTitle(boolean centerTitle) {
        this.centerTitle = centerTitle;
        return this;
    }

    public DirectWindow setHeaderControls(boolean collapse, boolean close) {
        if (this.showCollapseControl != collapse || this.showCloseControl != close) {
            this.showCollapseControl = collapse;
            this.showCloseControl = close;
            markLayoutDirty();
        }
        return this;
    }

    public DirectWindow setTitleTone(UiTone titleTone) {
        UiTone next = titleTone == null ? UiTone.TITLE : titleTone;
        if (this.titleTone != next) {
            this.titleTone = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectWindow setTitleAreaInsets(int titleLeftInset, int titleRightInset) {
        int nextLeft = Math.max(0, titleLeftInset);
        int nextRight = Math.max(0, titleRightInset);
        if (this.titleLeftInset != nextLeft || this.titleRightInset != nextRight) {
            this.titleLeftInset = nextLeft;
            this.titleRightInset = nextRight;
            markLayoutDirty();
        }
        return this;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        int headerH = Math.max(context.theme().headerHeight(), context.theme().lineHeight(titleTone, 4));
        float bodyHeight = showBody ? content.preferredHeight(context, availableWidth) : 0.0f;
        return headerH + bodyHeight;
    }

    @Override
    protected void layoutChildren(DirectRenderContext context) {
        int headerH = Math.max(context.theme().headerHeight(), context.theme().lineHeight(titleTone, 4));
        content.setVisible(showBody);
        content.setBounds(x, y + headerH, width, showBody ? Math.max(0, height - headerH) : 0);
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;

        DirectRenderContext drawContext = context;
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        int drawW = Math.round(width);
        int drawH = Math.round(height);
        int headerH = Math.max(context.theme().headerHeight(), context.theme().lineHeight(titleTone, 4));
        CompactWindow.renderFrame(
            UiContexts.overlay(drawContext.drawContext(), drawContext.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
            UiBounds.of(drawX, drawY, drawW, drawH),
            title,
            !showBody,
            showCollapseControl,
            showCloseControl,
            headerHovered,
            active,
            Math.max(4, context.theme().scale(titleLeftInset)),
            Math.max(34, context.theme().scale(titleRightInset)),
            headerH
        );

        if (showBody) {
            int clipTop = Math.round(y + headerH);
            int clipBottom = Math.round(y + height);
            if (clipBottom > clipTop) {
                drawContext.viewport().enableScissor(drawContext.drawContext(), x, y + headerH, x + width, y + height);
                try {
                    super.render(drawContext);
                } finally {
                    drawContext.viewport().disableScissor(drawContext.drawContext());
                }
            }
        }

        if (!active) {
            autismclient.gui.vanillaui.UiRenderer.rect(drawContext.drawContext(),
                UiBounds.of(drawX + 1, drawY + 1, Math.max(0, drawW - 2), Math.max(0, drawH - 2)), 0x24000000);
        }
    }
}
