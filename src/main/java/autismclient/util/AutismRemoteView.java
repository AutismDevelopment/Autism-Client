package autismclient.util;

import autismclient.modules.PackFreecamState;
import autismclient.util.multi.MultiTakeoverState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class AutismRemoteView {
    private static volatile Player target;
    private static volatile UUID targetUuid;
    private static volatile String targetName = "";
    private static volatile ClientLevel targetLevel;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean forcedFirstPerson;

    private AutismRemoteView() {
    }

    public record Result(boolean ok, String message) {
    }

    public static boolean isActive() {
        return targetUuid != null;
    }

    public static String targetName() {
        return targetName;
    }

    public static Result start(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null) {
            return new Result(false, "Join a world first.");
        }
        if (player == null || player.isRemoved() || player.level() != mc.level) {
            return new Result(false, "That player is not loaded.");
        }
        if (player == mc.player) return new Result(false, "You cannot remote-view yourself.");
        if (MultiTakeoverState.isActive()) return new Result(false, "Leave Multi POV first.");
        if (PackFreecamState.isActive()) return new Result(false, "Disable Freecam first.");

        if (isActive() && player.getUUID().equals(targetUuid)) {
            stop(false);
            return new Result(true, "Remote view stopped.");
        }
        if (isActive()) stop(false);

        previousCameraType = mc.options.getCameraType();
        forcedFirstPerson = !previousCameraType.isFirstPerson();
        target = player;
        targetUuid = player.getUUID();
        targetName = profileName(player);
        targetLevel = mc.level;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(player);
        return new Result(true, "Viewing " + targetName + ". Use "
            + autismclient.commands.AutismCommands.effectivePrefix() + "rv stop to leave.");
    }

    public static void stop(boolean notify) {
        Minecraft mc = Minecraft.getInstance();
        Player oldTarget = target;
        String oldName = targetName;
        boolean restorePerspective = forcedFirstPerson;
        CameraType restoreType = previousCameraType;
        clear();

        if (mc != null) {
            Entity camera = mc.getCameraEntity();
            if (mc.player != null && (camera == oldTarget || camera == null || camera.isRemoved())) {
                mc.setCameraEntity(mc.player);
            }

            if (restorePerspective && mc.options.getCameraType().isFirstPerson()) {
                mc.options.setCameraType(restoreType);
            }
        }
        if (notify && oldTarget != null) {
            AutismNotifications.show("Remote view ended" + (oldName.isBlank() ? "" : ": " + oldName), 0xFFFFC857);
        }
    }

    public static void tick() {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        Player current = target;
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null
            || mc.level != targetLevel || current == null || current.isRemoved()
            || !current.getUUID().equals(targetUuid) || mc.getCameraEntity() != current) {
            stop(true);
        }
    }

    public static Player viewedPlayer() {
        Minecraft mc = Minecraft.getInstance();
        Player current = target;
        return isActive() && current != null && !current.isRemoved() && mc != null
            && mc.level == targetLevel && mc.getCameraEntity() == current ? current : null;
    }

    public static Player firstPersonPlayer(Player multiPilot) {
        return multiPilot != null ? multiPilot : viewedPlayer();
    }

    public static Entity beginMainPlayerPick(Minecraft mc) {
        Player current = viewedPlayer();
        if (current == null || mc == null || mc.player == null || mc.getCameraEntity() != current) return null;
        mc.setCameraEntity(mc.player);
        return current;
    }

    public static void endMainPlayerPick(Minecraft mc, Entity restore) {
        if (mc == null || restore == null || !isActive() || restore != target || restore.isRemoved()) return;
        if (mc.getCameraEntity() == mc.player) mc.setCameraEntity(restore);
    }

    private static String profileName(Player player) {
        String name = player.getGameProfile() == null ? null : player.getGameProfile().name();
        if (name == null || name.isBlank()) name = player.getName().getString();
        return name == null || name.isBlank() ? "player" : name;
    }

    private static void clear() {
        target = null;
        targetUuid = null;
        targetName = "";
        targetLevel = null;
        previousCameraType = CameraType.FIRST_PERSON;
        forcedFirstPerson = false;
    }
}
