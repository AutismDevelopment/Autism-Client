package autismclient.mixin.accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface AutismItemStackRenderStateAccessor {
    @Accessor("activeLayerCount")
    int autism$getActiveLayerCount();

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] autism$getLayers();
}
