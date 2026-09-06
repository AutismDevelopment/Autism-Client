package autismclient.util;

public final class AutismPlacementTick {
    private static String owner;
    private static int ownedTick = Integer.MIN_VALUE;

    private AutismPlacementTick() {
    }

    public static synchronized boolean claim(String moduleId) {
        int tick = AutismSharedState.get().getClientTickCounter();

        if (tick == ownedTick) return owner != null && owner.equals(moduleId);
        owner = moduleId;
        ownedTick = tick;
        return true;
    }

    public static synchronized String owner() {
        return AutismSharedState.get().getClientTickCounter() == ownedTick ? owner : null;
    }
}
