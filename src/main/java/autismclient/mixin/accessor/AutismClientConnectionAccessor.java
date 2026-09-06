package autismclient.mixin.accessor;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface AutismClientConnectionAccessor {
    @Accessor("channel")
    Channel getChannel();

    @Accessor("packetListener")
    void autism$setPacketListener(PacketListener listener);
}
