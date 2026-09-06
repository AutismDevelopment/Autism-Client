package autismclient.util.multi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class MultiProfile {
    public static final int MAX_SESSIONS = 500;
    public static final String DEFAULT_ACCOUNT_ID = "default";
    public static final String BEST_PROXY_ID = "best";
    public static final int QUICK_ACTIONS = 4;

    public enum Pacing {
        Gentle(1, 1000),
        Balanced(4, 250),
        Fast(8, 100),
        Immediate(MAX_SESSIONS, 0),
        Custom(4, 250);

        private final int concurrency;
        private final int delayMs;

        Pacing(int concurrency, int delayMs) {
            this.concurrency = concurrency;
            this.delayMs = delayMs;
        }

        public int concurrency() {
            return concurrency;
        }

        public int delayMs() {
            return delayMs;
        }
    }

    public enum LoginMode { Off, Auto, Custom }

    public enum ProxyMode {
        Off,
        Auto,
        Manual
    }

    public record SessionSpec(String accountId, String proxyId, String macroName) {
        public SessionSpec {
            accountId = accountId == null ? "" : accountId.trim();
            proxyId = proxyId == null ? "" : proxyId.trim();
            macroName = macroName == null ? "" : macroName.trim();
        }

        public SessionSpec(String accountId, String proxyId) {
            this(accountId, proxyId, "");
        }

        public boolean direct() {
            return proxyId.isBlank();
        }

        public boolean bestProxy() {
            return BEST_PROXY_ID.equals(proxyId);
        }

        public SessionSpec withMacro(String macro) {
            return new SessionSpec(accountId, proxyId, macro);
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("accountId", accountId);
            tag.putString("proxyId", proxyId);
            if (!macroName.isBlank()) tag.putString("macroName", macroName);
            return tag;
        }

        static SessionSpec fromTag(CompoundTag tag) {
            return new SessionSpec(tag.getStringOr("accountId", ""), tag.getStringOr("proxyId", ""),
                tag.getStringOr("macroName", ""));
        }
    }

    public String id = UUID.randomUUID().toString();
    public String name = "New profile";
    public String serverAddress = "";
    public Pacing pacing = Pacing.Balanced;
    public ProxyMode proxyMode = ProxyMode.Off;
    public int customConcurrency = 4;
    public int customDelayMs = 250;
    public int autoMaxPingMs = 200;
    public String allMacroName = "";
    public boolean runMacroWhileJoining;
    public LoginMode loginMode = LoginMode.Auto;
    public final List<SessionSpec> sessions = new ArrayList<>();
    private final Map<String, Map<String, String>> sealedFormValues = new LinkedHashMap<>();
    public MultiPacketPolicy packetPolicy = new MultiPacketPolicy();
    public MultiAutoAccept autoAccept = new MultiAutoAccept();
    public final List<MultiQuickAction> quickActions = new ArrayList<>(defaultQuickActions());

    public MultiProfile() {
    }

    public MultiProfile(MultiProfile source) {
        if (source == null) return;
        id = source.id;
        name = source.name;
        serverAddress = source.serverAddress;
        pacing = source.pacing;
        proxyMode = source.proxyMode;
        customConcurrency = source.customConcurrency;
        customDelayMs = source.customDelayMs;
        autoMaxPingMs = source.autoMaxPingMs;
        allMacroName = source.allMacroName;
        runMacroWhileJoining = source.runMacroWhileJoining;
        loginMode = source.loginMode;
        sessions.addAll(source.sessions);
        source.sealedFormValues.forEach((account, values) ->
            sealedFormValues.put(account, new LinkedHashMap<>(values)));
        packetPolicy = new MultiPacketPolicy(source.packetPolicy);
        autoAccept = new MultiAutoAccept(source.autoAccept);
        quickActions.clear();
        for (MultiQuickAction action : source.quickActions) quickActions.add(new MultiQuickAction(action));
        normalize();
    }

    public int concurrency() {
        return pacing == Pacing.Custom ? customConcurrency : pacing.concurrency();
    }

    public int delayMs() {
        return pacing == Pacing.Custom ? customDelayMs : pacing.delayMs();
    }

    public void normalize() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        name = name == null || name.isBlank() ? "New profile" : name.trim();
        serverAddress = serverAddress == null ? "" : serverAddress.trim();
        if (pacing == null) pacing = Pacing.Balanced;
        if (proxyMode == null) proxyMode = ProxyMode.Off;
        allMacroName = allMacroName == null ? "" : allMacroName.trim();
        customConcurrency = Math.max(1, Math.min(MAX_SESSIONS, customConcurrency));
        customDelayMs = Math.max(0, Math.min(5000, customDelayMs));
        autoMaxPingMs = Math.max(50, Math.min(1000, autoMaxPingMs));
        if (packetPolicy == null) packetPolicy = new MultiPacketPolicy();
        if (autoAccept == null) autoAccept = new MultiAutoAccept();
        autoAccept.normalize();
        normalizeQuickActions();
        Set<String> accounts = new HashSet<>();
        sessions.removeIf(spec -> spec == null || spec.accountId().isBlank() || !accounts.add(spec.accountId()));
        while (sessions.size() > MAX_SESSIONS) sessions.remove(sessions.size() - 1);
        sealedFormValues.keySet().removeIf(account -> !accounts.contains(account));
        sealedFormValues.values().forEach(values -> values.entrySet().removeIf(entry ->
            normalizeSecretName(entry.getKey()).isEmpty() || entry.getValue() == null || entry.getValue().isBlank()));
        sealedFormValues.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public boolean setFormValue(String accountId, String name, String value) {
        String account = accountId == null ? "" : accountId.trim();
        String key = normalizeSecretName(name);
        if (account.isEmpty() || key.isEmpty() || value == null) return false;
        byte[] sealed = autismclient.util.mm.crypto.AtRestSeal.seal(value.getBytes(StandardCharsets.UTF_8));
        sealedFormValues.computeIfAbsent(account, ignored -> new LinkedHashMap<>())
            .put(key, Base64.getEncoder().encodeToString(sealed));
        return true;
    }

    public void removeFormValue(String accountId, String name) {
        Map<String, String> values = sealedFormValues.get(accountId == null ? "" : accountId.trim());
        if (values == null) return;
        values.remove(normalizeSecretName(name));
        if (values.isEmpty()) sealedFormValues.remove(accountId == null ? "" : accountId.trim());
    }

    public void clearFormValues(String accountId) {
        sealedFormValues.remove(accountId == null ? "" : accountId.trim());
    }

    public Set<String> formValueNames(String accountId) {
        Map<String, String> values = sealedFormValues.get(accountId == null ? "" : accountId.trim());
        return values == null ? Set.of() : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(values.keySet()));
    }

    public Map<String, String> openFormValues(String accountId) {
        Map<String, String> sealed = sealedFormValues.get(accountId == null ? "" : accountId.trim());
        if (sealed == null || sealed.isEmpty()) return Map.of();
        Map<String, String> opened = new LinkedHashMap<>();
        sealed.forEach((name, encoded) -> {
            try {
                byte[] plain = autismclient.util.mm.crypto.AtRestSeal.unseal(Base64.getDecoder().decode(encoded));
                if (plain != null) opened.put(name, new String(plain, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {  }
        });
        return Map.copyOf(opened);
    }

    public static String normalizeSecretName(String name) {
        if (name == null) return "";
        String value = name.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("secret.")) value = value.substring("secret.".length());
        return value.matches("[a-z0-9_.-]{1,64}") ? value : "";
    }

    public MultiQuickAction quickAction(int index) {
        if (index < 0 || index >= QUICK_ACTIONS) throw new IndexOutOfBoundsException(index);
        normalizeQuickActions();
        return new MultiQuickAction(quickActions.get(index));
    }

    public void setQuickAction(int index, MultiQuickAction action) {
        if (index < 0 || index >= QUICK_ACTIONS) throw new IndexOutOfBoundsException(index);
        normalizeQuickActions();
        quickActions.set(index, action == null ? new MultiQuickAction() : new MultiQuickAction(action));
    }

    private void normalizeQuickActions() {
        while (quickActions.size() < QUICK_ACTIONS) quickActions.add(new MultiQuickAction());
        while (quickActions.size() > QUICK_ACTIONS) quickActions.remove(quickActions.size() - 1);
        for (int i = 0; i < quickActions.size(); i++) {
            MultiQuickAction action = quickActions.get(i);
            if (action == null) action = new MultiQuickAction();
            action.normalize();
            quickActions.set(i, action);
        }
    }

    public void resetQuickActions() {
        quickActions.clear();
        quickActions.addAll(defaultQuickActions());
    }

    public boolean replaceMacroReference(String oldName, String newName) {
        String before = oldName == null ? "" : oldName.trim();
        if (before.isBlank()) return false;
        String after = newName == null ? "" : newName.trim();
        boolean changed = false;
        if (before.equals(allMacroName)) {
            allMacroName = after;
            changed = true;
        }
        for (int i = 0; i < sessions.size(); i++) {
            SessionSpec spec = sessions.get(i);
            if (before.equals(spec.macroName())) {
                sessions.set(i, spec.withMacro(after));
                changed = true;
            }
        }
        return changed;
    }

    public CompoundTag toTag() {
        normalize();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("server", serverAddress);
        tag.putString("pacing", pacing.name());
        tag.putString("proxyMode", proxyMode.name());
        tag.putInt("customConcurrency", customConcurrency);
        tag.putInt("customDelayMs", customDelayMs);
        tag.putInt("autoMaxPingMs", autoMaxPingMs);
        tag.putString("allMacroName", allMacroName);
        tag.putBoolean("runMacroWhileJoining", runMacroWhileJoining);
        tag.putString("loginMode", loginMode.name());
        ListTag sessionTags = new ListTag();
        for (SessionSpec session : sessions) sessionTags.add(session.toTag());
        tag.put("sessions", sessionTags);
        ListTag formAccounts = new ListTag();
        sealedFormValues.forEach((accountId, values) -> {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putString("accountId", accountId);
            ListTag valueTags = new ListTag();
            values.forEach((key, sealed) -> {
                CompoundTag valueTag = new CompoundTag();
                valueTag.putString("name", key);
                valueTag.putString("sealed", sealed);
                valueTags.add(valueTag);
            });
            accountTag.put("values", valueTags);
            formAccounts.add(accountTag);
        });
        tag.put("formValues", formAccounts);
        tag.put("packetPolicy", packetPolicy.toTag());
        tag.put("autoAccept", autoAccept.toTag());
        ListTag actionTags = new ListTag();
        for (MultiQuickAction action : quickActions) actionTags.add(action.toTag());
        tag.put("quickActions", actionTags);
        return tag;
    }

    public static MultiProfile fromTag(CompoundTag tag) {
        MultiProfile profile = new MultiProfile();
        profile.id = tag.getStringOr("id", profile.id);
        profile.name = tag.getStringOr("name", profile.name);
        profile.serverAddress = tag.getStringOr("server", "");
        String pacingName = tag.getStringOr("pacing", Pacing.Balanced.name());
        profile.pacing = Pacing.Balanced;
        for (Pacing value : Pacing.values()) {
            if (value.name().equalsIgnoreCase(pacingName)) {
                profile.pacing = value;
                break;
            }
        }
        profile.customConcurrency = tag.getIntOr("customConcurrency", 4);
        profile.customDelayMs = tag.getIntOr("customDelayMs", 250);
        profile.autoMaxPingMs = tag.getIntOr("autoMaxPingMs", 200);

        profile.allMacroName = tag.getStringOr("allMacroName", "");
        profile.runMacroWhileJoining = tag.getBooleanOr("runMacroWhileJoining", false);
        profile.loginMode = LoginMode.Auto;
        String loginModeName = tag.getStringOr("loginMode", "");
        for (LoginMode value : LoginMode.values()) {
            if (value.name().equalsIgnoreCase(loginModeName)) {
                profile.loginMode = value;
                break;
            }
        }
        profile.sessions.clear();
        for (Tag value : tag.getListOrEmpty("sessions")) {
            if (value instanceof CompoundTag compound) profile.sessions.add(SessionSpec.fromTag(compound));
        }
        profile.sealedFormValues.clear();
        for (Tag accountValue : tag.getListOrEmpty("formValues")) {
            if (!(accountValue instanceof CompoundTag accountTag)) continue;
            String accountId = accountTag.getStringOr("accountId", "").trim();
            if (accountId.isEmpty()) continue;
            Map<String, String> values = new LinkedHashMap<>();
            for (Tag formValue : accountTag.getListOrEmpty("values")) {
                if (!(formValue instanceof CompoundTag valueTag)) continue;
                String key = normalizeSecretName(valueTag.getStringOr("name", ""));
                String sealed = valueTag.getStringOr("sealed", "");
                if (!key.isEmpty() && !sealed.isBlank()) values.put(key, sealed);
            }
            if (!values.isEmpty()) profile.sealedFormValues.put(accountId, values);
        }

        String proxyModeName = tag.getStringOr("proxyMode", "");
        if (proxyModeName.isBlank()) {
            boolean anyProxy = profile.sessions.stream().anyMatch(spec -> !spec.proxyId().isBlank());
            profile.proxyMode = anyProxy ? ProxyMode.Manual : ProxyMode.Off;
        } else {
            profile.proxyMode = ProxyMode.Off;
            for (ProxyMode value : ProxyMode.values()) {
                if (value.name().equalsIgnoreCase(proxyModeName)) {
                    profile.proxyMode = value;
                    break;
                }
            }
        }
        profile.packetPolicy = tag.getCompound("packetPolicy").map(MultiPacketPolicy::fromTag).orElseGet(MultiPacketPolicy::new);
        profile.autoAccept = tag.getCompound("autoAccept").map(MultiAutoAccept::fromTag).orElseGet(MultiAutoAccept::new);
        profile.quickActions.clear();
        ListTag actionTags = tag.getListOrEmpty("quickActions");
        if (actionTags.isEmpty()) {
            profile.quickActions.addAll(migrateQuickActions(profile.packetPolicy));
        } else {
            for (Tag value : actionTags) {
                if (value instanceof CompoundTag compound) profile.quickActions.add(MultiQuickAction.fromTag(compound));
            }
        }
        profile.normalize();
        return profile;
    }

    public static List<MultiQuickAction> defaultQuickActions() {
        List<MultiQuickAction> actions = new ArrayList<>(QUICK_ACTIONS);
        for (int i = 0; i < QUICK_ACTIONS; i++) actions.add(new MultiQuickAction());
        return actions;
    }

    private static List<MultiQuickAction> migrateQuickActions(MultiPacketPolicy policy) {
        List<MultiQuickAction> actions = defaultQuickActions();
        if (policy == null) return actions;
        List<MultiPacketPolicy.Slot> slots = policy.slots();
        for (int i = 0; i < Math.min(QUICK_ACTIONS, slots.size()); i++) {
            MultiPacketPolicy.Slot slot = slots.get(i);
            if (slot != null && slot.enabled() && !slot.packetClass().isBlank()) {
                actions.set(i, new MultiQuickAction("Slot " + (i + 1), slot.packetClass(), ""));
            }
        }
        return actions;
    }
}
