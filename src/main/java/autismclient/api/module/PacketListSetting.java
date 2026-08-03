package autismclient.api.module;

import java.util.List;

public final class PacketListSetting extends ListSetting<PacketListSetting> {
    public PacketListSetting(String name, String title) {
        super(Kind.PACKET_LIST, name, title, List.of());
    }

    public PacketListSetting(String name, String title, List<String> defaultValue) {
        super(Kind.PACKET_LIST, name, title, defaultValue);
    }

    public PacketListSetting(String name, String title, String defaultValue) {
        super(Kind.PACKET_LIST, name, title, defaultValue);
    }
}
