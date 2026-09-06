package autismclient.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismWaypointsTest {
    @TempDir
    static Path gameDir;

    @BeforeAll
    static void setUp() {
        ensureGameDir(gameDir);
    }

    @BeforeEach
    void cleanFile() {
        new File(autismclient.AutismClientAddon.FOLDER, "waypoints.json").delete();
    }

    private static synchronized void ensureGameDir(Path dir) {
        try {
            Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            Class<?> impl = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
            Field gd = impl.getDeclaredField("gameDir");
            gd.setAccessible(true);
            if (gd.get(loader) == null) {
                Method m = impl.getDeclaredMethod("setGameDir", Path.class);
                m.setAccessible(true);
                m.invoke(loader, dir);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("could not initialize a test game dir", t);
        }
    }

    private static AutismWaypoints freshLoad() throws Exception {
        Method load = AutismWaypoints.class.getDeclaredMethod("load");
        load.setAccessible(true);
        return (AutismWaypoints) load.invoke(null);
    }

    private static void flush(AutismWaypoints waypoints) throws Exception {
        Method flush = AutismWaypoints.class.getDeclaredMethod("flush");
        flush.setAccessible(true);
        flush.invoke(waypoints);
    }

    private static AutismWaypoints.Waypoint waypoint(String name, int x, int y, int z) {
        return new AutismWaypoints.Waypoint(name, x, y, z, 0xFF3BD7FF, 1_000_000L);
    }

    @Test
    void scopesAreFullyIsolated() throws Exception {
        AutismWaypoints w = freshLoad();
        w.add("server-a", waypoint("Home", 1, 64, 2));
        w.add("server-a", waypoint("Base", 10, 70, 20));
        w.add("server-b", waypoint("Farm", -5, 63, 9));

        assertEquals(2, w.list("server-a").size());
        assertEquals(1, w.list("server-b").size());
        assertEquals("Farm", w.list("server-b").get(0).name());
        assertTrue(w.list("server-c").isEmpty(), "an untouched scope stays empty");

        w.remove("server-a", "Home");
        assertEquals(1, w.list("server-a").size(), "removal hits only its own scope");
        assertEquals("Base", w.list("server-a").get(0).name());
        assertEquals(1, w.list("server-b").size(), "other scopes are untouched by removal");

        w.clear("server-a");
        assertTrue(w.list("server-a").isEmpty());
        assertEquals(1, w.list("server-b").size(), "clear must never cross scopes");
    }

    @Test
    void sameNameReplacesWithinScopeOnly() throws Exception {
        AutismWaypoints w = freshLoad();
        w.add("server-a", waypoint("Home", 1, 64, 2));
        w.add("server-a", waypoint("home", 50, 80, 60));
        w.add("server-b", waypoint("Home", -1, 60, -2));

        assertEquals(1, w.list("server-a").size(), "same-name add replaces within the scope");
        assertEquals(50, w.list("server-a").get(0).x());
        assertEquals(-1, w.list("server-b").get(0).x(), "the other scope's entry keeps its own coords");
    }

    @Test
    void persistenceRoundTrip() throws Exception {
        AutismWaypoints w = freshLoad();
        w.add("play.example.com", waypoint("Home", 100, 64, -200));
        w.add("New World", new AutismWaypoints.Waypoint("Death #1", 5, 70, 6, 0xFFFF3B3B, 1_700_000_000_000L));
        flush(w);

        AutismWaypoints reloaded = freshLoad();
        List<AutismWaypoints.Waypoint> server = reloaded.list("play.example.com");
        assertEquals(1, server.size(), "entries must survive a reload");
        AutismWaypoints.Waypoint home = server.get(0);
        assertEquals("Home", home.name());
        assertEquals(100, home.x());
        assertEquals(64, home.y());
        assertEquals(-200, home.z());
        assertEquals(0xFF3BD7FF, home.color());

        List<AutismWaypoints.Waypoint> world = reloaded.list("New World");
        assertEquals(1, world.size(), "singleplayer scopes persist independently");
        assertEquals(0xFFFF3B3B, world.get(0).color());
        assertEquals(1_700_000_000_000L, world.get(0).createdMs());
    }

    @Test
    void deathNamingIncrementsAndDedupesWithinWindow() throws Exception {
        AutismWaypoints w = freshLoad();
        long t0 = 1_000_000L;
        String scope = "server-a";

        AutismWaypoints.Waypoint first = w.addDeath(scope, 10, 64, 10, 0xFFFF3B3B, t0, 50);
        assertEquals("Death #1", first.name(), "first death in a scope starts at #1");

        assertNull(w.addDeath(scope, 10, 64, 10, 0xFFFF3B3B, t0 + 10_000L, 50),
            "identical position within 30s is deduped");
        assertEquals(1, w.list(scope).size(), "dedupe must not add entries");

        AutismWaypoints.Waypoint otherSpot = w.addDeath(scope, 99, 70, 99, 0xFFFF3B3B, t0 + 15_000L, 50);
        assertEquals("Death #2", otherSpot.name(), "a different position still increments the counter");

        AutismWaypoints.Waypoint afterWindow = w.addDeath(scope, 10, 64, 10, 0xFFFF3B3B,
            t0 + AutismWaypoints.DEATH_DEDUPE_MS + 1_000L, 50);
        assertEquals("Death #3", afterWindow.name(), "same position past the window adds again");
        assertEquals(3, w.list(scope).size());

        assertNull(w.list("server-b").stream().filter(wp -> wp.name().startsWith("Death")).findFirst().orElse(null),
            "death names increment per scope, not globally");
        assertEquals("Death #1", w.addDeath("server-b", 10, 64, 10, 0xFFFF3B3B, t0, 50).name(),
            "another scope counts its own deaths");
    }

    @Test
    void nextNameSkipsExistingNumbers() throws Exception {
        AutismWaypoints w = freshLoad();
        assertEquals("Waypoint #1", w.nextName("server-a", "Waypoint"));
        w.add("server-a", waypoint("Waypoint #1", 0, 64, 0));
        w.add("server-a", waypoint("Waypoint #7", 1, 64, 1));
        assertEquals("Waypoint #8", w.nextName("server-a", "Waypoint"), "next name is max existing + 1");
        assertEquals("Waypoint #1", w.nextName("server-b", "Waypoint"), "numbering is per scope");
    }

    @Test
    void renameSuccessPreservesDataAndBumpsRevision() throws Exception {
        AutismWaypoints w = freshLoad();
        w.add("server-a", new AutismWaypoints.Waypoint("Home", 5, 64, 7, 0xFF35D873, 123_456L));
        w.add("server-b", waypoint("Home", -1, 60, -2));
        long before = w.revision();

        assertTrue(w.rename("server-a", "Home", "Base"));
        assertTrue(w.revision() > before, "a real rename must bump the store revision");

        assertNull(w.find("server-a", "Home"), "old name is gone after a rename");
        AutismWaypoints.Waypoint renamed = w.find("server-a", "Base");
        assertEquals(5, renamed.x(), "rename keeps coords");
        assertEquals(64, renamed.y());
        assertEquals(7, renamed.z());
        assertEquals(0xFF35D873, renamed.color(), "rename keeps color");
        assertEquals(123_456L, renamed.createdMs(), "rename keeps the creation timestamp");
        assertEquals(-1, w.find("server-b", "Home").x(), "renames never touch other scopes");
    }

    @Test
    void renameTrimsTheNewName() throws Exception {
        AutismWaypoints w = freshLoad();
        w.add("server-a", waypoint("Home", 1, 64, 2));
        assertTrue(w.rename("server-a", "Home", "  Base  "));
        assertEquals("Base", w.list("server-a").get(0).name(), "the stored name is trimmed");
        assertEquals(1, w.find("server-a", "Base").x());
    }

    @Test
    void renameConflictCaseInsensitiveRejected() throws Exception {
        AutismWaypoints w = freshLoad();
        w.add("server-a", waypoint("Home", 1, 64, 2));
        w.add("server-a", waypoint("Base", 10, 70, 20));
        long before = w.revision();

        assertFalse(w.rename("server-a", "Home", "base"), "clash with a different waypoint must reject");
        assertEquals(before, w.revision(), "a rejected rename does not bump the revision");
        assertEquals(1, w.find("server-a", "Home").x(), "the original entry is untouched");
        assertEquals(10, w.find("server-a", "Base").x(), "the conflicting entry is untouched");
        assertEquals(2, w.list("server-a").size());
    }

    @Test
    void renameBlankRejected() throws Exception {
        AutismWaypoints w = freshLoad();
        w.add("server-a", waypoint("Home", 1, 64, 2));
        long before = w.revision();

        assertFalse(w.rename("server-a", "Home", ""));
        assertFalse(w.rename("server-a", "Home", "   "), "whitespace-only is blank after trim");
        assertEquals(before, w.revision());
        assertEquals(1, w.find("server-a", "Home").x(), "the original entry survives rejected renames");
    }

    @Test
    void renameSameNameIsNoopSuccess() throws Exception {
        AutismWaypoints w = freshLoad();
        w.add("server-a", waypoint("Home", 1, 64, 2));
        long before = w.revision();

        assertTrue(w.rename("server-a", "Home", "Home"), "same name is a no-op success");
        assertEquals(before, w.revision(), "a no-op rename does not bump the revision");
        assertEquals(1, w.list("server-a").size());
        assertEquals("Home", w.list("server-a").get(0).name());
    }

    @Test
    void setColorRecolorsAndListIsImmutable() throws Exception {
        AutismWaypoints w = freshLoad();
        w.add("server-a", waypoint("Home", 1, 64, 2));
        assertTrue(w.setColor("server-a", "Home", 0xFF35D873));
        assertEquals(0xFF35D873, w.colorOf("server-a", "Home", 0), "recolor applies");
        assertEquals(1, w.list("server-a").get(0).x(), "recolor keeps coords");
        assertEquals(0, w.colorOf("server-a", "Missing", 0), "unknown names yield the fallback");

        List<AutismWaypoints.Waypoint> snapshot = w.list("server-a");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear(),
            "list() hands out an immutable snapshot");
        w.add("server-a", waypoint("Second", 3, 64, 4));
        assertEquals(1, snapshot.size(), "earlier snapshots don't see later mutations");
    }

    @Test
    void deathMemoryCapKeepsNewestAndSparesUserWaypoints() throws Exception {
        AutismWaypoints w = freshLoad();
        String scope = "server-a";

        w.add(scope, new AutismWaypoints.Waypoint("Home", 0, 64, 0, 0xFF35D873, 500_000L));

        for (int i = 1; i <= 5; i++) {
            w.addDeath(scope, i, 64, i, 0xFFFF3B3B, 1_000_000L + i * 1_000L, 3);
        }

        List<AutismWaypoints.Waypoint> deaths =
            w.list(scope).stream().filter(AutismWaypoints.Waypoint::death).toList();
        assertEquals(3, deaths.size(), "only the newest 'cap' deaths are kept");
        assertNull(w.find(scope, "Death #1"), "the two oldest deaths are pruned");
        assertNull(w.find(scope, "Death #2"));
        assertTrue(deaths.stream().anyMatch(p -> p.name().equals("Death #5")), "the newest death survives");
        assertEquals(64, w.find(scope, "Home").y(), "user waypoints are never pruned by the cap");
        assertEquals(4, w.list(scope).size(), "3 capped deaths + 1 user waypoint");
    }

    @Test
    void pruneDeathsNoCapAndRePruneSparesUser() throws Exception {
        AutismWaypoints w = freshLoad();
        String scope = "server-a";
        w.add(scope, new AutismWaypoints.Waypoint("Home", 0, 64, 0, 0xFF35D873, 500_000L));

        for (int i = 1; i <= 4; i++) {
            w.addDeath(scope, i, 64, i, 0xFFFF3B3B, 1_000_000L + i * 1_000L, 0);
        }
        assertEquals(5, w.list(scope).size(), "no cap keeps every death (4 deaths + 1 user waypoint)");

        w.pruneDeaths(scope, 2);
        assertEquals(2, w.list(scope).stream().filter(AutismWaypoints.Waypoint::death).count(),
            "a re-prune trims to the newest deaths");
        assertEquals(64, w.find(scope, "Home").y(), "a re-prune never removes user waypoints");
        assertEquals(3, w.list(scope).size(), "2 deaths + 1 user waypoint after the re-prune");
    }
}
