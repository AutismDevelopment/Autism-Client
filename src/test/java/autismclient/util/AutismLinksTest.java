package autismclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AutismLinksTest {
    @Test
    void allowsOnlyWebAndMailSchemes() {
        assertTrue(AutismLinks.isOpenableUrl("https://dupedb.net/exploit/1"));
        assertTrue(AutismLinks.isOpenableUrl("http://example.com"));
        assertTrue(AutismLinks.isOpenableUrl("HTTPS://Example.com"));
        assertTrue(AutismLinks.isOpenableUrl("mailto:a@b.com"));
    }

    @Test
    void rejectsLocalAndCodeSchemes() {
        assertFalse(AutismLinks.isOpenableUrl("file:///etc/passwd"));
        assertFalse(AutismLinks.isOpenableUrl("file:///C:/Windows/System32/calc.exe"));
        assertFalse(AutismLinks.isOpenableUrl("javascript:alert(1)"));
        assertFalse(AutismLinks.isOpenableUrl("data:text/html,<script>"));
        assertFalse(AutismLinks.isOpenableUrl("steam://run/12345"));
        assertFalse(AutismLinks.isOpenableUrl("  file://x"));
        assertFalse(AutismLinks.isOpenableUrl(""));
        assertFalse(AutismLinks.isOpenableUrl(null));
    }
}
