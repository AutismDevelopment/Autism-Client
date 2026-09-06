package autismclient.mixin.compat;

import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.packets.PacketTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Pseudo
@Mixin(targets = "com.replaymod.replaystudio.protocol.packets.PacketTeam")
public abstract class AutismReplayStudioTeamMixin {

    @Overwrite
    private static void skipTeamInfo(Packet packet, Packet.Reader in) throws IOException {
        in.readText();
        if (!packet.atLeast(ProtocolVersion.v1_13)) {
            in.readString();
            in.readString();
            in.readByte();
        }
        if (packet.atLeast(ProtocolVersion.v1_13)) {
            in.readText();
            in.readText();
        }
        if (packet.atLeast(ProtocolVersion.v1_8)) {
            if (packet.atLeast(ProtocolVersion.v1_21_5)) {
                in.readVarInt();
                in.readVarInt();
            } else {
                in.readString();
                if (packet.atLeast(ProtocolVersion.v1_9)) {
                    in.readString();
                }
            }
            if (packet.atLeast(ProtocolVersion.v1_13)) {
                if (packet.olderThan(ProtocolVersion.v26_2)) {
                    in.readVarInt();
                } else if (in.readBoolean()) {
                    in.readVarInt();
                }
                in.readByte();
            } else {
                in.readByte();
            }
        } else {
            in.readString();
            in.readByte();
        }
    }

    @Overwrite
    public static List<String> getPlayers(Packet packet) throws IOException {
        try {
            try (Packet.Reader in = packet.reader()) {
                in.readString();
                PacketTeam.Action action = PacketTeam.Action.values()[in.readByte()];
                if (action != PacketTeam.Action.CREATE && action != PacketTeam.Action.ADD_PLAYER
                    && action != PacketTeam.Action.REMOVE_PLAYER) {
                    return Collections.emptyList();
                }
                if (action == PacketTeam.Action.CREATE) {
                    skipTeamInfo(packet, in);
                }
                int count = packet.atLeast(ProtocolVersion.v1_8) ? in.readVarInt() : in.readShort();
                List<String> result = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    result.add(in.readString());
                }
                return result;
            }
        } catch (IOException | RuntimeException ignored) {
            return Collections.emptyList();
        }
    }
}
