package autismclient.modules;

import autismclient.util.oresim.AutismOreSimEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModuleOreSimStatusTest {

    @Test
    void reportsExactTargetChunkProgress() {
        assertEquals("7/49 chunks",
            ModuleOreSim.progressInfo(new AutismOreSimEngine.TargetProgress(11L, 7, 49)));
        assertEquals("49/49 chunks",
            ModuleOreSim.progressInfo(new AutismOreSimEngine.TargetProgress(11L, 49, 49)));
    }
}
