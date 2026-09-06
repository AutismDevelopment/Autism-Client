package autismclient.util;

import autismclient.mixin.AutismRenderTypeStateAccessor;
import net.minecraft.client.renderer.rendertype.AutismChamsRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class AutismChams {

    public static final int FULLBRIGHT = 0xF000F0;

    private AutismChams() {
    }

    public static RenderType chamsVisible(RenderType original) {
        Identifier texture = textureOf(original);
        return texture == null ? null : AutismChamsRenderTypes.visible(texture, AutismChamsPipelines.visible());
    }

    public static RenderType chamsOccluded(RenderType original) {
        Identifier texture = textureOf(original);
        return texture == null ? null : AutismChamsRenderTypes.occluded(texture, AutismChamsPipelines.occluded());
    }

    private static Identifier textureOf(RenderType original) {
        try {
            RenderSetup setup = ((AutismRenderTypeStateAccessor) (Object) original).autism$getState();
            return setup == null ? null : AutismChamsRenderTypes.textureOf(setup);
        } catch (Throwable t) {
            return null;
        }
    }
}
