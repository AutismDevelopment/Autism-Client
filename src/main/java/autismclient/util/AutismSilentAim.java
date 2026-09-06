package autismclient.util;

import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.ScaffoldModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public final class AutismSilentAim {

    public interface Owner {

        boolean silentCorrectionApplies();
    }

    private AutismSilentAim() {
    }

    public static boolean scaffoldOwnsRotation() {
        return ScaffoldModule.reservesTellyInput() || ScaffoldModule.hasActiveSilentMovementRotation();
    }

    public static AutismRotationUtil.Rotation packetRotation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        if (rotation == null) return null;
        Module module = ModuleRegistry.get(AutismKillAuraRotation.currentOwner());
        return module instanceof Owner owner && owner.silentCorrectionApplies() ? rotation : null;
    }

    public static Input modifyMovementInput(ClientInput source, Input input) {
        Minecraft mc = Minecraft.getInstance();
        if (input == null || mc == null || mc.player == null || mc.player.input != source) return input;
        AutismRotationUtil.Rotation rotation = packetRotation();
        if (rotation == null) return input;

        return ScaffoldModule.transformSilentMovementInput(input, mc.player.getYRot(), rotation.yaw());
    }

    public static float correctedMovementYaw(Entity entity, float vanillaYaw) {
        AutismRotationUtil.Rotation rotation = activeMovementRotation(entity);
        return rotation == null ? vanillaYaw : rotation.yaw();
    }

    public static float outgoingMovementYaw(LocalPlayer player, float vanillaYaw) {
        return correctedMovementYaw(player, vanillaYaw);
    }

    public static float outgoingMovementPitch(LocalPlayer player, float vanillaPitch) {
        AutismRotationUtil.Rotation rotation = activeMovementRotation(player);
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    public static Vec3 silentViewVector(LocalPlayer player, Vec3 vanillaVector) {
        AutismRotationUtil.Rotation rotation = activeMovementRotation(player);
        return rotation == null ? vanillaVector
            : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
    }

    public static Vec3 correctedJumpImpulse(LivingEntity entity, Vec3 vanillaImpulse) {
        AutismRotationUtil.Rotation rotation = activeMovementRotation(entity);
        if (rotation == null) return vanillaImpulse;
        float yaw = rotation.yaw() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * 0.2F, vanillaImpulse.y, Mth.cos(yaw) * 0.2F);
    }

    public static float correctedFallFlyingPitch(LivingEntity entity, float vanillaPitch) {
        AutismRotationUtil.Rotation rotation = activeMovementRotation(entity);
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    public static Vec3 correctedFallFlyingLook(LivingEntity entity, Vec3 vanillaLook) {
        AutismRotationUtil.Rotation rotation = activeMovementRotation(entity);
        return rotation == null ? vanillaLook
            : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
    }

    private static AutismRotationUtil.Rotation activeMovementRotation(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || mc == null || entity != mc.player) return null;
        return packetRotation();
    }

    public static AutismRotationUtil.Rotation activeOutgoingRotation(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == null || mc == null || player != mc.player) return null;
        AutismRotationUtil.Rotation scaffold = ScaffoldModule.activeOutgoingRotation();
        return scaffold != null ? scaffold : packetRotation();
    }

    public static AutismRotationUtil.Rotation activeUseItemRotation(LocalPlayer player) {
        return activeOutgoingRotation(player);
    }
}
