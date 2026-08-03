package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class PublicInteractionLoggingTest {
    @Test
    void productionBytecodeContainsNoTemporaryInteractionTraces() throws IOException {
        String bytecode = classBytes("autismclient/util/multi/MultiPilot.class")
            + classBytes("autismclient/util/multi/MultiSession.class")
            + classBytes("autismclient/mixin/AutismClientConnectionMixin.class");

        assertFalse(bytecode.contains("[Multi POV use]"));
        assertFalse(bytecode.contains("[Vanilla entity use]"));
        assertFalse(bytecode.contains("[Multi load]"));
        assertFalse(bytecode.contains("tracePovUse"));
        assertFalse(bytecode.contains("POV pilot use failed"));
    }

    private static String classBytes(String resource) throws IOException {
        try (InputStream input = PublicInteractionLoggingTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
