package autismclient.api.hud;

import autismclient.AutismClientAddon;
import autismclient.api.AddonRegistrationResult;
import autismclient.addons.AddonManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudElements {
    private static final Map<String, HudElementProvider> PROVIDERS = new LinkedHashMap<>();
    private static final Map<String, String> OWNERS = new LinkedHashMap<>();
    private static int revision;

    private HudElements() {}

    public static int revision() {
        return revision;
    }

    public static boolean register(HudElementProvider provider) {
        return registerDetailed(provider).accepted();
    }

    public static AddonRegistrationResult registerDetailed(HudElementProvider provider) {
        if (provider == null) return AddonRegistrationResult.rejected("hud", "", "provider was null");
        String ownerAddonId = AddonManager.currentAddonId();
        if (ownerAddonId == null || ownerAddonId.isBlank()) {
            return reject(ownerAddonId, "", "registration outside an addon lifecycle - register from onInitialize() or onRegisterCategories()");
        }
        String id = provider.id();
        if (id == null || !id.contains(":")) {
            return reject(ownerAddonId, id, "non-namespaced id - scope it with AutismAddons.id(...) so it becomes addonId:localId");
        }
        if (!id.startsWith(ownerAddonId + ":")) {
            return reject(ownerAddonId, id, "foreign namespace - the id must start with your addon id; scope it with AutismAddons.id(...)");
        }
        if (PROVIDERS.containsKey(id)) {
            return reject(ownerAddonId, id, "duplicate id");
        }
        AddonRegistrationResult sizeCheck = validateSize(ownerAddonId, id, provider);
        if (!sizeCheck.accepted()) return sizeCheck;
        PROVIDERS.put(id, provider);
        OWNERS.put(id, ownerAddonId);
        revision++;
        AddonManager.recordAcceptedRegistration("hud", id);
        return AddonRegistrationResult.accepted("hud", id);
    }

    private static AddonRegistrationResult validateSize(String ownerAddonId, String id, HudElementProvider provider) {
        int width;
        int height;
        try {
            width = provider.width();
            height = provider.height();
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Hud] Rejecting HUD element '{}': sizing threw", id, t);
            AddonManager.recordRejectedRegistration(ownerAddonId, "hud", id, "sizing threw " + t.getClass().getSimpleName());
            return AddonRegistrationResult.rejected("hud", id, "sizing threw " + t.getClass().getSimpleName());
        }
        if (width <= 0) return reject(ownerAddonId, id, "width must be positive");
        if (height <= 0) return reject(ownerAddonId, id, "height must be positive");
        return AddonRegistrationResult.accepted("hud", id);
    }

    private static AddonRegistrationResult reject(String ownerAddonId, String id, String reason) {
        AutismClientAddon.LOG.warn("[Hud] Rejecting HUD element '{}': {}", id, reason);
        AddonManager.recordRejectedRegistration(ownerAddonId, "hud", id, reason);
        return AddonRegistrationResult.rejected("hud", id, reason);
    }

    public static void unregisterAddon(String addonId) {
        if (addonId == null || addonId.isBlank()) return;
        List<String> remove = new ArrayList<>();
        for (Map.Entry<String, String> owner : OWNERS.entrySet()) {
            if (addonId.equals(owner.getValue())) remove.add(owner.getKey());
        }
        if (remove.isEmpty()) return;
        for (String id : remove) {
            PROVIDERS.remove(id);
            OWNERS.remove(id);
        }
        revision++;
    }

    public static HudElementProvider get(String id) {
        return id == null ? null : PROVIDERS.get(id);
    }

    public static boolean isAddon(String id) {
        return id != null && PROVIDERS.containsKey(id);
    }

    public static List<String> ids() {
        return Collections.unmodifiableList(new ArrayList<>(PROVIDERS.keySet()));
    }

    public static boolean isEmpty() {
        return PROVIDERS.isEmpty();
    }
}
