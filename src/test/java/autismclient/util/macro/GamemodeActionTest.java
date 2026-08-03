package autismclient.util.macro;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GamemodeActionTest {
    private static FakeGamemodeAction load(CompoundTag tag) {
        FakeGamemodeAction action = new FakeGamemodeAction();
        action.fromTag(tag);
        return action;
    }

    @Test
    void methodSurvivesARoundTrip() {
        FakeGamemodeAction action = new FakeGamemodeAction();
        action.mode = FakeGamemodeAction.Mode.CREATIVE;
        action.method = FakeGamemodeAction.Method.REAL;
        FakeGamemodeAction loaded = load(action.toTag());
        assertEquals(FakeGamemodeAction.Mode.CREATIVE, loaded.mode);
        assertEquals(FakeGamemodeAction.Method.REAL, loaded.method);
    }

    @Test
    void macroSavedBeforeTheMethodFieldStaysFake() {

        CompoundTag legacy = new CompoundTag();
        legacy.putString("type", "FAKE_GAMEMODE");
        legacy.putString("mode", "CREATIVE");
        FakeGamemodeAction loaded = load(legacy);
        assertEquals(FakeGamemodeAction.Mode.CREATIVE, loaded.mode);
        assertEquals(FakeGamemodeAction.Method.FAKE, loaded.method);
    }

    @Test
    void unknownMethodFallsBackToFake() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", "SPECTATOR");
        tag.putString("method", "NOT_A_METHOD");
        assertEquals(FakeGamemodeAction.Method.FAKE, load(tag).method);
    }

    @Test
    void displayNameShowsTheMethodExceptForReset() {
        FakeGamemodeAction action = new FakeGamemodeAction();
        action.mode = FakeGamemodeAction.Mode.CREATIVE;
        action.method = FakeGamemodeAction.Method.REAL;
        assertEquals("GM: Creative (Real)", action.getDisplayName());
        action.method = FakeGamemodeAction.Method.FAKE;
        assertEquals("GM: Creative (Fake)", action.getDisplayName());
        action.mode = FakeGamemodeAction.Mode.RESET;
        assertEquals("GM: Reset", action.getDisplayName());
    }
}
