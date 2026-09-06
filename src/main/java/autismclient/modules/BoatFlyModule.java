

package autismclient.modules;
import autismclient.api.module.*;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class BoatFlyModule extends Module {
    private static BoatFlyModule instance;

    public BoatFlyModule() {
        super("boat-fly", "BoatFly", ModuleCategory.MOVEMENT, "Lets ridden vehicles fly.");
        instance = this;

        add(new BoolSetting("change-forward-speed", "Change Forward Speed", false)
            .description("Use custom forward speed.").build());
        add(new DoubleSetting("forward-speed", "Forward Speed", 1.0, 0.05, 5.0, 0.05)
            .visibleWhen(() -> bool("change-forward-speed"))
            .description("Sets forward speed.").build());
        add(new DoubleSetting("upward-speed", "Upward Speed", 0.3, 0.0, 5.0, 0.05)
            .description("Sets upward speed.").build());
    }

    static Vec3 modifyVehicleMovement(Entity vehicle, MoverType type, Vec3 movement) {
        BoatFlyModule module = instance;
        if (module == null || !module.isEnabled() || PackHideState.isHardLocked()) return movement;
        if (type != MoverType.SELF || !isControlledVehicle(vehicle)) return movement;

        double motionX = movement.x;
        double motionY = 0.0;
        double motionZ = movement.z;

        if (MC.options.keyJump.isDown()) {
            motionY = module.decimal("upward-speed");
        } else if (MC.options.keySprint.isDown()) {
            motionY = movement.y;
        }

        if (MC.options.keyUp.isDown() && module.bool("change-forward-speed")) {
            double speed = module.decimal("forward-speed");
            float yawRadians = vehicle.getYRot() * Mth.DEG_TO_RAD;
            motionX = Mth.sin(-yawRadians) * speed;
            motionZ = Mth.cos(yawRadians) * speed;
        }

        return new Vec3(motionX, motionY, motionZ);
    }

    private static boolean isControlledVehicle(Entity vehicle) {
        return MC.player != null
            && vehicle != null
            && MC.player.getVehicle() == vehicle;
    }
}
