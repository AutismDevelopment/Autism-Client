package autismclient.gui.vanillaui.assets;

import net.minecraft.resources.Identifier;

public final class UiAssets {
    public static final Identifier FONT_BODY = Identifier.fromNamespaceAndPath("autismclient", "pack_ui_body");
    public static final Identifier FONT_LABEL = Identifier.fromNamespaceAndPath("autismclient", "pack_ui_label");
    public static final Identifier FONT_TITLE = Identifier.fromNamespaceAndPath("autismclient", "pack_ui_title");

    public static final Identifier ICON_KEYBINDS = icon("keybinds");
    public static final Identifier ICON_LANSYNC = icon("lansync");
    public static final Identifier ICON_MACROS = icon("macros");
    public static final Identifier ICON_FABRICATOR = icon("fabricator");
    public static final Identifier ICON_FILTER = icon("filter");
    public static final Identifier ICON_PACKET_LOGGER = icon("packetlogger");
    public static final Identifier ICON_PACKET_Q_EDITOR = icon("packetqeditor");
    public static final Identifier ICON_SERVER_INFO = icon("serverinfo");
    public static final Identifier ICON_MAIN_MENU_CATEGORY = icon("mainmenucategory");
    public static final Identifier ICON_PACKET_CATEGORY = icon("packetcategory");
    public static final Identifier ICON_SCREEN_CATEGORY = icon("screencategory");
    public static final Identifier ICON_CHAT_CATEGORY = icon("chatcategory");
    public static final Identifier ICON_MATCHMAKING = icon("matchmaking");
    public static final Identifier ICON_MULTI = icon("multi");
    public static final Identifier ICON_PROFILES = icon("profiles");

    private UiAssets() {
    }

    private static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("autismclient", "textures/gui/vanillaui/icons/" + name + ".png");
    }
}
