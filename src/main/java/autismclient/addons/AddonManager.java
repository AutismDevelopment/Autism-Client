package autismclient.addons;

import autismclient.AutismClientAddon;
import autismclient.api.ApiVersion;
import autismclient.api.AutismAddon;
import autismclient.util.AutismConfig;
import autismclient.util.AutismNotifications;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class AddonManager {
    private static final int MAX_UI_ADDON_NAME_LENGTH = 22;
    private static final List<AutismAddon> LOADED = new ArrayList<>();
    private static final Map<String, AddonReport> REPORTS = new LinkedHashMap<>();

    private record LoadingContext(String addonId, String addonName) {}
    private static volatile LoadingContext currentContext = null;

    private static boolean failuresSurfaced = false;

    private AddonManager() {}

    public static void init() {
        LOADED.clear();
        REPORTS.clear();
        failuresSurfaced = false;

        AutismConfig config = AutismConfig.getGlobal();
        List<LoadedAddon> accepted = new ArrayList<>();
        for (EntrypointContainer<AutismAddon> container : FabricLoader.getInstance()
                .getEntrypointContainers("autism", AutismAddon.class)) {
            ModContainer mod;
            try {
                mod = container.getProvider();
            } catch (Throwable t) {
                AutismClientAddon.LOG.error("[Addons] Failed to inspect an addon entrypoint", t);
                continue;
            }

            String modId = mod.getMetadata().getId();
            AddonReport report = ensureReport(mod.getMetadata());
            if (isDisabledOnRestart(modId)) {
                report.markDisabled("Disabled in AUTISM config. Enable it and restart to load.");
                continue;
            }

            AutismAddon addon;
            try {
                addon = container.getEntrypoint();
            } catch (Throwable t) {
                report.markFailed("construct", "Failed to construct entrypoint: " + shortError(t));
                AutismClientAddon.LOG.error("[Addons] Failed to construct addon '{}'", modId, t);
                continue;
            }

            try {
                int declared = addon.apiVersion();
                report.setApiVersion(declared);
                if (declared <= 0) {
                    report.markFailed("apiVersion", "Invalid API version " + declared + ".");
                    AutismClientAddon.LOG.warn("[Addons] Addon '{}' declared invalid API v{}; skipping it.", modId, declared);
                    continue;
                }
                if (declared > ApiVersion.CURRENT) {
                    report.markNeedsNewerApi("Needs API v" + declared + ", host provides v" + ApiVersion.CURRENT + ".");
                    AutismClientAddon.LOG.warn("[Addons] Addon '{}' needs API v{} but this client provides v{}; skipping it.",
                            modId, declared, ApiVersion.CURRENT);
                    continue;
                }
                if (declared < ApiVersion.CURRENT) {
                    AutismClientAddon.LOG.info("[Addons] Addon '{}' built against API v{} (current v{}); loading.",
                            modId, declared, ApiVersion.CURRENT);
                }
                applyMetadata(addon, mod.getMetadata());
                report.copyRuntimeMetadata(addon);
                accepted.add(new LoadedAddon(modId, addon));
            } catch (AbstractMethodError e) {
                report.markMissingApiVersion("Missing apiVersion() override.");
                AutismClientAddon.LOG.error("[Addons] Addon '{}' does not declare apiVersion(); skipping it.", modId, e);
            } catch (Throwable t) {
                report.markFailed("prepare", "Failed to prepare: " + shortError(t));
                AutismClientAddon.LOG.error("[Addons] Error preparing addon '{}'", modId, t);
            }
        }

        List<LoadedAddon> categoryReady = new ArrayList<>();
        for (LoadedAddon la : accepted) {
            if (runPhase(la, "onRegisterCategories", la.addon::onRegisterCategories)) {
                categoryReady.add(la);
            } else {
                invokeUnload(la);
                rollbackRegistrations(la.modId);
            }
        }

        for (LoadedAddon la : categoryReady) {
            if (runPhase(la, "onInitialize", la.addon::onInitialize)) {
                LOADED.add(la.addon);
                AddonReport report = REPORTS.get(la.modId);
                if (report != null) report.markLoaded();
            } else {
                invokeUnload(la);
                rollbackRegistrations(la.modId);
            }
        }

        if (!LOADED.isEmpty()) {
            AutismClientAddon.LOG.info("[Addons] Loaded {} addon(s).", LOADED.size());
        }
        if (config.disabledAddonIds != null) {
            for (String id : config.disabledAddonIds) {
                if (id == null || id.isBlank() || REPORTS.containsKey(id)) continue;
                AddonReport report = new AddonReport(id.trim(), id.trim(), "", "", 0xFFAA66FF);
                report.markDisabled("Disabled in AUTISM config, but no matching addon entrypoint was discovered.");
                REPORTS.put(report.modId(), report);
            }
        }
    }

    private static boolean runPhase(LoadedAddon la, String phase, Runnable body) {
        String name = la.addon.name == null || la.addon.name.isBlank() ? la.modId : la.addon.name;
        currentContext = new LoadingContext(la.modId, name);
        AddonReport report = REPORTS.get(la.modId);
        if (report != null) report.setPhase(phase);
        try {
            body.run();
            return true;
        } catch (Throwable t) {
            if (report != null) report.markFailed(phase, "Threw during " + phase + ": " + shortError(t));
            AutismClientAddon.LOG.error("[Addons] Addon '{}' threw during {}", la.modId, phase, t);
            return false;
        } finally {
            currentContext = null;
        }
    }

    private static void invokeUnload(LoadedAddon la) {
        if (la == null || la.addon == null) return;
        try {
            la.addon.onUnload();
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Addons] Addon '{}' threw during onUnload", la.modId, t);
        }
    }

    private static void rollbackRegistrations(String addonId) {
        if (addonId == null || addonId.isBlank()) return;
        AddonReport report = REPORTS.get(addonId);
        rollbackStep(addonId, report, "macro actions", () -> autismclient.api.macro.MacroActionRegistry.unregisterAddon(addonId));
        rollbackStep(addonId, report, "custom menu adapters", () -> autismclient.api.custommenu.CustomMenuAdapterRegistry.unregisterAddon(addonId));
        rollbackStep(addonId, report, "presets", () -> autismclient.api.macro.MacroPresetRegistry.unregisterAddon(addonId));
        rollbackStep(addonId, report, "events", () -> autismclient.api.event.AddonEvents.unregisterAddon(addonId));
        rollbackStep(addonId, report, "hud", () -> autismclient.api.hud.HudElements.unregisterAddon(addonId));
        rollbackStep(addonId, report, "modules", () -> autismclient.modules.ModuleRegistry.unregisterAddonModules(addonId));
        rollbackStep(addonId, report, "commands", () -> autismclient.commands.AutismCommands.unregisterAddonCommands(addonId));
        if (report != null) report.addRollback("Rolled back partial registrations after load failure.");
    }

    private static void rollbackStep(String addonId, AddonReport report, String kind, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            String detail = "Failed to rollback " + kind + ": " + shortError(t);
            if (report != null) report.addRollback(detail);
            AutismClientAddon.LOG.warn("[Addons] Addon '{}' rollback failed for {}", addonId, kind, t);
        }
    }

    private static AddonReport ensureReport(ModMetadata meta) {
        String modId = meta.getId();
        AddonReport existing = REPORTS.get(modId);
        if (existing != null) return existing;
        AddonReport report = new AddonReport(
            modId,
            meta.getName() == null || meta.getName().isBlank() ? modId : meta.getName(),
            meta.getVersion().getFriendlyString(),
            meta.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", ")),
            metadataColor(meta, 0xFFAA66FF)
        );
        REPORTS.put(modId, report);
        return report;
    }

    private static void applyMetadata(AutismAddon addon, ModMetadata meta) {
        addon.name = meta.getName();
        addon.authors = meta.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
        addon.color = metadataColor(meta, addon.color);
    }

    private static int metadataColor(ModMetadata meta, int fallback) {
        CustomValue color = meta.containsCustomValue("autism:color") ? meta.getCustomValue("autism:color") : null;
        if (color != null && color.getType() == CustomValue.CvType.STRING) {
            return parseColor(color.getAsString(), fallback);
        }
        return fallback;
    }

    private static int parseColor(String raw, int fallback) {
        try {
            String[] parts = raw.split(",");
            if (parts.length != 3) return fallback;
            int r = clamp(Integer.parseInt(parts[0].trim()));
            int g = clamp(Integer.parseInt(parts[1].trim()));
            int b = clamp(Integer.parseInt(parts[2].trim()));
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static String currentId() {
        LoadingContext ctx = currentContext;
        return ctx == null ? null : ctx.addonId();
    }

    private static String currentName() {
        LoadingContext ctx = currentContext;
        return ctx == null ? null : ctx.addonName();
    }

    public static String currentAddonId() {
        return currentId();
    }

    public static boolean isLifecycleActive() {
        String id = currentId();
        return id != null && !id.isBlank();
    }

    public static String currentAddonName() {
        return currentName();
    }

    public static String normalizedAddonName(String name, String fallback) {
        String value = name == null ? "" : name.replaceAll("\\s+", " ").trim();
        if (value.isBlank()) value = fallback == null ? "" : fallback.replaceAll("\\s+", " ").trim();
        return value.isBlank() ? "Addon" : value;
    }

    public static String compactAddonName(String name, String fallback) {
        String value = normalizedAddonName(name, fallback);
        if (value.length() <= MAX_UI_ADDON_NAME_LENGTH) return value;
        return value.substring(0, Math.max(1, MAX_UI_ADDON_NAME_LENGTH - 3)).trim() + "...";
    }

    public static String scopedId(String rawId) {
        String raw = rawId == null ? "" : rawId.trim();
        if (raw.contains(":")) return raw;
        String owner = currentId();
        if (owner == null || owner.isBlank()) return raw;
        return owner + ":" + raw;
    }

    public static String scopedCategoryLabel(String customLabel) {
        LoadingContext ctx = currentContext;
        String id = ctx == null ? null : ctx.addonId();
        String name = ctx == null ? null : ctx.addonName();
        if (name == null || name.isBlank()) name = id;
        return compactAddonName(name, id);
    }

    public static List<AutismAddon> loaded() {
        return Collections.unmodifiableList(LOADED);
    }

    public static List<AddonReport> reports() {
        return Collections.unmodifiableList(new ArrayList<>(REPORTS.values()));
    }

    public static AddonReport report(String addonId) {
        return addonId == null ? null : REPORTS.get(addonId);
    }

    public static File modsFolder() {
        return FabricLoader.getInstance().getGameDir().resolve("mods").toFile();
    }

    public static boolean isDisabledOnRestart(String addonId) {
        if (addonId == null || addonId.isBlank()) return false;
        AutismConfig config = AutismConfig.getGlobal();
        if (config.disabledAddonIds == null) return false;
        String needle = addonId.trim().toLowerCase(Locale.ROOT);
        for (String id : config.disabledAddonIds) {
            if (id != null && needle.equals(id.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public static void setDisabledOnRestart(String addonId, boolean disabled) {
        if (addonId == null || addonId.isBlank()) return;
        AutismConfig config = AutismConfig.getGlobal();
        if (config.disabledAddonIds == null) config.disabledAddonIds = new ArrayList<>();
        String normalized = addonId.trim();
        boolean currently = isDisabledOnRestart(normalized);
        if (disabled && !currently) {
            config.disabledAddonIds.add(normalized);
        } else if (!disabled && currently) {
            config.disabledAddonIds.removeIf(id -> id != null && normalized.equalsIgnoreCase(id.trim()));
        }
        config.save();
    }

    public static void recordAcceptedRegistration(String kind, String id) {
        AddonReport report = REPORTS.get(currentId());
        if (report != null) report.recordAccepted(kind, id);
    }

    public static void recordRejectedRegistration(String addonId, String kind, String id, String reason) {
        AddonReport report = REPORTS.get(addonId == null || addonId.isBlank() ? currentId() : addonId);
        if (report != null) report.recordRejected(kind, id, reason);
    }

    public static void recordRuntimeError(String addonId, String detail) {
        AddonReport report = REPORTS.get(addonId);
        if (report != null) report.recordRuntimeError(detail);
    }

    public static void surfaceFailuresOnJoin() {
        if (failuresSurfaced) return;
        int issues = issueCount();
        if (issues <= 0) return;
        failuresSurfaced = true;
        AutismNotifications.warning(issues + " addon issue(s). Open Addons for details.");
    }

    private static int issueCount() {
        int count = 0;
        for (AddonReport report : REPORTS.values()) {
            if (report.status().isIssue()) count++;
        }
        return count;
    }

    private static String shortError(Throwable t) {
        if (t == null) return "Unknown error";
        String msg = t.getMessage();
        return msg == null || msg.isBlank() ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + msg;
    }

    private record LoadedAddon(String modId, AutismAddon addon) {}

    public enum AddonLoadStatus {
        LOADED("Loaded", false),
        DISABLED("Disabled on restart", false),
        FAILED("Failed", true),
        NEEDS_NEWER_API("Needs newer API", true),
        MISSING_API_VERSION("Missing apiVersion", true);

        private final String label;
        private final boolean issue;

        AddonLoadStatus(String label, boolean issue) {
            this.label = label;
            this.issue = issue;
        }

        public String label() { return label; }
        public boolean isIssue() { return issue; }
    }

    public static final class AddonReport {
        private final String modId;
        private String name;
        private String version;
        private String authors;
        private int color;
        private int apiVersion = -1;
        private int hostApiVersion = ApiVersion.CURRENT;
        private AddonLoadStatus status = AddonLoadStatus.FAILED;
        private String phase = "discovered";
        private String failureReason = "";
        private final Map<String, Integer> accepted = new LinkedHashMap<>();
        private final Map<String, Integer> rejected = new LinkedHashMap<>();
        private final List<String> rejectionDetails = new ArrayList<>();
        private final List<String> rollbackDetails = new ArrayList<>();
        private int runtimeErrors;
        private String lastRuntimeError = "";

        private AddonReport(String modId, String name, String version, String authors, int color) {
            this.modId = modId == null ? "" : modId;
            this.name = name == null || name.isBlank() ? this.modId : name;
            this.version = version == null ? "" : version;
            this.authors = authors == null ? "" : authors;
            this.color = color;
        }

        private void copyRuntimeMetadata(AutismAddon addon) {
            if (addon == null) return;
            if (addon.name != null && !addon.name.isBlank()) this.name = addon.name;
            if (addon.authors != null && !addon.authors.isBlank()) this.authors = addon.authors;
            this.color = addon.color;
        }

        private void setApiVersion(int apiVersion) {
            this.apiVersion = apiVersion;
        }

        private void setPhase(String phase) {
            this.phase = phase == null || phase.isBlank() ? this.phase : phase;
        }

        private void markLoaded() {
            this.status = AddonLoadStatus.LOADED;
            this.phase = "loaded";
            this.failureReason = "";
        }

        private void markDisabled(String reason) {
            this.status = AddonLoadStatus.DISABLED;
            this.phase = "disabled";
            this.failureReason = reason == null ? "" : reason;
        }

        private void markFailed(String phase, String reason) {
            this.status = AddonLoadStatus.FAILED;
            this.phase = phase == null || phase.isBlank() ? "failed" : phase;
            this.failureReason = reason == null ? "" : reason;
        }

        private void markNeedsNewerApi(String reason) {
            this.status = AddonLoadStatus.NEEDS_NEWER_API;
            this.phase = "apiVersion";
            this.failureReason = reason == null ? "" : reason;
        }

        private void markMissingApiVersion(String reason) {
            this.status = AddonLoadStatus.MISSING_API_VERSION;
            this.phase = "apiVersion";
            this.failureReason = reason == null ? "" : reason;
        }

        private void recordAccepted(String kind, String id) {
            String key = kind == null || kind.isBlank() ? "item" : kind;
            accepted.merge(key, 1, Integer::sum);
        }

        private void recordRejected(String kind, String id, String reason) {
            String key = kind == null || kind.isBlank() ? "item" : kind;
            rejected.merge(key, 1, Integer::sum);
            String detail = key + (id == null || id.isBlank() ? "" : " " + id) + ": "
                + (reason == null || reason.isBlank() ? "rejected" : reason);
            rejectionDetails.add(detail);
        }

        private void addRollback(String detail) {
            if (detail != null && !detail.isBlank()) rollbackDetails.add(detail);
        }

        private void recordRuntimeError(String detail) {
            runtimeErrors++;
            lastRuntimeError = detail == null || detail.isBlank() ? "Runtime error" : detail;
        }

        public String modId() { return modId; }
        public String name() { return name; }
        public String version() { return version; }
        public String authors() { return authors; }
        public int color() { return color; }
        public int apiVersion() { return apiVersion; }
        public int hostApiVersion() { return hostApiVersion; }
        public AddonLoadStatus status() { return status; }
        public String phase() { return phase; }
        public String failureReason() { return failureReason; }
        public int runtimeErrors() { return runtimeErrors; }
        public String lastRuntimeError() { return lastRuntimeError; }
        public Map<String, Integer> accepted() { return Collections.unmodifiableMap(accepted); }
        public Map<String, Integer> rejected() { return Collections.unmodifiableMap(rejected); }
        public List<String> rejectionDetails() { return Collections.unmodifiableList(rejectionDetails); }
        public List<String> rollbackDetails() { return Collections.unmodifiableList(rollbackDetails); }

        public int acceptedTotal() {
            int total = 0;
            for (int value : accepted.values()) total += value;
            return total;
        }

        public int rejectedTotal() {
            int total = 0;
            for (int value : rejected.values()) total += value;
            return total;
        }

        public String summaryCounts() {
            if (accepted.isEmpty()) return "No registered extensions";
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : accepted.entrySet()) {
                parts.add(entry.getValue() + " " + entry.getKey());
            }
            return String.join(", ", parts);
        }

        public String copyReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("Addon: ").append(name).append('\n');
            sb.append("ID: ").append(modId).append('\n');
            if (!version.isBlank()) sb.append("Version: ").append(version).append('\n');
            if (!authors.isBlank()) sb.append("Authors: ").append(authors).append('\n');
            sb.append("Status: ").append(status.label()).append('\n');
            sb.append("Phase: ").append(phase).append('\n');
            sb.append("API: addon v").append(apiVersion < 0 ? "unknown" : Integer.toString(apiVersion))
                .append(", host v").append(hostApiVersion).append('\n');
            sb.append("Disabled on restart: ").append(isDisabledOnRestart(modId)).append('\n');
            if (!failureReason.isBlank()) sb.append("Reason: ").append(failureReason).append('\n');
            sb.append("Accepted: ").append(accepted).append('\n');
            if (!rejected.isEmpty()) sb.append("Rejected: ").append(rejected).append('\n');
            for (String detail : rejectionDetails) sb.append("- ").append(detail).append('\n');
            for (String detail : rollbackDetails) sb.append("- ").append(detail).append('\n');
            if (runtimeErrors > 0) {
                sb.append("Runtime errors: ").append(runtimeErrors).append('\n');
                sb.append("Last runtime error: ").append(lastRuntimeError).append('\n');
            }
            return sb.toString();
        }
    }
}
