package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultiClipCommandTest {
    @Test
    void headlessBotsAcceptExactClipDistances() {
        assertNull(MultiClientCommands.denyReason("vclip"));
        assertNull(MultiClientCommands.denyReason("hclip"));
        assertNull(MultiClientCommands.batchDenyReason("vclip", "-25"));
        assertNull(MultiClientCommands.batchDenyReason("hclip", "12"));
    }

    @Test
    void plainDistanceSegmentsEveryTenBlocks() {
        MultiClientCommands.ClipSpec spec = MultiClientCommands.parseClip("vclip", "25").spec();
        assertNotNull(spec);
        assertEquals(25.0D, spec.blocks());
        assertEquals(3, spec.segments());
        assertTrue(spec.forceGround(), "vclip reports on-ground by default, like VClipAction");

        MultiClientCommands.ClipSpec horizontal = MultiClientCommands.parseClip("hclip", "12").spec();
        assertNotNull(horizontal);
        assertEquals(2, horizontal.segments());
        assertFalse(horizontal.forceGround(), "hclip reports airborne by default, like HClipAction");
    }

    @Test
    void singleModeSendsOnePacket() {
        for (String mode : new String[] {"single", "normal"}) {
            MultiClientCommands.ClipSpec spec = MultiClientCommands.parseClip("vclip", mode + " -25").spec();
            assertNotNull(spec, mode);
            assertEquals(-25.0D, spec.blocks());
            assertEquals(1, spec.segments());
        }
    }

    @Test
    void customModeReadsSegmentSizeMaxPacketsAndGroundFlag() {
        MultiClientCommands.ClipSpec spec =
            MultiClientCommands.parseClip("hclip", "custom -25 5 20 true true true").spec();
        assertNotNull(spec);
        assertEquals(-25.0D, spec.blocks());
        assertEquals(5, spec.segments());
        assertTrue(spec.forceGround());
    }

    @Test
    void aSegmentCountOverThePacketCapCollapsesToOnePacket() {
        MultiClientCommands.ClipSpec spec = MultiClientCommands.parseClip("vclip", "custom -300 1 20").spec();
        assertNotNull(spec);
        assertEquals(1, spec.segments());
    }

    @Test
    void worldScanModesAreRefusedWithAReason() {
        for (String mode : new String[] {"top", "bottom", "forward", "back"}) {
            MultiClientCommands.ClipRequest request = MultiClientCommands.parseClip("vclip", mode);
            assertNull(request.spec(), mode);
            assertTrue(request.error().contains("exact distances"), request.error());
            assertNotNull(MultiClientCommands.batchDenyReason("vclip", mode), mode);
        }
    }

    @Test
    void garbageAndEmptyArgumentsReportUsage() {
        assertTrue(MultiClientCommands.parseClip("vclip", "").error().startsWith("Usage:"));
        assertTrue(MultiClientCommands.parseClip("vclip", "custom").error().startsWith("Usage:"));
        assertTrue(MultiClientCommands.parseClip("hclip", "sideways").error().startsWith("Bad hclip distance"));
        assertTrue(MultiClientCommands.parseClip("vclip", "NaN").error().startsWith("Bad vclip distance"));
    }
}
