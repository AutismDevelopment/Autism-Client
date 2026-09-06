package autismclient.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class AutismWorldGeometry {
    private AutismWorldGeometry() {
    }

    public static void line(PoseStack.Pose pose, VertexConsumer buffer, Vec3 start, Vec3 end, int color, float width) {
        if (start == null || end == null) return;
        line(pose, buffer, start.x, start.y, start.z, end.x, end.y, end.z, color, width);
    }

    public static void line(PoseStack.Pose pose, VertexConsumer buffer,
                            double x1, double y1, double z1,
                            double x2, double y2, double z2,
                            int color, float width) {
        Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1));
        if (normal.lengthSquared() <= 1.0E-8F) return;
        normal.normalize();
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, normal).setLineWidth(width);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color).setNormal(pose, normal).setLineWidth(width);
    }
}
