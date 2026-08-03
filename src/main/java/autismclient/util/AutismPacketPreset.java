package autismclient.util;

import java.util.HashSet;
import java.util.Set;

public class AutismPacketPreset {
    public String name;
    public Set<String> c2sPackets = new HashSet<>();
    public Set<String> s2cPackets = new HashSet<>();

    public AutismPacketPreset() {}

    public AutismPacketPreset(String name, Set<String> c2sPackets, Set<String> s2cPackets) {
        this.name = name;
        this.c2sPackets = c2sPackets;
        this.s2cPackets = s2cPackets;
    }
}
