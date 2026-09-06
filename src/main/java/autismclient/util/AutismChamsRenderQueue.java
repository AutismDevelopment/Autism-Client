package autismclient.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.ArrayList;
import java.util.List;

public final class AutismChamsRenderQueue {
    private static final List<BodySubmit> BODIES = new ArrayList<>();
    private static final List<ModelSubmit> LAYERS = new ArrayList<>();

    private static final AutismBufferSource.Holder BUFFERS =
        new AutismBufferSource.Holder(RenderType.BIG_BUFFER_SIZE);

    private AutismChamsRenderQueue() {
    }

    public static void clear() {
        BODIES.clear();
        LAYERS.clear();
    }

    public static boolean hasPending() {
        return !BODIES.isEmpty();
    }

    public static void submitBody(Model<?> model, Object state, PoseStack.Pose pose,
                                  RenderType visibleType, RenderType occludedType,
                                  int light, int overlay, int visibleColor, int occludedColor,
                                  TextureAtlasSprite sprite) {
        BODIES.add(new BodySubmit(model, state, pose, visibleType, occludedType,
            light, overlay, visibleColor, occludedColor, sprite));
    }

    public static void submitLayer(Model<?> model, Object state, PoseStack.Pose pose, RenderType type,
                                   int light, int overlay, int color, TextureAtlasSprite sprite) {
        LAYERS.add(new ModelSubmit(model, state, pose, type, light, overlay, color, sprite));
    }

    public static void flush(PoseStack framePose) {
        if (BODIES.isEmpty()) return;

        List<BodySubmit> bodies = List.copyOf(BODIES);
        List<ModelSubmit> layers = List.copyOf(LAYERS);
        clear();

        AutismBufferSource buffers = BUFFERS.get();

        for (BodySubmit body : bodies) {
            draw(buffers, framePose, body.model(), body.state(), body.pose(), body.occludedType(),
                body.light(), body.overlay(), body.occludedColor(), body.sprite());
        }

        for (BodySubmit body : bodies) {
            draw(buffers, framePose, body.model(), body.state(), body.pose(), body.visibleType(),
                body.light(), body.overlay(), body.visibleColor(), body.sprite());
        }

        for (ModelSubmit layer : layers) {
            draw(buffers, framePose, layer.model(), layer.state(), layer.pose(), layer.type(),
                layer.light(), layer.overlay(), layer.color(), layer.sprite());
        }

        buffers.uploadAndDraw();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void draw(AutismBufferSource buffers, PoseStack framePose, Model<?> model, Object state,
                             PoseStack.Pose modelPose, RenderType type, int light, int overlay, int color,
                             TextureAtlasSprite sprite) {
        framePose.pushPose();
        try {

            framePose.mulPose(modelPose.pose());
            VertexConsumer consumer = buffers.getBuffer(type);
            if (sprite != null) consumer = sprite.wrap(consumer);

            Model rawModel = model;
            rawModel.setupAnim(state);
            rawModel.renderToBuffer(framePose, consumer, light, overlay, color);
        } finally {
            framePose.popPose();
        }
    }

    private record BodySubmit(Model<?> model, Object state, PoseStack.Pose pose,
                              RenderType visibleType, RenderType occludedType,
                              int light, int overlay, int visibleColor, int occludedColor,
                              TextureAtlasSprite sprite) {
    }

    private record ModelSubmit(Model<?> model, Object state, PoseStack.Pose pose, RenderType type,
                               int light, int overlay, int color, TextureAtlasSprite sprite) {
    }
}