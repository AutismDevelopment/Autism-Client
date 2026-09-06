package autismclient.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AutismFarmActionWatchdog<T> {
    private record Attempt<T>(T state, int lastTick, int ticks) {}
    private final Map<Long, Attempt<T>> attempts = new LinkedHashMap<>();

    public boolean allow(long cell, T state, int tick, int limit) {
        Attempt<T> old = attempts.get(cell);
        int elapsed = old == null ? 0 : tick - old.lastTick();
        int count = old == null || !Objects.equals(old.state(), state) || elapsed < 0 || elapsed > 20
            ? 1 : old.ticks() + (elapsed == 0 ? 0 : 1);
        if (count > Math.max(1, limit)) {
            attempts.remove(cell);
            return false;
        }
        attempts.put(cell, new Attempt<>(state, tick, count));
        if (attempts.size() > 256) attempts.remove(attempts.keySet().iterator().next());
        return true;
    }

    public void clear() {
        attempts.clear();
    }
}
