package autismclient.util.oresim;

final class NearestPositionSelector {

    private final double[] distances;
    private final long[] positions;
    private final int[] states;
    private int size;
    private boolean truncated;

    NearestPositionSelector(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        distances = new double[capacity];
        positions = new long[capacity];
        states = new int[capacity];
    }

    boolean isFull() {
        return size == distances.length;
    }

    double farthestDistanceSquared() {
        if (size == 0) throw new IllegalStateException("selector is empty");
        return distances[0];
    }

    boolean truncated() {
        return truncated;
    }

    void offer(double distanceSquared, long packedPosition, int stateId) {
        if (size < distances.length) {
            distances[size] = distanceSquared;
            positions[size] = packedPosition;
            states[size] = stateId;
            siftUp(size++);
            return;
        }

        truncated = true;
        if (compare(distanceSquared, packedPosition, stateId,
            distances[0], positions[0], states[0]) >= 0) return;

        distances[0] = distanceSquared;
        positions[0] = packedPosition;
        states[0] = stateId;
        siftDown(0, size);
    }

    int writeNearestFirst(long[] outPositions, int[] outStates) {
        if (outPositions.length < size || outStates.length < size) {
            throw new IllegalArgumentException("output arrays are smaller than the selection");
        }
        int remaining = size;
        while (remaining > 1) {
            swap(0, remaining - 1);
            siftDown(0, --remaining);
        }
        System.arraycopy(positions, 0, outPositions, 0, size);
        System.arraycopy(states, 0, outStates, 0, size);
        return size;
    }

    private void siftUp(int index) {
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (compare(parent, index) >= 0) return;
            swap(parent, index);
            index = parent;
        }
    }

    private void siftDown(int index, int heapSize) {
        while (true) {
            int left = index * 2 + 1;
            if (left >= heapSize) return;
            int largest = left;
            int right = left + 1;
            if (right < heapSize && compare(right, left) > 0) largest = right;
            if (compare(index, largest) >= 0) return;
            swap(index, largest);
            index = largest;
        }
    }

    private int compare(int a, int b) {
        return compare(distances[a], positions[a], states[a], distances[b], positions[b], states[b]);
    }

    private static int compare(double distanceA, long positionA, int stateA,
                               double distanceB, long positionB, int stateB) {
        int distanceOrder = Double.compare(distanceA, distanceB);
        if (distanceOrder != 0) return distanceOrder;
        int positionOrder = Long.compare(positionA, positionB);
        return positionOrder != 0 ? positionOrder : Integer.compare(stateA, stateB);
    }

    private void swap(int a, int b) {
        double distance = distances[a];
        distances[a] = distances[b];
        distances[b] = distance;
        long position = positions[a];
        positions[a] = positions[b];
        positions[b] = position;
        int state = states[a];
        states[a] = states[b];
        states[b] = state;
    }
}
