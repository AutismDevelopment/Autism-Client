package autismclient.mixin.accessor;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface AutismMovePlayerPacketAccessor {
    @Mutable
    @Accessor("y")
    void autism$setY(double y);

    @Mutable
    @Accessor("onGround")
    void autism$setOnGround(boolean onGround);

    @Mutable
    @Accessor("yRot")
    void autism$setYRot(float yRot);

    @Mutable
    @Accessor("xRot")
    void autism$setXRot(float xRot);
}
