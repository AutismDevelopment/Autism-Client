package autismclient.mixin.accessor;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundPlayerCommandPacket.class)
public interface AutismPlayerCommandPacketAccessor {
    @Mutable
    @Accessor("id")
    void autism$setEntityId(int entityId);
}
