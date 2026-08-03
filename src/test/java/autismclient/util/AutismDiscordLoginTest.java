package autismclient.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismDiscordLoginTest {
    private static boolean revocation(String code) throws Exception {
        Method m = AutismDiscordLogin.class.getDeclaredMethod("isDefinitiveRevocation", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, code);
    }

    @Test
    void definitiveRevocationsSignOut() throws Exception {
        assertTrue(revocation("bad_session"));
        assertTrue(revocation("expired_session"));
        assertTrue(revocation("banned"));
        assertTrue(revocation("not_member"));
    }

    @Test
    void transientErrorsKeepTheSession() throws Exception {

        assertFalse(revocation("proof_required"));
        assertFalse(revocation("warming_up"));
        assertFalse(revocation("rate_limited"));
        assertFalse(revocation("old_version"));
        assertFalse(revocation("bad_request"));
        assertFalse(revocation("bad_code"));
        assertFalse(revocation("cancelled"));
        assertFalse(revocation("network"));
        assertFalse(revocation(""));
        assertFalse(revocation(null));
        assertFalse(revocation("some_future_unknown_code"));
    }
}
