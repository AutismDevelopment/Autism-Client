package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroEditorOverlay;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismUiScale;
import autismclient.util.IAutismOverlay;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiProfileManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static autismclient.gui.screen.AutismScreenPalette.BG;
import static autismclient.gui.screen.AutismScreenPalette.BORDER;
import static autismclient.gui.screen.AutismScreenPalette.BORDER_ACTIVE;
import static autismclient.gui.screen.AutismScreenPalette.MUTED;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG_SOFT;
import static autismclient.gui.screen.AutismScreenPalette.SUCCESS;
import static autismclient.gui.screen.AutismScreenPalette.TEXT;

public final class AutismMultiMacroPickerScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int MARGIN = 14;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_FRAME_H = ROW_HEIGHT - 6;

    private final Screen parent;
    private String currentName;
    private final Consumer<String> onPick;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private EditBox searchField;
    private String search = "";
    private String pendingDelete = "";
    private int scrollOffset;
    private boolean scrollbarDragging;
    private int scrollbarGrab;
    private long cachedNamesRevision = Long.MIN_VALUE;
    private String cachedNamesSearch = "";
    private List<String> cachedNames = List.of();
    private boolean searchDirty;

    public AutismMultiMacroPickerScreen(Screen parent, String currentName, Consumer<String> onPick) {
        super(Component.literal("Choose Macro"));
        this.parent = parent;
        this.currentName = currentName == null ? "" : currentName;
        this.onPick = onPick;
    }

    @Override
    public void tick() {
        super.tick();

        if (searchDirty || AutismMacroManager.get().getRevision() != cachedNamesRevision) {
            searchDirty = false;
            rebuild();
        }
    }

    @Override
    protected void init() {
        searchField = new EditBox(font, MARGIN + 10, 42, panelW() - 24, 18, Component.literal("Search macros"));
        searchField.setHint(Component.literal("Search macros..."));
        searchField.setMaxLength(128);
        searchField.setValue(search);
        searchField.setResponder(value -> {
            search = safeTrim(value);
            scrollOffset = 0;
            searchDirty = true;
        });
        addRenderableWidget(searchField);
        rebuild();
    }

    private void rebuild() {
        buttons.clear();
        rows.clear();
        buttons.add(CompactOverlayButton.create(screenWidth() - MARGIN - 10 - 60, 22, 60, 18, Component.literal("Back"),
            b -> onClose()).setVariant(CompactOverlayButton.Variant.SECONDARY));
        buttons.add(CompactOverlayButton.create(screenWidth() - MARGIN - 10 - 60 - 6 - 92, 22, 92, 18,
            Component.literal("New Macro"), b -> openMacroEditor(null)).setVariant(CompactOverlayButton.Variant.PRIMARY));

        List<String> names = filteredNames();
        int total = 1 + names.size();
        int viewport = rowsBottom() - rowsTop();
        int visible = Math.max(1, viewport / ROW_HEIGHT);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, total - visible)));
        int y = rowsTop();
        for (int i = scrollOffset; i < total && y + ROW_HEIGHT <= rowsBottom(); i++) {
            String name = i == 0 ? null : names.get(i - 1);
            CompactOverlayButton edit = null;
            CompactOverlayButton delete = null;
            if (name != null) {
                final String macroName = name;
                edit = CompactOverlayButton.create(rowRight() - 46, y + 3, 42, ROW_FRAME_H - 6, Component.literal("Edit"),
                    b -> openMacroEditor(AutismMacroManager.get().get(macroName))).setVariant(CompactOverlayButton.Variant.SECONDARY);
                boolean arming = macroName.equals(pendingDelete);
                delete = CompactOverlayButton.create(rowRight() - 46 - 6 - 52, y + 3, 52, ROW_FRAME_H - 6,
                    Component.literal(arming ? "Sure?" : "Delete"),
                    b -> onDelete(macroName)).setVariant(CompactOverlayButton.Variant.DANGER);
            }
            rows.add(new Row(name, y, delete, edit));
            y += ROW_HEIGHT;
        }
    }

    private void onDelete(String name) {
        if (name == null || name.isBlank()) return;
        if (name.equals(pendingDelete)) {
            AutismMacro macro = AutismMacroManager.get().get(name);
            if (macro != null) AutismMacroManager.get().delete(macro);
            MultiProfileManager.get().replaceMacroReferences(name, "");
            MultiManager.get().replaceMacroReference(name, "");
            if (name.equals(currentName)) {
                currentName = "";
                if (onPick != null) onPick.accept("");
            }
            pendingDelete = "";
        } else {
            pendingDelete = name;
        }
        rebuild();
    }

    private void openMacroEditor(AutismMacro macro) {
        AutismMacroEditorOverlay editor = AutismMacroEditorOverlay.getSharedOverlay();
        if (editor == null) return;
        AutismOverlayManager.get().register(editor, IAutismOverlay.OverlayScope.HOST_SCREEN);
        boolean inWorld = minecraft != null && minecraft.player != null && minecraft.level != null;
        editor.setConfigurationOnly(!inWorld);
        String oldName = macro == null || macro.name == null ? "" : macro.name;
        editor.openForMulti(macro, saved -> {
            if (saved == null || saved.name == null || oldName.isBlank() || oldName.equals(saved.name)) return;
            MultiProfileManager.get().replaceMacroReferences(oldName, saved.name);
            MultiManager.get().replaceMacroReference(oldName, saved.name);
            if (oldName.equals(currentName)) {
                currentName = saved.name;
                if (onPick != null) onPick.accept(saved.name);
            }
        });
        if (minecraft != null) minecraft.gui.setScreen(new AutismOverlayHostScreen(editor, this, true));
    }

    private void pick(String name) {
        if (onPick != null) onPick.accept(name == null ? "" : name);
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private List<String> filteredNames() {
        String query = search.toLowerCase(Locale.ROOT);
        long revision = AutismMacroManager.get().getRevision();
        if (revision == cachedNamesRevision && query.equals(cachedNamesSearch)) return cachedNames;
        List<String> out = new ArrayList<>();
        for (AutismMacro macro : AutismMacroManager.get().getAll()) {
            if (macro == null || macro.name == null) continue;
            if (query.isEmpty() || macro.name.toLowerCase(Locale.ROOT).contains(query)) out.add(macro.name);
        }
        cachedNamesRevision = revision;
        cachedNamesSearch = query;
        cachedNames = List.copyOf(out);
        return cachedNames;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (virtualEvent.button() == 0) {
            CompactScrollbar.Metrics bar = scrollbarMetrics();
            if (bar.hasScroll() && bar.contains(virtualEvent.x(), virtualEvent.y())) {
                scrollbarDragging = true;
                scrollbarGrab = bar.overThumb(virtualEvent.x(), virtualEvent.y()) ? (int) Math.round(virtualEvent.y() - bar.thumbY()) : bar.thumbHeight() / 2;
                scrollOffset = clampScroll(CompactScrollbar.scrollFromThumb(bar, virtualEvent.y(), scrollbarGrab) / ROW_HEIGHT);
                rebuild();
                return true;
            }
            for (CompactOverlayButton button : buttons) {
                if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
            }
            for (Row row : rows) {
                if (row.delete() != null && CompactOverlayButton.fireIfHit(row.delete(), virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
            }
            for (Row row : rows) {
                if (row.edit() != null && CompactOverlayButton.fireIfHit(row.edit(), virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
            }
            for (Row row : rows) {
                if (virtualEvent.x() >= rowX() && virtualEvent.x() < rowRight() && virtualEvent.y() >= row.y() && virtualEvent.y() < row.y() + ROW_FRAME_H) {
                    pendingDelete = "";
                    pick(row.name() == null ? "" : row.name());
                    return true;
                }
            }
        }
        return super.mouseClicked(virtualEvent, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (scrollbarDragging) {
            CompactScrollbar.Metrics bar = scrollbarMetrics();
            scrollOffset = clampScroll(CompactScrollbar.scrollFromThumb(bar, virtualEvent.y(), scrollbarGrab) / ROW_HEIGHT);
            rebuild();
            return true;
        }
        return super.mouseDragged(virtualEvent, AutismUiScale.toVirtual(dragX), AutismUiScale.toVirtual(dragY));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(virtualEvent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollOffset = clampScroll(scrollOffset + (vertical < 0 ? 1 : -1));
        rebuild();
        return true;
    }

    private CompactScrollbar.Metrics scrollbarMetrics() {
        int total = 1 + filteredNames().size();
        int viewport = rowsBottom() - rowsTop();
        return CompactScrollbar.compute(total * ROW_HEIGHT, viewport, MARGIN + panelW() - 8, rowsTop(), 4, viewport, scrollOffset * ROW_HEIGHT);
    }

    private int clampScroll(int rowIndex) {
        int total = 1 + filteredNames().size();
        int visible = Math.max(1, (rowsBottom() - rowsTop()) / ROW_HEIGHT);
        return Math.max(0, Math.min(rowIndex, Math.max(0, total - visible)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), themeBg());
            UiRenderer.frame(graphics, UiBounds.of(MARGIN, 14, panelW(), screenHeight() - 28), themePanel(), themeBorder());
            drawText(graphics, "Choose Macro", MARGIN + 10, 24, themeText());
            for (Row row : rows) renderRow(graphics, row);
            for (CompactOverlayButton button : buttons) CompactOverlayButton.renderStyled(graphics, font, button, virtualMouseX, virtualMouseY);
            CompactScrollbar.Metrics bar = scrollbarMetrics();
            if (bar.hasScroll()) CompactScrollbar.draw(graphics, bar, bar.contains(virtualMouseX, virtualMouseY), scrollbarDragging);
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, Row row) {
        boolean none = row.name() == null;
        boolean selected = none ? currentName.isBlank() : currentName.equals(row.name());
        int fill = selected ? themeSelected() : AutismTheme.recolor(PANEL_BG_SOFT, Channel.BUTTON);
        UiRenderer.rect(graphics, UiBounds.of(rowX(), row.y(), rowRight() - rowX(), ROW_FRAME_H), fill);
        if (selected) {
            UiRenderer.rect(graphics, UiBounds.of(rowX(), row.y(), 2, ROW_FRAME_H), AutismTheme.recolor(BORDER_ACTIVE, Channel.SUCCESS));
        }
        String label = none ? "None (clear macro)" : row.name();
        int textRight = row.delete() != null ? row.delete().getX() - 6
            : (row.edit() != null ? row.edit().getX() - 6 : rowRight() - 8);
        drawFitted(graphics, label, rowX() + 8, row.y() + 5, Math.max(20, textRight - rowX() - 8), selected ? themeSuccess() : themeText());
        if (row.delete() != null) CompactOverlayButton.renderStyled(graphics, font, row.delete(), Integer.MIN_VALUE, Integer.MIN_VALUE);
        if (row.edit() != null) CompactOverlayButton.renderStyled(graphics, font, row.edit(), Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private int panelW() {
        return screenWidth() - 2 * MARGIN;
    }

    private int rowX() {
        return MARGIN + 10;
    }

    private int rowRight() {
        return MARGIN + panelW() - 14;
    }

    private int rowsTop() {
        return 66;
    }

    private int rowsBottom() {
        return screenHeight() - 18;
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        UiText.draw(graphics, font, text, THEME.fontFor(UiTone.BODY), color, x, y, false);
    }

    private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        String safe = UiText.trimToWidthEllipsis(font, text == null ? "" : text, Math.max(1, maxWidth), fontId, color);
        UiText.draw(graphics, font, safe, fontId, color, x, y, false);
    }

    private static int themeBg() {
        return AutismTheme.recolor(BG, Channel.BACKDROP);
    }

    private static int themePanel() {
        return AutismTheme.recolor(PANEL_BG, Channel.BUTTON);
    }

    private static int themeSelected() {
        return AutismTheme.recolor(0x3324D86A, Channel.SUCCESS);
    }

    private static int themeBorder() {
        return AutismTheme.recolor(BORDER, Channel.OUTLINE);
    }

    private static int themeText() {
        return AutismTheme.recolor(TEXT, Channel.TEXT);
    }

    private static int themeSuccess() {
        return AutismTheme.recolor(SUCCESS, Channel.SUCCESS);
    }

    private record Row(String name, int y, CompactOverlayButton delete, CompactOverlayButton edit) {
    }
}
