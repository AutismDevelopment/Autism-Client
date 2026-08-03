package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.TextWrapLayout;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class CompactTextInput extends DirectUiNode implements FocusableTextInput {
    private static final int DEFAULT_MAX_LENGTH = 512;
    private static final long DOUBLE_CLICK_MS = 320L;
    private static final float DOUBLE_CLICK_MAX_DIST = 4.0f;

    private String value = "";
    private String placeholder = "";
    private int maxLength = DEFAULT_MAX_LENGTH;
    private float minWidth = 56.0f;
    private float maxWidth = Float.MAX_VALUE;
    private float preferredWidth = -1.0f;
    private int horizontalPadding = 6;
    private int fixedHeight = -1;
    private int textYOffset = 1;
    private boolean editable = true;
    private boolean drawBackground = true;
    private boolean hoverEffectsEnabled = true;
    private boolean focusEffectsEnabled = true;
    private int backgroundColorOverride = 0;
    private boolean focused = false;
    private int cursor = 0;
    private int selectionAnchor = 0;
    private int viewOffset = 0;
    private int wrapViewLine = 0;
    private boolean draggingMultilineScrollbar = false;
    private int multilineScrollbarGrabOffset = 0;
    private boolean manualWrapScroll = false;
    private boolean draggingSelection = false;
    private boolean multiline = false;
    private long blinkStartMs = System.currentTimeMillis();
    private Identifier fontId = null;
    private UiTone textTone = UiTone.BODY;
    private UiTone placeholderTone = UiTone.MUTED;
    private Predicate<String> filter = next -> true;
    private Consumer<String> onChange;
    private Consumer<String> onSubmit;
    private boolean submitOnEnter;
    private Function<String, Component> displayTextProvider;
    private LongSupplier displayTextRevisionProvider;
    private boolean historyNavigationEnabled = false;
    private final List<String> historyEntries = new ArrayList<>();
    private Supplier<Collection<String>> historyProvider;
    private int historyIndex = -1;
    private String historyDraft = "";
    private boolean applyingHistoryValue = false;
    private String cachedRichValue = "";
    private Function<String, Component> cachedRichProvider;
    private RichMetrics cachedRichMetrics;
    private boolean cachedRichComputed;
    private long cachedRichProviderRevision = Long.MIN_VALUE;
    private long textRevision = 1L;
    private long styleRevision = 1L;
    private long measuredTextRevision = Long.MIN_VALUE;
    private long measuredStyleRevision = Long.MIN_VALUE;
    private Font measuredRenderer;
    private Identifier measuredFont;
    private int measuredReloadGeneration = Integer.MIN_VALUE;
    private int measuredColor;
    private RichMetrics measuredRichMetrics;
    private int[] cumulativeWidths = new int[]{0};
    private long wrappedTextRevision = Long.MIN_VALUE;
    private long wrappedStyleRevision = Long.MIN_VALUE;
    private Font wrappedRenderer;
    private Identifier wrappedFont;
    private int wrappedReloadGeneration = Integer.MIN_VALUE;
    private int wrappedWidth = Integer.MIN_VALUE;
    private boolean wrappedMultiline;
    private RichMetrics wrappedRichMetrics;
    private List<WrappedLine> cachedWrappedLines = List.of();
    private String cachedPlaceholderText = "";
    private Font cachedPlaceholderRenderer;
    private Identifier cachedPlaceholderFont;
    private int cachedPlaceholderReloadGeneration = Integer.MIN_VALUE;
    private int cachedPlaceholderWidth = Integer.MIN_VALUE;
    private List<String> cachedPlaceholderLines = List.of();
    private long lastClickMs = 0L;
    private float lastClickX = Float.NaN;
    private float lastClickY = Float.NaN;
    private int repeatedClickCount = 0;

    private record WrappedLine(int start, int end, int renderEnd) {
    }

    private record RichMetrics(String text, List<Style> styles) {
    }

    public CompactTextInput() {
        this.height = 16;
    }

    public CompactTextInput setText(String value) {
        String sanitized = sanitize(value);
        if (!Objects.equals(this.value, sanitized)) {
            this.value = sanitized;
            invalidateTextCaches();
            markLayoutDirty();
            if (cursor > this.value.length()) cursor = this.value.length();
            if (selectionAnchor > this.value.length()) selectionAnchor = this.value.length();
            manualWrapScroll = false;
            if (onChange != null) onChange.accept(this.value);
        }
        ensureSelectionOrder();
        viewOffset = Math.min(viewOffset, this.value.length());
        if (historyNavigationEnabled && !applyingHistoryValue) {
            historyIndex = -1;
            historyDraft = this.value;
        }
        return this;
    }

    public String text() {
        return value;
    }

    public CompactTextInput setPlaceholder(String placeholder) {
        String next = placeholder == null ? "" : placeholder;
        if (!this.placeholder.equals(next)) {
            this.placeholder = next;
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        setText(value);
        return this;
    }

    public CompactTextInput setMinWidth(float minWidth) {
        float next = Math.max(0.0f, minWidth);
        if (Float.compare(this.minWidth, next) != 0) {
            this.minWidth = next;
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setMaxWidth(float maxWidth) {
        float next = Math.max(this.minWidth, maxWidth);
        if (Float.compare(this.maxWidth, next) != 0) {
            this.maxWidth = next;
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setPreferredWidth(float preferredWidth) {
        if (Float.compare(this.preferredWidth, preferredWidth) != 0) {
            this.preferredWidth = preferredWidth;
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setHorizontalPadding(int horizontalPadding) {
        int next = Math.max(0, horizontalPadding);
        if (this.horizontalPadding != next) {
            this.horizontalPadding = next;
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setFieldHeight(int fixedHeight) {
        int next = Math.max(1, fixedHeight);
        if (this.fixedHeight != next) {
            this.fixedHeight = next;
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setTextYOffset(int textYOffset) {
        this.textYOffset = textYOffset;
        return this;
    }

    public CompactTextInput setEditable(boolean editable) {
        this.editable = editable;
        if (!editable) {
            setFocused(false);
        }
        return this;
    }

    public boolean isEditable() {
        return editable;
    }

    public CompactTextInput setDrawBackground(boolean drawBackground) {
        this.drawBackground = drawBackground;
        return this;
    }

    public CompactTextInput setHoverEffectsEnabled(boolean hoverEffectsEnabled) {
        this.hoverEffectsEnabled = hoverEffectsEnabled;
        return this;
    }

    public CompactTextInput setFocusEffectsEnabled(boolean focusEffectsEnabled) {
        this.focusEffectsEnabled = focusEffectsEnabled;
        return this;
    }

    public CompactTextInput setBackgroundColorOverride(int backgroundColorOverride) {
        this.backgroundColorOverride = backgroundColorOverride;
        return this;
    }

    public CompactTextInput setFontId(Identifier fontId) {
        if (!Objects.equals(this.fontId, fontId)) {
            this.fontId = fontId;
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setTextTone(UiTone textTone) {
        UiTone next = textTone == null ? UiTone.BODY : textTone;
        if (this.textTone != next) {
            this.textTone = next;
            styleRevision++;
            invalidateWrappedLines();
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setPlaceholderTone(UiTone placeholderTone) {
        UiTone next = placeholderTone == null ? UiTone.MUTED : placeholderTone;
        if (this.placeholderTone != next) {
            this.placeholderTone = next;
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setFilter(Predicate<String> filter) {
        this.filter = filter == null ? next -> true : filter;
        return this;
    }

    public CompactTextInput setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public CompactTextInput setOnSubmit(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
        return this;
    }

    public CompactTextInput setDisplayTextProvider(Function<String, Component> displayTextProvider) {
        if (this.displayTextProvider != displayTextProvider) {
            this.displayTextProvider = displayTextProvider;
            styleRevision++;
            invalidateRichMetrics();
            invalidateWrappedLines();
        }
        return this;
    }

    public CompactTextInput setDisplayTextRevisionProvider(LongSupplier revisionProvider) {
        if (this.displayTextRevisionProvider != revisionProvider) {
            this.displayTextRevisionProvider = revisionProvider;
            styleRevision++;
            invalidateRichMetrics();
            invalidateWrappedLines();
        }
        return this;
    }

    public CompactTextInput setMultiline(boolean multiline) {
        if (this.multiline != multiline) {
            this.multiline = multiline;
            if (multiline) viewOffset = 0;
            else wrapViewLine = 0;
            markLayoutDirty();
        }
        return this;
    }

    public CompactTextInput setSubmitOnEnter(boolean submitOnEnter) {
        this.submitOnEnter = submitOnEnter;
        return this;
    }

    public int wrappedRowCount(DirectRenderContext context, int fieldWidth) {
        if (value.isEmpty()) return 1;
        Identifier font = resolvedFont(context);
        int scaledHorizontalPadding = context.theme().scale(horizontalPadding);
        int frameInset = context.theme().scale(2);
        int innerW = contentWidth(fieldWidth, scaledHorizontalPadding, frameInset);
        int bodyColor = context.theme().color(textTone);
        return Math.max(1, wrapValueLines(context.textRenderer(), font, bodyColor, innerW, buildRichMetrics(value)).size());
    }

    public int rowsToHeight(DirectRenderContext context, int rows) {
        int lineHeight = context.theme().fontHeight(textTone) + context.theme().scale(1);
        return Math.max(1, rows) * lineHeight + context.theme().scale(6);
    }

    public CompactTextInput setHistoryNavigationEnabled(boolean historyNavigationEnabled) {
        this.historyNavigationEnabled = historyNavigationEnabled;
        if (!historyNavigationEnabled) {
            historyIndex = -1;
            historyDraft = "";
        }
        return this;
    }

    public CompactTextInput setHistoryProvider(Supplier<Collection<String>> historyProvider) {
        this.historyProvider = historyProvider;
        historyIndex = -1;
        historyDraft = "";
        return this;
    }

    public CompactTextInput addHistoryEntry(String entry) {
        String sanitized = sanitize(entry);
        if (sanitized.isEmpty()) return this;
        if (!sanitized.equals(historyEntries.isEmpty() ? null : historyEntries.get(historyEntries.size() - 1))) {
            historyEntries.add(sanitized);
            while (historyEntries.size() > 100) historyEntries.remove(0);
        }
        historyIndex = -1;
        historyDraft = "";
        return this;
    }

    @Override
    public CompactTextInput setGrowX(boolean growX) {
        super.setGrowX(growX);
        return this;
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        if (preferredWidth > 0.0f) return UiSizing.clamp(preferredWidth, minWidth, maxWidth);
        String sample = value.isEmpty() ? placeholder : value;
        if (sample.isEmpty()) sample = "Component";
        Identifier font = resolvedFont(context);
        int color = context.theme().color(textTone);
        return UiSizing.fitTextWidth(context.textRenderer(), sample, font, color, context.theme().scale(horizontalPadding + 2), minWidth, maxWidth);
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        int resolvedFixedHeight = fixedHeight > 0 ? fixedHeight : Math.max(context.theme().buttonHeight(), context.theme().scale(16));
        int contentHeight = context.theme().fontHeight(textTone) + context.theme().scale(5);
        return Math.max(12, Math.max(resolvedFixedHeight, contentHeight));
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;

        int drawX = Math.round(x);
        int drawY = Math.round(y);
        int drawW = Math.round(width);
        int drawH = Math.round(height);
        boolean hovered = contains(context.mouseX(), context.mouseY());
        float hover = updateHover(hovered, context.delta());
        Identifier font = resolvedFont(context);
        int fontHeight = context.theme().fontHeight(textTone);
        int scaledHorizontalPadding = context.theme().scale(horizontalPadding);
        int textYOffsetPx = context.theme().fieldTextNudge() + textYOffset - 1;
        int contentInset = context.theme().scale(3);
        int frameInset = context.theme().scale(2);
        int lineSpacing = context.theme().scale(1);
        int bodyColor = context.theme().color(textTone);
        int placeholderColor = context.theme().color(placeholderTone);
        int bg = backgroundColorOverride != 0
                ? backgroundColorOverride
                : (focused && focusEffectsEnabled) ? 0xA30C0C0F : 0x7C0A090C;
        int border = !editable
                ? AutismTheme.recolor(0x99662C2C, Channel.OUTLINE)
                : (focused && focusEffectsEnabled) ? context.theme().headerAccent()
                : (hoverEffectsEnabled && hover > 0.0f) ? AutismTheme.recolor(0x99662C2C, Channel.OUTLINE)
                : context.theme().borderColor();
        int innerW = contentWidth(drawW, scaledHorizontalPadding, frameInset);
        RichMetrics richMetrics = value.isEmpty() ? null : buildRichMetrics(value);
        if (!multiline) ensureViewOffset(context.textRenderer(), font, bodyColor, innerW, richMetrics);

        UiBounds fieldBounds = UiBounds.of(drawX, drawY, drawW, drawH);
        if (drawBackground) UiRenderer.rect(context.drawContext(), fieldBounds, context.applyAlpha(bg));
        if (editable && hoverEffectsEnabled && hover > 0.0f && !focused) {
            int hoverTint = AutismTheme.recolor(((int) (hover * 14) << 24) | 0x00FF2F2F, Channel.ACCENT);
            UiRenderer.rect(context.drawContext(), fieldBounds.inset(1), context.applyAlpha(hoverTint));
        }
        UiRenderer.outline(context.drawContext(), fieldBounds, context.applyAlpha(border));
        if (!editable) {
            UiRenderer.rect(context.drawContext(), fieldBounds.inset(1), context.applyAlpha(0x66141518));
        } else if (focused && focusEffectsEnabled) {
            UiRenderer.rect(context.drawContext(), fieldBounds.inset(1), context.applyAlpha(AutismTheme.recolor(0x12FF3B3B, Channel.ACCENT)));
        }

        int textX = drawX + scaledHorizontalPadding;

        if (value.isEmpty()) {
            if (multiline) {
                int textY = drawY + contentInset + textYOffsetPx;
                context.viewport().enableScissor(context.drawContext(), drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1);
                try {
                    for (String line : wrapPlainText(context.textRenderer(), font, placeholderColor, placeholder, innerW)) {
                        if (textY + fontHeight > drawY + drawH - frameInset) break;
                        UiText.draw(context.drawContext(), context.textRenderer(), line, font, context.applyAlpha(placeholderColor), textX, textY, false);
                        textY += fontHeight + lineSpacing;
                    }
                } finally {
                    context.viewport().disableScissor(context.drawContext());
                }
                if (focused && blinkVisible()) {
                    context.drawContext().fill(textX, drawY + contentInset, textX + 1, drawY + contentInset + fontHeight, context.applyAlpha(AutismTheme.recolor(0xFFFFEDED, Channel.TEXT)));
                }
            } else {
                int textY = UiSizing.alignTextY(drawY, drawH, fontHeight, textYOffsetPx);
                String displayPlaceholder = UiText.trimToWidth(context.textRenderer(), placeholder, innerW, font, placeholderColor);
                UiText.draw(context.drawContext(), context.textRenderer(), displayPlaceholder, font, context.applyAlpha(placeholderColor), textX, textY, false);
                if (focused && blinkVisible()) {
                    context.drawContext().fill(textX, drawY + contentInset, textX + 1, drawY + drawH - contentInset, context.applyAlpha(AutismTheme.recolor(0xFFFFEDED, Channel.TEXT)));
                }
            }
        } else {
            if (multiline) {
                List<WrappedLine> wrappedLines = wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
                int lineHeight = fontHeight + lineSpacing;
                int visibleLineCount = Math.max(1, Math.max(1, drawH - context.theme().scale(6)) / lineHeight);
                ensureWrapViewLine(visibleLineCount, wrappedLines);
                int selectionStart = Math.min(cursor, selectionAnchor);
                int selectionEnd = Math.max(cursor, selectionAnchor);
                int startLine = Math.min(wrapViewLine, Math.max(0, wrappedLines.size() - 1));
                int endLine = Math.min(wrappedLines.size(), startLine + visibleLineCount);

                context.viewport().enableScissor(context.drawContext(), drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1);
                try {
                    int lineY = drawY + contentInset + textYOffsetPx;
                    for (int lineIndex = startLine; lineIndex < endLine; lineIndex++) {
                        WrappedLine line = wrappedLines.get(lineIndex);
                        if (selectionStart != selectionEnd) {
                            int visibleSelectionStart = Math.max(selectionStart, line.start());
                            int visibleSelectionEnd = Math.min(selectionEnd, line.renderEnd());
                            if (visibleSelectionStart < visibleSelectionEnd) {
                                int selectionX = textX + renderSubstringWidth(context.textRenderer(), font, bodyColor,
                                        value, richMetrics, line.start(), visibleSelectionStart);
                                int selectionW = renderSubstringWidth(context.textRenderer(), font, bodyColor,
                                        value, richMetrics, visibleSelectionStart, visibleSelectionEnd);
                                context.drawContext().fill(selectionX, lineY - 1, selectionX + selectionW,
                                lineY + fontHeight + lineSpacing, context.applyAlpha(AutismTheme.recolor(0x66FF4A4A, Channel.ACCENT)));
                            }
                        }

                        if (richMetrics != null) {
                            renderRichSubstring(context.drawContext(), context.textRenderer(), font, bodyColor,
                                    value, richMetrics, line.start(), line.renderEnd(), textX, lineY);
                        } else {
                            String lineComponent = value.substring(line.start(), line.renderEnd());
                            UiText.draw(context.drawContext(), context.textRenderer(), lineComponent, font,
                                    context.applyAlpha(bodyColor), textX, lineY, false);
                        }
                        lineY += lineHeight;
                    }
                } finally {
                    context.viewport().disableScissor(context.drawContext());
                }

                if (focused && blinkVisible()) {
                    int cursorLine = lineIndexForCursor(wrappedLines, cursor);
                    if (cursorLine >= startLine && cursorLine < endLine) {
                        WrappedLine line = wrappedLines.get(cursorLine);

                        int visibleCursor = Math.min(cursor, lineContentEnd(line));
                        int caretX = textX + renderSubstringWidth(context.textRenderer(), font, bodyColor,
                                value, richMetrics, line.start(), visibleCursor);
                        int caretY = drawY + contentInset + textYOffsetPx + ((cursorLine - startLine) * lineHeight);
                        context.drawContext().fill(caretX, caretY - 1, caretX + 1,
                                caretY + fontHeight + lineSpacing, context.applyAlpha(AutismTheme.recolor(0xFFFFEDED, Channel.TEXT)));
                    }
                }
                drawMultilineScrollbar(context, drawX, drawY, drawW, drawH, lineHeight, visibleLineCount, wrappedLines.size());
            } else {
                int textY = UiSizing.alignTextY(drawY, drawH, fontHeight, textYOffsetPx);
                int visibleEnd = visibleEndIndex(context.textRenderer(), font, bodyColor, innerW, richMetrics);
                int selectionStart = Math.min(cursor, selectionAnchor);
                int selectionEnd = Math.max(cursor, selectionAnchor);

                if (selectionStart != selectionEnd) {
                    int visibleSelectionStart = Math.max(selectionStart, viewOffset);
                    int visibleSelectionEnd = Math.min(selectionEnd, visibleEnd);
                    if (visibleSelectionStart < visibleSelectionEnd) {
                        int selectionX = textX + renderSubstringWidth(context.textRenderer(), font, bodyColor, value, richMetrics, viewOffset, visibleSelectionStart);
                        int selectionW = renderSubstringWidth(context.textRenderer(), font, bodyColor, value, richMetrics, visibleSelectionStart, visibleSelectionEnd);
                        context.drawContext().fill(selectionX, drawY + 2, selectionX + selectionW, drawY + drawH - 2, context.applyAlpha(AutismTheme.recolor(0x66FF4A4A, Channel.ACCENT)));
                    }
                }

                if (richMetrics != null) {
                    context.viewport().enableScissor(context.drawContext(), drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1);
                    try {
                        renderRichSubstring(context.drawContext(), context.textRenderer(), font, bodyColor,
                                value, richMetrics, viewOffset, visibleEnd, textX, textY);
                    } finally {
                        context.viewport().disableScissor(context.drawContext());
                    }
                } else {
                    context.viewport().enableScissor(context.drawContext(), drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1);
                    try {
                        UiText.draw(context.drawContext(), context.textRenderer(), value.substring(viewOffset, visibleEnd), font, context.applyAlpha(bodyColor), textX, textY, false);
                    } finally {
                        context.viewport().disableScissor(context.drawContext());
                    }
                }

                if (focused && blinkVisible()) {
                    int caretX = textX + renderSubstringWidth(context.textRenderer(), font, bodyColor, value, richMetrics, viewOffset, cursor);
                    context.drawContext().fill(caretX, drawY + contentInset, caretX + 1, drawY + drawH - contentInset, context.applyAlpha(AutismTheme.recolor(0xFFFFEDED, Channel.TEXT)));
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) return false;
        if (multiline && tryStartMultilineScrollbarDrag(context, mouseX, mouseY)) {
            focused = editable;
            return true;
        }
        if (!editable) return false;
        focused = true;
        draggingSelection = true;
        int innerW = contentWidth(Math.round(width), context.theme().scale(horizontalPadding), context.theme().scale(2));
        Identifier font = resolvedFont(context);
        int bodyColor = context.theme().color(textTone);
        RichMetrics richMetrics = buildRichMetrics(value);
        int clickCursor;
        if (multiline) {
            List<WrappedLine> wrappedLines = wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            int lineHeight = context.theme().fontHeight(textTone) + context.theme().scale(1);
            int visibleLineCount = Math.max(1, Math.max(1, Math.round(height) - context.theme().scale(6)) / lineHeight);
            ensureWrapViewLine(visibleLineCount, wrappedLines);
            clickCursor = cursorFromMousePosition(context.textRenderer(), font, bodyColor,
                    mouseX - (x + context.theme().scale(horizontalPadding)), mouseY - multilineTextTopOffset(context), lineHeight,
                    wrappedLines, richMetrics);
        } else {
            ensureViewOffset(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            clickCursor = cursorFromMouseX(context.textRenderer(), font, bodyColor, mouseX - (x + context.theme().scale(horizontalPadding)), richMetrics);
        }
        int clickCount = updateClickCount(mouseX, mouseY);
        if (clickCount >= 3) {
            selectLineAt(clickCursor);
        } else if (clickCount == 2) {
            selectWordAt(clickCursor);
        } else {
            cursor = clickCursor;
            selectionAnchor = clickCursor;
        }
        manualWrapScroll = false;
        restartBlink();
        return true;
    }

    @Override
    public boolean mouseReleased(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (button == 0 && draggingMultilineScrollbar) {
            draggingMultilineScrollbar = false;
            multilineScrollbarGrabOffset = 0;
            return true;
        }
        if (button == 0 && draggingSelection) {
            draggingSelection = false;
            return focused;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(DirectRenderContext context, float mouseX, float mouseY, int button, float deltaX, float deltaY) {
        if (button == 0 && draggingMultilineScrollbar) {
            dragMultilineScrollbar(context, mouseY);
            return true;
        }
        if (!editable || !focused || !draggingSelection || button != 0) return false;
        Identifier font = resolvedFont(context);
        int bodyColor = context.theme().color(textTone);
        int innerW = contentWidth(Math.round(width), context.theme().scale(horizontalPadding), context.theme().scale(2));
        RichMetrics richMetrics = buildRichMetrics(value);
        if (multiline) {
            List<WrappedLine> wrappedLines = wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            int lineHeight = context.theme().fontHeight(textTone) + context.theme().scale(1);
            int visibleLineCount = Math.max(1, Math.max(1, Math.round(height) - context.theme().scale(6)) / lineHeight);
            ensureWrapViewLine(visibleLineCount, wrappedLines);
            cursor = cursorFromMousePosition(context.textRenderer(), font, bodyColor,
                    mouseX - (x + context.theme().scale(horizontalPadding)), mouseY - multilineTextTopOffset(context), lineHeight,
                    wrappedLines, richMetrics);
        } else {
            ensureViewOffset(context.textRenderer(), font, bodyColor, innerW, richMetrics);
            cursor = cursorFromMouseX(context.textRenderer(), font, bodyColor, mouseX - (x + context.theme().scale(horizontalPadding)), richMetrics);
        }
        manualWrapScroll = false;
        restartBlink();
        return true;
    }

    private void drawMultilineScrollbar(DirectRenderContext context, int drawX, int drawY, int drawW, int drawH,
                                        int lineHeight, int visibleLineCount, int wrappedLineCount) {
        int maxStart = Math.max(0, wrappedLineCount - visibleLineCount);
        if (maxStart <= 0) return;
        CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
                Math.max(lineHeight, wrappedLineCount * lineHeight),
                Math.max(lineHeight, visibleLineCount * lineHeight),
                drawX + drawW - 5,
                drawY + 2,
                3,
                Math.max(1, drawH - 4),
                wrapViewLine * lineHeight
        );
        CompactScrollbar.draw(context.drawContext(), metrics, metrics.contains(context.mouseX(), context.mouseY()), draggingMultilineScrollbar);
    }

    private boolean tryStartMultilineScrollbarDrag(DirectRenderContext context, float mouseX, float mouseY) {
        MultilineScrollInfo info = multilineScrollInfo(context);
        if (info == null || !info.metrics().hasScroll() || !info.metrics().contains(mouseX, mouseY)) return false;
        draggingMultilineScrollbar = true;
        multilineScrollbarGrabOffset = info.metrics().overThumb(mouseX, mouseY)
                ? Math.max(0, Math.round(mouseY) - info.metrics().thumbY())
                : info.metrics().thumbHeight() / 2;
        dragMultilineScrollbar(context, mouseY);
        return true;
    }

    private void dragMultilineScrollbar(DirectRenderContext context, float mouseY) {
        MultilineScrollInfo info = multilineScrollInfo(context);
        if (info == null || !info.metrics().hasScroll()) return;
        int pixels = CompactScrollbar.scrollFromThumb(info.metrics(), mouseY, multilineScrollbarGrabOffset);
        wrapViewLine = Math.max(0, Math.min(info.maxStart(), Math.round(pixels / (float) Math.max(1, info.lineHeight()))));
        manualWrapScroll = true;
    }

    private MultilineScrollInfo multilineScrollInfo(DirectRenderContext context) {
        if (!multiline) return null;
        Identifier font = resolvedFont(context);
        int bodyColor = context.theme().color(textTone);
        int innerW = contentWidth(Math.round(width), context.theme().scale(horizontalPadding), context.theme().scale(2));
        RichMetrics richMetrics = buildRichMetrics(value);
        List<WrappedLine> wrappedLines = value.isEmpty()
                ? wrapPlainAsLines(context.textRenderer(), font, bodyColor, placeholder, innerW)
                : wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
        int lineHeight = context.theme().fontHeight(textTone) + context.theme().scale(1);
        int visibleLineCount = Math.max(1, Math.max(1, Math.round(height) - context.theme().scale(6)) / lineHeight);
        int maxStart = Math.max(0, wrappedLines.size() - visibleLineCount);
        CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
                Math.max(lineHeight, wrappedLines.size() * lineHeight),
                Math.max(lineHeight, visibleLineCount * lineHeight),
                Math.round(x + width) - 5,
                Math.round(y) + 2,
                3,
                Math.max(1, Math.round(height) - 4),
                wrapViewLine * lineHeight
        );
        return new MultilineScrollInfo(metrics, lineHeight, maxStart);
    }

    private List<WrappedLine> wrapPlainAsLines(Font renderer, Identifier font, int color, String text, int maxWidth) {
        String source = text == null || text.isEmpty() ? " " : text;
        List<WrappedLine> lines = new ArrayList<>();
        int offset = 0;
        for (String line : wrapPlainText(renderer, font, color, source, maxWidth)) {
            int end = Math.min(source.length(), offset + line.length());
            lines.add(new WrappedLine(offset, end, end));
            offset = Math.min(source.length(), end + 1);
        }
        if (lines.isEmpty()) lines.add(new WrappedLine(0, 0, 0));
        return lines;
    }

    private record MultilineScrollInfo(CompactScrollbar.Metrics metrics, int lineHeight, int maxStart) {
    }

    @Override
    public boolean mouseScrolled(DirectRenderContext context, float mouseX, float mouseY, float amount) {
        if (!multiline || !contains(mouseX, mouseY)) return false;
        Identifier font = resolvedFont(context);
        int bodyColor = context.theme().color(textTone);
        int innerW = contentWidth(Math.round(width), context.theme().scale(horizontalPadding), context.theme().scale(2));
        RichMetrics richMetrics = buildRichMetrics(value);
        List<WrappedLine> wrappedLines = wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
        int lineHeight = context.theme().fontHeight(textTone) + context.theme().scale(1);
        int visibleLineCount = Math.max(1, Math.max(1, Math.round(height) - context.theme().scale(6)) / lineHeight);
        int maxStart = Math.max(0, wrappedLines.size() - visibleLineCount);
        if (maxStart <= 0) return false;

        int step = Math.max(1, Math.round(Math.abs(amount) * 3.0f));
        int next = wrapViewLine + (amount < 0.0f ? step : -step);
        next = Math.max(0, Math.min(maxStart, next));
        if (next == wrapViewLine) return false;
        wrapViewLine = next;
        manualWrapScroll = true;
        return true;
    }

    @Override
    public boolean keyPressed(DirectRenderContext context, int keyCode, int scanCode, int modifiers) {
        if (!focused || !editable) return false;
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                setFocused(false);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {

                if (multiline && !(submitOnEnter && !shift)) {
                    replaceSelection("\n");
                    return true;
                }
                if (onSubmit != null) onSubmit.accept(value);
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    selectionAnchor = 0;
                    cursor = value.length();
                    manualWrapScroll = false;
                    restartBlink();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) {
                    copySelection();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl) {
                    copySelection();
                    deleteSelection();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    pasteClipboard();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (ctrl) setCursor(previousWordBoundary(cursor), shift);
                else moveCursor(-1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (ctrl) setCursor(nextWordBoundary(cursor), shift);
                else moveCursor(1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                if (!multiline && historyNavigationEnabled) {
                    navigateHistory(-1);
                    return true;
                }
                if (multiline) {
                    moveCursorVertically(context, -1, shift);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (!multiline && historyNavigationEnabled) {
                    navigateHistory(1);
                    return true;
                }
                if (multiline) {
                    moveCursorVertically(context, 1, shift);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (ctrl || !multiline) setCursor(0, shift);
                else setCursor(lineStart(cursor), shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (ctrl || !multiline) setCursor(value.length(), shift);
                else setCursor(lineEnd(cursor), shift);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (deleteSelection()) return true;
                if (cursor > 0) {
                    int from = ctrl ? previousWordBoundary(cursor) : Utf16TextMetrics.previousBoundary(value, cursor);
                    replaceRange(from, cursor, "");
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (deleteSelection()) return true;
                if (cursor < value.length()) {
                    int to = ctrl ? nextWordBoundary(cursor) : Utf16TextMetrics.nextBoundary(value, cursor);
                    replaceRange(cursor, to, "");
                }
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(DirectRenderContext context, char chr, int modifiers) {
        if (!focused || !editable || !isAllowedCharacter(chr)) return false;
        replaceSelection(Character.toString(chr));
        return true;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean nextFocused = focused && editable;
        if (!nextFocused || this.focused != nextFocused) {
            this.draggingSelection = false;
        }
        this.focused = nextFocused;
        restartBlink();
    }

    public CompactTextInput setSelectionEnd(int selectionEnd) {
        cursor = Math.max(0, Math.min(value.length(), selectionEnd));
        manualWrapScroll = false;
        restartBlink();
        return this;
    }

    public CompactTextInput moveCursorToEnd() {
        setCursor(value.length(), false);
        return this;
    }

    public int selectionStart() {
        return Math.min(cursor, selectionAnchor);
    }

    public int selectionEnd() {
        return Math.max(cursor, selectionAnchor);
    }

    public String selectedText() {
        return value.substring(selectionStart(), selectionEnd());
    }

    public CompactTextInput insertText(String text) {
        replaceSelection(text == null ? "" : text);
        return this;
    }

    private Identifier resolvedFont(DirectRenderContext context) {
        return fontId != null ? fontId : context.theme().fontFor(UiTone.BODY);
    }

    private boolean blinkVisible() {
        return ((System.currentTimeMillis() - blinkStartMs) / 530L) % 2L == 0L;
    }

    private void restartBlink() {
        blinkStartMs = System.currentTimeMillis();
    }

    private void ensureSelectionOrder() {
        cursor = Utf16TextMetrics.floorBoundary(value, cursor);
        selectionAnchor = Utf16TextMetrics.floorBoundary(value, selectionAnchor);
    }

    private int visibleEndIndex(Font renderer, Identifier font, int color, int availableWidth, RichMetrics richMetrics) {
        ensureValueMeasurements(renderer, font, color, richMetrics);
        int start = Utf16TextMetrics.floorBoundary(value, viewOffset);
        int target = cumulativeWidths[start] + Math.max(0, availableWidth);
        return Utf16TextMetrics.floorBoundary(value,
            upperBound(cumulativeWidths, start, value.length(), target));
    }

    private void ensureViewOffset(Font renderer, Identifier font, int color, int availableWidth, RichMetrics richMetrics) {
        ensureSelectionOrder();
        ensureValueMeasurements(renderer, font, color, richMetrics);
        int safeCursor = Utf16TextMetrics.floorBoundary(value, cursor);
        int target = cumulativeWidths[safeCursor] - Math.max(0, availableWidth);
        viewOffset = Utf16TextMetrics.floorBoundary(value,
            lowerBound(cumulativeWidths, 0, safeCursor, target));
        if (viewOffset == safeCursor && safeCursor > 0) {
            viewOffset = Utf16TextMetrics.previousBoundary(value, safeCursor);
        }
    }

    private int cursorFromMouseX(Font renderer, Identifier font, int color, float mouseInnerX, RichMetrics richMetrics) {
        ensureValueMeasurements(renderer, font, color, richMetrics);
        int start = Utf16TextMetrics.floorBoundary(value, viewOffset);
        int target = cumulativeWidths[start] + Math.max(0, Math.round(mouseInnerX));
        int candidate = nearestIndex(cumulativeWidths, start, value.length(), target);
        return Utf16TextMetrics.nearestBoundary(value, cumulativeWidths, candidate, target);
    }

    private List<String> wrapPlainText(Font renderer, Identifier font, int color, String text, int availableWidth) {
        if (text == null || text.isEmpty()) return List.of("");
        int safeWidth = Math.max(1, availableWidth);
        int reloadGeneration = UiText.reloadGeneration();
        if (Objects.equals(text, placeholder)
                && Objects.equals(cachedPlaceholderText, text)
                && cachedPlaceholderRenderer == renderer
                && Objects.equals(cachedPlaceholderFont, font)
                && cachedPlaceholderReloadGeneration == reloadGeneration
                && cachedPlaceholderWidth == safeWidth) {
            return cachedPlaceholderLines;
        }
        List<String> wrapped = new ArrayList<>();
        for (WrappedLine line : wrapLinesForText(renderer, font, color, text, safeWidth, null)) {
            wrapped.add(text.substring(line.start(), line.renderEnd()));
        }
        List<String> result = wrapped.isEmpty() ? List.of("") : List.copyOf(wrapped);
        if (Objects.equals(text, placeholder)) {
            cachedPlaceholderText = text;
            cachedPlaceholderRenderer = renderer;
            cachedPlaceholderFont = font;
            cachedPlaceholderReloadGeneration = reloadGeneration;
            cachedPlaceholderWidth = safeWidth;
            cachedPlaceholderLines = result;
        }
        return result;
    }

    private RichMetrics buildRichMetrics(String sourceValue) {
        if (displayTextProvider == null || sourceValue == null || sourceValue.isEmpty()) return null;
        long providerRevision = displayTextProviderRevision();
        if (cachedRichComputed
            && cachedRichProvider == displayTextProvider
            && cachedRichProviderRevision == providerRevision
            && Objects.equals(cachedRichValue, sourceValue)) {
            return cachedRichMetrics;
        }
        Component richDisplay = displayTextProvider.apply(sourceValue);
        if (richDisplay == null) {
            cacheRichMetrics(sourceValue, providerRevision, null);
            return null;
        }
        String richComponent = richDisplay.getString();
        if (!Objects.equals(richComponent, sourceValue)) {
            cacheRichMetrics(sourceValue, providerRevision, null);
            return null;
        }

        List<Style> rawStyles = new ArrayList<>(richComponent.length());
        richDisplay.visit((style, part) -> {
            if (part != null && !part.isEmpty()) {
                Style safeStyle = style == null ? Style.EMPTY : style;
                for (int i = 0; i < part.length(); i++) rawStyles.add(safeStyle);
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);

        List<Style> charStyles = new ArrayList<>(rawStyles);
        if (charStyles.isEmpty()) {
            cacheRichMetrics(sourceValue, providerRevision, null);
            return null;
        }
        while (charStyles.size() < richComponent.length()) charStyles.add(Style.EMPTY);
        if (charStyles.size() > richComponent.length()) {
            charStyles = new ArrayList<>(charStyles.subList(0, richComponent.length()));
        }
        RichMetrics metrics = new RichMetrics(richComponent, charStyles);
        cacheRichMetrics(sourceValue, providerRevision, metrics);
        return metrics;
    }

    private long displayTextProviderRevision() {
        if (displayTextRevisionProvider == null) return 0L;
        try {
            return displayTextRevisionProvider.getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private void cacheRichMetrics(String sourceValue, long providerRevision, RichMetrics metrics) {
        cachedRichValue = sourceValue == null ? "" : sourceValue;
        cachedRichProvider = displayTextProvider;
        cachedRichProviderRevision = providerRevision;
        cachedRichMetrics = metrics;
        cachedRichComputed = true;
    }

    private void invalidateRichMetrics() {
        cachedRichValue = "";
        cachedRichProvider = null;
        cachedRichProviderRevision = Long.MIN_VALUE;
        cachedRichMetrics = null;
        cachedRichComputed = false;
    }

    private void invalidateTextCaches() {
        textRevision++;
        invalidateRichMetrics();
        measuredTextRevision = Long.MIN_VALUE;
        invalidateWrappedLines();
    }

    private void invalidateWrappedLines() {
        wrappedTextRevision = Long.MIN_VALUE;
        wrappedRichMetrics = null;
        cachedWrappedLines = List.of();
    }

    private void ensureValueMeasurements(Font renderer, Identifier font, int color, RichMetrics richMetrics) {
        int reloadGeneration = UiText.reloadGeneration();
        if (measuredTextRevision == textRevision
                && measuredStyleRevision == styleRevision
                && measuredRenderer == renderer
                && Objects.equals(measuredFont, font)
                && measuredReloadGeneration == reloadGeneration
                && measuredColor == color
                && measuredRichMetrics == richMetrics
                && cumulativeWidths.length == value.length() + 1) {
            return;
        }

        cumulativeWidths = Utf16TextMetrics.cumulativeWidths(value, (text, start, end) -> {
            String glyph = text.substring(start, end);
            if (richMetrics == null) return renderer.width(glyph);
            Style style = richMetrics.styles().get(Math.min(start, richMetrics.styles().size() - 1));
            return renderer.width(Component.literal(glyph).setStyle(style == null ? Style.EMPTY : style));
        });
        measuredTextRevision = textRevision;
        measuredStyleRevision = styleRevision;
        measuredRenderer = renderer;
        measuredFont = font;
        measuredReloadGeneration = reloadGeneration;
        measuredColor = color;
        measuredRichMetrics = richMetrics;
    }

    private Component buildRichSubstringText(String sourceText, RichMetrics richMetrics, int start, int end) {
        int safeStart = Utf16TextMetrics.floorBoundary(sourceText, start);
        int safeEnd = Math.max(safeStart, Utf16TextMetrics.floorBoundary(sourceText, end));
        if (richMetrics == null || safeStart >= safeEnd) {
            return Component.literal(sourceText.substring(safeStart, safeEnd));
        }

        MutableComponent out = Component.empty();
        StringBuilder segment = new StringBuilder();
        Style currentStyle = null;
        for (int i = safeStart; i < safeEnd; i++) {
            Style style = richMetrics.styles().get(Math.min(i, richMetrics.styles().size() - 1));
            if (currentStyle == null) {
                currentStyle = style;
            } else if (!currentStyle.equals(style)) {
                out.append(Component.literal(segment.toString()).setStyle(currentStyle));
                segment.setLength(0);
                currentStyle = style;
            }
            segment.append(sourceText.charAt(i));
        }
        if (!segment.isEmpty()) {
            out.append(Component.literal(segment.toString()).setStyle(currentStyle == null ? Style.EMPTY : currentStyle));
        }
        return out;
    }

    private int renderSubstringWidth(Font renderer, Identifier font, int color, String sourceText,
                                     RichMetrics richMetrics, int start, int end) {
        int safeStart = Utf16TextMetrics.floorBoundary(sourceText, start);
        int safeEnd = Math.max(safeStart, Utf16TextMetrics.floorBoundary(sourceText, end));
        if (safeStart >= safeEnd) return 0;
        if (Objects.equals(sourceText, value)) {
            ensureValueMeasurements(renderer, font, color, richMetrics);
            return cumulativeWidths[safeEnd] - cumulativeWidths[safeStart];
        }
        if (richMetrics == null) return substringWidth(renderer, font, color, sourceText.substring(safeStart, safeEnd));
        return richSubstringWidth(renderer, font, color, sourceText, richMetrics, safeStart, safeEnd);
    }

    private int richSubstringWidth(Font renderer, Identifier font, int fallbackColor, String sourceText,
                                   RichMetrics richMetrics, int start, int end) {
        int safeStart = Utf16TextMetrics.floorBoundary(sourceText, start);
        int safeEnd = Math.max(safeStart, Utf16TextMetrics.floorBoundary(sourceText, end));
        return renderer.width(buildRichSubstringText(sourceText, richMetrics, safeStart, safeEnd));
    }

    private void renderRichSubstring(GuiGraphicsExtractor context, Font renderer, Identifier font, int fallbackColor,
                                     String sourceText, RichMetrics richMetrics, int start, int end, int x, int y) {
        int safeStart = Utf16TextMetrics.floorBoundary(sourceText, start);
        int safeEnd = Math.max(safeStart, Utf16TextMetrics.floorBoundary(sourceText, end));
        if (safeStart >= safeEnd) return;
        context.text(renderer, buildRichSubstringText(sourceText, richMetrics, safeStart, safeEnd),
            x, y, fallbackColor, false);
    }

    private List<WrappedLine> wrapValueLines(Font renderer, Identifier font, int color, int availableWidth, RichMetrics richMetrics) {
        int safeWidth = Math.max(1, availableWidth);
        int reloadGeneration = UiText.reloadGeneration();
        if (wrappedTextRevision == textRevision
                && wrappedStyleRevision == styleRevision
                && wrappedRenderer == renderer
                && Objects.equals(wrappedFont, font)
                && wrappedReloadGeneration == reloadGeneration
                && wrappedWidth == safeWidth
                && wrappedMultiline == multiline
                && wrappedRichMetrics == richMetrics) {
            return cachedWrappedLines;
        }

        ensureValueMeasurements(renderer, font, color, richMetrics);
        List<WrappedLine> lines = wrapLinesForText(renderer, font, color, value, safeWidth, richMetrics);
        cachedWrappedLines = List.copyOf(lines);
        wrappedTextRevision = textRevision;
        wrappedStyleRevision = styleRevision;
        wrappedRenderer = renderer;
        wrappedFont = font;
        wrappedReloadGeneration = reloadGeneration;
        wrappedWidth = safeWidth;
        wrappedMultiline = multiline;
        wrappedRichMetrics = richMetrics;
        return cachedWrappedLines;
    }

    private List<WrappedLine> wrapLinesForText(Font renderer, Identifier font, int color, String text, int availableWidth, RichMetrics richMetrics) {
        String safeComponent = text == null ? "" : text;
        List<WrappedLine> lines = new ArrayList<>();
        for (TextWrapLayout.Line line : TextWrapLayout.layout(safeComponent, availableWidth,
            (start, end) -> renderSubstringWidth(renderer, font, color, safeComponent, richMetrics, start, end))) {
            lines.add(new WrappedLine(line.start(), line.end(), line.renderEnd()));
        }
        return lines;
    }

    private void ensureWrapViewLine(int visibleLineCount, List<WrappedLine> wrappedLines) {
        if (wrappedLines.isEmpty()) {
            wrapViewLine = 0;
            return;
        }
        int cursorLine = lineIndexForCursor(wrappedLines, cursor);
        int maxStart = Math.max(0, wrappedLines.size() - visibleLineCount);
        if (manualWrapScroll) {
            wrapViewLine = Math.max(0, Math.min(wrapViewLine, maxStart));
            return;
        }
        if (cursorLine < wrapViewLine) wrapViewLine = cursorLine;
        if (cursorLine >= wrapViewLine + visibleLineCount) wrapViewLine = cursorLine - visibleLineCount + 1;
        wrapViewLine = Math.max(0, Math.min(wrapViewLine, maxStart));
    }

    private int lineIndexForCursor(List<WrappedLine> wrappedLines, int index) {
        if (wrappedLines.isEmpty()) return 0;
        int clamped = Math.max(0, Math.min(index, value.length()));
        for (int i = 0; i < wrappedLines.size(); i++) {
            WrappedLine line = wrappedLines.get(i);
            if (clamped < line.end()) return i;
            if (clamped == line.end()) {
                if (i == wrappedLines.size() - 1) return i;
                if (clamped == line.start()) return i;
            }
        }
        return wrappedLines.size() - 1;
    }

    private float multilineTextTopOffset(DirectRenderContext context) {
        return y + context.theme().scale(3) + context.theme().fieldTextNudge() + textYOffset - 1;
    }

    private int cursorFromMousePosition(Font renderer, Identifier font, int color,
                                        float mouseInnerX, float mouseInnerY, int lineHeight,
                                        List<WrappedLine> wrappedLines, RichMetrics richMetrics) {
        if (wrappedLines.isEmpty()) return 0;

        int lineIndex = wrapViewLine + Math.max(0, (int) Math.floor(mouseInnerY / (double) Math.max(1, lineHeight)));
        lineIndex = Math.max(0, Math.min(lineIndex, wrappedLines.size() - 1));
        WrappedLine line = wrappedLines.get(lineIndex);
        return cursorFromMouseXForLine(renderer, font, color, mouseInnerX, line, richMetrics);
    }

    private int cursorFromMouseXForLine(Font renderer, Identifier font, int color, float mouseInnerX, WrappedLine line, RichMetrics richMetrics) {
        ensureValueMeasurements(renderer, font, color, richMetrics);
        int contentEnd = lineContentEnd(line);
        int start = Math.max(0, Math.min(line.start(), value.length()));
        int visibleEnd = Math.max(start, Math.min(contentEnd, value.length()));
        int target = cumulativeWidths[start] + Math.max(0, Math.round(mouseInnerX));
        int candidate = nearestIndex(cumulativeWidths, start, visibleEnd, target);
        int best = Utf16TextMetrics.nearestBoundary(value, cumulativeWidths, candidate, target);
        return Math.max(line.start(), Math.min(best, contentEnd));
    }

    private int lineContentEnd(WrappedLine line) {
        int end = line.end();
        if (end > line.start() && end <= value.length() && value.charAt(end - 1) == '\n') end--;
        return end;
    }

    private void moveCursorVertically(DirectRenderContext context, int direction, boolean keepSelection) {
        Identifier font = resolvedFont(context);
        int bodyColor = context.theme().color(textTone);
        int innerW = contentWidth(Math.round(width), context.theme().scale(horizontalPadding), context.theme().scale(2));
        RichMetrics richMetrics = buildRichMetrics(value);
        List<WrappedLine> wrappedLines = wrapValueLines(context.textRenderer(), font, bodyColor, innerW, richMetrics);
        if (wrappedLines.isEmpty()) return;
        int currentLineIndex = lineIndexForCursor(wrappedLines, cursor);
        int targetLineIndex = Math.max(0, Math.min(wrappedLines.size() - 1, currentLineIndex + direction));
        if (targetLineIndex == currentLineIndex) return;

        WrappedLine currentLine = wrappedLines.get(currentLineIndex);
        int visibleCursor = Math.min(cursor, lineContentEnd(currentLine));
        int cursorX = renderSubstringWidth(context.textRenderer(), font, bodyColor, value, richMetrics, currentLine.start(), visibleCursor);
        WrappedLine targetLine = wrappedLines.get(targetLineIndex);
        int targetCursor = cursorFromMouseXForLine(context.textRenderer(), font, bodyColor, cursorX, targetLine, richMetrics);
        setCursor(targetCursor, keepSelection);
    }

    private void moveCursor(int delta, boolean keepSelection) {
        int next = cursor;
        if (delta < 0) {
            for (int i = 0; i < -delta; i++) next = Utf16TextMetrics.previousBoundary(value, next);
        } else {
            for (int i = 0; i < delta; i++) next = Utf16TextMetrics.nextBoundary(value, next);
        }
        setCursor(next, keepSelection);
    }

    private int contentWidth(int totalWidth, int padding, int frameInset) {
        return Math.max(1, totalWidth - (padding * 2) - frameInset - (multiline ? 5 : 0));
    }

    private int updateClickCount(float mouseX, float mouseY) {
        long now = System.currentTimeMillis();
        boolean closeEnough = !Float.isNaN(lastClickX)
                && Math.abs(mouseX - lastClickX) <= DOUBLE_CLICK_MAX_DIST
                && Math.abs(mouseY - lastClickY) <= DOUBLE_CLICK_MAX_DIST;
        if (now - lastClickMs <= DOUBLE_CLICK_MS && closeEnough) {
            repeatedClickCount++;
        } else {
            repeatedClickCount = 1;
        }
        lastClickMs = now;
        lastClickX = mouseX;
        lastClickY = mouseY;
        return repeatedClickCount;
    }

    private void selectWordAt(int index) {
        int clamped = Math.max(0, Math.min(index, value.length()));
        if (value.isEmpty()) {
            cursor = selectionAnchor = 0;
            return;
        }
        int probe = clamped;
        if (probe == value.length() && probe > 0) probe--;
        if (probe < value.length() && !isWordChar(value.charAt(probe)) && probe > 0 && isWordChar(value.charAt(probe - 1))) {
            probe--;
        }
        int start = probe;
        int end = probe;
        if (probe < value.length() && isWordChar(value.charAt(probe))) {
            while (start > 0 && isWordChar(value.charAt(start - 1))) start--;
            end = probe + 1;
            while (end < value.length() && isWordChar(value.charAt(end))) end++;
        } else {
            while (start > 0 && !isWordChar(value.charAt(start - 1)) && value.charAt(start - 1) != '\n') start--;
            end = Math.min(value.length(), probe + 1);
            while (end < value.length() && !isWordChar(value.charAt(end)) && value.charAt(end) != '\n') end++;
        }
        selectionAnchor = Utf16TextMetrics.floorBoundary(value, start);
        cursor = Utf16TextMetrics.ceilBoundary(value, Math.max(start, end));
    }

    private void selectLineAt(int index) {
        int clamped = Math.max(0, Math.min(index, value.length()));
        selectionAnchor = Utf16TextMetrics.floorBoundary(value, lineStart(clamped));
        cursor = Utf16TextMetrics.floorBoundary(value, lineEnd(clamped));
        if (cursor < value.length() && value.charAt(cursor) == '\n') cursor++;
    }

    private int previousWordBoundary(int index) {
        int i = Math.max(0, Math.min(index, value.length()));
        while (i > 0 && Character.isWhitespace(value.charAt(i - 1))) i--;
        if (i > 0 && isWordChar(value.charAt(i - 1))) {
            while (i > 0 && isWordChar(value.charAt(i - 1))) i--;
        } else {
            while (i > 0 && !Character.isWhitespace(value.charAt(i - 1)) && !isWordChar(value.charAt(i - 1))) i--;
        }
        return i;
    }

    private int nextWordBoundary(int index) {
        int i = Math.max(0, Math.min(index, value.length()));
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        if (i < value.length() && isWordChar(value.charAt(i))) {
            while (i < value.length() && isWordChar(value.charAt(i))) i++;
        } else {
            while (i < value.length() && !Character.isWhitespace(value.charAt(i)) && !isWordChar(value.charAt(i))) i++;
        }
        return i;
    }

    private int lineStart(int index) {
        int i = Math.max(0, Math.min(index, value.length()));
        while (i > 0 && value.charAt(i - 1) != '\n') i--;
        return i;
    }

    private int lineEnd(int index) {
        int i = Math.max(0, Math.min(index, value.length()));
        while (i < value.length() && value.charAt(i) != '\n') i++;
        return i;
    }

    private boolean isWordChar(char chr) {
        return Character.isLetterOrDigit(chr) || chr == '_' || chr == '-' || chr == ':' || chr == '.';
    }

    private void navigateHistory(int direction) {
        List<String> history = historySnapshot();
        if (history.isEmpty()) return;
        if (historyIndex >= history.size()) historyIndex = history.size() - 1;
        if (direction < 0) {
            if (historyIndex == -1) {
                historyDraft = value;
                historyIndex = history.size() - 1;
            } else if (historyIndex > 0) {
                historyIndex--;
            }
            applyHistoryValue(history.get(historyIndex));
        } else {
            if (historyIndex == -1) return;
            if (historyIndex < history.size() - 1) {
                historyIndex++;
                applyHistoryValue(history.get(historyIndex));
            } else {
                historyIndex = -1;
                applyHistoryValue(historyDraft);
            }
        }
    }

    private List<String> historySnapshot() {
        List<String> out = new ArrayList<>();
        if (historyProvider != null) {
            Collection<String> provided = historyProvider.get();
            if (provided != null) {
                for (String entry : provided) appendHistorySnapshotEntry(out, entry);
            }
        }
        for (String entry : historyEntries) appendHistorySnapshotEntry(out, entry);
        return out;
    }

    private void appendHistorySnapshotEntry(List<String> out, String entry) {
        String sanitized = sanitize(entry);
        if (sanitized.isEmpty()) return;
        if (!out.isEmpty() && sanitized.equals(out.get(out.size() - 1))) return;
        out.add(sanitized);
        while (out.size() > 100) out.remove(0);
    }

    private void applyHistoryValue(String nextValue) {
        applyingHistoryValue = true;
        try {
            setText(nextValue);
        } finally {
            applyingHistoryValue = false;
        }
        setCursor(value.length(), false);
    }

    private void setCursor(int nextCursor, boolean keepSelection) {
        cursor = Utf16TextMetrics.floorBoundary(value, nextCursor);
        if (!keepSelection) selectionAnchor = cursor;
        manualWrapScroll = false;
        restartBlink();
    }

    private boolean hasSelection() {
        return cursor != selectionAnchor;
    }

    private void copySelection() {
        if (!hasSelection()) return;
        int start = Math.min(cursor, selectionAnchor);
        int end = Math.max(cursor, selectionAnchor);
        Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(start, end));
    }

    private void pasteClipboard() {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isEmpty()) return;
        replaceSelection(sanitize(clipboard));
    }

    private boolean deleteSelection() {
        if (!hasSelection()) return false;
        int start = Math.min(cursor, selectionAnchor);
        int end = Math.max(cursor, selectionAnchor);
        replaceRange(start, end, "");
        return true;
    }

    private void replaceSelection(String replacement) {
        int start = Math.min(cursor, selectionAnchor);
        int end = Math.max(cursor, selectionAnchor);
        replaceRange(start, end, replacement);
    }

    private void replaceRange(int start, int end, String replacement) {
        int safeStart = Utf16TextMetrics.floorBoundary(value, start);
        int safeEnd = Math.max(safeStart, Utf16TextMetrics.ceilBoundary(value, end));
        String sanitizedReplacement = sanitize(replacement);
        String next = value.substring(0, safeStart) + sanitizedReplacement + value.substring(safeEnd);
        if (next.length() > maxLength) {
            next = Utf16TextMetrics.truncateAtBoundary(next, maxLength);
        }
        if (!filter.test(next)) return;
        value = next;
        invalidateTextCaches();
        cursor = Utf16TextMetrics.floorBoundary(next,
            Math.min(next.length(), safeStart + sanitizedReplacement.length()));
        selectionAnchor = cursor;
        manualWrapScroll = false;
        restartBlink();
        if (historyNavigationEnabled && !applyingHistoryValue) {
            historyIndex = -1;
            historyDraft = value;
        }
        if (onChange != null) onChange.accept(value);
    }

    private String sanitize(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length() && sb.length() < maxLength; ) {
            int codePoint = input.codePointAt(i);
            int units = Character.charCount(codePoint);
            i += units;
            if (!isAllowedCharacter(codePoint) || sb.length() + units > maxLength) continue;
            sb.appendCodePoint(codePoint);
        }
        String next = sb.toString();
        return filter.test(next) ? next : value;
    }

    private boolean isAllowedCharacter(int codePoint) {
        if (codePoint == '\n') return multiline;
        if (codePoint == '\r') return false;
        return codePoint >= 32 && codePoint != 127;
    }

    private int substringWidth(Font renderer, Identifier font, int color, String text) {
        return UiText.width(renderer, text, font, color);
    }

    private static int lowerBound(int[] widths, int from, int to, int target) {
        int low = Math.max(0, from);
        int high = Math.max(low, Math.min(to, widths.length - 1));
        while (low < high) {
            int middle = low + ((high - low) >>> 1);
            if (widths[middle] < target) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static int upperBound(int[] widths, int from, int to, int target) {
        int low = Math.max(0, from);
        int high = Math.max(low, Math.min(to, widths.length - 1));
        int answer = low;
        while (low <= high) {
            int middle = low + ((high - low) >>> 1);
            if (widths[middle] <= target) {
                answer = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return answer;
    }

    private static int nearestIndex(int[] widths, int from, int to, int target) {
        if (target <= widths[from]) return from;
        if (target >= widths[to]) return to;
        int right = lowerBound(widths, from, to, target);
        if (right <= from) return from;
        int left = right - 1;
        return target - widths[left] < widths[right] - target ? left : right;
    }
}
