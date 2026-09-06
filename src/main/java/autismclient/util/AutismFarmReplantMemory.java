package autismclient.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AutismFarmReplantMemory<T> {
    private final int capacity;
    private final Map<Long, T> cells = new LinkedHashMap<>();

    public AutismFarmReplantMemory(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public boolean hasRoomFor(long position) {
        return cells.containsKey(position) || cells.size() < capacity;
    }

    public boolean remember(long position, T value) {
        if (value == null) throw new NullPointerException("value");
        if (!hasRoomFor(position)) return false;
        cells.put(position, value);
        return true;
    }

    public T get(long position) {
        return cells.get(position);
    }

    public void remove(long position) {
        cells.remove(position);
    }

    public Collection<T> values() {
        return cells.values();
    }

    public int size() {
        return cells.size();
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public void clear() {
        cells.clear();
    }
}
