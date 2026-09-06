package autismclient.api.custommenu;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.chat.ClickEvent;
import java.util.List;

public record CustomMenuSubmitResult(
    boolean success,
    String error,
    List<Packet<?>> packets,
    CustomMenuSnapshot replacement,
    ClickEvent clientAction
) {
    public CustomMenuSubmitResult {
        error = error == null ? "" : error;
        packets = packets == null ? List.of() : List.copyOf(packets);
    }

    public static CustomMenuSubmitResult failure(String error) {
        return new CustomMenuSubmitResult(false, error, List.of(), null, null);
    }

    public static CustomMenuSubmitResult packets(List<Packet<?>> packets) {
        return new CustomMenuSubmitResult(true, "", packets, null, null);
    }

    public static CustomMenuSubmitResult replacement(CustomMenuSnapshot replacement, ClickEvent clientAction) {
        return new CustomMenuSubmitResult(true, "", List.of(), replacement, clientAction);
    }
}
