package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiProfileManagerTest {
    @Test
    void uniqueNameUsesWindowsStyleNumberingCaseInsensitively() {
        MultiProfile original = named("Farm");
        MultiProfile firstCopy = named("farm (1)");
        MultiProfile secondCopy = named("Farm (2)");

        assertEquals("Farm (3)", MultiProfileManager.uniqueName(
            "Farm", "new-id", List.of(original, firstCopy, secondCopy)));
    }

    @Test
    void uniqueNameDoesNotRenameTheProfileBeingUpdated() {
        MultiProfile profile = named("Main");

        assertEquals("Main", MultiProfileManager.uniqueName("Main", profile.id, List.of(profile)));
    }

    @Test
    void numberedNameContinuesFromItsOwnSuffix() {
        MultiProfile numbered = named("Farm (4)");

        assertEquals("Farm (5)", MultiProfileManager.uniqueName("Farm (4)", "new-id", List.of(numbered)));
    }

    private static MultiProfile named(String name) {
        MultiProfile profile = new MultiProfile();
        profile.name = name;
        return profile;
    }
}
