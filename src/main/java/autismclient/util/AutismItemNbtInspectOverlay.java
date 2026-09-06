package autismclient.util;

import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.direct.DirectScrollViewport;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.InspectorLayout;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.TextWrapLayout;
import autismclient.modules.AutismAdminToolsBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutismItemNbtInspectOverlay extends AutismOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final int SCROLLBAR_WIDTH = InspectorLayout.SCROLLBAR_WIDTH;
    private static AutismItemNbtInspectOverlay sharedOverlay;

    private final Font textRenderer;
    private final CompactTheme theme = new CompactTheme();
    private AutismItemNbtInspector.Inspection inspection;
    private DirectScrollViewport viewport;
    private List<WrappedInspectionLine> wrappedLines = Collections.emptyList();
    private boolean wrapDirty = true;
    private boolean wrappedRawView;
    private int wrappedWidth;
    private boolean rawView;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public AutismItemNbtInspectOverlay(Font textRenderer) {
        super("item-nbt-viewer", 280, 230);
        this.textRenderer = textRenderer;
        this.panelX = 110;
        this.panelY = 42;
    }

    public static AutismItemNbtInspectOverlay getSharedOverlay(Font textRenderer) {
        if (textRenderer == null) return null;
        if (sharedOverlay == null) sharedOverlay = new AutismItemNbtInspectOverlay(textRenderer);
        return sharedOverlay;
    }

    public static void dismissShared() {
        if (sharedOverlay == null) return;
        sharedOverlay.setVisible(false);
        AutismOverlayManager.get().unregister(sharedOverlay);
    }

    public static boolean openGlobal(ItemStack stack) {
        if (stack == null || stack.isEmpty() || MC.font == null) return false;
        AutismItemNbtInspectOverlay overlay = getSharedOverlay(MC.font);
        if (overlay == null) return false;
        int screenW = MC.getWindow() == null ? 640 : AutismUiScale.getVirtualScreenWidth();
        int x = Math.max(8, (screenW - 320) / 2);
        overlay.open(stack, x, 42);
        return true;
    }

    public static boolean openGlobal(AutismItemNbtInspector.Inspection inspection) {
        if (inspection == null || MC.font == null) return false;
        AutismItemNbtInspectOverlay overlay = getSharedOverlay(MC.font);
        if (overlay == null) return false;
        int screenW = MC.getWindow() == null ? 640 : AutismUiScale.getVirtualScreenWidth();
        overlay.open(inspection, Math.max(8, (screenW - 320) / 2), 42);
        return true;
    }

    public void open(ItemStack stack, int anchorX, int anchorY) {
        if (stack == null || stack.isEmpty()) return;
        open(AutismItemNbtInspector.inspect(stack), anchorX, anchorY);
    }

    public void open(AutismItemNbtInspector.Inspection inspection, int anchorX, int anchorY) {
        if (inspection == null) return;
        AutismOverlayManager manager = AutismOverlayManager.get();
        manager.register(this);
        this.inspection = inspection;
        this.rawView = false;
        this.visible = true;
        this.collapsed = false;
        this.viewport = null;
        this.wrapDirty = true;
        int screenW = MC.getWindow() == null ? 640 : AutismUiScale.getVirtualScreenWidth();
        int screenH = MC.getWindow() == null ? 360 : AutismUiScale.getVirtualScreenHeight();
        int availableW = Math.max(1, screenW - 18);
        int availableH = Math.max(HEADER_HEIGHT, screenH - 18);
        int width = Math.min(320, Math.max(Math.min(260, availableW), Math.min(screenW / 3, availableW)));
        int height = Math.max(Math.min(230, availableH), Math.min(screenH / 2, availableH));
        setBounds(new AutismWindowLayout(Math.max(8, Math.min(anchorX, screenW - width - 8)),
            Math.max(8, Math.min(anchorY, screenH - height - 8)), width, height, true, false));
        manager.bringToFront(this);
    }

    public void close() {
        visible = false;
        dragging = false;
        viewport = null;
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        AutismWindowLayout bounds = getBounds();
        String title = inspection == null ? "NBT Inspector" : inspection.windowTitle();
        renderWindowFrame(context, mouseX, mouseY, bounds, title, collapsed, dragging);
        boolean clipped = beginWindowBodyClip(context, bounds, collapsed);
        if (!clipped) {
            renderWindowInactiveOverlay(context, bounds, collapsed, dragging);
            return;
        }
        try {
            if (inspection == null) {
                CompactListRenderer.drawEmptyState(context, textRenderer, "No item selected.", panelX + 8, panelY + HEADER_HEIGHT + 8, panelWidth - 16);
                return;
            }

            int tabY = panelY + HEADER_HEIGHT + 6;
            for (FooterButton tab : tabButtons(tabY)) {
                CompactOverlayControls.tab(context, textRenderer, tab.x(), tab.y(), tab.width(), buttonHeight(), tab.label(),
                    "Raw".equals(tab.label()) == rawView, mouseX, mouseY);
            }

            int listX = panelX + outerPad();
            int listY = tabY + buttonHeight() + 6;
            int listWidth = panelWidth - outerPad() * 2;
            int footerY = panelY + panelHeight - footerHeight() + footerTopInset();
            int listHeight = Math.max(1, footerY - listY - 5);
            int lineH = lineHeight();
            int textX = listX + innerPad();
            int maxTextWidth = InspectorLayout.contentWidth(listWidth, innerPad());
            ensureWrappedLines(maxTextWidth);

            if (viewport == null || viewport.getX() != listX || viewport.getY() != listY
                || viewport.getWidth() != listWidth || viewport.getHeight() != listHeight) {
                int oldScroll = viewport == null ? 0 : viewport.getScrollOffset();
                viewport = new DirectScrollViewport(listX, listY, listWidth, listHeight, lineH, SCROLLBAR_WIDTH);
                viewport.jumpTo(oldScroll);
            }
            viewport.setContentHeight(wrappedLines.size() * lineH);
            viewport.beginRender(context, theme.borderSoft(), theme.listFill());
            try {
                viewport.renderSimple(context, wrappedLines.size(), (idx, bnd) -> {
                    WrappedInspectionLine line = wrappedLines.get(idx);
                    int y = UiSizing.alignTextY(bnd.y, lineH, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());
                    drawInspectionLine(context, line, textX, y);
                });
            } finally {
                viewport.endRender(context);
            }
            viewport.renderScrollbar(context, mouseX, mouseY);

            UiText.draw(context, textRenderer, wrappedLines.size() + " lines", theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED),
                panelX + outerPad() + 5, footerY + footerLabelInset(), false);
            for (FooterButton button : footerButtons(footerY)) {
                CompactOverlayControls.action(context, textRenderer, button.x(), button.y(), button.width(), buttonHeight(), button.label(),
                    button.primary() ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.GHOST, true, mouseX, mouseY);
            }
        } finally {
            endWindowBodyClip(context, clipped);
            renderWindowInactiveOverlay(context, bounds, collapsed, dragging);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        if (button == 0 && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT && mouseX >= panelX && mouseX <= panelX + panelWidth) {
            if (isOverCloseButton(mouseX, mouseY, bounds)) {
                close();
                return true;
            }
            dragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            return true;
        }
        if (collapsed) return true;
        if (button == 0) {
            int tabY = panelY + HEADER_HEIGHT + 6;
            for (FooterButton tab : tabButtons(tabY)) {
                if (tab.contains(mouseX, mouseY, buttonHeight())) {
                    rawView = "Raw".equals(tab.label());
                    wrapDirty = true;
                    if (viewport != null) viewport.jumpTo(0);
                    return true;
                }
            }
            if (viewport != null && viewport.contains(mouseX, mouseY) && viewport.mouseClicked(mouseX, mouseY, button)) return true;
            int footerY = panelY + panelHeight - footerHeight() + footerTopInset();
            for (FooterButton footer : footerButtons(footerY)) {
                if (footer.contains(mouseX, mouseY, buttonHeight())) {
                    handleFooter(footer.label());
                    return true;
                }
            }
        }
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            saveLayout();
            return true;
        }
        if (button == 0 && viewport != null) {
            viewport.mouseReleased();
            return true;
        }
        dragging = false;
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (viewport != null) viewport.mouseDragged(mouseX, mouseY);
        if (!visible || button != 0 || !dragging) return false;
        setBounds(new AutismWindowLayout((int) Math.round(mouseX - dragOffsetX), (int) Math.round(mouseY - dragOffsetY), panelWidth, panelHeight, visible, collapsed));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || collapsed || viewport == null || !viewport.contains(mouseX, mouseY)) return false;
        return viewport.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) close();
        else this.visible = true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        int height = collapsed ? HEADER_HEIGHT : panelHeight;
        return mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + height;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        return mouseX >= panelX && mouseX <= panelX + panelWidth
            && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT
            && !isOverWindowControl(mouseX, mouseY, getBounds());
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) return;
        this.collapsed = collapsed;
        dragging = false;
        if (collapsed) clearHiddenInteractionState();
        saveLayout();
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public int getZLevel() {
        return 13;
    }

    @Override
    public int getMinWidth() {
        return 240;
    }

    @Override
    public int getMinHeight() {
        return 190;
    }

    private List<AutismItemNbtInspector.InspectionLine> currentLines() {
        if (inspection == null) return List.of();
        return rawView ? inspection.rawLines() : inspection.niceLines();
    }

    private void ensureWrappedLines(int maxWidth) {
        if (!wrapDirty && wrappedRawView == rawView && wrappedWidth == maxWidth) return;
        if (inspection == null) {
            wrappedLines = Collections.emptyList();
            wrapDirty = false;
            wrappedRawView = rawView;
            wrappedWidth = maxWidth;
            return;
        }

        List<WrappedInspectionLine> wrapped = new ArrayList<>();
        for (AutismItemNbtInspector.InspectionLine line : currentLines()) {
            wrapLine(line, maxWidth, wrapped);
        }
        wrappedLines = wrapped;
        wrapDirty = false;
        wrappedRawView = rawView;
        wrappedWidth = maxWidth;
    }

    private void wrapLine(AutismItemNbtInspector.InspectionLine line, int maxWidth, List<WrappedInspectionLine> target) {
        if (line.tokens() != null && !line.tokens().isEmpty()) {
            wrapTokenLine(line.tokens(), maxWidth, target);
            return;
        }

        String text = line.text();
        if (text == null || text.isEmpty()) {
            target.add(WrappedInspectionLine.plain("", 0, line.color()));
            return;
        }

        if (isSectionLine(text)) {
            wrapPlainText(text, 0, line.color(), maxWidth, target);
            return;
        }

        int leadingSpaces = countLeadingSpaces(text);
        String indentComponent = " ".repeat(leadingSpaces);
        int indentWidth = styledWidth(indentComponent);
        int colonIndex = text.indexOf(':', leadingSpaces);
        if (colonIndex > leadingSpaces && colonIndex < text.length() - 1) {
            String label = text.substring(leadingSpaces, colonIndex + 1).trim();
            String value = text.substring(colonIndex + 1).stripLeading();
            String prefix = indentComponent + label + " ";
            int prefixWidth = styledWidth(prefix);
            int continuationOffset = Math.min(prefixWidth, indentWidth + continuationIndent());
            wrapKeyValue(prefix, value, line.color(), prefixWidth, continuationOffset, maxWidth, target);
            return;
        }

        wrapPlainText(text.substring(leadingSpaces), indentWidth, line.color(), maxWidth, target);
    }

    private void wrapKeyValue(String prefix, String value, int valueColor, int prefixWidth, int continuationOffset, int maxWidth,
                              List<WrappedInspectionLine> target) {
        int safeContinuationOffset = InspectorLayout.clampTextOffset(maxWidth, continuationOffset);
        if (prefixWidth >= maxWidth) {
            wrapPlainText(prefix.stripTrailing(), 0, AutismColors.packetGray(), maxWidth, target);
            wrapPlainText(value, safeContinuationOffset, valueColor, maxWidth, target);
            return;
        }
        if (value == null || value.isEmpty()) {
            target.add(WrappedInspectionLine.of(List.of(
                new AutismItemNbtInspector.TextToken(prefix, AutismColors.packetGray()),
                new AutismItemNbtInspector.TextToken("", valueColor)
            ), 0));
            return;
        }

        String remaining = value;
        boolean firstLine = true;
        while (!remaining.isEmpty()) {
            int offset = firstLine ? prefixWidth : safeContinuationOffset;
            int availableWidth = InspectorLayout.remainingTextWidth(maxWidth, offset);
            String current = remaining;
            int split = TextWrapLayout.nextLineEnd(current, 0, current.length(), availableWidth,
                (start, end) -> styledWidth(current.substring(start, end)));
            String part = remaining.substring(0, split).stripTrailing();
            target.add(keyValueWrappedLine(firstLine ? prefix : null, part, valueColor, offset));
            remaining = remaining.substring(split).stripLeading();
            firstLine = false;
        }
    }

    private void wrapPlainText(String text, int offset, int color, int maxWidth, List<WrappedInspectionLine> target) {
        if (text == null) return;
        if (text.isEmpty()) {
            target.add(WrappedInspectionLine.plain("", offset, color));
            return;
        }

        int safeOffset = InspectorLayout.clampTextOffset(maxWidth, offset);
        String remaining = text;
        while (!remaining.isEmpty()) {
            int availableWidth = InspectorLayout.remainingTextWidth(maxWidth, safeOffset);
            String current = remaining;
            int split = TextWrapLayout.nextLineEnd(current, 0, current.length(), availableWidth,
                (start, end) -> styledWidth(current.substring(start, end)));
            String part = remaining.substring(0, split).stripTrailing();
            target.add(WrappedInspectionLine.plain(part, safeOffset, color));
            remaining = remaining.substring(split).stripLeading();
        }
    }

    private void drawInspectionLine(GuiGraphicsExtractor context, WrappedInspectionLine line, int x, int y) {
        if (line == null || line.tokens() == null) return;
        int cursor = x + line.offset();
        for (AutismItemNbtInspector.TextToken token : line.tokens()) {
            if (token == null || token.text() == null || token.text().isEmpty()) continue;
            UiText.draw(context, textRenderer, token.text(), theme.fontFor(UiTone.BODY), token.color(), cursor, y, false);
            cursor += styledWidth(token.text());
        }
    }

    private void wrapTokenLine(List<AutismItemNbtInspector.TextToken> source, int maxWidth, List<WrappedInspectionLine> target) {
        if (source == null || source.isEmpty()) {
            target.add(WrappedInspectionLine.plain("", 0, AutismColors.textMuted()));
            return;
        }

        int indent = leadingWhitespaceWidth(source);
        int continuationOffset = InspectorLayout.clampTextOffset(maxWidth, indent + continuationIndent());
        List<AutismItemNbtInspector.TextToken> current = new ArrayList<>();
        int currentWidth = 0;
        boolean continuation = false;

        for (AutismItemNbtInspector.TextToken token : source) {
            if (token == null || token.text() == null || token.text().isEmpty()) continue;
            String remaining = token.text();
            while (!remaining.isEmpty()) {
                int limit = maxWidth - (continuation ? continuationOffset : 0);
                int room = Math.max(1, limit - currentWidth);
                String piece = trimTokenToWidth(remaining, room);
                if (piece.isEmpty()) {
                    if (!current.isEmpty()) {
                        target.add(WrappedInspectionLine.of(current, continuation ? continuationOffset : 0));
                        current = new ArrayList<>();
                        currentWidth = 0;
                        continuation = true;
                        continue;
                    }
                    piece = remaining.substring(0, 1);
                }

                current.add(new AutismItemNbtInspector.TextToken(piece, token.color()));
                currentWidth += styledWidth(piece);
                remaining = remaining.substring(piece.length());

                if (!remaining.isEmpty()) {
                    target.add(WrappedInspectionLine.of(current, continuation ? continuationOffset : 0));
                    current = new ArrayList<>();
                    currentWidth = 0;
                    continuation = true;
                    remaining = remaining.stripLeading();
                } else if (currentWidth >= Math.max(22, limit - 4)) {
                    target.add(WrappedInspectionLine.of(current, continuation ? continuationOffset : 0));
                    current = new ArrayList<>();
                    currentWidth = 0;
                    continuation = true;
                }
            }
        }

        if (!current.isEmpty()) target.add(WrappedInspectionLine.of(current, continuation ? continuationOffset : 0));
    }

    private WrappedInspectionLine keyValueWrappedLine(String prefix, String value, int valueColor, int offset) {
        List<AutismItemNbtInspector.TextToken> tokens = new ArrayList<>();
        if (prefix != null && !prefix.isEmpty()) tokens.add(new AutismItemNbtInspector.TextToken(prefix, AutismColors.packetGray()));
        tokens.add(new AutismItemNbtInspector.TextToken(value == null ? "" : value, valueColor));
        return WrappedInspectionLine.of(tokens, prefix == null || prefix.isEmpty() ? offset : 0);
    }

    private int leadingWhitespaceWidth(List<AutismItemNbtInspector.TextToken> tokens) {
        int width = 0;
        for (AutismItemNbtInspector.TextToken token : tokens) {
            if (token == null || token.text() == null || token.text().isEmpty()) continue;
            String text = token.text();
            int spaces = 0;
            while (spaces < text.length() && Character.isWhitespace(text.charAt(spaces))) spaces++;
            if (spaces <= 0) return width;
            width += styledWidth(text.substring(0, spaces));
            if (spaces < text.length()) return width;
        }
        return width;
    }

    private String trimTokenToWidth(String value, int maxWidth) {
        if (value == null || value.isEmpty()) return "";
        if (styledWidth(value) <= maxWidth) return value;
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String candidate = value.substring(0, mid);
            if (styledWidth(candidate) <= maxWidth) low = mid;
            else high = mid - 1;
        }
        return low <= 0 ? "" : value.substring(0, low);
    }

    private boolean isSectionLine(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private int countLeadingSpaces(String text) {
        int count = 0;
        while (count < text.length() && text.charAt(count) == ' ') count++;
        return count;
    }

    private int styledWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        return UiText.width(textRenderer, text, theme.fontFor(UiTone.BODY), AutismColors.packetWhite());
    }

    private void handleFooter(String label) {
        if (inspection == null || MC.keyboardHandler == null) return;
        switch (label) {
            case "Copy Raw" -> {
                MC.keyboardHandler.setClipboard(inspection.rawCopyText());
                AutismNotifications.copied("Copied raw " + inspection.subject() + " NBT.");
            }
            case "Copy Nice" -> {
                MC.keyboardHandler.setClipboard(inspection.prettyCopyText());
                AutismNotifications.copied("Copied " + inspection.subject() + " NBT summary.");
            }
            case "Fill Editor" -> {

                if (!autismclient.util.AutismLiteVariant.enabled()
                    && inspection.stack() != null && !inspection.stack().isEmpty()
                    && AutismAdminToolsBridge.openFilledAdminEditor(inspection.stack())) {
                    AutismNotifications.show("Filled Admin Tools with complete item NBT.", 0xFF35D873);
                } else {
                    AutismClientMessaging.sendPrefixed("Admin Tools: could not fill NBT editor.");
                }
            }
        }
    }

    private List<FooterButton> tabButtons(int y) {
        int x = panelX + outerPad();
        return List.of(new FooterButton("Nice", x, y, 54, false), new FooterButton("Raw", x + 60, y, 54, false));
    }

    private List<FooterButton> footerButtons(int y) {
        java.util.ArrayList<FooterButton> buttons = new java.util.ArrayList<>();

        boolean editor = !autismclient.util.AutismLiteVariant.enabled()
            && inspection != null && inspection.stack() != null && !inspection.stack().isEmpty();
        if (panelWidth < 306) {
            int firstCursor = panelX + panelWidth - outerPad();
            if (editor) addFooter(buttons, firstCursor, y + 14, "Fill Editor", true);

            int secondCursor = panelX + panelWidth - outerPad();
            secondCursor = addFooter(buttons, secondCursor, y + 30, "Copy Raw", false);
            addFooter(buttons, secondCursor, y + 30, "Copy Nice", false);
            return buttons;
        }

        int cursor = panelX + panelWidth - outerPad();
        if (editor) cursor = addFooter(buttons, cursor, y, "Fill Editor", true);
        cursor = addFooter(buttons, cursor, y, "Copy Raw", false);
        addFooter(buttons, cursor, y, "Copy Nice", false);
        return buttons;
    }

    private int addFooter(List<FooterButton> buttons, int cursorX, int y, String label, boolean primary) {
        int width = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, label, 5, 42, 78);
        int x = cursorX - width;
        buttons.add(new FooterButton(label, x, y, width, primary));
        return x - buttonGap();
    }

    private int outerPad() { return 7; }
    private int innerPad() { return 5; }
    private int footerHeight() { return panelWidth < 306 ? 50 : 24; }
    private int footerTopInset() { return 4; }
    private int footerLabelInset() { return 5; }
    private int buttonHeight() { return 14; }
    private int buttonGap() { return 4; }
    private int lineHeight() { return 12; }
    private int continuationIndent() { return 12; }

    private record FooterButton(String label, int x, int y, int width, boolean primary) {
        boolean contains(double mouseX, double mouseY, int height) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record WrappedInspectionLine(List<AutismItemNbtInspector.TextToken> tokens, int offset) {
        static WrappedInspectionLine plain(String text, int offset, int color) {
            return new WrappedInspectionLine(List.of(new AutismItemNbtInspector.TextToken(text, color)), offset);
        }

        static WrappedInspectionLine of(List<AutismItemNbtInspector.TextToken> tokens, int offset) {
            return new WrappedInspectionLine(List.copyOf(tokens), offset);
        }
    }
}
