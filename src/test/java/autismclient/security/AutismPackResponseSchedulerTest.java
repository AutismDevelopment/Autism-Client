package autismclient.security;

import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismPackResponseSchedulerTest {
    @BeforeEach
    @AfterEach
    void clearQueue() {
        AutismPackResponseScheduler.clearAll();
    }

    @Test
    void acceptedDownloadedAppliedAreStrictlyOrdered() {

        for (int i = 0; i < 200; i++) {
            long accepted = AutismPackResponseScheduler.acceptDelayMs();
            long downloaded = AutismPackResponseScheduler.downloadedDelayMs(accepted);
            long applied = AutismPackResponseScheduler.appliedDelayMs(downloaded);
            long failed = AutismPackResponseScheduler.failedDelayMs(accepted);
            assertTrue(accepted > 0, "accepted must not be same-tick");
            assertTrue(downloaded > accepted, "downloaded must follow accepted");
            assertTrue(applied > downloaded, "applied must follow downloaded");
            assertTrue(failed > accepted, "failed download must follow accepted");
        }
    }

    @Test
    void aDueReplyIsReleasedOnTick() {
        UUID pack = UUID.randomUUID();
        List<ServerboundResourcePackPacket> sent = new ArrayList<>();
        AutismPackResponseScheduler.schedule(pack, ServerboundResourcePackPacket.Action.DECLINED, 0L, sent::add);
        assertTrue(AutismPackResponseScheduler.hasPending());
        AutismPackResponseScheduler.tick();
        assertEquals(1, sent.size());
        assertEquals(ServerboundResourcePackPacket.Action.DECLINED, sent.get(0).action());
        assertEquals(pack, sent.get(0).id());
        assertFalse(AutismPackResponseScheduler.hasPending());
    }

    @Test
    void aReplyThatIsNotDueYetStaysQueued() {
        UUID pack = UUID.randomUUID();
        List<ServerboundResourcePackPacket> sent = new ArrayList<>();
        AutismPackResponseScheduler.schedule(pack, ServerboundResourcePackPacket.Action.DECLINED, 600_000L, sent::add);
        AutismPackResponseScheduler.tick();
        assertTrue(sent.isEmpty());
        assertTrue(AutismPackResponseScheduler.hasPending());
    }

    @Test
    void poppingAPackDropsOnlyItsQueuedReplies() {
        UUID popped = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        List<ServerboundResourcePackPacket> sent = new ArrayList<>();
        AutismPackResponseScheduler.schedule(popped, ServerboundResourcePackPacket.Action.ACCEPTED, 0L, sent::add);
        AutismPackResponseScheduler.schedule(kept, ServerboundResourcePackPacket.Action.ACCEPTED, 0L, sent::add);
        AutismPackResponseScheduler.cancel(popped);
        AutismPackResponseScheduler.tick();
        assertEquals(1, sent.size());
        assertEquals(kept, sent.get(0).id());
    }

    @Test
    void onSentRunsOnlyWhenTheReplyActuallyGoesOut() {
        UUID pack = UUID.randomUUID();
        boolean[] notified = {false};
        AutismPackResponseScheduler.schedule(pack, ServerboundResourcePackPacket.Action.DECLINED, 0L,
            p -> { }, () -> notified[0] = true);
        assertFalse(notified[0]);
        AutismPackResponseScheduler.tick();
        assertTrue(notified[0]);
    }

    @Test
    void clearAllDropsEverything() {
        List<ServerboundResourcePackPacket> sent = new ArrayList<>();
        AutismPackResponseScheduler.schedule(UUID.randomUUID(), ServerboundResourcePackPacket.Action.ACCEPTED, 0L, sent::add);
        AutismPackResponseScheduler.schedule(UUID.randomUUID(), ServerboundResourcePackPacket.Action.DECLINED, 0L, sent::add);
        AutismPackResponseScheduler.clearAll();
        AutismPackResponseScheduler.tick();
        assertTrue(sent.isEmpty());
    }
}
