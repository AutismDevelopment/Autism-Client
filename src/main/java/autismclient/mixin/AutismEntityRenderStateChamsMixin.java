package autismclient.mixin;

import autismclient.util.AutismChamsHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class AutismEntityRenderStateChamsMixin implements AutismChamsHolder {
    @Unique private boolean autism$active;
    @Unique private int autism$visible;
    @Unique private int autism$occluded;

    @Override
    public void autism$setChams(boolean active, int visibleColor, int occludedColor) {
        this.autism$active = active;
        this.autism$visible = visibleColor;
        this.autism$occluded = occludedColor;
    }

    @Override
    public boolean autism$chamsActive() {
        return autism$active;
    }

    @Override
    public int autism$chamsVisible() {
        return autism$visible;
    }

    @Override
    public int autism$chamsOccluded() {
        return autism$occluded;
    }
}
