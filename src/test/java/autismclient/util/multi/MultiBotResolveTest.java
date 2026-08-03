package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MultiBotResolveTest {
    private static MultiManager.BotHandle bot(String accountId, String username) {
        return new MultiManager.BotHandle(accountId, username);
    }

    @Test
    void matchesUsernameCaseInsensitivelyAndTrimmed() {
        List<MultiManager.BotHandle> bots = List.of(bot("acc-1", "Notch"), bot("acc-2", "Herobrine"));
        assertEquals("acc-1", MultiManager.resolveBot(bots, "notch").accountId());
        assertEquals("acc-1", MultiManager.resolveBot(bots, "  NOTCH  ").accountId());
        assertEquals("acc-2", MultiManager.resolveBot(bots, "Herobrine").accountId());
    }

    @Test
    void fallsBackToAccountIdWhenNoUsernameMatches() {
        List<MultiManager.BotHandle> bots = List.of(bot("acc-1", "Notch"));
        assertEquals("acc-1", MultiManager.resolveBot(bots, "acc-1").accountId());
    }

    @Test
    void usernameWinsOverAnotherBotsAccountId() {

        List<MultiManager.BotHandle> bots = List.of(bot("Notch", "Alpha"), bot("acc-2", "Notch"));
        assertEquals("acc-2", MultiManager.resolveBot(bots, "Notch").accountId());
    }

    @Test
    void returnsNullForUnknownBlankOrNull() {
        List<MultiManager.BotHandle> bots = List.of(bot("acc-1", "Notch"));
        assertNull(MultiManager.resolveBot(bots, "nobody"));
        assertNull(MultiManager.resolveBot(bots, ""));
        assertNull(MultiManager.resolveBot(bots, "   "));
        assertNull(MultiManager.resolveBot(bots, null));
        assertNull(MultiManager.resolveBot(null, "notch"));
    }
}
