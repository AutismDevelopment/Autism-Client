package autismclient.modules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import autismclient.util.mm.MmBlobs;
import org.junit.jupiter.api.Test;

class ModuleShareGuardTest {

    private static class PlainModule extends Module {
        PlainModule() {
            super("test-plain", "TestPlain", ModuleCategory.MISC, "Shareable test module.");
        }
    }

    private static class SecretModule extends Module {
        SecretModule() {
            super("test-secret", "TestSecret", ModuleCategory.MISC, "Credential-bearing test module.");
        }

        @Override
        public boolean settingsShareable() {
            return false;
        }
    }

    @Test
    void modulesAreShareableByDefault() {
        assertTrue(new PlainModule().settingsShareable());
    }

    @Test
    void aProtectedModuleCannotBeCapturedForSharing() {
        assertNull(MmBlobs.captureModule(new SecretModule()),
            "a credential-bearing module must never produce a share blob");
    }

    @Test
    void anOrdinaryModuleIsStillCapturable() {

        assertNotNull(MmBlobs.captureModule(new PlainModule()));
    }

    @Test
    void captureRejectsNull() {
        assertNull(MmBlobs.captureModule(null));
    }

    @Test
    void autoLoginDeclaresTheOptOut() throws Exception {

        assertTrue(AutoLoginModule.class.getDeclaredMethod("settingsShareable").getDeclaringClass()
                == AutoLoginModule.class,
            "AutoLoginModule must override settingsShareable() -- it stores a server password");
    }
}
