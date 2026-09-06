package autismclient.util.macro;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;

public final class MacroGuiMatcher {
    public enum GuiType {
        ANY,
        CONTAINER,
        INVENTORY,
        SIGN,
        HANGING_SIGN,
        BOOK,
        BOOK_EDIT,
        BOOK_SIGN,
        BOOK_VIEW,
        CHAT,
        CUSTOM_MENU
    }

    private MacroGuiMatcher() {}

    public static boolean matches(Screen screen, String query) {
        if (screen == null || isOwnScreen(screen)) return false;
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("any")) return true;
        GuiType type = parseType(trimmed);
        if (type != null && matchesType(screen, type)) return true;
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        String semantic = semanticName(screen);
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        return title.toLowerCase(java.util.Locale.ROOT).contains(lower)
            || semantic.toLowerCase(java.util.Locale.ROOT).contains(lower);
    }

    public static boolean matches(Screen screen, String guiType, String titleFilter) {
        GuiType type = parseType(guiType);
        if (type == null) type = GuiType.ANY;
        if (!matchesType(screen, type)) return false;
        return titleFilter == null || titleFilter.isBlank() || matches(screen, titleFilter);
    }

    public static boolean matchesType(Screen screen, GuiType type) {
        if (screen == null || isOwnScreen(screen)) return false;
        return switch (type == null ? GuiType.ANY : type) {
            case ANY -> true;

            case CONTAINER -> screen instanceof AbstractContainerScreen<?>
                    && !(screen instanceof InventoryScreen)
                    && !(screen instanceof CreativeModeInventoryScreen);
            case INVENTORY -> screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
            case SIGN -> screen instanceof AbstractSignEditScreen;
            case HANGING_SIGN -> screen instanceof HangingSignEditScreen;
            case BOOK -> screen instanceof BookEditScreen || screen instanceof BookSignScreen || screen instanceof BookViewScreen;
            case BOOK_EDIT -> screen instanceof BookEditScreen;
            case BOOK_SIGN -> screen instanceof BookSignScreen;
            case BOOK_VIEW -> screen instanceof BookViewScreen;
            case CHAT -> screen instanceof ChatScreen;
            case CUSTOM_MENU -> screen instanceof DialogScreen<?>;
        };
    }

    public static String semanticName(Screen screen) {
        if (screen == null) return "";
        if (screen instanceof DialogScreen<?>) return "CustomScreen";
        if (screen instanceof HangingSignEditScreen) return "Hanging Sign";
        if (screen instanceof SignEditScreen || screen instanceof AbstractSignEditScreen) return "Sign";
        if (screen instanceof BookSignScreen) return "Book Sign";
        if (screen instanceof BookEditScreen) return "Book Edit";
        if (screen instanceof BookViewScreen) return "Book View";
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return "Inventory";
        if (screen instanceof AbstractContainerScreen<?>) return "Container";
        if (screen instanceof ChatScreen) return "Chat";
        return screen.getClass().getSimpleName();
    }

    public static boolean isOwnScreen(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("autismclient.");
    }

    private static GuiType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (key) {
            case "ANY" -> GuiType.ANY;
            case "CONTAINER", "GUI", "CHEST", "SHULKER" -> GuiType.CONTAINER;
            case "INVENTORY", "PLAYER_INVENTORY", "PLAYER", "INV" -> GuiType.INVENTORY;
            case "SIGN", "SIGN_GUI" -> GuiType.SIGN;
            case "HANGING_SIGN", "HANGINGSIGN" -> GuiType.HANGING_SIGN;
            case "BOOK" -> GuiType.BOOK;
            case "BOOK_EDIT", "EDIT_BOOK", "WRITABLE_BOOK" -> GuiType.BOOK_EDIT;
            case "BOOK_SIGN", "SIGN_BOOK" -> GuiType.BOOK_SIGN;
            case "BOOK_VIEW", "READ_BOOK" -> GuiType.BOOK_VIEW;
            case "CHAT" -> GuiType.CHAT;
            case "CUSTOM_MENU", "CUSTOM", "CUSTOM_SCREEN", "CUSTOMSCREEN", "DIALOG" -> GuiType.CUSTOM_MENU;
            default -> null;
        };
    }

    public static boolean isCustomMenuType(String raw) {
        return parseType(raw) == GuiType.CUSTOM_MENU;
    }
}
