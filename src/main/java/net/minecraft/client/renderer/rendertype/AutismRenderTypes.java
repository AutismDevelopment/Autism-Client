package net.minecraft.client.renderer.rendertype;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.function.Function;

public final class AutismRenderTypes {
    private static final RenderPipeline STORAGE_ESP_FILL_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.DEBUG_FILLED_BOX)
        .withLocation(Identifier.fromNamespaceAndPath("autismclient", "pipeline/storage_esp_fill_see_through"))
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderPipeline STORAGE_ESP_LINES_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.LINES)
        .withLocation(Identifier.fromNamespaceAndPath("autismclient", "pipeline/storage_esp_lines_see_through"))
        .withVertexShader("core/rendertype_lines")
        .withFragmentShader("core/rendertype_lines")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderPipeline TRAJECTORY_LINES_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.LINES)
        .withLocation(Identifier.fromNamespaceAndPath("autismclient", "pipeline/trajectory_lines"))
        .withVertexShader("core/rendertype_lines")
        .withFragmentShader("core/rendertype_lines")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderType TRAJECTORY_LINES = RenderType.create(
        "autism_trajectory_lines",
        RenderSetup.builder(TRAJECTORY_LINES_PIPELINE).createRenderSetup()
    );

    public static RenderType trajectoryLines() {
        return TRAJECTORY_LINES;
    }

    private static final RenderPipeline TRAJECTORY_THIN_LINES_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.DEBUG_FILLED_BOX)
        .withLocation(Identifier.fromNamespaceAndPath("autismclient", "pipeline/trajectory_thin_lines"))
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderType TRAJECTORY_THIN_LINES = RenderType.create(
        "autism_trajectory_thin_lines",
        RenderSetup.builder(TRAJECTORY_THIN_LINES_PIPELINE).createRenderSetup()
    );

    public static RenderType trajectoryThinLines() {
        return TRAJECTORY_THIN_LINES;
    }

    private static final RenderType STORAGE_ESP_FILL = RenderType.create(
        "autism_storage_esp_fill_see_through",
        RenderSetup.builder(STORAGE_ESP_FILL_PIPELINE).createRenderSetup()
    );

    private static final RenderType STORAGE_ESP_LINES = RenderType.create(
        "autism_storage_esp_lines_see_through",
        RenderSetup.builder(STORAGE_ESP_LINES_PIPELINE).createRenderSetup()
    );

    private static final RenderPipeline TRACER_ESP_LINES_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.LINES)
        .withLocation(Identifier.fromNamespaceAndPath("autismclient", "pipeline/tracer_esp_lines"))
        .withVertexShader(Identifier.fromNamespaceAndPath("autismclient", "core/fogless_lines"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("autismclient", "core/fogless_lines"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderType TRACER_ESP_LINES = RenderType.create(
        "autism_tracer_esp_lines",
        RenderSetup.builder(TRACER_ESP_LINES_PIPELINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup()
    );

    private static final RenderPipeline WAYPOINT_DISC_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.GUI_TEXTURED)
        .withLocation(Identifier.fromNamespaceAndPath("autismclient", "pipeline/waypoint_disc_see_through"))
        .withVertexShader("core/position_tex_color")
        .withFragmentShader("core/position_tex_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final Function<Identifier, RenderType> WAYPOINT_DISC_SEE_THROUGH = Util.memoize(
        (Function<Identifier, RenderType>) texture -> RenderType.create(
            "autism_waypoint_disc_see_through",
            RenderSetup.builder(WAYPOINT_DISC_PIPELINE)
                .withTexture("Sampler0", texture)
                .sortOnUpload()
                .createRenderSetup()
        )
    );

    private static final RenderPipeline ORE_GHOST_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.GUI_TEXTURED)
        .withLocation(Identifier.fromNamespaceAndPath("autismclient", "pipeline/ore_ghost_see_through"))
        .withVertexShader("core/position_tex_color")
        .withFragmentShader("core/position_tex_color")
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();

    private static final RenderType ORE_GHOST = RenderType.create(
        "autism_ore_ghost_see_through",
        RenderSetup.builder(ORE_GHOST_PIPELINE)

            .withTexture("Sampler0", Sheets.BLOCKS_MAPPER.sheet(),
                () -> RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.NEAREST, true))
            .createRenderSetup()
    );

    private static final Function<Identifier, RenderType> FEMALE_BODY_TRANSLUCENT_CULL = Util.memoize(
        (Function<Identifier, RenderType>) texture -> RenderType.create(
            "autism_female_body_translucent_cull",
            RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup()
        )
    );

    private AutismRenderTypes() {
    }

    public static RenderType skinPreview(Identifier texture) {
        return RenderType.create(
            "autism_skin_preview",
            RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup()
        );
    }

    public static RenderType storageEspFillSeeThrough() {
        return STORAGE_ESP_FILL;
    }

    public static RenderType storageEspLinesSeeThrough() {
        return STORAGE_ESP_LINES;
    }

    public static RenderType tracerEspLines() {
        return TRACER_ESP_LINES;
    }

    public static RenderType waypointDiscSeeThrough(Identifier texture) {
        return WAYPOINT_DISC_SEE_THROUGH.apply(texture);
    }

    public static RenderType oreGhostSeeThrough() {
        return ORE_GHOST;
    }

    public static RenderType femaleBodyTranslucentCull(Identifier texture) {
        return FEMALE_BODY_TRANSLUCENT_CULL.apply(texture);
    }

    private static RenderPipeline.Builder copyLayouts(RenderPipeline.Builder builder, RenderPipeline template) {
        for (BindGroupLayout layout : template.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        return builder;
    }
}
