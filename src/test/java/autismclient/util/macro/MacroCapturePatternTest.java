package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MacroCapturePatternTest {
    @Test
    void readablePatternCapturesMultipleValues() {
        var result = MacroCapturePattern.match(
            MacroCapturePattern.Mode.CAPTURE,
            "{player} just paid you {amount} Dollars",
            "MelonikLVL10 just paid you 203M Dollars"
        );

        assertTrue(result.isPresent());
        assertEquals("MelonikLVL10", result.get().values().get("player").value());
        assertEquals("203M", result.get().values().get("amount").value());
    }

    @Test
    void namedRegexGroupsBecomeVariables() {
        var result = MacroCapturePattern.match(
            MacroCapturePattern.Mode.REGEX,
            "Click the block (?<item>[A-Za-z ]+)",
            "Click the block Polished Obsidian"
        );

        assertTrue(result.isPresent());
        assertEquals("Polished Obsidian", result.get().values().get("item").value());
    }

    @Test
    void finalCaptureConsumesTheFullValueAndEscapedBracesStayLiteral() {
        var result = MacroCapturePattern.match(
            MacroCapturePattern.Mode.CAPTURE,
            "Reward {{daily}}: {item}",
            "Reward {daily}: Polished Obsidian"
        );

        assertTrue(result.isPresent());
        assertEquals("Polished Obsidian", result.get().values().get("item").value());
        assertEquals(java.util.Set.of("item"),
            MacroCapturePattern.declaredNames(MacroCapturePattern.Mode.CAPTURE, "Reward {{daily}}: {item}"));
    }
}
