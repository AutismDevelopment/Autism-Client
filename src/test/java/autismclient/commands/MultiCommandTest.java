package autismclient.commands;

import autismclient.commands.impl.MultiCommand;
import autismclient.util.multi.MultiProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MultiCommandTest {
    private static MultiProfile named(String name) {
        MultiProfile p = new MultiProfile();
        p.name = name;
        return p;
    }

    @Test
    void resolveByNameIsCaseInsensitiveFirstMatch() {
        MultiProfile a = named("Shop Farm");
        MultiProfile b = named("shop farm");
        MultiProfile c = named("Other");
        List<MultiProfile> profiles = new ArrayList<>(List.of(a, b, c));
        assertSame(a, MultiCommand.resolveByName(profiles, "shop farm"));
        assertSame(a, MultiCommand.resolveByName(profiles, "  SHOP FARM  "));
        assertSame(c, MultiCommand.resolveByName(profiles, "other"));
    }

    @Test
    void resolveByNameReturnsNullWhenMissingOrBlank() {
        List<MultiProfile> profiles = List.of(named("Alpha"));
        assertNull(MultiCommand.resolveByName(profiles, "beta"));
        assertNull(MultiCommand.resolveByName(profiles, ""));
        assertNull(MultiCommand.resolveByName(profiles, "   "));
        assertNull(MultiCommand.resolveByName(profiles, null));
        assertNull(MultiCommand.resolveByName(null, "alpha"));
    }
}
