package autismclient.api.macro;

import autismclient.AutismClientAddon;
import autismclient.api.AddonRegistrationResult;
import autismclient.addons.AddonManager;
import autismclient.gui.macro.editor.ActionFieldSchema;
import autismclient.util.macro.MacroAction;
import autismclient.util.macro.MacroActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class MacroActionRegistry {
    private static final Map<String, MacroActionEntry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, ActionCategory> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, String> ENTRY_OWNERS = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORY_OWNERS = new LinkedHashMap<>();

    private static final Map<String, ActionFieldSchema> RESOLVED_SCHEMAS = new LinkedHashMap<>();

    public record ActionCategory(String id, String label, int color) {}

    public static final int ADDON_AUTO_CATEGORY_COLOR = 0xFFAA66FF;

    private static final String ADDON_CAT_PREFIX = "addon:";

    private MacroActionRegistry() {}

    public static String scopeCategoryId(String rawId) {
        if (AddonManager.isLifecycleActive()) {
            String addonId = AddonManager.currentAddonId();
            return ADDON_CAT_PREFIX + (addonId == null || addonId.isBlank() ? "shared" : addonId);
        }
        if (rawId != null && rawId.startsWith(ADDON_CAT_PREFIX)) return rawId;
        String addonId = AddonManager.currentAddonId();
        String base = addonId == null || addonId.isBlank() ? "shared" : addonId;
        return rawId == null || rawId.isBlank()
            ? ADDON_CAT_PREFIX + base
            : ADDON_CAT_PREFIX + base + "/" + rawId;
    }

    public static String ensureScopedCategory(String rawId) {
        String scopedId = scopeCategoryId(rawId);
        if (!CATEGORIES.containsKey(scopedId)) {
            registerCategoryInternal(scopedId, AddonManager.scopedCategoryLabel(null), ADDON_AUTO_CATEGORY_COLOR,
                    AddonManager.currentAddonId());
        }
        return scopedId;
    }

    public static void registerScopedCategory(String rawId, String label, int color) {
        String visible = AddonManager.isLifecycleActive() ? AddonManager.scopedCategoryLabel(null) : label;
        registerCategoryInternal(scopeCategoryId(rawId), visible, color,
                AddonManager.currentAddonId());
    }

    public static void registerCategory(String id, String label, int color) {
        registerCategoryInternal(id, label, color, null);
    }

    private static void registerCategoryInternal(String id, String label, int color, String ownerAddonId) {
        if (id == null || id.isBlank()) return;
        CATEGORIES.put(id, new ActionCategory(id, label == null || label.isBlank() ? id : label, color));
        if (ownerAddonId != null && !ownerAddonId.isBlank()) CATEGORY_OWNERS.put(id, ownerAddonId);
    }

    public static ActionCategory category(String id) {
        return id == null ? null : CATEGORIES.get(id);
    }

    public static boolean register(MacroActionEntry entry) {
        return registerDetailed(entry).accepted();
    }

    public static AddonRegistrationResult registerDetailed(MacroActionEntry entry) {
        if (entry == null) return AddonRegistrationResult.rejected("macro action", "", "entry was null");
        String ownerAddonId = AddonManager.currentAddonId();
        if (ownerAddonId == null || ownerAddonId.isBlank()) {
            return reject(ownerAddonId, "macro action", "", "registration outside an addon lifecycle - register from onInitialize() or onRegisterCategories()");
        }

        String typeId = entry.typeId();
        if (typeId == null || !typeId.contains(":")) {
            return reject(ownerAddonId, "macro action", typeId, "non-namespaced type id - scope it with AutismAddons.id(...) so it becomes addonId:localId");
        }
        if (!typeId.startsWith(ownerAddonId + ":")) {
            return reject(ownerAddonId, "macro action", typeId, "foreign namespace - the id must start with your addon id; scope it with AutismAddons.id(...)");
        }
        if (entry.factory() == null) {
            return reject(ownerAddonId, "macro action", typeId, "missing factory");
        }
        if (isBuiltinName(typeId)) {
            return reject(ownerAddonId, "macro action", typeId, "collides with a built-in type");
        }
        if (ENTRIES.containsKey(typeId)) {
            return reject(ownerAddonId, "macro action", typeId, "duplicate type id");
        }

        if (entry.wantsPicker()) {
            entry.setPickerCategory(ensureScopedCategory(entry.pickerCategory()));
        }

        ActionFieldSchema effectiveSchema = entry.schema();
        MacroAction probe;
        try {
            probe = entry.factory().get();
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[MacroActions] Rejecting action '{}' because its factory threw during validation", typeId, t);
            return reject(ownerAddonId, "macro action", typeId, "factory threw: " + errorName(t));
        }
        if (probe == null) {
            return reject(ownerAddonId, "macro action", typeId, "factory returned null");
        }
        if (effectiveSchema == null) {
            ActionSchema declared = AddonAction.schemaOf(probe);
            if (declared != null) effectiveSchema = declared.internal();
        }
        String validationError = validateProbe(typeId, entry, probe, effectiveSchema);
        if (validationError != null) return reject(ownerAddonId, "macro action", typeId, validationError);

        ENTRIES.put(typeId, entry);
        ENTRY_OWNERS.put(typeId, ownerAddonId);
        if (effectiveSchema != null) RESOLVED_SCHEMAS.put(typeId, effectiveSchema);
        AddonManager.recordAcceptedRegistration("macro action", typeId);
        return AddonRegistrationResult.accepted("macro action", typeId);
    }

    private static AddonRegistrationResult reject(String ownerAddonId, String kind, String id, String reason) {
        AutismClientAddon.LOG.warn("[MacroActions] Rejecting {} '{}': {}", kind, id, reason);
        AddonManager.recordRejectedRegistration(ownerAddonId, kind, id, reason);
        return AddonRegistrationResult.rejected(kind, id, reason);
    }

    private static String validateProbe(String typeId, MacroActionEntry entry, MacroAction probe, ActionFieldSchema schema) {
        String writtenType;
        net.minecraft.nbt.CompoundTag tag;
        try {
            tag = probe.toTag();
            writtenType = tag.getStringOr("type", "");
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[MacroActions] Rejecting '{}' because toTag() threw", typeId, t);
            return "toTag() threw: " + errorName(t);
        }
        if (!typeId.equals(writtenType)) {
            return "writes type=\"" + writtenType + "\" instead of \"" + typeId + "\"";
        }
        if (schema != null) {
            for (autismclient.gui.macro.editor.FieldDef field : schema.fields()) {
                String key = field.key();
                if (field.type() == autismclient.gui.macro.editor.FieldType.BLOCK_POS) {
                    for (String axisKey : field.xyzKeys()) {
                        if (axisKey != null && !axisKey.isEmpty() && !tag.contains(axisKey)) {
                            return "schema field '" + axisKey + "' is not written by toTag()/save()";
                        }
                    }
                } else if (key != null && !key.isEmpty() && !tag.contains(key)) {
                    return "schema field '" + key + "' is not written by toTag()/save()";
                }
            }
        }
        if (entry.isCondition() && !(probe instanceof ContextMacroAction)) {
            return "conditions must implement ContextMacroAction";
        }
        return null;
    }

    public static void unregisterAddon(String addonId) {
        if (addonId == null || addonId.isBlank()) return;
        List<String> removeTypes = new ArrayList<>();
        for (Map.Entry<String, String> owner : ENTRY_OWNERS.entrySet()) {
            if (addonId.equals(owner.getValue())) removeTypes.add(owner.getKey());
        }
        for (String typeId : removeTypes) {
            ENTRIES.remove(typeId);
            RESOLVED_SCHEMAS.remove(typeId);
            ENTRY_OWNERS.remove(typeId);
        }

        List<String> removeCategories = new ArrayList<>();
        for (Map.Entry<String, String> owner : CATEGORY_OWNERS.entrySet()) {
            if (addonId.equals(owner.getValue())) removeCategories.add(owner.getKey());
        }
        for (String categoryId : removeCategories) {
            CATEGORIES.remove(categoryId);
            CATEGORY_OWNERS.remove(categoryId);
        }
    }

    public static Supplier<MacroAction> factory(String typeId) {
        MacroActionEntry e = typeId == null ? null : ENTRIES.get(typeId);
        return e == null ? null : e.factory();
    }

    public static ActionFieldSchema schema(String typeId) {
        return typeId == null ? null : RESOLVED_SCHEMAS.get(typeId);
    }

    public static boolean isCondition(String typeId) {
        MacroActionEntry e = typeId == null ? null : ENTRIES.get(typeId);
        return e != null && e.isCondition();
    }

    public static String ownerOf(String typeId) {
        return typeId == null ? null : ENTRY_OWNERS.get(typeId);
    }

    public static List<MacroActionEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
    }

    private static boolean isBuiltinName(String typeId) {
        try {
            MacroActionType.valueOf(typeId.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String errorName(Throwable t) {
        if (t == null) return "unknown";
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + message;
    }
}
