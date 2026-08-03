package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiColumn;
import autismclient.gui.vanillaui.direct.DirectUiContainer;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.CompactWindow;

public class DirectAccordionSection extends DirectUiContainer {
    private final DirectUiColumn content = new DirectUiColumn();
    private String title;
    private boolean expanded = true;
    private int headerHeight = 18;
    private int contentTopGap = 2;

    public DirectAccordionSection(String title) {
        this.title = title == null ? "" : title;
        content.setPadding(DirectUiInsets.NONE).setGap(2);
        add(content);
    }

    public DirectUiColumn content() {
        return content;
    }

    public DirectAccordionSection setTitle(String title) {
        String next = title == null ? "" : title;
        if (!this.title.equals(next)) {
            this.title = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectAccordionSection setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            markLayoutDirty();
        }
        return this;
    }

    public DirectAccordionSection syncExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            markLayoutDirty();
        }
        return this;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public DirectAccordionSection setHeaderHeight(int headerHeight) {
        int next = Math.max(12, headerHeight);
        if (this.headerHeight != next) {
            this.headerHeight = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectAccordionSection setContentTopGap(int contentTopGap) {
        int next = Math.max(0, contentTopGap);
        if (this.contentTopGap != next) {
            this.contentTopGap = next;
            markLayoutDirty();
        }
        return this;
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        return Math.max(UiText.width(context.textRenderer(), title, context.theme().fontFor(UiTone.LABEL), context.theme().color(UiTone.LABEL)) + context.theme().scale(18),
            content.preferredWidth(context));
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        int scaledHeaderHeight = Math.max(context.theme().scale(headerHeight), context.theme().lineHeight(UiTone.LABEL, 4));
        if (!expanded) return scaledHeaderHeight;
        int scaledContentTopGap = context.theme().scale(contentTopGap);
        float bodyHeight = scaledContentTopGap + content.preferredHeight(context, availableWidth);
        return scaledHeaderHeight + bodyHeight;
    }

    @Override
    protected void layoutChildren(DirectRenderContext context) {
        int scaledHeaderHeight = Math.max(context.theme().scale(headerHeight), context.theme().lineHeight(UiTone.LABEL, 4));
        int scaledContentTopGap = context.theme().scale(contentTopGap);
        float contentY = y + scaledHeaderHeight + (expanded ? scaledContentTopGap : 0.0f);
        float contentH = expanded ? Math.max(0.0f, height - scaledHeaderHeight - scaledContentTopGap) : 0.0f;
        content.setVisible(expanded);
        content.setBounds(x, contentY, width, contentH);
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        int drawW = Math.round(width);
        int drawHeaderH = Math.max(context.theme().scale(headerHeight), context.theme().lineHeight(UiTone.LABEL, 4));
        boolean hovered = context.mouseX() >= x && context.mouseX() <= x + width && context.mouseY() >= y && context.mouseY() <= y + drawHeaderH;
        CompactWindow.renderFrame(
            UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
            UiBounds.of(drawX, drawY, drawW, Math.round(height)),
            title,
            !expanded,
            true,
            false,
            hovered,
            true,
            4,
            4,
            drawHeaderH
        );
        renderBody(context, drawHeaderH);
    }

    private void renderBody(DirectRenderContext context, int drawHeaderH) {
        if (!expanded) return;
        int clipTop = Math.round(y + drawHeaderH);
        int clipBottom = Math.round(y + height);
        if (clipBottom <= clipTop) return;
        context.viewport().enableScissor(context.drawContext(), x, y + drawHeaderH, x + width, y + height);
        try {
            super.render(context);
        } finally {
            context.viewport().disableScissor(context.drawContext());
        }
    }

    @Override
    public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
        int scaledHeaderHeight = Math.max(context.theme().scale(headerHeight), context.theme().lineHeight(UiTone.LABEL, 4));
        if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + scaledHeaderHeight) {
            expanded = !expanded;
            markLayoutDirty();
            return true;
        }
        return expanded && super.mouseClicked(context, mouseX, mouseY, button);
    }

}
