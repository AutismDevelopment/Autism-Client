package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class AutismPlayerProbe {

    private static final int PROBE_MAX_QUERIES = 5_000;

    private AutismPlayerProbe() {
    }

    public static List<String> everyone(Minecraft mc, boolean probeHidden) {
        return everyone(mc, probeHidden, () -> false);
    }

    public static List<String> everyone(Minecraft mc, boolean probeHidden, BooleanSupplier cancelled) {
        String self = selfName(mc);
        LinkedHashMap<String, String> names = AutismNameHarvest.instantNames(mc, self);
        ClientPacketListener connection = mc == null ? null : mc.getConnection();
        if (probeHidden && connection != null) {
            List<AutismNameHarvest.Vector> vectors = AutismNameHarvest.discoverVectors(connection);
            AutismNameHarvest.sweep(connection, names, self, vectors, new AutismNameHarvest.Control() {
                @Override
                public boolean cancelled() {
                    Minecraft m = Minecraft.getInstance();
                    return (cancelled != null && cancelled.getAsBoolean())
                        || m == null || m.getConnection() != connection;
                }

                @Override
                public boolean paused() {
                    return false;
                }

                @Override
                public int limit() {
                    return Integer.MAX_VALUE;
                }

                @Override
                public int maxQueries() {
                    return PROBE_MAX_QUERIES;
                }
            });
        }
        return new ArrayList<>(names.values());
    }

    private static String selfName(Minecraft mc) {
        return mc == null || mc.player == null ? "" : mc.player.getName().getString();
    }
}
