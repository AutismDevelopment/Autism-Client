package autismclient.util.multi;

import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MultiEntityTracker {
    private static final int CAP = 512;
    private static final double RELATIVE_SCALE = 1.0D / 4096.0D;

    record State(int id, UUID uuid, String type, Vec3 position, Vec3 movement, float yRot, float xRot, float headYRot,
                  boolean onGround, int ownerId, int hookedId) {
        PositionMoveRotation positionMoveRotation() {
            return new PositionMoveRotation(position, movement, yRot, xRot);
        }
    }

    private final Map<Integer, State> byId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> byUuid = new ConcurrentHashMap<>();

    void put(int id, String type, double x, double y, double z) {
        put(id, null, type, x, y, z, Vec3.ZERO, 0.0F, 0.0F, 0.0F, false, -1);
    }

    void put(int id, UUID uuid, String type, double x, double y, double z, Vec3 movement,
              float yRot, float xRot, float headYRot, boolean onGround, int ownerId) {
        put(id, uuid, type, x, y, z, movement, yRot, xRot, headYRot, onGround, ownerId, null);
    }

    void put(int id, UUID uuid, String type, double x, double y, double z, Vec3 movement,
             float yRot, float xRot, float headYRot, boolean onGround, int ownerId, Vec3 focus) {
        String safeType = type == null ? "" : type;
        Vec3 newPosition = new Vec3(x, y, z);
        if (byId.size() >= CAP && !byId.containsKey(id)) {

            if (!evictFor(safeType, newPosition, focus)) return;
        }
        State state = new State(id, uuid, safeType, newPosition,
            movement == null ? Vec3.ZERO : movement, yRot, xRot, headYRot, onGround, ownerId, -1);
        State replaced = byId.put(id, state);
        if (replaced != null && replaced.uuid() != null && !replaced.uuid().equals(uuid)) {
            byUuid.remove(replaced.uuid(), id);
        }
        if (uuid != null) {
            Integer oldId = byUuid.put(uuid, id);
            if (oldId != null && oldId != id) {
                State stale = byId.get(oldId);
                if (stale != null && uuid.equals(stale.uuid())) byId.remove(oldId, stale);
            }
        }
    }

    private boolean evictFor(String newType, Vec3 newPosition, Vec3 focus) {
        int newRank = priorityRank(newType);
        State victim = null;
        int victimRank = -1;
        double victimDistance = -1.0D;
        double newDistance = focus == null ? Double.POSITIVE_INFINITY : newPosition.distanceToSqr(focus);
        for (State value : byId.values()) {
            int rank = priorityRank(value.type());
            double distance = focus == null ? 0.0D : value.position().distanceToSqr(focus);
            boolean lowerPriority = rank > newRank;
            boolean sameButFarther = rank == newRank && focus != null && distance > newDistance;
            if (!lowerPriority && !sameButFarther) continue;
            if (victim == null || rank > victimRank || (rank == victimRank && distance > victimDistance)) {
                victim = value;
                victimRank = rank;
                victimDistance = distance;
            }
        }
        if (victim == null) return false;
        State removed = byId.remove(victim.id());
        if (removed == null) return false;
        if (removed.uuid() != null) byUuid.remove(removed.uuid(), removed.id());
        return true;
    }

    private static int priorityRank(String type) {
        String normalized = normalize(type);
        if (normalized.equals("player") || normalized.equals("fishing bobber")) return 0;
        if (normalized.equals("item") || normalized.equals("experience orb")
            || normalized.contains("arrow") || normalized.contains("projectile")
            || normalized.contains("display") || normalized.equals("falling block")
            || normalized.equals("area effect cloud") || normalized.equals("firework rocket")) return 2;

        return 1;
    }

    void move(int id, double x, double y, double z) {
        byId.computeIfPresent(id, (ignored, old) -> copy(old, new Vec3(x, y, z), old.movement(),
            old.yRot(), old.xRot(), old.headYRot(), old.onGround(), old.hookedId()));
    }

    void moveRelative(int id, short xa, short ya, short za, boolean hasPosition,
                      float yRot, float xRot, boolean hasRotation, boolean onGround) {
        byId.computeIfPresent(id, (ignored, old) -> {
            Vec3 pos = old.position();
            if (hasPosition) {
                pos = pos.add(xa * RELATIVE_SCALE, ya * RELATIVE_SCALE, za * RELATIVE_SCALE);
            }
            return copy(old, pos, old.movement(), hasRotation ? yRot : old.yRot(),
                hasRotation ? xRot : old.xRot(), old.headYRot(), onGround, old.hookedId());
        });
    }

    void sync(int id, PositionMoveRotation values, boolean onGround) {
        if (values == null) return;
        byId.computeIfPresent(id, (ignored, old) -> copy(old, values.position(), values.deltaMovement(),
            values.yRot(), values.xRot(), old.headYRot(), onGround, old.hookedId()));
    }

    void moveAbsolute(int id, Vec3 position, Vec3 movement, float yRot, float xRot) {
        if (position == null) return;
        byId.computeIfPresent(id, (ignored, old) -> copy(old, position,
            movement == null ? old.movement() : movement, yRot, xRot,
            old.headYRot(), old.onGround(), old.hookedId()));
    }

    void teleport(int id, PositionMoveRotation change, Set<Relative> relatives, boolean onGround) {
        if (change == null) return;
        Set<Relative> relativeSet = relatives == null ? Set.of() : relatives;
        byId.computeIfPresent(id, (ignored, old) -> {
            PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(
                old.positionMoveRotation(), change, relativeSet);
            return copy(old, absolute.position(), absolute.deltaMovement(), absolute.yRot(), absolute.xRot(),
                old.headYRot(), onGround, old.hookedId());
        });
    }

    void motion(int id, Vec3 movement) {
        if (movement == null) return;
        byId.computeIfPresent(id, (ignored, old) -> copy(old, old.position(), movement,
            old.yRot(), old.xRot(), old.headYRot(), old.onGround(), old.hookedId()));
    }

    void headRotation(int id, float headYRot) {
        byId.computeIfPresent(id, (ignored, old) -> copy(old, old.position(), old.movement(),
            old.yRot(), old.xRot(), headYRot, old.onGround(), old.hookedId()));
    }

    void fishingHookTarget(int hookId, int encodedTargetId) {
        int targetId = encodedTargetId > 0 ? encodedTargetId - 1 : -1;
        byId.computeIfPresent(hookId, (ignored, old) -> copy(old, old.position(), old.movement(),
            old.yRot(), old.xRot(), old.headYRot(), old.onGround(), targetId));
    }

    State state(int id) {
        return byId.get(id);
    }

    State state(UUID uuid) {
        if (uuid == null) return null;
        Integer id = byUuid.get(uuid);
        if (id == null) return null;
        State state = byId.get(id);
        if (state == null || !uuid.equals(state.uuid())) {
            byUuid.remove(uuid, id);
            return null;
        }
        return state;
    }

    Iterable<State> states() {
        return byId.values();
    }

    int size() {
        return byId.size();
    }

    private static State copy(State old, Vec3 position, Vec3 movement, float yRot, float xRot,
                              float headYRot, boolean onGround, int hookedId) {
        return new State(old.id(), old.uuid(), old.type(), position, movement, yRot, xRot, headYRot,
            onGround, old.ownerId(), hookedId);
    }

    void remove(int id) {
        State removed = byId.remove(id);
        if (removed != null && removed.uuid() != null) byUuid.remove(removed.uuid(), id);
    }

    void clear() {
        byId.clear();
        byUuid.clear();
    }

    int nearest(String typeQuery, Vec3 from) {
        String q = normalize(typeQuery);
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (State value : byId.values()) {
            if (!q.isEmpty() && !normalize(value.type()).contains(q)) continue;
            double distance = value.position().distanceToSqr(from);
            if (distance < bestDist) {
                bestDist = distance;
                best = value.id();
            }
        }
        return best;
    }

    double[] pos(int id) {
        State value = byId.get(id);
        return value == null ? null : new double[]{value.position().x, value.position().y, value.position().z};
    }

    String typeOf(int id) {
        State value = byId.get(id);
        return value == null ? null : value.type();
    }

    boolean present(List<String> typeQueries, boolean containerOnly,
                    double cx, double cy, double cz, double radius) {
        double radiusSq = radius * radius;
        java.util.ArrayList<String> queries = new java.util.ArrayList<>();
        if (typeQueries != null) {
            for (String query : typeQueries) {
                if (query == null || query.isBlank() || query.startsWith("~")) continue;
                queries.add(normalize(query));
            }
        }
        for (State value : byId.values()) {
            Vec3 pos = value.position();
            double dx = pos.x - cx;
            double dy = pos.y - cy;
            double dz = pos.z - cz;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
            String type = normalize(value.type());
            if (containerOnly && !(type.contains("boat") || type.contains("minecart")
                || type.contains("llama") || type.contains("chest"))) continue;
            if (queries.isEmpty()) return true;
            for (String query : queries) if (type.contains(query)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon >= 0) normalized = normalized.substring(colon + 1);
        return normalized.replace('_', ' ');
    }
}
