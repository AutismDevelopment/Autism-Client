package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactScreenPanel;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.modules.Module;
import autismclient.modules.BuiltinModules;
import autismclient.api.module.Setting;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismRegistryLabels;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantment;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AutismAdminItemStructuredScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int PANEL_W = 540;
    private static final int PANEL_H = 318;
    private static final int HEADER_H = 18;
    private static final int ROW_H = 18;
    private static final List<String> OPERATIONS = List.of("add_value", "add_multiplied_base", "add_multiplied_total");
    private static final List<String> SLOTS = List.of(
        "any", "mainhand", "offhand", "hand", "head", "chest", "legs", "feet", "armor", "body", "saddle"
    );

    public enum Mode {
        ENCHANTMENTS("Enchantments", "Pick an enchantment, set its level, then add it."),
        ATTRIBUTES("Attributes", "Pick an attribute and keep the exact modifier controls editable.");

        private final String title;
        private final String tip;

        Mode(String title, String tip) {
            this.title = title;
            this.tip = tip;
        }
    }

    private final Screen parent;
    private final Module module;
    private final Setting<?, ?> option;
    private final Mode mode;
    private final CompactTextInput search = input("Search registry");
    private final CompactTextInput level = input("1").setText("1");
    private final CompactTextInput amount = input("1").setText("1");
    private final CompactTextInput modifier = input("autism:modifier").setText("autism:modifier");
    private final List<String> entries = new ArrayList<>();
    private DirectRenderContext direct;
    private List<RegistryRow> filteredRows = List.of();
    private String cachedSearch = null;
    private int resultScroll;
    private int entryScroll;
    private int selectedIndex = -1;
    private String operation = OPERATIONS.get(0);
    private String slot = "mainhand";
    private UiBounds closeBounds = empty();
    private UiBounds saveBounds = empty();
    private UiBounds cancelBounds = empty();
    private UiBounds rawBounds = empty();
    private UiBounds applyBounds = empty();
    private UiBounds operationBounds = empty();
    private UiBounds slotBounds = empty();
    private UiBounds resultBounds = empty();
    private UiBounds entryBounds = empty();
    private final List<RowHit> resultHits = new ArrayList<>();
    private final List<RowHit> entryHits = new ArrayList<>();

    public AutismAdminItemStructuredScreen(Screen parent, Module module, Setting<?, ?> option, Mode mode) {
        super(Component.literal("Edit " + mode.title));
        this.parent = parent;
        this.module = module;
        this.option = option;
        this.mode = mode;
        entries.addAll(splitTopLevel(module == null || option == null ? "" : module.value(option.id())));
    }

    private static CompactTextInput input(String placeholder) {
        return new CompactTextInput()
            .setPlaceholder(placeholder)
            .setMaxLength(4096)
            .setHorizontalPadding(4)
            .setFieldHeight(16);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            int sw = screenWidth();
            int sh = screenHeight();
            int w = Math.min(PANEL_W, Math.max(300, sw - 8));
            int h = Math.min(PANEL_H, Math.max(188, sh - 8));
            int x = Math.max(4, (sw - w) / 2);
            int y = Math.max(4, (sh - h) / 2);
            UiContext ui = UiContexts.overlay(graphics, font, mx, my);
            direct = new DirectRenderContext(graphics, font, DirectViewport.current(1.0f), THEME, mx, my, delta);
            UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 0x99000000);
            UiBounds panel = UiBounds.of(x, y, w, h);
            CompactScreenPanel.render(ui, panel, HEADER_H, "Edit " + mode.title, mx >= x && mx < x + w && my >= y && my < y + HEADER_H);
            closeBounds = CompactScreenPanel.closeButton(panel, HEADER_H);
            int tipY = y + HEADER_H + 5;
            List<String> tipLines = ui.text().wrapFully(mode.tip, Math.max(1, w - 14));
            int tipLineCount = Math.min(2, tipLines.size());
            int lineH = ui.theme().typography().lineHeight;
            for (int i = 0; i < tipLineCount; i++) {
                ui.text().draw(graphics, tipLines.get(i), x + 7, tipY + i * lineH, ui.theme().colors().muted);
            }

            int searchY = tipY + Math.max(1, tipLineCount) * lineH + 4;
            search.setBounds(x + 6, searchY, w - 12, 16);
            search.render(direct);
            refreshRows();

            int contentY = searchY + 20;
            int footerY = y + h - 20;
            int contentH = Math.max(50, footerY - contentY - 5);
            boolean stacked = w < 430 || contentH < 132;
            if (stacked) {
                int available = Math.max(96, contentH - 6);
                int resultH = Math.max(42, Math.min(available / 2, contentH - 48));
                resultBounds = UiBounds.of(x + 6, contentY, w - 12, resultH);
                entryBounds = UiBounds.of(x + 6, resultBounds.bottom() + 6, w - 12, Math.max(42, footerY - resultBounds.bottom() - 11));
            } else {
                int leftW = Math.max(128, (w - 18) / 2);
                resultBounds = UiBounds.of(x + 6, contentY, leftW, contentH);
                entryBounds = UiBounds.of(resultBounds.right() + 6, contentY, Math.max(120, w - leftW - 18), contentH);
            }
            renderResults(ui, mx, my);
            renderEntries(ui, mx, my);

            rawBounds = UiBounds.of(x + 6, footerY, 48, 16);
            saveBounds = UiBounds.of(x + w - 110, footerY, 50, 16);
            cancelBounds = UiBounds.of(x + w - 56, footerY, 50, 16);
            Button.render(ui, rawBounds, "Raw", Button.Tone.NORMAL, rawBounds.contains(mx, my), false);
            Button.render(ui, saveBounds, "Save", Button.Tone.SUCCESS, saveBounds.contains(mx, my), false);
            Button.render(ui, cancelBounds, "Cancel", Button.Tone.NORMAL, cancelBounds.contains(mx, my), false);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderResults(UiContext ui, int mx, int my) {
        UiRenderer.frame(ui.graphics(), resultBounds, ui.theme().colors().windowStrong, ui.theme().colors().borderSoft);
        int headerH = 15;
        ui.text().drawFitted(ui.graphics(), "Available", resultBounds.x() + 5, resultBounds.y() + 4, resultBounds.width() - 10, ui.theme().colors().muted);
        resultHits.clear();
        int visible = Math.max(1, (resultBounds.height() - headerH - 2) / ROW_H);
        resultScroll = clamp(resultScroll, 0, Math.max(0, filteredRows.size() - visible));
        for (int row = 0; row < visible; row++) {
            int index = resultScroll + row;
            if (index >= filteredRows.size()) break;
            RegistryRow entry = filteredRows.get(index);
            UiBounds bounds = UiBounds.of(resultBounds.x() + 1, resultBounds.y() + headerH + row * ROW_H, resultBounds.width() - 2, ROW_H);
            UiRenderer.rect(ui.graphics(), bounds, bounds.contains(mx, my) ? ui.theme().colors().rowHover : ui.theme().colors().row);
            ui.text().drawFitted(ui.graphics(), entry.label, bounds.x() + 4, bounds.y() + 5, bounds.width() - 8, ui.theme().colors().text);
            resultHits.add(new RowHit(bounds, index));
        }
    }

    private void renderEntries(UiContext ui, int mx, int my) {
        UiRenderer.frame(ui.graphics(), entryBounds, ui.theme().colors().windowStrong, ui.theme().colors().borderSoft);
        int x = entryBounds.x() + 5;
        int w = entryBounds.width() - 10;
        int controlsH;
        if (mode == Mode.ENCHANTMENTS) {
            ui.text().drawFitted(ui.graphics(), "Level", x, entryBounds.y() + 4, 36, ui.theme().colors().muted);
            level.setBounds(x + 38, entryBounds.y() + 2, Math.max(42, w - 38), 16);
            level.render(direct);
            operationBounds = empty();
            slotBounds = empty();
            controlsH = 23;
        } else {
            ui.text().drawFitted(ui.graphics(), "Amount", x, entryBounds.y() + 4, 42, ui.theme().colors().muted);
            amount.setBounds(x + 44, entryBounds.y() + 2, Math.max(38, w - 44), 16);
            amount.render(direct);
            modifier.setBounds(x, entryBounds.y() + 21, w, 16);
            modifier.render(direct);
            operationBounds = UiBounds.of(x, entryBounds.y() + 40, Math.max(42, (w - 4) / 2), 16);
            slotBounds = UiBounds.of(operationBounds.right() + 4, entryBounds.y() + 40, Math.max(38, w - operationBounds.width() - 4), 16);
            Button.render(ui, operationBounds, shortOperation(), Button.Tone.NORMAL, operationBounds.contains(mx, my), false);
            Button.render(ui, slotBounds, slot, Button.Tone.NORMAL, slotBounds.contains(mx, my), false);
            controlsH = 62;
        }
        applyBounds = UiBounds.of(x, entryBounds.y() + controlsH, w, 16);
        Button.render(ui, applyBounds, selectedIndex >= 0 ? "Update Selected" : "Select an available entry", Button.Tone.PRIMARY,
            applyBounds.contains(mx, my), false);

        int listY = applyBounds.bottom() + 5;
        ui.text().drawFitted(ui.graphics(), "Current entries", x, listY, w, ui.theme().colors().muted);
        listY += 13;
        int listH = Math.max(16, entryBounds.bottom() - listY - 2);
        entryHits.clear();
        int visible = Math.max(1, listH / ROW_H);
        entryScroll = clamp(entryScroll, 0, Math.max(0, entries.size() - visible));
        for (int row = 0; row < visible; row++) {
            int index = entryScroll + row;
            if (index >= entries.size()) break;
            UiBounds bounds = UiBounds.of(entryBounds.x() + 1, listY + row * ROW_H, entryBounds.width() - 2, ROW_H);
            boolean selected = selectedIndex == index;
            UiRenderer.rect(ui.graphics(), bounds, selected ? ui.theme().colors().accentSoft : bounds.contains(mx, my) ? ui.theme().colors().rowHover : ui.theme().colors().row);
            ui.text().drawFitted(ui.graphics(), entries.get(index), bounds.x() + 4, bounds.y() + 5, Math.max(1, bounds.width() - 24), ui.theme().colors().text);
            UiBounds remove = UiBounds.of(bounds.right() - 17, bounds.y() + 2, 14, 14);
            Button.render(ui, remove, "X", Button.Tone.DANGER, remove.contains(mx, my), false);
            entryHits.add(new RowHit(bounds, index, remove));
        }
    }

    private void refreshRows() {
        String filter = search.text().trim().toLowerCase(Locale.ROOT);
        if (filter.equals(cachedSearch)) return;
        cachedSearch = filter;
        List<RegistryRow> rows = new ArrayList<>();
        if (mode == Mode.ATTRIBUTES) {
            for (var entry : BuiltInRegistries.ATTRIBUTE.entrySet()) {
                Identifier id = entry.getKey().identifier();
                Attribute attribute = entry.getValue();
                String label = Component.translatable(attribute.getDescriptionId()).getString();
                addRow(rows, filter, id.toString(), label);
            }
        } else {
            Registry<Enchantment> registry = enchantmentRegistry();
            if (registry != null) {
                for (var entry : registry.entrySet()) {
                    Identifier id = entry.getKey().identifier();
                    Enchantment enchantment = entry.getValue();
                    String label = enchantment.description() == null ? "" : enchantment.description().getString();
                    addRow(rows, filter, id.toString(), label);
                }
            }
        }
        rows.sort(Comparator.comparing(RegistryRow::label, String.CASE_INSENSITIVE_ORDER).thenComparing(RegistryRow::id));
        filteredRows = List.copyOf(rows);
        resultScroll = 0;
    }

    private void addRow(List<RegistryRow> rows, String filter, String id, String label) {
        String resolved = label == null || label.isBlank() ? AutismRegistryLabels.identifier(id) : label;
        String searchable = (id + " " + resolved).toLowerCase(Locale.ROOT);
        if (filter.isEmpty() || searchable.contains(filter)) rows.add(new RegistryRow(id, resolved));
    }

    private Registry<Enchantment> enchantmentRegistry() {
        try {
            RegistryAccess access;
            if (minecraft != null && minecraft.level != null) access = minecraft.level.registryAccess();
            else if (minecraft != null && minecraft.getConnection() != null) access = minecraft.getConnection().registryAccess();
            else access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            Optional<Registry<Enchantment>> registry = access.lookup(Registries.ENCHANTMENT);
            return registry.orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (event.button() == 0 && closeBounds.contains(mx, my)) {
            onClose();
            return true;
        }
        if (event.button() == 0 && saveBounds.contains(mx, my)) {
            save();
            return true;
        }
        if (event.button() == 0 && cancelBounds.contains(mx, my)) {
            onClose();
            return true;
        }
        if (event.button() == 0 && rawBounds.contains(mx, my)) {
            saveDraft();
            if (minecraft != null) minecraft.gui.setScreen(new AutismAdminItemOptionScreen(parent, module, option,
                mode == Mode.ENCHANTMENTS ? AutismAdminItemOptionScreen.Mode.ENCHANTMENTS : AutismAdminItemOptionScreen.Mode.ATTRIBUTES));
            return true;
        }
        if (event.button() == 0 && operationBounds.contains(mx, my)) {
            operation = cycle(OPERATIONS, operation);
            return true;
        }
        if (event.button() == 0 && slotBounds.contains(mx, my)) {
            slot = cycle(SLOTS, slot);
            return true;
        }
        if (event.button() == 0 && applyBounds.contains(mx, my) && selectedIndex >= 0 && selectedIndex < entries.size()) {
            entries.set(selectedIndex, updatedEntry(entries.get(selectedIndex)));
            return true;
        }
        for (RowHit hit : entryHits) {
            if (event.button() == 0 && hit.remove.contains(mx, my)) {
                entries.remove(hit.index);
                if (selectedIndex == hit.index) selectedIndex = -1;
                else if (selectedIndex > hit.index) selectedIndex--;
                return true;
            }
            if (event.button() == 0 && hit.bounds.contains(mx, my)) {
                selectEntry(hit.index);
                return true;
            }
        }
        for (RowHit hit : resultHits) {
            if (event.button() == 0 && hit.bounds.contains(mx, my)) {
                entries.add(newEntry(filteredRows.get(hit.index).id));
                selectedIndex = entries.size() - 1;
                selectEntry(selectedIndex);
                return true;
            }
        }
        if (direct == null) return false;
        unfocusInputs();
        return search.mouseClicked(direct, mx, my, event.button())
            || level.mouseClicked(direct, mx, my, event.button())
            || amount.mouseClicked(direct, mx, my, event.button())
            || modifier.mouseClicked(direct, mx, my, event.button());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        int direction = scrollY < 0 ? 1 : -1;
        if (resultBounds.contains(mx, my)) {
            resultScroll = Math.max(0, resultScroll + direction);
            return true;
        }
        if (entryBounds.contains(mx, my)) {
            entryScroll = Math.max(0, entryScroll + direction);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (direct == null) return false;
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        return search.mouseReleased(direct, mx, my, event.button())
            || level.mouseReleased(direct, mx, my, event.button())
            || amount.mouseReleased(direct, mx, my, event.button())
            || modifier.mouseReleased(direct, mx, my, event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (direct == null) return false;
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        return search.mouseDragged(direct, mx, my, event.button(), (float) dx, (float) dy)
            || level.mouseDragged(direct, mx, my, event.button(), (float) dx, (float) dy)
            || amount.mouseDragged(direct, mx, my, event.button(), (float) dx, (float) dy)
            || modifier.mouseDragged(direct, mx, my, event.button(), (float) dx, (float) dy);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (direct == null) return false;
        return search.keyPressed(direct, input.key(), input.scancode(), input.modifiers())
            || level.keyPressed(direct, input.key(), input.scancode(), input.modifiers())
            || amount.keyPressed(direct, input.key(), input.scancode(), input.modifiers())
            || modifier.keyPressed(direct, input.key(), input.scancode(), input.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (direct == null) return false;
        char chr = (char) input.codepoint();
        return search.charTyped(direct, chr, 0)
            || level.charTyped(direct, chr, 0)
            || amount.charTyped(direct, chr, 0)
            || modifier.charTyped(direct, chr, 0);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void save() {
        saveDraft();
        AutismNotifications.show(mode.title + " updated.", 0xFF35D873);
        onClose();
    }

    private void saveDraft() {
        if (module != null && option != null) {
            module.setValue(option.id(), String.join(",", entries));
            if (module instanceof BuiltinModules.AdminToolsModule adminTools) {
                adminTools.prepareRawItemStackEditor();
            }
        }
    }

    private void selectEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        selectedIndex = index;
        String entry = entries.get(index);
        if (mode == Mode.ENCHANTMENTS) {
            int split = entry.lastIndexOf(':');
            level.setText(split > 0 && split < entry.length() - 1 ? entry.substring(split + 1) : "1");
        } else {
            amount.setText(extract(entry, "amount", "1"));
            operation = sanitizeChoice(OPERATIONS, extract(entry, "operation", OPERATIONS.get(0)));
            slot = sanitizeChoice(SLOTS, extract(entry, "slot", "mainhand"));
            modifier.setText(extract(entry, "id", "autism:modifier"));
        }
    }

    private String newEntry(String id) {
        if (mode == Mode.ENCHANTMENTS) return stripMinecraft(id) + ":" + clamp(parseInt(level.text(), 1), 1, 255);
        return attributeEntry(id, null);
    }

    private String updatedEntry(String existing) {
        if (mode == Mode.ENCHANTMENTS) {
            int split = existing.lastIndexOf(':');
            String id = split > 0 ? existing.substring(0, split) : existing;
            return stripMinecraft(id) + ":" + clamp(parseInt(level.text(), 1), 1, 255);
        }
        return attributeEntry(extract(existing, "type", "minecraft:generic.attack_damage"), existing);
    }

    private String attributeEntry(String type, String existing) {
        String value = sanitizeNumber(amount.text(), "1");
        String modifierId = sanitizeIdentifier(modifier.text(), "autism:modifier");
        String display = extractTopLevelValue(existing, "display");
        return "{type:\"" + escape(type) + "\",amount:" + value + ",operation:\"" + escape(operation)
            + "\",slot:\"" + escape(slot) + "\",id:\"" + escape(modifierId) + "\""
            + (display.isBlank() ? "" : ",display:" + display) + "}";
    }

    private static String extractTopLevelValue(String entry, String key) {
        if (entry == null || key == null || key.isBlank()) return "";
        String text = entry.trim();
        if (text.startsWith("{") && text.endsWith("}")) text = text.substring(1, text.length() - 1);
        for (String part : splitTopLevel(text)) {
            int separator = topLevelSeparator(part);
            if (separator <= 0) continue;
            String candidate = part.substring(0, separator).trim();
            if ((candidate.startsWith("\"") && candidate.endsWith("\""))
                || (candidate.startsWith("'") && candidate.endsWith("'"))) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            if (key.equals(candidate)) return part.substring(separator + 1).trim();
        }
        return "";
    }

    private static int topLevelSeparator(String text) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '{' || c == '[' || c == '(') depth++;
            else if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
            else if (c == ':' && depth == 0) return i;
        }
        return -1;
    }

    private String shortOperation() {
        return switch (operation) {
            case "add_multiplied_base" -> "Multiply Base";
            case "add_multiplied_total" -> "Multiply Total";
            default -> "Add Value";
        };
    }

    private void unfocusInputs() {
        search.setFocused(false);
        level.setFocused(false);
        amount.setFocused(false);
        modifier.setFocused(false);
    }

    private static String extract(String entry, String key, String fallback) {
        if (entry == null) return fallback;
        String needle = key + ":";
        int start = entry.indexOf(needle);
        if (start < 0) return fallback;
        start += needle.length();
        while (start < entry.length() && Character.isWhitespace(entry.charAt(start))) start++;
        if (start >= entry.length()) return fallback;
        char quote = entry.charAt(start);
        if (quote == '"' || quote == '\'') {
            int end = entry.indexOf(quote, start + 1);
            return end > start ? entry.substring(start + 1, end) : fallback;
        }
        int end = start;
        while (end < entry.length() && entry.charAt(end) != ',' && entry.charAt(end) != '}') end++;
        String value = entry.substring(start, end).trim();
        return value.isEmpty() ? fallback : value;
    }

    private static List<String> splitTopLevel(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        String text = raw.trim();
        if (text.startsWith("[") && text.endsWith("]")) text = text.substring(1, text.length() - 1);
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                current.append(c);
                escaped = true;
            } else if (quoted) {
                current.append(c);
                if (c == quote) quoted = false;
            } else if (c == '\'' || c == '"') {
                current.append(c);
                quoted = true;
                quote = c;
            } else {
                if (c == '{' || c == '[' || c == '(') depth++;
                else if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
                if (c == ',' && depth == 0) {
                    String value = current.toString().trim();
                    if (!value.isEmpty()) out.add(value);
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            }
        }
        String value = current.toString().trim();
        if (!value.isEmpty()) out.add(value);
        return out;
    }

    private static String cycle(List<String> choices, String current) {
        int index = choices.indexOf(current);
        return choices.get((index + 1 + choices.size()) % choices.size());
    }

    private static String sanitizeChoice(List<String> choices, String value) {
        return choices.contains(value) ? value : choices.get(0);
    }

    private static String sanitizeNumber(String value, String fallback) {
        try {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String sanitizeIdentifier(String value, String fallback) {
        return Identifier.tryParse(value) == null ? fallback : value;
    }

    private static String stripMinecraft(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static UiBounds empty() {
        return UiBounds.of(0, 0, 0, 0);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private record RegistryRow(String id, String label) {
    }

    private record RowHit(UiBounds bounds, int index, UiBounds remove) {
        private RowHit(UiBounds bounds, int index) {
            this(bounds, index, empty());
        }
    }
}
