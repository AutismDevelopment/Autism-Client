package autismclient.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundRotateHeadPacket.class)
public interface AutismRotateHeadPacketAccessor {
    @Accessor("entityId")
    int autism$getEntityId();
}
