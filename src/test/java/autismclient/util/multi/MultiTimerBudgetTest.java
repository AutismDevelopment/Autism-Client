package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultiTimerBudgetTest {
    private static final long TICK = MultiTimerBudget.TICK_NANOS;
    private static final long DRIFT = MultiTimerBudget.DRIFT_NANOS;

    @Test
    void aBurstSpendsOnlyTheDriftAllowance() {
        long now = 1_000_000_000L;
        MultiTimerBudget budget = new MultiTimerBudget();
        budget.reset(now);

        int granted = 0;
        for (int i = 0; i < 50; i++) if (budget.reserve(now)) granted++;
        assertEquals((int) (DRIFT / TICK) + 1, granted);
        assertEquals(0, budget.available(now));
    }

    @Test
    void idleTimeNeverBanksMoreThanTheDriftAllowance() {
        long now = 1_000_000_000L;
        MultiTimerBudget budget = new MultiTimerBudget();
        budget.reset(now);
        while (budget.reserve(now)) {  }

        long later = now + 1_000_000_000L;
        int granted = 0;
        while (budget.reserve(later)) granted++;
        assertEquals((int) (DRIFT / TICK) + 1, granted);
    }

    @Test
    void aSteadyTwentyTicksPerSecondStreamIsNeverThrottled() {
        long now = 1_000_000_000L;
        MultiTimerBudget budget = new MultiTimerBudget();
        budget.reset(now);
        for (int i = 0; i < 200; i++) {
            assertTrue(budget.reserve(now), "a real-time 20 TPS stream must always fit the budget");
            now += TICK;
        }
    }

    @Test
    void drainMillisMatchesWhatReserveActuallyGrants() {
        long now = 1_000_000_000L;
        MultiTimerBudget budget = new MultiTimerBudget();
        budget.reset(now);

        int packets = 10;
        long predicted = budget.drainMillis(now, packets);

        long clock = now;
        int sent = 0;
        while (sent < packets) {
            if (budget.reserve(clock)) sent++;
            else clock += TICK / 10;
        }
        assertEquals(predicted, (clock - now) / 1_000_000L);
    }

    @Test
    void anEmptyDrainCostsNothing() {
        long now = 1_000_000_000L;
        MultiTimerBudget budget = new MultiTimerBudget();
        budget.reset(now);
        assertEquals(0L, budget.drainMillis(now, 0));
        assertEquals(0L, budget.drainMillis(now, -3));
    }
}
