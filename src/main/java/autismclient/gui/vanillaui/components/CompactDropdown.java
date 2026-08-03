package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiInputResult;
import autismclient.gui.vanillaui.components.Dropdown;
import autismclient.util.AutismText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class CompactDropdown {
    private static boolean suppressUnderlyingPointerUntilRelease;

    public boolean active = true;
    public boolean visible = true;

    private final Dropdown dropdown;
    private int x;
    private int y;
    private int width;
    private int height;
    private List<String> options;
    private List<String> displayOptions;
    private IntConsumer onSelect;
    private int selectedIndex;
    private String buttonLabelOverride = "";

    public CompactDropdown(int x, int y, int width, int height, List<String> options, int selectedIndex, IntConsumer onSelect) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.options = copy(options);
        this.displayOptions = displayOptions(this.options);
        this.selectedIndex = clampIndex(selectedIndex, this.options.size());
        this.onSelect = onSelect;
        this.dropdown = new Dropdown(bounds(), this.displayOptions, selectedDisplay(), this::selectDisplayValue);
    }

    public CompactDropdown setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        dropdown.setBounds(bounds());
        return this;
    }

    public CompactDropdown setOptions(List<String> options) {
        List<String> next = copy(options);
        if (this.options.equals(next)) return this;
        this.options = next;
        this.displayOptions = displayOptions(next);
        selectedIndex = clampIndex(selectedIndex, next.size());
        dropdown.setOptions(displayOptions);
        dropdown.setSelected(selectedDisplay());
        return this;
    }

    public CompactDropdown setSelectedIndex(int selectedIndex) {
        this.selectedIndex = clampIndex(selectedIndex, options.size());
        dropdown.setSelected(selectedDisplay());
        return this;
    }

    public CompactDropdown setOnSelect(IntConsumer onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public CompactDropdown setButtonLabelOverride(String label) {
        buttonLabelOverride = label == null ? "" : label;
        return this;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public static void renderAll(GuiGraphicsExtractor graphics, Font font, List<CompactDropdown> dropdowns, int mouseX, int mouseY) {
        renderButtons(graphics, font, dropdowns, mouseX, mouseY);
        renderOpenMenu(graphics, font, dropdowns, mouseX, mouseY);
    }

    public static void renderButtons(GuiGraphicsExtractor graphics, Font font, List<CompactDropdown> dropdowns, int mouseX, int mouseY) {
        if (graphics == null || font == null || dropdowns == null) return;
        UiContext context = context(graphics, font, mouseX, mouseY);
        boolean menuOpen = isMenuOpen(dropdowns);
        boolean suppressHover = menuOpen || shouldSuppressUnderlyingPointer();
        for (CompactDropdown dropdown : dropdowns) {
            if (dropdown == null || !dropdown.visible) continue;
            boolean hovered = dropdown.active && !suppressHover && dropdown.bounds().contains(mouseX, mouseY);
            Dropdown.renderControl(context, dropdown.bounds(), dropdown.buttonLabel(), hovered,
                dropdown.dropdown.isOpen(), dropdown.active);
        }
    }

    public static void renderOpenMenu(GuiGraphicsExtractor graphics, Font font, List<CompactDropdown> dropdowns, int mouseX, int mouseY) {
        if (graphics == null || font == null || dropdowns == null) return;
        for (CompactDropdown dropdown : dropdowns) {
            if (dropdown != null && dropdown.visible && dropdown.dropdown.isOpen()) {
                dropdown.dropdown.render(context(graphics, font, mouseX, mouseY));
                return;
            }
        }
    }

    public static boolean isMenuOpen(List<CompactDropdown> dropdowns) {
        if (dropdowns == null) {
            suppressUnderlyingPointerUntilRelease = false;
            return false;
        }
        for (CompactDropdown dropdown : dropdowns) {
            if (dropdown != null && dropdown.visible && dropdown.dropdown.isOpen()) return true;
        }
        suppressUnderlyingPointerUntilRelease = false;
        return false;
    }

    public static boolean shouldBlockUnderlyingPointer(List<CompactDropdown> dropdowns) {
        return shouldSuppressUnderlyingPointer() || isMenuOpen(dropdowns);
    }

    public static boolean isInsideOpenMenu(List<CompactDropdown> dropdowns, double mouseX, double mouseY) {
        if (dropdowns == null) return false;
        for (CompactDropdown dropdown : dropdowns) {
            if (dropdown != null && dropdown.visible && dropdown.dropdown.containsMenu((int) mouseX, (int) mouseY)) return true;
        }
        return false;
    }

    public static boolean shouldSuppressUnderlyingPointer() {
        return suppressUnderlyingPointerUntilRelease;
    }

    public static boolean closeOpenMenu(List<CompactDropdown> dropdowns) {
        if (dropdowns == null) return false;
        boolean closed = false;
        for (CompactDropdown dropdown : dropdowns) {
            if (dropdown != null && dropdown.dropdown.isOpen()) {
                dropdown.close();
                closed = true;
            }
        }
        return closed;
    }

    public static boolean mouseClicked(List<CompactDropdown> dropdowns, double mouseX, double mouseY, int mouseButton) {
        if (dropdowns == null) return false;
        CompactDropdown openDropdown = openDropdown(dropdowns);
        if (openDropdown != null) {
            if (mouseButton == 0) suppressUnderlyingPointerUntilRelease = true;
            openDropdown.dropdown.mouseClicked((int) mouseX, (int) mouseY, mouseButton);
            if (mouseButton != 0 && !openDropdown.bounds().contains((int) mouseX, (int) mouseY)
                && !openDropdown.dropdown.containsMenu((int) mouseX, (int) mouseY)) {
                openDropdown.close();
            }
            suppressUnderlyingPointerUntilRelease = openDropdown.dropdown.isOpen();
            return true;
        }
        if (mouseButton != 0) return false;
        for (int i = dropdowns.size() - 1; i >= 0; i--) {
            CompactDropdown dropdown = dropdowns.get(i);
            if (dropdown == null || !dropdown.visible || !dropdown.active || !dropdown.bounds().contains((int) mouseX, (int) mouseY)) continue;
            closeOpenMenu(dropdowns);
            dropdown.dropdown.open();
            suppressUnderlyingPointerUntilRelease = true;
            return true;
        }
        return false;
    }

    public static boolean mouseScrolled(List<CompactDropdown> dropdowns, double mouseX, double mouseY, double deltaY) {
        CompactDropdown dropdown = openDropdown(dropdowns);
        if (dropdown == null) return false;

        if (!dropdown.dropdown.containsMenu((int) mouseX, (int) mouseY)) return false;
        dropdown.dropdown.mouseScrolled((int) mouseX, (int) mouseY, deltaY);
        return true;
    }

    public static boolean mouseDragged(List<CompactDropdown> dropdowns, double mouseX, double mouseY, int button) {
        CompactDropdown dropdown = openDropdown(dropdowns);
        if (dropdown == null) return false;
        dropdown.dropdown.mouseDragged((int) mouseX, (int) mouseY, button, 0.0, 0.0);
        return true;
    }

    public static boolean mouseReleased(List<CompactDropdown> dropdowns) {
        boolean consumed = suppressUnderlyingPointerUntilRelease || isMenuOpen(dropdowns);
        suppressUnderlyingPointerUntilRelease = false;
        if (dropdowns == null) return consumed;
        for (CompactDropdown dropdown : dropdowns) {
            if (dropdown != null && dropdown.dropdown.mouseReleased(0, 0, 0) == UiInputResult.HANDLED) consumed = true;
        }
        return consumed;
    }

    public void close() {
        dropdown.close();
        suppressUnderlyingPointerUntilRelease = false;
    }

    private UiBounds bounds() {
        return UiBounds.of(x, y, width, height);
    }

    private String buttonLabel() {
        return buttonLabelOverride.isBlank() ? selectedDisplay() : buttonLabelOverride;
    }

    private String selectedDisplay() {
        if (displayOptions.isEmpty()) return "";
        return displayOptions.get(clampIndex(selectedIndex, displayOptions.size()));
    }

    private void selectDisplayValue(String displayValue) {
        int index = displayOptions.indexOf(displayValue);
        if (index < 0) return;
        selectedIndex = index;
        if (onSelect != null) onSelect.accept(index);
    }

    private static CompactDropdown openDropdown(List<CompactDropdown> dropdowns) {
        if (dropdowns == null) return null;
        for (CompactDropdown dropdown : dropdowns) {
            if (dropdown != null && dropdown.visible && dropdown.dropdown.isOpen()) return dropdown;
        }
        return null;
    }

    private static UiContext context(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        return UiContexts.overlay(graphics, font, mouseX, mouseY);
    }

    private static List<String> copy(List<String> options) {
        return options == null ? List.of() : List.copyOf(options);
    }

    private static List<String> displayOptions(List<String> options) {
        List<String> labels = new ArrayList<>(options.size());
        for (String option : options) labels.add(prettify(option));
        return List.copyOf(labels);
    }

    private static String prettify(String raw) {
        String sanitized = AutismText.sanitizeUiLabel(raw);
        if (sanitized == null || sanitized.isEmpty()) return "";
        if (sanitized.length() <= 3 && sanitized.chars().anyMatch(Character::isDigit)) return sanitized;
        boolean looksEnum = true;
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c == '_' || c == ' ' || Character.isDigit(c)) continue;
            if (Character.isLetter(c) && c == Character.toUpperCase(c)) continue;
            looksEnum = false;
            break;
        }
        if (!looksEnum) return sanitized;
        StringBuilder out = new StringBuilder(sanitized.length());
        boolean newWord = true;
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c == '_' || c == ' ') {
                if (!out.isEmpty() && out.charAt(out.length() - 1) != ' ') out.append(' ');
                newWord = true;
            } else if (newWord) {
                out.append(Character.toUpperCase(c));
                newWord = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString().trim();
    }

    private static int clampIndex(int index, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(size - 1, index));
    }
}
