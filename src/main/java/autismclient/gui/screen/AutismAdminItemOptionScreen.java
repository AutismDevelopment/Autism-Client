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
import autismclient.api.module.Setting;
import autismclient.modules.BuiltinModules;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismItemCommandSerializer;
import autismclient.util.AutismItemNbtInspector;
import autismclient.util.AutismUiScale;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

public final class AutismAdminItemOptionScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int PANEL_W = 430;
    private static final int PANEL_H = 286;
    private static final int HEADER_H = 18;
    private static final int FOOTER_H = 24;
    private static final int FULL_NBT_FOOTER_H = 44;
    private static final int TOOLBAR_GAP = 4;

    public enum Mode {
        LORE("Lore", "One lore line per row. Order is preserved."),
        ENCHANTMENTS("Enchantments", "One enchantment per row: minecraft:id:level"),
        ATTRIBUTES("Attributes", "Raw attribute component rows. Advanced values stay exact."),
        FULL_NBT("Full ItemStack NBT", "Complete ItemStack SNBT. Saving validates every field before it can be applied."),
        RAW("Raw Data", "Exact raw value. Multiline editing does not discard data.");

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
    private final CompactTextInput editor = new CompactTextInput()
        .setMultiline(true)
        .setMaxLength(1_048_576)
        .setHorizontalPadding(5)
        .setHoverEffectsEnabled(false)
        .setFocusEffectsEnabled(true);
    private DirectRenderContext lastDirectContext;
    private UiBounds closeBounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds saveBounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds cancelBounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds formatBounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds validateBounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds copyBounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds pasteBounds = UiBounds.of(0, 0, 0, 0);
    private String validationStatus = "";
    private boolean validationOk;

    public AutismAdminItemOptionScreen(Screen parent, Module module, Setting<?, ?> option, Mode mode) {
        super(Component.literal("Edit " + (mode == null ? "Value" : mode.title)));
        this.parent = parent;
        this.module = module;
        this.option = option;
        this.mode = mode == null ? Mode.RAW : mode;
        if (this.mode == Mode.FULL_NBT) {
            editor.setDisplayTextProvider(this::snbtSyntax);
            editor.setBackgroundColorOverride(0x520A090C);
            editor.setHoverEffectsEnabled(false);
            editor.setFocusEffectsEnabled(false);
        }
        editor.setText(decode(module == null || option == null ? "" : module.value(option.id())));
        editor.setOnChange(ignored -> validationStatus = "");
        editor.setFocused(true);
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
            int w = Math.min(PANEL_W, Math.max(180, sw - 8));
            int h = Math.min(PANEL_H, Math.max(130, sh - 8));
            int x = Math.max(4, (sw - w) / 2);
            int y = Math.max(4, (sh - h) / 2);
            UiContext ui = UiContexts.overlay(graphics, font, mx, my);
            UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 0x99000000);
            UiBounds panel = UiBounds.of(x, y, w, h);
            CompactScreenPanel.render(ui, panel, HEADER_H, "Edit " + mode.title, mx >= x && mx < x + w && my >= y && my < y + HEADER_H);
            closeBounds = CompactScreenPanel.closeButton(panel, HEADER_H);

            int footerHeight = mode == Mode.FULL_NBT ? FULL_NBT_FOOTER_H : FOOTER_H;
            int footerTop = y + h - footerHeight;
            int tipY = y + HEADER_H + 5;
            List<String> tipLines = ui.text().wrapFully(mode.tip, Math.max(1, w - 14));
            int maxTipLines = Math.min(2, tipLines.size());
            int lineH = ui.theme().typography().lineHeight;
            for (int i = 0; i < maxTipLines; i++) {
                ui.text().draw(graphics, tipLines.get(i), x + 7, tipY + i * lineH, ui.theme().colors().muted);
            }

            int editorY = tipY + Math.max(1, maxTipLines) * lineH + 4;
            int editorH = Math.max(42, footerTop - editorY - 5);
            editor.setBounds(x + 6, editorY, w - 12, editorH);
            DirectViewport viewport = DirectViewport.current(1.0f);
            lastDirectContext = new DirectRenderContext(graphics, font, viewport, THEME, mx, my, delta);
            editor.render(lastDirectContext);

            int buttonY = footerTop + 4;
            if (mode == Mode.FULL_NBT) {
                int toolbarX = x + 6;
                int toolbarW = Math.max(1, w - 12);
                int cellW = Math.max(1, (toolbarW - TOOLBAR_GAP * 3) / 4);
                formatBounds = UiBounds.of(toolbarX, buttonY, cellW, 16);
                validateBounds = UiBounds.of(formatBounds.right() + TOOLBAR_GAP, buttonY, cellW, 16);
                copyBounds = UiBounds.of(validateBounds.right() + TOOLBAR_GAP, buttonY, cellW, 16);
                pasteBounds = UiBounds.of(copyBounds.right() + TOOLBAR_GAP, buttonY,
                    Math.max(1, x + w - 6 - copyBounds.right() - TOOLBAR_GAP), 16);
                Button.render(ui, formatBounds, "Format", Button.Tone.NORMAL, formatBounds.contains(mx, my), false);
                Button.render(ui, validateBounds, "Validate", Button.Tone.NORMAL, validateBounds.contains(mx, my), false);
                Button.render(ui, copyBounds, "Copy", Button.Tone.NORMAL, copyBounds.contains(mx, my), false);
                Button.render(ui, pasteBounds, "Paste", Button.Tone.NORMAL, pasteBounds.contains(mx, my), false);
                buttonY += 20;
                String status = validationStatus.isBlank()
                    ? editor.text().length() + " / 1,048,576 chars  •  Ctrl+S save  •  Ctrl+Shift+F format"
                    : validationStatus;
                ui.text().drawFitted(graphics, status, x + 7, buttonY + 5, Math.max(1, w - 124),
                    validationStatus.isBlank() ? ui.theme().colors().muted
                        : validationOk ? ui.theme().colors().success : ui.theme().colors().bad);
            } else {
                formatBounds = UiBounds.of(0, 0, 0, 0);
                validateBounds = UiBounds.of(0, 0, 0, 0);
                copyBounds = UiBounds.of(0, 0, 0, 0);
                pasteBounds = UiBounds.of(0, 0, 0, 0);
            }
            saveBounds = UiBounds.of(x + w - 110, buttonY, 50, 16);
            cancelBounds = UiBounds.of(x + w - 56, buttonY, 50, 16);
            Button.render(ui, saveBounds, "Save", Button.Tone.SUCCESS, saveBounds.contains(mx, my), false);
            Button.render(ui, cancelBounds, "Cancel", Button.Tone.NORMAL, cancelBounds.contains(mx, my), false);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
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
        if (event.button() == 0 && formatBounds.contains(mx, my)) {
            formatFullNbt();
            return true;
        }
        if (event.button() == 0 && validateBounds.contains(mx, my)) {
            validateFullNbt();
            return true;
        }
        if (event.button() == 0 && copyBounds.contains(mx, my)) {
            copyRaw();
            return true;
        }
        if (event.button() == 0 && pasteBounds.contains(mx, my)) {
            pasteRaw();
            return true;
        }
        return lastDirectContext == null || editor.mouseClicked(lastDirectContext, mx, my, event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return lastDirectContext == null || editor.mouseReleased(lastDirectContext,
            AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return lastDirectContext == null || editor.mouseDragged(lastDirectContext,
            AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), event.button(), (float) dx, (float) dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return lastDirectContext == null || editor.mouseScrolled(lastDirectContext,
            AutismUiScale.toVirtualInt(mouseX), AutismUiScale.toVirtualInt(mouseY), (float) scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (mode == Mode.FULL_NBT && ctrl && input.key() == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (mode == Mode.FULL_NBT && ctrl && shift && input.key() == GLFW.GLFW_KEY_F) {
            formatFullNbt();
            return true;
        }
        if (mode == Mode.FULL_NBT && ctrl
            && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            validateFullNbt();
            return true;
        }
        if (mode == Mode.FULL_NBT && input.key() == GLFW.GLFW_KEY_TAB && editor.isFocused()) {
            editor.insertText("  ");
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return lastDirectContext == null || editor.keyPressed(lastDirectContext, input.key(), input.scancode(), input.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return lastDirectContext == null || editor.charTyped(lastDirectContext, (char) input.codepoint(), 0);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void save() {
        if (module != null && option != null) {
            String value = encode(editor.text());
            if ("nbt-item-components".equals(option.id()) && module instanceof BuiltinModules.AdminToolsModule adminTools) {
                adminTools.setRawItemComponents(value);
            } else if ("nbt-item-stack".equals(option.id()) && module instanceof BuiltinModules.AdminToolsModule adminTools) {
                if (!adminTools.setRawItemStackSnbt(value)) {
                    validationOk = false;
                    validationStatus = "Invalid full ItemStack SNBT";
                    AutismNotifications.error("Invalid full ItemStack SNBT.");
                    return;
                }
            } else {
                module.setValue(option.id(), value);
            }
            if (!"nbt-item-stack".equals(option.id())
                && module instanceof BuiltinModules.AdminToolsModule adminTools) {
                adminTools.prepareRawItemStackEditor();
            }
        }
        AutismNotifications.show(mode.title + " updated.", 0xFF35D873);
        onClose();
    }

    private boolean validateFullNbt() {
        if (mode != Mode.FULL_NBT) return true;
        var stack = AutismItemCommandSerializer.itemStackFromSnbt(editor.text());
        if (stack.isEmpty()) {
            validationOk = false;
            validationStatus = "Invalid ItemStack SNBT";
            AutismNotifications.error(validationStatus);
            return false;
        }
        String error = AutismItemCommandSerializer.validationError(stack);
        validationOk = error.isBlank();
        validationStatus = validationOk ? "Valid complete ItemStack" : error;
        if (validationOk) AutismNotifications.show("Full ItemStack NBT is valid.", 0xFF35D873);
        else AutismNotifications.error(error);
        return validationOk;
    }

    private void formatFullNbt() {
        if (mode != Mode.FULL_NBT) return;
        String repaired = autismclient.util.SnbtRepair.repair(editor.text());
        var stack = AutismItemCommandSerializer.itemStackFromSnbt(repaired);
        if (stack.isEmpty()) {
            editor.setText(AutismItemNbtInspector.prettySnbt(repaired));
            validationOk = false;
            validationStatus = "Formatted (structure repaired — still not a valid ItemStack; check ids/values)";
            return;
        }
        String error = AutismItemCommandSerializer.validationError(stack);
        editor.setText(AutismItemNbtInspector.prettySnbt(AutismItemCommandSerializer.itemStackSnbt(stack)));
        validationOk = error.isBlank();
        validationStatus = validationOk ? "Formatted and valid" : "Formatted — " + error;
    }

    private void copyRaw() {
        if (minecraft == null || minecraft.keyboardHandler == null) return;
        String selected = editor.selectedText();
        minecraft.keyboardHandler.setClipboard(selected.isEmpty() ? editor.text() : selected);
        AutismNotifications.copied(selected.isEmpty() ? "Copied full ItemStack NBT." : "Copied selection.");
    }

    private void pasteRaw() {
        if (minecraft == null || minecraft.keyboardHandler == null) return;
        editor.insertText(minecraft.keyboardHandler.getClipboard());
    }

    private Component snbtSyntax(String source) {
        String safe = source == null ? "" : source;
        if (safe.length() > 131_072) return Component.literal(safe);
        MutableComponent out = Component.empty();
        char quote = 0;
        boolean escaped = false;
        int runStart = 0;
        int runColor = snbtColor(safe, 0, quote);
        for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            int color;
            if (quote != 0) {
                color = 0xE6DB74;
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == quote) quote = 0;
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
                color = 0xE6DB74;
            } else {
                color = snbtColor(safe, i, quote);
            }
            if (i == 0) {
                runColor = color;
            } else if (color != runColor) {
                appendStyled(out, safe.substring(runStart, i), runColor);
                runStart = i;
                runColor = color;
            }
        }
        if (runStart < safe.length()) appendStyled(out, safe.substring(runStart), runColor);
        return out;
    }

    private int snbtColor(String source, int index, char quote) {
        if (source == null || index < 0 || index >= source.length()) return 0xF8F8F2;
        char ch = source.charAt(index);
        if (Character.isWhitespace(ch)) return 0x777777;
        if ("{}[],:;".indexOf(ch) >= 0) return 0x66D9EF;
        if (Character.isDigit(ch) || ch == '-' || ch == '+') return 0xAE81FF;
        if (Character.isLetter(ch) || ch == '_' || ch == '.' || ch == '!') return 0xA6E22E;
        return 0xF8F8F2;
    }

    private void appendStyled(MutableComponent out, String text, int color) {
        if (text == null || text.isEmpty()) return;
        out.append(Component.literal(text).withStyle(style -> style.withColor(color)));
    }

    private String decode(String raw) {
        String value = raw == null ? "" : raw;
        return switch (mode) {
            case LORE -> value.replace("|", "\n");
            case ENCHANTMENTS -> String.join("\n", splitTopLevel(value));
            case FULL_NBT -> AutismItemNbtInspector.prettySnbt(value);
            default -> value;
        };
    }

    private String encode(String displayed) {
        String value = displayed == null ? "" : displayed;
        return switch (mode) {
            case LORE -> String.join("|", nonEmptyLines(value));
            case ENCHANTMENTS -> String.join(",", nonEmptyLines(value));
            default -> value;
        };
    }

    private static List<String> nonEmptyLines(String value) {
        List<String> out = new ArrayList<>();
        for (String line : value.split("\\R", -1)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private static List<String> splitTopLevel(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
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
                    String entry = current.toString().trim();
                    if (!entry.isEmpty()) out.add(entry);
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            }
        }
        String entry = current.toString().trim();
        if (!entry.isEmpty()) out.add(entry);
        return out;
    }
}
