package autismclient.util.mm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LobbyDirectoryOrderTest {
    @TempDir
    static Path gameDir;

    @BeforeAll
    static void setUp() {
        MmTestEnv.ensureGameDir(gameDir);
    }

    private static LobbyListing listing(String name, boolean official) {
        LobbyListing l = new LobbyListing("id-" + name, new byte[8]);
        l.name = name;
        l.official = official;
        return l;
    }

    @Test
    void officialSortsFirstThenByName() {
        LobbyListing official = listing("zeta", true);
        LobbyListing alpha = listing("alpha", false);
        LobbyListing beta = listing("Beta", false);
        List<LobbyListing> out = new ArrayList<>(List.of(beta, official, alpha));
        out.sort(LobbyListing.DIRECTORY_ORDER);
        assertEquals(List.of(official, alpha, beta), out);
    }

    @Test
    void severalOfficialsKeepNameOrderAmongThemselves() {
        LobbyListing officialB = listing("official b", true);
        LobbyListing officialA = listing("Official A", true);
        LobbyListing plain = listing("aaa", false);
        List<LobbyListing> out = new ArrayList<>(List.of(officialB, plain, officialA));
        out.sort(LobbyListing.DIRECTORY_ORDER);
        assertEquals(List.of(officialA, officialB, plain), out);
    }

    @Test
    void orderComesFromTheFlagNotTheName() {
        LobbyListing imposter = listing("Official", false);
        LobbyListing real = listing("zzz", true);
        LobbyListing plain = listing("aaa", false);
        List<LobbyListing> out = new ArrayList<>(List.of(imposter, real, plain));
        out.sort(LobbyListing.DIRECTORY_ORDER);
        assertEquals(List.of(real, plain, imposter), out);
    }

    @Test
    void nameSortStaysStableForEqualNames() {
        LobbyListing first = listing("same", false);
        LobbyListing second = listing("SAME", false);
        List<LobbyListing> out = new ArrayList<>(List.of(first, second));
        out.sort(LobbyListing.DIRECTORY_ORDER);
        assertEquals(List.of(first, second), out);
    }
}
