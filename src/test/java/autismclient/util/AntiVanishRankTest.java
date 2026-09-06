package autismclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AntiVanishRankTest {
    private static final List<String> RANKS = List.of("mod", "admin", "staff", "owner");

    private static boolean detects(String tab, String name) {
        return !AntiVanishText.firstMatch(tab, name, RANKS, "Word").isBlank();
    }

    @Test
    void detectsFancyFontAndDecoratedRanks() {
        assertTrue(detects("ᴍᴏᴅ Steve", "Steve"), "small-caps ᴍᴏᴅ");
        assertTrue(detects("«Staff» Steve", "Steve"), "guillemet-decorated Staff");
        assertTrue(detects("§c§lADMIN §7Steve", "Steve"), "legacy-colour ADMIN");
        assertTrue(detects("§x§f§0§0§0§0§0OWNER Steve", "Steve"), "hex-gradient OWNER");
    }

    @Test
    void ignoresPlainPlayersAndSubstringLookalikes() {
        assertEquals("", AntiVanishText.firstMatch("Steve", "Steve", RANKS, "Word"), "plain player, no rank");
        assertEquals("", AntiVanishText.firstMatch("Nimrod", "Player", RANKS, "Word"), "'mod' inside 'nimrod' is not a rank");
    }
}
