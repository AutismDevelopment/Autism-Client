package autismclient.util.macro;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtBookActionPaginationTest {
    @Test
    void fixedCharacterPagesNeverSplitSupplementaryCharacters() {
        String text = "abc😀def😀ghi";

        List<String> pages = NbtBookAction.chunkTextIntoPages(text, 10, 4);

        assertEquals(text, String.join("", pages));
        assertTrue(pages.stream().allMatch(NbtBookActionPaginationTest::hasValidSurrogates));
        assertFalse(pages.isEmpty());
    }

    @Test
    void protocolSizedPagesPreserveAllUnicodeText() {
        String text = "x".repeat(1023) + "😀" + "tail";

        List<String> pages = NbtBookAction.chunkTextIntoPages(text, 10, 1024);

        assertEquals(text, String.join("", pages));
        assertTrue(pages.stream().allMatch(page -> page.length() <= 1024));
        assertTrue(pages.stream().allMatch(NbtBookActionPaginationTest::hasValidSurrogates));
    }

    private static boolean hasValidSurrogates(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i))) return false;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }
}
