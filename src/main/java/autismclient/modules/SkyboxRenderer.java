package autismclient.modules;

import autismclient.util.AutismBufferSource;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismThemeTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class SkyboxRenderer {

    private static final float[][] FACES = {
        { -1, -1,  1,  -1,  1,  1,   1,  1,  1,   1, -1,  1 },
        {  1, -1,  1,   1,  1,  1,   1,  1, -1,   1, -1, -1 },
        {  1, -1, -1,   1,  1, -1,  -1,  1, -1,  -1, -1, -1 },
        { -1, -1, -1,  -1,  1, -1,  -1,  1,  1,  -1, -1,  1 },
        { -1,  1, -1,  -1,  1,  1,   1,  1,  1,   1,  1, -1 },
        { -1, -1, -1,  -1, -1,  1,   1, -1,  1,   1, -1, -1 },
    };

    private static final float[][] UVS = {
        { 0, 0,  0, 1,  1, 1,  1, 0 },
        { 0, 0,  0, 1,  1, 1,  1, 0 },
        { 0, 0,  0, 1,  1, 1,  1, 0 },
        { 0, 0,  0, 1,  1, 1,  1, 0 },
        { 0, 1,  0, 0,  1, 0,  1, 1 },
        { 0, 0,  0, 1,  1, 1,  1, 0 },
    };

    private static final Identifier[] FACE_TEXTURES = {
        face("panorama_0"), face("panorama_1"), face("panorama_2"),
        face("panorama_3"), face("panorama_5"), face("panorama_4"),
    };

    private static final float SCALE = 64.0F;

    private static final float TILT_X = 32.0F;

    private static final float SPIN_DEGREES_PER_SECOND = 0.286F;

    private static float spinAngleDeg;
    private static long spinLastNanos;

    private SkyboxRenderer() {}

    public static boolean isActive() {
        WorldModule w = world();
        return w != null && w.isEnabled() && w.bool("skybox") && Minecraft.getInstance().level != null;
    }

    public static void render(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        WorldModule w = world();
        if (mc.level == null || w == null) return;
        boolean recolor = w.bool("skybox-recolor");
        int customColor = ModuleRenderUtil.color(w, "skybox-color", 0xFFFF3B3B);
        float speed = (float) Math.max(0.5, Math.min(3.0, w.decimal("skybox-speed")));
        float spin = spinAngle(w.bool("skybox-spin"), speed);

        Matrix4f rotation = new Matrix4f()
            .rotationY(-spin * Mth.DEG_TO_RAD)
            .rotateX((float) Math.PI + TILT_X * Mth.DEG_TO_RAD);
        Vector3f corner = new Vector3f();
        try {
            AutismBufferSource bufferSource = new AutismBufferSource();
            for (int face = 0; face < FACES.length; face++) {

                Identifier texture = recolor
                    ? AutismThemeTextures.recoloredTo(FACE_TEXTURES[face],
                        autismclient.util.AutismTheme.active().colorOf(Channel.BACKDROP))
                    : AutismThemeTextures.recoloredTo(FACE_TEXTURES[face], customColor);
                if (texture == null) continue;
                VertexConsumer buffer = bufferSource.getBuffer(AutismRenderTypes.waypointDiscSeeThrough(texture));
                float[] verts = FACES[face];
                float[] uvs = UVS[face];
                for (int i = 0; i < 4; i++) {
                    rotation.transformPosition(verts[i * 3], verts[i * 3 + 1], verts[i * 3 + 2], corner);
                    buffer.addVertex(corner.x * SCALE, corner.y * SCALE, corner.z * SCALE)
                        .setUv(uvs[i * 2], uvs[i * 2 + 1])
                        .setColor(0xFFFFFFFF);
                }
            }
            bufferSource.uploadAndDraw();
        } catch (Throwable ignored) {

        }
    }

    private static float spinAngle(boolean spinning, float speed) {
        long now = System.nanoTime();
        if (spinLastNanos == 0L) spinLastNanos = now;
        float dt = (now - spinLastNanos) / 1_000_000_000.0F;
        spinLastNanos = now;
        if (spinning && dt > 0.0F) {
            spinAngleDeg = (spinAngleDeg + Math.min(dt, 0.25F) * SPIN_DEGREES_PER_SECOND * speed) % 360.0F;
        }
        return spinning ? spinAngleDeg : 0.0F;
    }

    private static Identifier face(String name) {
        return Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/background/" + name + ".png");
    }

    private static WorldModule world() {
        return ModuleRegistry.get("world") instanceof WorldModule w ? w : null;
    }
}
