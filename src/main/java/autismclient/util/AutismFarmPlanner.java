package autismclient.util;

import java.util.List;

public final class AutismFarmPlanner {
    public enum Kind { HARVEST, REPLANT_RECENT, BONEMEAL, REPLANT, TILL }

    public record Option<T>(T target, Kind kind, float yaw, float pitch, int slot, boolean urgent) {}

    private AutismFarmPlanner() {}

    public static <T> T choose(List<Option<T>> options, float yaw, float pitch, int selected,
                               T previous) {
        boolean urgent = options.stream().anyMatch(Option::urgent);
        boolean planting = options.stream().anyMatch(o -> isPlanting(o.kind()));
        T best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (Option<T> first : options) {
            if (urgent && !first.urgent()) continue;
            double cost = transition(yaw, pitch, selected, first) + priorityCost(first.kind(), planting);
            int nextSlot = first.slot() < 0 ? selected : first.slot();
            double next = Double.POSITIVE_INFINITY;
            for (Option<T> second : options) {
                if (first == second || first.target().equals(second.target())) continue;
                next = Math.min(next, transition(first.yaw(), first.pitch(), nextSlot, second));
            }
            if (Double.isFinite(next)) cost += 0.65D * next;

            if (first.target().equals(previous)) cost -= 0.6D;
            if (cost < bestCost) {
                bestCost = cost;
                best = first.target();
            }
        }
        return best;
    }

    private static boolean isPlanting(Kind kind) {
        return kind == Kind.REPLANT || kind == Kind.REPLANT_RECENT;
    }

    private static double priorityCost(Kind kind, boolean planting) {

        return kind == Kind.TILL ? 8.0D : kind == Kind.BONEMEAL && planting ? 6.0D : 0.0D;
    }

    private static double transition(float yaw, float pitch, int selected, Option<?> next) {
        double yawDelta = Math.abs(Math.IEEEremainder(next.yaw() - yaw, 360.0D));
        double turn = Math.hypot(yawDelta, next.pitch() - pitch) / 20.0D;
        double change = next.slot() >= 0 && next.slot() != selected ? 2.0D : 0.0D;
        double use = next.kind() == Kind.HARVEST ? 1.0D : 4.0D;
        return turn + change + use;
    }
}
