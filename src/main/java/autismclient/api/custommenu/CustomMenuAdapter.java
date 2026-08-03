package autismclient.api.custommenu;

import net.minecraft.network.protocol.Packet;

public interface CustomMenuAdapter {
    String id();

    default boolean acceptsInbound(Packet<?> packet) {
        return true;
    }

    CustomMenuEvent inspectInbound(Packet<?> packet, String phase);

    CustomMenuSubmitResult submit(CustomMenuSnapshot snapshot, CustomMenuSubmission submission);
}
