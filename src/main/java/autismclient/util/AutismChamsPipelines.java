package autismclient.util;

import autismclient.mixin.AutismRenderPipelineInvoker;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class AutismChamsPipelines {
    private static RenderPipeline visible;
    private static RenderPipeline occluded;
    private static boolean tried;

    private AutismChamsPipelines() {
    }

    public static RenderPipeline visible() {
        ensureBuilt();
        return visible != null ? visible : RenderPipelines.ENTITY_TRANSLUCENT_CULL;
    }

    public static RenderPipeline occluded() {
        ensureBuilt();
        return occluded != null ? occluded : RenderPipelines.ENTITY_TRANSLUCENT_CULL;
    }

    private static void ensureBuilt() {
        if (tried) return;
        tried = true;
        RenderPipeline base = RenderPipelines.ENTITY_TRANSLUCENT_CULL;
        try {
            DepthStencilState baseDepth = base.getDepthStencilState();
            CompareOp visibleOp = baseDepth == null ? CompareOp.GREATER_THAN_OR_EQUAL : baseDepth.depthTest();
            CompareOp occludedOp = invertDepth(visibleOp);
            visible = clone(base, "chams_visible", new DepthStencilState(visibleOp, true));
            occluded = clone(base, "chams_occluded", new DepthStencilState(occludedOp, false));
        } catch (Throwable t) {

            visible = base;
            occluded = base;
        }
    }

    private static final Identifier CHAMS_FRAGMENT = Identifier.fromNamespaceAndPath("autismclient", "core/chams");

    private static RenderPipeline clone(RenderPipeline base, String name, DepthStencilState depth) {
        return AutismRenderPipelineInvoker.autism$create(
            Identifier.fromNamespaceAndPath("autismclient", "pipeline/" + name),
            base.getVertexShader(),
            CHAMS_FRAGMENT,
            base.getShaderDefines(),
            base.getBindGroupLayouts(),
            base.getColorTargetStates(),
            depth,
            base.getPolygonMode(),
            true,
            base.getVertexFormatBindings(),
            base.getPrimitiveTopology(),
            base.getSortKey()
        );
    }

    private static CompareOp invertDepth(CompareOp op) {
        return switch (op) {
            case GREATER_THAN_OR_EQUAL -> CompareOp.LESS_THAN;
            case GREATER_THAN -> CompareOp.LESS_THAN_OR_EQUAL;
            case LESS_THAN_OR_EQUAL -> CompareOp.GREATER_THAN;
            case LESS_THAN -> CompareOp.GREATER_THAN_OR_EQUAL;
            default -> CompareOp.LESS_THAN;
        };
    }
}
