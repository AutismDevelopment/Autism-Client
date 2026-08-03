package net.minecraft.client.renderer.rendertype;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AutismChamsRenderTypes {
    private static final Map<Identifier, RenderType> VISIBLE = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> OCCLUDED = new ConcurrentHashMap<>();

    private AutismChamsRenderTypes() {
    }

    public static Identifier textureOf(RenderSetup setup) {
        try {
            RenderSetup.TextureBinding binding = setup.textures.get("Sampler0");
            return binding == null ? null : binding.location();
        } catch (Throwable t) {
            return null;
        }
    }

    public static RenderType visible(Identifier texture, RenderPipeline pipeline) {
        return VISIBLE.computeIfAbsent(texture, tex -> RenderType.create(
            "autism_chams_visible",
            RenderSetup.builder(pipeline)
                .withTexture("Sampler0", tex)
                .useLightmap()
                .useOverlay()
                .createRenderSetup()
        ));
    }

    public static RenderType occluded(Identifier texture, RenderPipeline pipeline) {
        return OCCLUDED.computeIfAbsent(texture, tex -> RenderType.create(
            "autism_chams_occluded",
            RenderSetup.builder(pipeline)
                .withTexture("Sampler0", tex)
                .useLightmap()
                .useOverlay()
                .createRenderSetup()
        ));
    }
}
