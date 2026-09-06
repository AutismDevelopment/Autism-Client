package autismclient.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AutismAntiBot {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final int GROUND_VL = 10;

    private static final int PROFILE_GRACE = 40;

    private static final Map<Integer, Integer> groundViolations = new HashMap<>();

    private static final Set<UUID> confirmedBots = new HashSet<>();

    private static final Set<UUID> loggedBots = new HashSet<>();

    private static final Map<Long, double[]> botSpots = new HashMap<>();
    private static final int CACHE_MAX = 4096;
    private static final int BOT_SPOTS_MAX = 512;
    private static final double SPOT_EPS = 0.15;
    private static final double STILL_EPS = 0.02;

    private static final Set<Integer> duplicateNameIds = new HashSet<>();
    private static Object dupLevel;
    private static int dupSize = -1;

    private static Module cachedModule;
    private static int cachedModuleRev = -1;

    private AutismAntiBot() {}

    private static Module module() {
        int rev = ModuleRegistry.revision();
        if (rev != cachedModuleRev) {
            Module m = ModuleRegistry.get("antibot");
            cachedModule = (m != null && m.isEnabled()) ? m : null;
            cachedModuleRev = rev;
        }
        return cachedModule;
    }

    public static boolean isBot(Entity entity) {
        Module m = module();
        if (m == null) return false;
        if (!(entity instanceof Player player) || MC.player == null || player == MC.player) return false;

        UUID uuid = player.getUUID();
        if (confirmedBots.contains(uuid)) return true;

        String reason = stickyReason(player, m);
        if (reason != null) { confirm(uuid); rememberSpot(player); logHidden(player, reason); return true; }

        if (missingProfile(player)) { rememberSpot(player); logHidden(player, "no player-info entry"); return true; }

        if (atKnownBotSpot(player)) { logHidden(player, "known bot location"); return true; }
        return false;
    }

    private static void confirm(UUID uuid) {
        if (confirmedBots.size() >= CACHE_MAX) confirmedBots.clear();
        confirmedBots.add(uuid);
    }

    private static void rememberSpot(Player player) {
        if (botSpots.size() >= BOT_SPOTS_MAX) botSpots.clear();
        botSpots.put(player.blockPosition().asLong(), new double[]{player.getX(), player.getY(), player.getZ()});
    }

    private static boolean atKnownBotSpot(Player player) {
        if (botSpots.isEmpty()) return false;
        double moved = Math.abs(player.getX() - player.xOld) + Math.abs(player.getZ() - player.zOld);
        if (moved > STILL_EPS) return false;
        double[] spot = botSpots.get(player.blockPosition().asLong());
        if (spot == null) return false;
        double dx = player.getX() - spot[0], dy = player.getY() - spot[1], dz = player.getZ() - spot[2];
        return dx * dx + dy * dy + dz * dz <= SPOT_EPS * SPOT_EPS;
    }

    private static String stickyReason(Player player, Module m) {
        if (duplicates().contains(player.getId())) return "duplicate name";
        float pitch = player.getXRot();
        if (Math.abs(pitch) > 90.0f) return "impossible pitch " + pitch;
        var profile = player.getGameProfile();
        if (profile == null || !autismclient.util.AutismPlayerScanner.isUsername(profile.name()))
            return "invalid profile name '" + (profile == null ? "<null>" : profile.name()) + "'";
        if ("Aggressive".equals(m.value("mode")) && groundViolations.getOrDefault(player.getId(), 0) >= GROUND_VL)
            return "flying-on-ground (aggressive)";
        return null;
    }

    private static void logHidden(Player player, String reason) {
        if (loggedBots.size() >= CACHE_MAX) loggedBots.clear();
        if (loggedBots.add(player.getUUID())) {
            var p = player.getGameProfile();
            autismclient.AutismClientAddon.LOG.debug("[antibot] hiding player id={} name='{}' — {}",
                player.getId(), p == null ? "?" : p.name(), reason);
        }
    }

    public static boolean suppress(Entity entity) {
        return isBot(entity);
    }

    public static boolean isConfirmedBot(UUID uuid) {
        return uuid != null && confirmedBots.contains(uuid);
    }

    public static void tick() {
        Module m = module();
        if (m == null || MC.level == null || MC.player == null) { reset(); return; }
        rebuildDuplicates();
        if ("Aggressive".equals(m.value("mode"))) updateGround();
        else if (!groundViolations.isEmpty()) groundViolations.clear();
    }

    public static void reset() {
        groundViolations.clear();
        confirmedBots.clear();
        loggedBots.clear();
        botSpots.clear();
        duplicateNameIds.clear();
        dupLevel = null;
        dupSize = -1;
    }

    private static boolean missingProfile(Player player) {
        var conn = MC.getConnection();
        if (conn == null) return false;
        return player.tickCount >= PROFILE_GRACE && conn.getPlayerInfo(player.getUUID()) == null;
    }

    private static Set<Integer> duplicates() {
        var level = MC.level;
        if (level == null) return duplicateNameIds;
        if (level != dupLevel || level.players().size() != dupSize) rebuildDuplicates();
        return duplicateNameIds;
    }

    private static void rebuildDuplicates() {
        var level = MC.level;
        duplicateNameIds.clear();
        if (level == null) { dupLevel = null; dupSize = -1; return; }
        List<? extends Player> players = level.players();
        int n = players.size();
        for (int i = 0; i < n; i++) {
            Player a = players.get(i);
            var pa = a.getGameProfile();
            if (pa == null || pa.name() == null) continue;
            for (int j = i + 1; j < n; j++) {
                Player b = players.get(j);
                var pb = b.getGameProfile();
                if (pb == null) continue;
                if (pa.name().equals(pb.name()) && !pa.id().equals(pb.id())) {
                    duplicateNameIds.add(a.getId());
                    duplicateNameIds.add(b.getId());
                }
            }
        }
        dupLevel = level;
        dupSize = n;
    }

    private static void updateGround() {
        Set<Integer> present = new HashSet<>();
        for (Player p : MC.level.players()) {
            if (p == MC.player) continue;
            int id = p.getId();
            present.add(id);
            int vl = groundViolations.getOrDefault(id, 0);
            if (p.onGround() && p.yOld != p.getY()) {
                groundViolations.put(id, vl + 1);
            } else if (!p.onGround() && vl > 0) {
                int next = vl / 2;
                if (next <= 0) groundViolations.remove(id);
                else groundViolations.put(id, next);
            }
        }
        groundViolations.keySet().retainAll(present);
    }
}
