package autismclient.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AntiVanishTextTest {
    @Test
    void foldsSmallCapsAndDecorations() {
        assertEquals("mod staff", AntiVanishText.normalize("§c«ᴍᴏᴅ»  [Śtáff]"));
    }

    @Test
    void foldsCommonCrossScriptConfusables() {
        assertEquals("admin", AntiVanishText.normalize("аԁᴍɪո"));
    }

    @Test
    void removesUsernameBeforeMatching() {
        assertEquals("", AntiVanishText.firstMatch("ModPlayer", "ModPlayer", List.of("mod"), "Contains"));
        assertEquals("mod", AntiVanishText.firstMatch("[ᴍᴏᴅ] ModPlayer", "ModPlayer", List.of("mod"), "Contains"));
    }

    @Test
    void supportsWordAndExactModes() {
        assertEquals("mod", AntiVanishText.firstMatch("Senior Mod", "Alice", List.of("mod"), "Word"));
        assertEquals("", AntiVanishText.firstMatch("Moderator", "Alice", List.of("mod"), "Word"));
        assertEquals("owner", AntiVanishText.firstMatch("«Owner»", "Alice", List.of("owner"), "Exact"));
    }

    @Test
    void matchesScannedPrivateFontTags() {
        assertEquals("\uE123\uE124", AntiVanishText.firstMatch(
            "\uE123\uE124 Alice", "Alice", List.of("\uE123\uE124"), "Contains"));
    }

    @Test
    void matchesFormattedDepartureNames() {
        assertTrue(AntiVanishText.containsPlayerName("§c[Admin] Alice", "Alice"));
        assertTrue(AntiVanishText.containsPlayerName("Alice", "Alice"));
        assertFalse(AntiVanishText.containsPlayerName("Malice", "Alice"));
        assertFalse(AntiVanishText.containsPlayerName("Admin_Alice", "Alice"));
        assertTrue(AntiVanishText.containsPlayerName("[Admin] Admin_Alice", "Admin_Alice"));
    }

    @Test
    void onlyLeaveShapedMessagesConfirmDeparture() {
        assertTrue(AntiVanishText.looksLikeLeaveMessage("Alice left the game", "Alice"));
        assertTrue(AntiVanishText.looksLikeLeaveMessage("[Quit] Alice disconnected", "Alice"));
        assertFalse(AntiVanishText.looksLikeLeaveMessage("Alice killed Bob", "Alice"));
        assertFalse(AntiVanishText.looksLikeLeaveMessage("Staff watching Alice", "Alice"));
        assertFalse(AntiVanishText.looksLikeLeaveMessage("Malice left the game", "Alice"));
    }
}
