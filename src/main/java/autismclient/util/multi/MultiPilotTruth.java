package autismclient.util.multi;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Iterator;

final class MultiPilotTruth {
    enum Result { UNCHANGED, ACKNOWLEDGED, REBASE }

    private record Sent(Vec3 position, long at) {}

    private static final int MAX_SENT = 64;
    private static final long MAX_AGE_MS = 4_000L;
    private static final double ACK_DISTANCE_SQR = 0.20D * 0.20D;
    private static final double CHANGE_DISTANCE_SQR = 1.0E-8D;
    private static final long TRANSITION_GRACE_MS = 750L;

    private final ArrayDeque<Sent> sent = new ArrayDeque<>();
    private Vec3 lastObserved;
    private long rebaseAfter;
    private boolean pendingUnexpected;

    synchronized void reset(Vec3 observed, long now) {
        sent.clear();
        lastObserved = observed;
        rebaseAfter = now + TRANSITION_GRACE_MS;
        pendingUnexpected = false;
    }

    synchronized void recordSent(Vec3 position, long now) {
        if (position == null) return;
        sent.addLast(new Sent(position, now));
        while (sent.size() > MAX_SENT) sent.removeFirst();
        prune(now);
    }

    synchronized Result observe(Vec3 observed, long now) {
        if (observed == null) return Result.UNCHANGED;
        if (lastObserved != null && lastObserved.distanceToSqr(observed) <= CHANGE_DISTANCE_SQR) {
            prune(now);
            if (pendingUnexpected && now >= rebaseAfter) {
                pendingUnexpected = false;
                sent.clear();
                return Result.REBASE;
            }
            return Result.UNCHANGED;
        }
        lastObserved = observed;
        prune(now);
        Sent matched = null;
        for (Iterator<Sent> it = sent.descendingIterator(); it.hasNext();) {
            Sent candidate = it.next();
            if (candidate.position().distanceToSqr(observed) <= ACK_DISTANCE_SQR) {
                matched = candidate;
                break;
            }
        }
        if (matched == null) {
            if (now < rebaseAfter) {
                pendingUnexpected = true;
                return Result.UNCHANGED;
            }
            pendingUnexpected = false;
            sent.clear();
            return Result.REBASE;
        }
        pendingUnexpected = false;

        while (!sent.isEmpty()) {
            Sent first = sent.removeFirst();
            if (first == matched) break;
        }
        return Result.ACKNOWLEDGED;
    }

    private void prune(long now) {
        while (!sent.isEmpty() && now - sent.peekFirst().at() > MAX_AGE_MS) sent.removeFirst();
    }
}
