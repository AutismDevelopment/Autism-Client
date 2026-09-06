package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.world.level.GameType;

public final class AutismGamemode {
    private AutismGamemode() {
    }

    public static AutismFakeGamemode.Result real(GameType mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.connection == null) {
            return AutismFakeGamemode.Result.fail("Not connected.");
        }
        if (mode == null) {
            return AutismFakeGamemode.Result.fail("Unknown game mode.");
        }

        boolean clearedFake = AutismFakeGamemode.snapshot().fakeActive();
        if (clearedFake) AutismFakeGamemode.reset();
        mc.player.connection.send(new ServerboundChangeGameModePacket(mode));
        return AutismFakeGamemode.Result.ok("Requested real gamemode: " + mode.getName()
            + (clearedFake ? " (fake cleared)" : ""));
    }
}
