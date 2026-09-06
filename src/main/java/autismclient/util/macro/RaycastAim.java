package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public interface RaycastAim {
    boolean isRaycast();

    void setRaycast(boolean value);

    Target raycastTarget(Minecraft mc);

    record Target(BlockPos block, Entity entity, Vec3 point) {
        public static Target ofBlock(BlockPos pos) {
            return pos == null ? null : new Target(pos, null, null);
        }

        public static Target ofEntity(Entity entity) {
            return entity == null ? null : new Target(null, entity, null);
        }

        public static Target ofPoint(Vec3 point) {
            return point == null ? null : new Target(null, null, point);
        }
    }
}
