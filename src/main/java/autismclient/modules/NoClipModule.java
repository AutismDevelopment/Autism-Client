package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class NoClipModule extends Module {
    private static NoClipModule instance;

    private boolean noClipSet;
    private Entity forcedVehicle;

    private volatile boolean setbackPending;

    public NoClipModule() {
        super("no-clip", "NoClip", ModuleCategory.MOVEMENT, "Move through blocks.");
        instance = this;

        add(new DoubleSetting("speed", "Speed", 0.32, 0.1, 0.4, 0.01)
            .description("Movement speed.").build());
        add(new BoolSetting("only-in-vehicle", "Only In Vehicle", false)
            .description("Only while riding.").build());
        add(new BoolSetting("disable-on-setback", "Disable On Setback", true)
            .description("Disable on server setback.").build());
    }

    @Override
    public void preMovementTick() {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        if (setbackPending) {
            setbackPending = false;
            disableWithToggleMessage("NoClip disabled: server set you back.");
            return;
        }

        LocalPlayer player = MC.player;
        if (player == null) return;
        if (paused()) {
            if (noClipSet) restore();
            return;
        }

        noClipSet = true;
        player.noPhysics = true;
        player.fallDistance = 0.0;
        player.setOnGround(false);

        double speed = decimal("speed");
        Entity vehicle = player.getControlledVehicle();
        releaseForcedVehicle(vehicle);
        if (vehicle != null) {
            vehicle.noPhysics = true;
            forcedVehicle = vehicle;

            if (!ModuleRegistry.isModuleEnabled("boat-fly")) applyStrafe(vehicle, player, speed);
        } else {
            applyStrafe(player, player, speed);
        }
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {

        if (packet instanceof ClientboundPlayerPositionPacket && bool("disable-on-setback") && !paused()) {
            setbackPending = true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        noClipSet = false;
        setbackPending = false;
    }

    @Override
    public void onDisable() {
        restore();
    }

    @Override
    public void onGameLeft() {
        restore();
    }

    public static boolean holdsNoPhysics() {
        NoClipModule module = instance;
        return module != null && module.isEnabled() && !PackHideState.isHardLocked() && !module.paused();
    }

    private boolean paused() {

        LocalPlayer player = MC.player;
        return bool("only-in-vehicle") && (player == null || player.getControlledVehicle() == null);
    }

    private void restore() {
        noClipSet = false;
        if (MC.player != null) {
            MC.player.noPhysics = false;
            Entity vehicle = MC.player.getControlledVehicle();
            if (vehicle != null) vehicle.noPhysics = false;
        }
        releaseForcedVehicle(null);
    }

    private void releaseForcedVehicle(Entity keep) {
        if (forcedVehicle == null || forcedVehicle == keep) return;
        forcedVehicle.noPhysics = false;
        forcedVehicle = null;
    }

    private static void applyStrafe(Entity target, LocalPlayer player, double speed) {
        double y = MC.options.keyJump.isDown() ? speed
            : MC.options.keyShift.isDown() ? -speed
            : 0.0;
        target.setDeltaMovement(ModuleMovementUtil.withStrafe(player, new Vec3(0.0, y, 0.0), speed));
    }
}
