package autismclient.util.mm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MmPrefsTest {
    @TempDir
    static Path gameDir;

    private File prefsFile;

    @BeforeAll
    static void setUp() {
        MmTestEnv.ensureGameDir(gameDir);
    }

    @BeforeEach
    void cleanFile() {
        prefsFile = new File(autismclient.AutismClientAddon.FOLDER, "mm_prefs.properties");
        prefsFile.delete();
    }

    private static MmPrefs freshLoad() throws Exception {
        Method load = MmPrefs.class.getDeclaredMethod("load");
        load.setAccessible(true);
        return (MmPrefs) load.invoke(null);
    }

    private static void flush(MmPrefs p) throws Exception {
        Method flush = MmPrefs.class.getDeclaredMethod("flush");
        flush.setAccessible(true);
        flush.invoke(p);
    }

    @Test
    void autoJoinPublicDefaultsFalseAndPersists() throws Exception {
        MmPrefs p = freshLoad();
        assertFalse(p.autoJoinPublic(), "auto-join must default to off");
        p.setAutoJoinPublic(true);
        flush(p);
        assertTrue(freshLoad().autoJoinPublic(), "auto-join=true must survive a reload");
    }

    @Test
    void lobbyShareDefaultsFalsePersistsAndIsIndependentPerLobby() throws Exception {
        MmPrefs p = freshLoad();
        assertFalse(p.lobbyShareServer("lobbyA"));
        assertFalse(p.lobbyShareLocation("lobbyA"));
        p.setLobbyShareServer("lobbyA", true);
        p.setLobbyShareLocation("lobbyB", true);
        flush(p);

        MmPrefs r = freshLoad();
        assertTrue(r.lobbyShareServer("lobbyA"));
        assertFalse(r.lobbyShareLocation("lobbyA"), "the server flag must not leak onto location");
        assertFalse(r.lobbyShareServer("lobbyB"), "different lobbyIds are independent");
        assertTrue(r.lobbyShareLocation("lobbyB"));
        assertFalse(r.lobbyShareServer("lobbyC"), "an untouched lobby stays at the default");
        assertFalse(r.lobbyShareLocation("lobbyC"));
    }

    @Test
    void settingBackToFalseRemovesTheKey() throws Exception {
        MmPrefs p = freshLoad();
        p.setLobbyShareServer("lobbyA", true);
        p.setLobbyShareServer("lobbyA", false);
        flush(p);

        assertFalse(freshLoad().lobbyShareServer("lobbyA"));
        String stored = Files.readString(prefsFile.toPath());
        assertFalse(stored.contains("lobby."), "defaulted values are not persisted:\n" + stored);
    }

    @Test
    void lobbyIdsWithSeparatorsRoundTrip() throws Exception {
        String weird = "0123456789abcdef:official lobby";
        MmPrefs p = freshLoad();
        p.setLobbyShareServer(weird, true);
        flush(p);
        assertTrue(freshLoad().lobbyShareServer(weird), "escaping must keep the id intact");
    }
}
