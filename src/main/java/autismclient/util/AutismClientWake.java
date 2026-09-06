package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;

public final class AutismClientWake {
    private static final double LEAVE_DISTANCE_SQR = 25.0;
    private static volatile boolean active;
    private static volatile long bedPos = Long.MIN_VALUE;

    private AutismClientWake() {}

    public static void wake() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        bedPos = player.getSleepingPos().map(BlockPos::asLong).orElse(Long.MIN_VALUE);
        active = true;
        forceAwake(player);
        closeBedScreen(mc);
    }

    public static void reset() {
        active = false;
        bedPos = Long.MIN_VALUE;
    }

    public static boolean isActive() {
        return active;
    }

    public static void tick(Minecraft mc) {
        if (!active) return;
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null) { reset(); return; }

        if (player.isSleeping()) {
            long current = player.getSleepingPos().map(BlockPos::asLong).orElse(Long.MIN_VALUE);
            if (current != bedPos) {

                reset();
                return;
            }

            forceAwake(player);
            closeBedScreen(mc);
        } else if (bedPos != Long.MIN_VALUE
                && player.blockPosition().distSqr(BlockPos.of(bedPos)) > LEAVE_DISTANCE_SQR) {

            reset();
        }
    }

    private static void forceAwake(LocalPlayer player) {
        player.clearSleepingPos();
        player.setPose(Pose.STANDING);
    }

    private static void closeBedScreen(Minecraft mc) {
        if (mc.gui.screen() instanceof InBedChatScreen) {
            mc.gui.setScreen(null);
        }
    }
}
