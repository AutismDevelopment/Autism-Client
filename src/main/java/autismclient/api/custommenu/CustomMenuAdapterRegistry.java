package autismclient.api.custommenu;

import autismclient.AutismClientAddon;
import autismclient.addons.AddonManager;
import autismclient.util.custommenu.VanillaDialogAdapter;
import net.minecraft.network.protocol.Packet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomMenuAdapterRegistry {
    private record Registered(CustomMenuAdapter adapter, String owner) {}
    private record RegistrySnapshot(Registered[] ordered, Map<String, Registered> byId, List<String> ids) {}
    private static final Map<String, Registered> ADAPTERS = new LinkedHashMap<>();
    private static volatile RegistrySnapshot SNAPSHOT;

    static {
        ADAPTERS.put(VanillaDialogAdapter.ID, new Registered(new VanillaDialogAdapter(), "autismclient"));
        publishSnapshot();
    }

    private CustomMenuAdapterRegistry() {}

    public static synchronized boolean register(CustomMenuAdapter adapter) {
        if (adapter == null || adapter.id() == null || adapter.id().isBlank()) return false;
        String id = adapter.id().trim().toLowerCase(java.util.Locale.ROOT);
        if (ADAPTERS.containsKey(id)) return false;
        ADAPTERS.put(id, new Registered(adapter, AddonManager.currentAddonId()));
        publishSnapshot();
        return true;
    }

    public static synchronized void unregisterAddon(String addonId) {
        if (addonId == null || addonId.isBlank()) return;
        if (ADAPTERS.entrySet().removeIf(entry -> addonId.equals(entry.getValue().owner()))) {
            publishSnapshot();
        }
    }

    public static boolean acceptsInbound(Packet<?> packet) {
        if (packet == null) return false;
        for (Registered registered : SNAPSHOT.ordered()) {
            try {
                if (registered.adapter().acceptsInbound(packet)) return true;
            } catch (Throwable error) {

                return true;
            }
        }
        return false;
    }

    public static CustomMenuEvent inspect(Packet<?> packet, String phase) {
        if (packet == null) return CustomMenuEvent.NONE;
        for (Registered registered : SNAPSHOT.ordered()) {
            try {
                if (!registered.adapter().acceptsInbound(packet)) continue;
                CustomMenuEvent event = registered.adapter().inspectInbound(packet, phase);
                if (event != null && event.type() != CustomMenuEvent.Type.NONE) return event;
            } catch (Throwable error) {
                AutismClientAddon.LOG.warn("[CustomMenus] Adapter '{}' failed while reading a packet", registered.adapter().id(), error);
            }
        }
        return CustomMenuEvent.NONE;
    }

    public static CustomMenuSubmitResult submit(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
        if (snapshot == null) return CustomMenuSubmitResult.failure("No custom menu is open");
        Registered registered = SNAPSHOT.byId().get(snapshot.adapterId().toLowerCase(java.util.Locale.ROOT));
        if (registered == null) return CustomMenuSubmitResult.failure("Custom menu adapter is unavailable");
        try {
            CustomMenuSubmitResult result = registered.adapter().submit(snapshot, submission);
            return result == null ? CustomMenuSubmitResult.failure("Custom menu adapter returned no result") : result;
        } catch (Throwable error) {

            AutismClientAddon.LOG.warn("[CustomMenus] Adapter '{}' failed while submitting a form ({})",
                registered.adapter().id(), error.getClass().getSimpleName());
            return CustomMenuSubmitResult.failure("Custom menu adapter failed");
        }
    }

    public static List<String> ids() {
        return SNAPSHOT.ids();
    }

    private static void publishSnapshot() {
        Registered[] ordered = ADAPTERS.values().toArray(Registered[]::new);
        SNAPSHOT = new RegistrySnapshot(ordered, Map.copyOf(ADAPTERS), List.copyOf(ADAPTERS.keySet()));
    }
}
