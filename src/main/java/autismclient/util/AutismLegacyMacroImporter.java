package autismclient.util;

import autismclient.AutismClientAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Locale;

public class AutismLegacyMacroImporter {

    public static AutismMacro importFromClipboard() {
        String base64 = getClipboardText();
        if (base64 == null || base64.trim().isEmpty()) return null;
        return importFromBase64(base64.trim());
    }

    public static AutismMacro importFromBase64(String base64) { // Holy if hell
        if (base64 == null || base64.isBlank()) return null;

        CompoundTag root = decodeBase64Nbt(base64);
        if (root == null) return null;

        AutismMacro current = tryCurrentFormat(root);
        if (current != null) return current;

        AutismMacro legacy = tryLegacyDirect(root);
        if (legacy != null) return legacy;

        AutismMacro legacyWrapped = tryLegacyWrapped(root);
        if (legacyWrapped != null) return legacyWrapped;

        AutismMacro legacyVersioned = tryLegacyVersioned(root);
        if (legacyVersioned != null) return legacyVersioned;

        AutismClientAddon.LOG.warn("[LegacyImporter] Could not parse macro from clipboard data");
        logTagStructure(root, "ROOT");
        return null;
    }

    private static AutismMacro tryCurrentFormat(CompoundTag root) {
        int version = root.getIntOr("version", 0);
        String type = root.getStringOr("type", "");

        if (version == 1 && "autism_macro".equals(type)) {
            CompoundTag macroTag = root.getCompound("macro").orElse(new CompoundTag());
            if (!macroTag.isEmpty()) {
                try {
                    AutismMacro macro = new AutismMacro().fromTag(macroTag).sanitizeForSharing();
                    AutismClientAddon.LOG.info("[LegacyImporter] Loaded macro via current format");
                    return macro;
                } catch (Exception e) {
                    AutismClientAddon.LOG.warn("[LegacyImporter] Current format parse failed: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    private static AutismMacro tryLegacyDirect(CompoundTag root) {
        if (!looksLikeMacroTag(root)) return null;

        try {
            CompoundTag fixed = remapLegacyActions(root.copy());
            AutismMacro macro = new AutismMacro().fromTag(fixed).sanitizeForSharing();

            if (macro.actions != null && !macro.actions.isEmpty()) {
                AutismClientAddon.LOG.info("[LegacyImporter] Loaded macro via legacy direct format ({} actions)", macro.actions.size());
                return macro;
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("[LegacyImporter] Legacy direct parse failed: {}", e.getMessage());
        }
        return null;
    }

    private static AutismMacro tryLegacyWrapped(CompoundTag root) {
        CompoundTag macroTag = root.getCompound("macro").orElse(new CompoundTag());
        if (macroTag.isEmpty() || !looksLikeMacroTag(macroTag)) return null;

        try {
            CompoundTag fixed = remapLegacyActions(macroTag.copy());
            AutismMacro macro = new AutismMacro().fromTag(fixed).sanitizeForSharing();

            if (macro.actions != null && !macro.actions.isEmpty()) {
                AutismClientAddon.LOG.info("[LegacyImporter] Loaded macro via legacy wrapped format ({} actions)", macro.actions.size());
                return macro;
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("[LegacyImporter] Legacy wrapped parse failed: {}", e.getMessage());
        }
        return null;
    }

    private static AutismMacro tryLegacyVersioned(CompoundTag root) {
        int version = root.getIntOr("version", 0);
        if (version == 0) return null;

        CompoundTag macroTag = root.getCompound("macro").orElse(new CompoundTag());
        if (macroTag.isEmpty()) {
            if (looksLikeMacroTag(root)) {
                return tryLegacyDirect(root);
            }
            return null;
        }

        try {
            CompoundTag fixed = remapLegacyActions(macroTag.copy());
            AutismMacro macro = new AutismMacro().fromTag(fixed).sanitizeForSharing();

            if (macro.actions != null && !macro.actions.isEmpty()) {
                AutismClientAddon.LOG.info("[LegacyImporter] Loaded macro via legacy versioned format (v{}, {} actions)", version, macro.actions.size());
                return macro;
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("[LegacyImporter] Legacy versioned parse failed: {}", e.getMessage());
        }
        return null;
    }

    private static CompoundTag remapLegacyActions(CompoundTag macroTag) {
        ListTag actions = macroTag.getList("actions").orElse(new ListTag());

        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i) instanceof CompoundTag actionTag) {
                remapSingleAction(actionTag);
            }
        }

        return macroTag;
    }

    private static void remapSingleAction(CompoundTag actionTag) {
        String type = actionTag.getStringOr("type", "");
        if (type.isBlank()) {
            type = actionTag.getStringOr("id", "");
            if (!type.isBlank()) {
                actionTag.putString("type", type);
            }
        }
        if (type.isBlank()) {
            type = actionTag.getStringOr("actionType", "");
            if (!type.isBlank()) {
                actionTag.putString("type", type);
            }
        }

        String mapped = remapActionType(type);
        if (!mapped.equals(type) && !mapped.isBlank()) {
            actionTag.putString("type", mapped);
            AutismClientAddon.LOG.debug("[LegacyImporter] Remapped action type: {} -> {}", type, mapped);
        }

        remapActionFields(actionTag, type);
    }

    private static String remapActionType(String oldType) {
        if (oldType == null || oldType.isBlank()) return "";

        String upper = oldType.toUpperCase(Locale.ROOT);
        try {
            autismclient.util.macro.MacroActionType.valueOf(upper);
            return upper;
        } catch (IllegalArgumentException ignored) {}

        return switch (oldType.toLowerCase(Locale.ROOT)) {
            case "packet" -> "PACKET";
            case "sendpacket", "send_packet" -> "SEND_PACKET";
            case "waitpacket", "wait_packet" -> "WAIT_PACKET";
            case "packetclick", "packet_click" -> "PACKET_CLICK";

            case "chat", "msg", "message", "sendchat", "send_chat" -> "SEND_CHAT";

            case "sleep", "wait" -> "DELAY";
            case "delay" -> "DELAY";
            case "waithealth", "wait_health" -> "WAIT_HEALTH";
            case "waitgui", "wait_gui" -> "WAIT_GUI";
            case "waitchat", "wait_chat" -> "WAIT_CHAT";
            case "waitblock", "wait_block" -> "WAIT_BLOCK";
            case "waitentity", "wait_entity" -> "WAIT_ENTITY";
            case "waitsound", "wait_sound" -> "WAIT_SOUND";
            case "waitcooldown", "wait_cooldown" -> "WAIT_COOLDOWN";
            case "waitslot", "wait_slot", "waitslotchange", "wait_slot_change" -> "WAIT_SLOT_CHANGE";
            case "waitpos", "wait_pos" -> "WAIT_POS";
            case "waititem", "wait_item" -> "WAIT_ITEM";

            case "move" -> "MOVE";
            case "goto", "go_to", "baritone" -> "GO_TO";
            case "jump" -> "JUMP";
            case "sneak" -> "SNEAK";
            case "sprint" -> "SPRINT";
            case "rotate" -> "ROTATE";
            case "lookat", "look_at", "lookatblock", "look_at_block" -> "LOOK_AT_BLOCK";

            case "click", "invclick", "inv_click" -> "CLICK";
            case "item", "itemaction" -> "ITEM";
            case "drop" -> "DROP";
            case "craft" -> "CRAFT";
            case "swap", "swapslots", "swap_slots" -> "SWAP_SLOTS";
            case "selectslot", "select_slot" -> "SELECT_SLOT";
            case "store", "storeitem", "store_item" -> "STORE_ITEM";
            case "xcarry", "x_carry" -> "XCARRY";
            case "inventory" -> "INVENTORY";
            case "pickupall", "pick_up_all" -> "PICK_UP_ALL";

            case "mine" -> "MINE";
            case "break", "breakblock" -> "BREAK";
            case "place", "placeblock" -> "PLACE";
            case "instabreak", "insta_break" -> "INSTA_BREAK";

            case "use", "useitem", "use_item" -> "USE_ITEM";
            case "useitemphase", "use_item_phase" -> "USE_ITEM_PHASE";

            case "opengui", "opencontainer", "open_container" -> "OPEN_CONTAINER";
            case "closegui", "close_gui" -> "CLOSE_GUI";
            case "savegui", "save_gui" -> "SAVE_GUI";
            case "restoregui", "restore_gui" -> "RESTORE_GUI";

            case "disconnect" -> "DISCONNECT";

            case "toggle", "togglemodule", "toggle_module" -> "TOGGLE_MODULE";

            case "repeat", "loop" -> "REPEAT";

            case "ticksync", "tick_sync" -> "TICK_SYNC";
            case "revisionsync", "revision_sync" -> "REVISION_SYNC";
            case "servertick", "server_tick_sync" -> "SERVER_TICK_SYNC";

            case "stop", "stopmacro", "stop_macro" -> "STOP_MACRO";
            case "startmacro", "start_macro" -> "START_MACRO";

            case "branch" -> "BRANCH";
            case "race" -> "RACE";
            case "report" -> "REPORT";
            case "finally" -> "FINALLY";
            case "assert" -> "ASSERT";
            case "vclip" -> "VCLIP";
            case "hclip" -> "HCLIP";
            case "payload" -> "PAYLOAD";
            case "pay" -> "PAY";
            case "nbtbook", "nbt_book" -> "NBT_BOOK";
            case "interactentity", "interact_entity" -> "INTERACT_ENTITY";
            case "desync" -> "DESYNC";
            case "delaypackets", "delay_packets" -> "DELAY_PACKETS";
            case "sendtoggle", "send_toggle" -> "SEND_TOGGLE";
            case "sendcommand", "send_command_packet" -> "SEND_COMMAND_PACKET";
            case "inventoryaudit", "inventory_audit" -> "INVENTORY_AUDIT";
            case "fakegamemode", "fake_gamemode" -> "FAKE_GAMEMODE";
            case "bundledupe", "bundle_dupe_v2" -> "BUNDLE_DUPE_V2";
            case "signedit", "sign_edit" -> "SIGN_EDIT";
            case "packetgate", "packet_gate" -> "PACKET_GATE";
            case "endpacketgate", "end_packet_gate" -> "END_PACKET_GATE";
            case "packetburst", "packet_burst" -> "PACKET_BURST";
            case "containerclicksequence", "container_click_sequence" -> "CONTAINER_CLICK_SEQUENCE";
            case "macrovariables", "macro_variables" -> "MACRO_VARIABLES";

            default -> oldType;
        };
    }

    private static void remapActionFields(CompoundTag tag, String originalType) {
        if (tag.contains("delay") && !tag.contains("delayMs")) {
            int delay = tag.getIntOr("delay", 0);
            tag.putInt("delayMs", delay);
        }

        if (tag.contains("slot") && !tag.contains("slotIndex")) {
            // Later
        }

        if (tag.contains("packetName") && !tag.contains("packet_name")) {
            // Later
        }

        if ("chat".equalsIgnoreCase(originalType) || "msg".equalsIgnoreCase(originalType)) {
            if (tag.contains("message") && !tag.contains("text")) {
                tag.putString("text", tag.getStringOr("message", ""));
            }
        }

        if (!tag.contains("enabled")) {
            tag.putBoolean("enabled", true);
        }
    }

    private static boolean looksLikeMacroTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return false;
        return tag.contains("actions")
                || tag.contains("name")
                || (tag.contains("loop") && tag.contains("actions"));
    }

    private static CompoundTag decodeBase64Nbt(String base64) {
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (IllegalArgumentException e) {
            AutismClientAddon.LOG.debug("[LegacyImporter] Not valid Base64");
            return null;
        } catch (Exception e) {
            AutismClientAddon.LOG.debug("[LegacyImporter] Failed to decode NBT: {}", e.getMessage());
            return null;
        }
    }

    private static String getClipboardText() {
        try {
            return Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (Exception e) {
            return null;
        }
    }

    private static void logTagStructure(CompoundTag tag, String prefix) {
        if (tag == null) {
            AutismClientAddon.LOG.debug("[LegacyImporter] {} = null", prefix);
            return;
        }

        for (String key : tag.keySet()) {
            Tag value = tag.get(key);
            String typeName = value == null ? "null" : value.getClass().getSimpleName();
            AutismClientAddon.LOG.debug("[LegacyImporter] {}.{} = {} ({})",
                    prefix, key, typeName,
                    value instanceof CompoundTag ? ((CompoundTag) value).keySet().size() + " keys"
                            : value instanceof ListTag ? ((ListTag) value).size() + " items"
                            : String.valueOf(value));
        }
    }
}
