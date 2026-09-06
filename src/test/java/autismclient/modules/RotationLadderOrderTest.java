package autismclient.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import autismclient.util.AutismKillAuraRotation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class RotationLadderOrderTest {

    private static final String[] LADDER_IDS = {
        AutismKillAuraRotation.OWNER_BED_DEFENDER,
        AutismKillAuraRotation.OWNER_SURROUND,
        AutismKillAuraRotation.OWNER_ANCHOR_AURA,
        AutismKillAuraRotation.OWNER_CRYSTAL_AURA,
        AutismKillAuraRotation.OWNER_AUTO_TRAP,
        AutismKillAuraRotation.OWNER_KILL_AURA,
        AutismKillAuraRotation.OWNER_AUTO_FARM
    };

    private static final int[] LADDER_PRIORITIES = {
        AutismKillAuraRotation.PRIORITY_BED_DEFENDER,
        AutismKillAuraRotation.PRIORITY_SURROUND,
        AutismKillAuraRotation.PRIORITY_ANCHOR_AURA,
        AutismKillAuraRotation.PRIORITY_CRYSTAL_AURA,
        AutismKillAuraRotation.PRIORITY_AUTO_TRAP,
        AutismKillAuraRotation.PRIORITY_KILL_AURA,
        AutismKillAuraRotation.PRIORITY_AUTO_FARM
    };

    private static final String[] LADDER_CLASSES = {
        "BedDefenderModule", "SurroundModule", "AnchorAuraModule", "CrystalAuraModule",
        "AutoTrapModule", "KillAuraModule", "AutoFarmModule"
    };

    @Test
    void rungsAreStrictlyDescending() {
        for (int i = 1; i < LADDER_PRIORITIES.length; i++) {
            assertTrue(LADDER_PRIORITIES[i] < LADDER_PRIORITIES[i - 1],
                LADDER_IDS[i] + " must sit strictly below " + LADDER_IDS[i - 1]
                    + "; an equal rung makes the winner depend on dispatch order");
        }
    }

    @Test
    void registrationOrderMirrorsTheLadder() throws IOException {
        String source = Files.readString(sourceFile("main", "modules/BuiltinModules.java"));
        List<String> registered = new ArrayList<>();
        Matcher matcher = Pattern.compile("ModuleRegistry\\.register\\(new (\\w+)\\(\\)\\)").matcher(source);
        while (matcher.find()) {
            for (String wanted : LADDER_CLASSES) {
                if (wanted.equals(matcher.group(1))) registered.add(wanted);
            }
        }
        assertEquals(List.of(LADDER_CLASSES), registered,
            "registration order is dispatch order; it must descend by rotation priority");
    }

    @Test
    void noModuleDeclaresItsOwnRung() throws IOException {
        for (String className : LADDER_CLASSES) {
            Path file = sourceFile("main", "modules/" + className + ".java");
            if (!Files.exists(file)) continue;
            assertFalse(Files.readString(file).contains("int ROTATION_PRIORITY"),
                className + " must use AutismKillAuraRotation.PRIORITY_* instead of a local rung");
        }
    }

    @Test
    void scaffoldRegistersAfterTheLadder() throws IOException {
        String source = Files.readString(sourceFile("main", "modules/BuiltinModules.java"));
        int killAura = source.indexOf("ModuleRegistry.register(new KillAuraModule())");
        int scaffold = source.indexOf("ModuleRegistry.register(new ScaffoldModule())");
        assertTrue(killAura >= 0 && scaffold > killAura,
            "ScaffoldModule must register after KillAuraModule; combat modules read its this-tick state");
    }

    private static Path sourceFile(String sourceSet, String relative) {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("src/" + sourceSet + "/java/autismclient/" + relative);
            if (Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("could not locate " + relative + " from " + Paths.get("").toAbsolutePath());
    }
}
