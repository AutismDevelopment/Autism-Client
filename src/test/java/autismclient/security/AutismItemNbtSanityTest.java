package autismclient.security;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismItemNbtSanityTest {
    @Test
    void tooltipUnderCapIsUntouched() {
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) lines.add(Component.literal("line " + i));
        List<Component> snapshot = List.copyOf(lines);

        assertFalse(AutismItemNbtSanity.trimTooltipLines(lines));
        assertEquals(snapshot, lines);
    }

    @Test
    void tooltipAtCapIsUntouched() {
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < AutismItemNbtSanity.MAX_TOOLTIP_LINES; i++) lines.add(Component.literal("line " + i));

        assertFalse(AutismItemNbtSanity.trimTooltipLines(lines));
        assertEquals(AutismItemNbtSanity.MAX_TOOLTIP_LINES, lines.size());
    }

    @Test
    void tooltipOverCapIsTrimmedWithNotice() {
        int total = AutismItemNbtSanity.MAX_TOOLTIP_LINES + 100;
        int expectedHidden = total - (AutismItemNbtSanity.MAX_TOOLTIP_LINES - 1);
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < total; i++) lines.add(Component.literal("line " + i));

        assertTrue(AutismItemNbtSanity.trimTooltipLines(lines));
        assertEquals(AutismItemNbtSanity.MAX_TOOLTIP_LINES, lines.size());

        assertEquals("line 0", lines.get(0).getString());
        assertEquals("line " + (AutismItemNbtSanity.MAX_TOOLTIP_LINES - 2),
            lines.get(AutismItemNbtSanity.MAX_TOOLTIP_LINES - 2).getString());
        String notice = lines.get(lines.size() - 1).getString();
        assertTrue(notice.contains(String.valueOf(expectedHidden)));
        assertTrue(notice.contains("tooltip lines hidden"));
    }

    @Test
    void unsafeTooltipLineIsReplaced() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("safe line"));
        lines.add(Component.literal("x".repeat(20_000)));
        lines.add(Component.literal("another safe line"));

        assertEquals(1, AutismItemNbtSanity.scrubUnsafeTooltipLines(lines));
        assertEquals("safe line", lines.get(0).getString());
        assertEquals("[unsafe tooltip line removed]", lines.get(1).getString());
        assertEquals("another safe line", lines.get(2).getString());
    }

    @Test
    void safeTooltipLinesAreNotScrubbed() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Firework Rocket"));
        lines.add(Component.literal("Bytes: 4096 (4.0 KiB)"));

        assertEquals(0, AutismItemNbtSanity.scrubUnsafeTooltipLines(lines));
        assertEquals("Firework Rocket", lines.get(0).getString());
    }
}
