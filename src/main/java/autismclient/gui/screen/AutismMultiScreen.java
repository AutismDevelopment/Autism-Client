package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismAccount;
import autismclient.util.AutismAccountManager;
import autismclient.util.AutismAccountSessionSwitcher;
import autismclient.util.AutismAccountType;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiProfile;
import autismclient.util.multi.MultiProfileManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static autismclient.gui.screen.AutismScreenPalette.BG;
import static autismclient.gui.screen.AutismScreenPalette.BORDER;
import static autismclient.gui.screen.AutismScreenPalette.BORDER_ACTIVE;
import static autismclient.gui.screen.AutismScreenPalette.ERROR;
import static autismclient.gui.screen.AutismScreenPalette.MUTED;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG_SOFT;
import static autismclient.gui.screen.AutismScreenPalette.SUCCESS;
import static autismclient.gui.screen.AutismScreenPalette.TEXT;

public final class AutismMultiScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int MARGIN = 14;
    private static final int LEFT_WIDTH = 176;
    private static final int GAP = 8;
    private static final int ROW_HEIGHT = 24;
    private static final int[] PING_OPTIONS = {50, 100, 200, 300, 500, 1000};
    private static final List<String> PACING_OPTIONS = List.of("Gentle", "Balanced", "Fast", "Immediate", "Custom");
    private static final List<String> PROXY_MODE_OPTIONS = List.of("Off", "Auto", "Manual");

    private final Screen parent;
    private final List<CompactDropdown> dropdowns = new ArrayList<>();
    private final String prefillServer;
    private final boolean openedFromActiveConsole;
    private final EnumSet<AutismAccountType> typeFilters = EnumSet.allOf(AutismAccountType.class);
    private MultiProfile draft;
    private String selectedProfileId;
    private boolean dirty;
    private long lastDirtyAt;
    private long savedFlashAt;
    private EditBox nameField;
    private EditBox serverField;
    private EditBox concurrencyField;
    private EditBox delayField;
    private autismclient.util.AutismChatField accountSearchField;
    private int profileScroll;
    private int accountScroll;
    private String lastActiveKey = "";

    public AutismMultiScreen(Screen parent, String prefillServer) {
        this(parent, prefillServer, false);
    }

    public AutismMultiScreen(Screen parent, String prefillServer, boolean openedFromActiveConsole) {
        super(Component.literal("Multi"));
        this.parent = parent;
        this.prefillServer = prefillServer == null ? "" : prefillServer.trim();
        this.openedFromActiveConsole = openedFromActiveConsole;
        List<MultiProfile> profiles = MultiProfileManager.get().all();
        MultiProfile active = MultiManager.get().activeProfile();

        MultiProfile shared = active == null ? MultiProfileManager.get().find(MultiProfileManager.get().selectedId()) : null;
        this.draft = active != null ? active : shared != null ? shared : profiles.isEmpty() ? newProfile() : new MultiProfile(profiles.getFirst());
        this.selectedProfileId = active != null || !profiles.isEmpty() ? this.draft.id : null;
        if (selectedProfileId != null) MultiProfileManager.get().setSelectedId(selectedProfileId);
    }

    @Override
    protected void init() {
        lastActiveKey = activeKey();
        rebuildControls();
    }

    @Override
    public void tick() {
        super.tick();

        if (dirty && System.currentTimeMillis() - lastDirtyAt >= 500L) {
            commit();
        }

        String key = activeKey();
        if (!key.equals(lastActiveKey) && !(getFocused() instanceof EditBox)) {
            captureFields();
            lastActiveKey = key;
            rebuildControls();
        }
    }

    private String activeKey() {
        MultiManager manager = MultiManager.get();
        return Boolean.toString(manager.isActive());
    }

    private void rebuildControls() {
        clearWidgets();
        dropdowns.clear();
        int ix = rightX() + 12;
        int innerW = rightWidth() - 24;
        int half = (innerW - 6) / 2;
        boolean custom = draft.pacing == MultiProfile.Pacing.Custom;
        boolean auto = draft.proxyMode == MultiProfile.ProxyMode.Auto;
        boolean locked = isActiveProfile(draft.id);
        nameField = serverField = concurrencyField = delayField = null;

        if (locked) buildActiveReadOnly(ix, innerW);
        if (!locked) {

        nameField = field(ix, 40, half, "Profile name", draft.name, 64);
        serverField = field(ix + half + 6, 40, innerW - half - 6, "host:port", draft.serverAddress, 255);

        if (auto) {
            int third = (innerW - 12) / 3;
            addPacingDropdown(ix, 66, third);
            addProxyModeDropdown(ix + third + 6, 66, third);
            int pingX = ix + 2 * (third + 6);
            dropdowns.add(new CompactDropdown(pingX, 66, innerW - 2 * (third + 6), 18,
                pingOptionLabels(), pingIndex(draft.autoMaxPingMs), index -> {
                    captureFields();
                    if (index >= 0 && index < PING_OPTIONS.length) draft.autoMaxPingMs = PING_OPTIONS[index];
                    commit();
                    rebuildControls();
                }).setButtonLabelOverride("<=" + draft.autoMaxPingMs + "ms"));
        } else {
            addPacingDropdown(ix, 66, half);
            addProxyModeDropdown(ix + half + 6, 66, innerW - half - 6);
        }

        if (custom) {
            concurrencyField = field(ix, 92, half, "Accounts 1-500", Integer.toString(draft.customConcurrency), 4);
            delayField = field(ix + half + 6, 92, innerW - half - 6, "Delay 0-5000ms", Integer.toString(draft.customDelayMs), 6);
        } else {
            concurrencyField = null;
            delayField = null;
        }

        int macroY = 92 + (custom ? 26 : 0);
        String allLabel = draft.allMacroName.isBlank() ? "Macro (all): none" : "Macro (all): " + draft.allMacroName;
        addStyled(ix, macroY, innerW, 18, allLabel,
            draft.allMacroName.isBlank() ? Button.Tone.NORMAL : Button.Tone.PRIMARY, b -> pickAllMacro());

        int formY = macroY + 22;
        addStyled(ix, formY, innerW, 18, "Passwords (login)", Button.Tone.NORMAL, b -> openFormValues());

        if (!compactVertical()) addAccountFilterChips(ix, chipsY(), innerW);
        }

        int footerY = screenHeight() - 32;
        int profileFooterY = compactFooter() ? screenHeight() - 54 : footerY;
        addStyled(MARGIN, profileFooterY, 60, 18, "New", Button.Tone.PRIMARY, b -> createNew());
        addStyled(MARGIN + 64, profileFooterY, 76, 18, "Duplicate", Button.Tone.NORMAL, b -> duplicateDraft());

        if (MultiManager.get().isActive()) {
            int activeW = Math.min(132, Math.max(1, screenWidth() - MARGIN * 2 - 146));
            AutismStyledButton activeButton = new AutismStyledButton(MARGIN + 146, profileFooterY, activeW, 18,
                Component.literal("Active Multi"), Button.Tone.SUCCESS,
                () -> "Active " + MultiManager.get().readyFraction(), b -> openActiveConsole());
            addRenderableWidget(activeButton);
        }

        int footerX = rightX() + 12;
        int footerW = Math.max(1, rightWidth() - 24);
        int footerGap = footerW >= 220 ? 6 : 3;
        boolean compactActiveReturn = openedFromActiveConsole && MultiManager.get().isActive();
        int count = compactActiveReturn ? 2 : 3;
        int footerEach = Math.max(1, (footerW - footerGap * (count - 1)) / count);
        int fx = footerX;
        if (!compactActiveReturn) {
            addStyled(fx, footerY, footerEach, 18, "Advanced", Button.Tone.NORMAL, b -> openAdvanced());
            fx += footerEach + footerGap;
        }
        addStyled(fx, footerY, footerEach, 18, "Connect", Button.Tone.SUCCESS, b -> connect());
        fx += footerEach + footerGap;
        addStyled(fx, footerY, Math.max(1, footerX + footerW - fx), 18, "Back", Button.Tone.NORMAL, b -> onClose());

        int mix = rightX() + 12;
        int minnerW = rightWidth() - 24;
        addStyled(mix + minnerW - MANAGE_BTN_W, accountsBaseY() + 12, MANAGE_BTN_W, 16,
            "Accounts...", Button.Tone.NORMAL, b -> openAccounts());

        if (showBulkProxy()) {
            String commonProxy = commonManualProxyId();
            String label = commonProxy == null
                ? "Set All Proxies (Mixed)..."
                : "Set All Proxies: " + proxyLabel(commonProxy);
            addStyled(mix, accountsHeaderBaseY(), minnerW, 18, label, Button.Tone.PRIMARY,
                button -> openManualProxyPicker("Proxy for all selected accounts", commonProxy, this::setAllProxies));
        }

        addProfileButtons();
        addAccountButtons();
    }

    private void openAccounts() {
        captureFields();
        if (minecraft != null) minecraft.gui.setScreen(new AutismAccountsScreen(this));
    }

    private void buildActiveReadOnly(int ix, int innerW) {
        disabledLabel(ix, 40, innerW, "Profile: " + draft.name);
        disabledLabel(ix, 62, innerW, "Server: " + (draft.serverAddress.isBlank() ? "-" : draft.serverAddress));
        disabledLabel(ix, 84, innerW, draft.sessions.size() + " accounts");
        AutismStyledButton note = addStyled(ix, 110, innerW, 18, "Batch running - Disconnect to edit", Button.Tone.DANGER, b -> {});
        note.active = false;
    }

    private void disabledLabel(int x, int y, int w, String text) {
        AutismStyledButton label = addStyled(x, y, w, 18, text, Button.Tone.NORMAL, b -> {});
        label.active = false;
    }

    private EditBox field(int x, int y, int w, String hint, String value, int max) {
        EditBox box = new EditBox(font, x, y, Math.max(1, w), 18, Component.literal(hint));
        box.setMaxLength(max);
        box.setValue(value);
        box.setHint(Component.literal(hint));
        box.setResponder(v -> markDirty());
        addRenderableWidget(box);
        return box;
    }

    private void addAccountFilterChips(int ix, int y, int innerW) {
        String[] labels = {"Cracked", "Session", "Microsoft", "TheAltening"};
        AutismAccountType[] types = {AutismAccountType.Cracked, AutismAccountType.Session, AutismAccountType.Microsoft, AutismAccountType.TheAltening};
        int gap = 4;
        int chipW = (innerW - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            AutismAccountType type = types[i];
            Button.Tone tone = typeFilters.contains(type) ? Button.Tone.SUCCESS : Button.Tone.NORMAL;
            AutismStyledButton filter = new AutismStyledButton(ix + i * (chipW + gap), y, Math.max(1, chipW), 14,
                Component.literal(fitLabel(labels[i], chipW - 6)), tone, b -> {
                captureFields();
                if (!typeFilters.add(type)) typeFilters.remove(type);
                accountScroll = 0;
                rebuildControls();
            });
            filter.setToggled(typeFilters.contains(type));
            addRenderableWidget(filter);
        }
    }

    private void addProfileButtons() {
        List<MultiProfile> profiles = MultiProfileManager.get().all();
        int top = 40;
        int bottom = profileListBottom();
        int visible = Math.max(1, (bottom - top) / ROW_HEIGHT);
        profileScroll = Math.max(0, Math.min(profileScroll, Math.max(0, profiles.size() - visible)));
        int end = Math.min(profiles.size(), profileScroll + visible);
        int y = top;
        int nameW = Math.max(24, leftWidth() - 16 - 26);
        for (int i = profileScroll; i < end; i++) {
            MultiProfile profile = profiles.get(i);
            boolean picked = profile.id.equals(selectedProfileId);
            Button.Tone tone = picked ? Button.Tone.SUCCESS : Button.Tone.NORMAL;
            String mark = picked ? "> " : "";
            addStyled(MARGIN + 8, y, nameW, 20, mark + profile.name, tone, b -> selectProfileRow(profile))
                .setToggled(picked);
            AutismStyledButton delete = addStyled(MARGIN + 8 + nameW + 4, y, 22, 20, "X", Button.Tone.DANGER,
                b -> deleteProfile(profile));
            delete.active = !isActiveProfile(profile.id);
            y += ROW_HEIGHT;
        }
    }

    private void addAccountButtons() {
        List<AccountChoice> choices = accountChoices();
        boolean manual = draft.proxyMode == MultiProfile.ProxyMode.Manual;
        boolean locked = isActiveProfile(draft.id);
        int top = accountsTop();
        int bottom = accountListBottom();
        int visible = Math.max(1, (bottom - top) / ROW_HEIGHT);
        accountScroll = Math.max(0, Math.min(accountScroll, Math.max(0, choices.size() - visible)));
        int end = Math.min(choices.size(), accountScroll + visible);
        int x = rightX() + 12;
        int w = rightWidth() - 24;
        int y = top;
        for (int i = accountScroll; i < end; i++) {
            AccountChoice choice = choices.get(i);
            boolean current = choice.current();

            MultiProfile.SessionSpec selected = current ? null : selectedSpec(choice.id());
            Button.Tone tone = current ? Button.Tone.DANGER : selected == null ? Button.Tone.NORMAL : Button.Tone.SUCCESS;
            boolean canToggle = !current;
            String accountLabel = choice.label() + (current ? " (Current)" : "");
            if (manual) {
                int proxyW = Math.max(1, Math.min(128, Math.max(36, w / 3)));
                if (proxyW + 4 >= w) proxyW = Math.max(1, w / 2);
                int toggleW = Math.max(1, w - proxyW - 4);
                AutismStyledButton toggle = addStyled(x, y, toggleW, 20, accountLabel, tone, b -> toggleAccount(choice.id()));
                toggle.active = canToggle && !locked;
                toggle.setToggled(selected != null || current);
                if (selected == null) {
                    AutismStyledButton proxyButton = addStyled(x + toggleW + 4, y, proxyW, 20,
                        "-", Button.Tone.NORMAL, b -> { });
                    proxyButton.active = false;
                } else {
                    AutismStyledButton proxyButton = addStyled(x + toggleW + 4, y, proxyW, 20,
                        proxyLabel(selected.proxyId()), Button.Tone.PRIMARY,
                        button -> openManualProxyPicker("Proxy for " + choice.label(), selected.proxyId(), proxyId -> {
                            MultiProfile.SessionSpec spec = selectedSpec(choice.id());
                            if (spec != null) setSessionProxy(spec, proxyId);
                        }));
                    proxyButton.active = !choice.current() && !locked;
                }
            } else {
                AutismStyledButton toggle = addStyled(x, y, w, 20, accountLabel, tone, b -> toggleAccount(choice.id()));
                toggle.active = canToggle && !locked;
                toggle.setToggled(selected != null || current);
            }
            y += ROW_HEIGHT;
        }
    }

    private void captureFields() {
        if (nameField != null) draft.name = safeTrim(nameField.getValue());
        if (serverField != null) draft.serverAddress = safeTrim(serverField.getValue());
        if (concurrencyField != null) draft.customConcurrency = parseInt(concurrencyField.getValue(), draft.customConcurrency);
        if (delayField != null) draft.customDelayMs = parseInt(delayField.getValue(), draft.customDelayMs);
        draft.normalize();
    }

    private void markDirty() {
        dirty = true;
        lastDirtyAt = System.currentTimeMillis();
    }

    private void commit() {
        captureFields();
        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        MultiProfileManager.get().setSelectedId(draft.id);
        boolean wasDirty = dirty;
        dirty = false;
        if (wasDirty) savedFlashAt = System.currentTimeMillis();
    }

    private void connect() {
        captureFields();
        draft.name = MultiProfileManager.get().nextAvailableName(draft.name, draft.id);
        MultiManager.StartResult result = MultiManager.get().start(draft);
        if (!result.ok()) {
            toast(MultiManager.singleLine(result.message(), 90), ERROR);
            return;
        }

        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        dirty = false;
        minecraft.gui.setScreen(new AutismMultiConsoleScreen(parent));
    }

    private void openActiveConsole() {
        if (minecraft != null) minecraft.gui.setScreen(new AutismMultiConsoleScreen(parent));
    }

    private void createNew() {
        if (dirty) commit();
        draft = newProfile();

        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        MultiProfileManager.get().setSelectedId(draft.id);
        dirty = false;
        profileScroll = 0;
        accountScroll = 0;
        toast("New profile created.", SUCCESS);
        rebuildControls();
    }

    private void duplicateDraft() {
        if (dirty) commit();
        MultiProfile copy = new MultiProfile(draft);
        copy.id = java.util.UUID.randomUUID().toString();
        copy.name = MultiProfileManager.get().nextAvailableName(copy.name, copy.id);
        draft = copy;

        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        dirty = false;
        toast("Duplicated.", SUCCESS);
        rebuildControls();
    }

    private void deleteProfile(MultiProfile profile) {
        captureFields();
        if (profile != null && isActiveProfile(profile.id)) {
            toast("Disconnect the active profile first.", ERROR);
            return;
        }
        MultiProfileManager.get().remove(profile.id);
        if (profile.id.equals(selectedProfileId) || draft.id.equals(profile.id)) {
            List<MultiProfile> remaining = MultiProfileManager.get().all();
            draft = remaining.isEmpty() ? newProfile() : new MultiProfile(remaining.getFirst());
            selectedProfileId = remaining.isEmpty() ? null : draft.id;
            dirty = false;
        }
        toast("Deleted " + MultiManager.singleLine(profile.name, 40) + ".", MUTED);
        rebuildControls();
    }

    private void openAdvanced() {
        captureFields();
        minecraft.gui.setScreen(new AutismMultiPacketPolicyScreen(this, draft.packetPolicy, policy -> {
            draft.packetPolicy = policy;
            commit();
        }, draft.autoAccept, cfg -> {
            draft.autoAccept = cfg;
            commit();
        }, false));
    }

    private void openFormValues() {
        captureFields();
        Set<String> accounts = new java.util.LinkedHashSet<>();
        for (MultiProfile.SessionSpec spec : draft.sessions) accounts.add(spec.accountId());
        minecraft.gui.setScreen(new AutismFormValuesScreen(this, draft, accounts, updated -> {
            draft = updated;
            commit();
            rebuildControls();
        }));
    }

    private void loadProfile(MultiProfile profile) {
        if (dirty) commit();
        draft = new MultiProfile(profile);
        selectedProfileId = draft.id;
        MultiProfileManager.get().setSelectedId(draft.id);
        dirty = false;
        accountScroll = 0;
        rebuildControls();
    }

    private void selectProfileRow(MultiProfile profile) {
        if (profile == null) return;
        if (profile.id.equals(selectedProfileId)) {

            return;
        }
        loadProfile(profile);
    }

    private void addPacingDropdown(int x, int y, int width) {
        dropdowns.add(new CompactDropdown(x, y, width, 18, PACING_OPTIONS, draft.pacing.ordinal(), index -> {
            captureFields();
            if (index >= 0 && index < MultiProfile.Pacing.values().length) {
                draft.pacing = MultiProfile.Pacing.values()[index];
                commit();
                rebuildControls();
            }
        }).setButtonLabelOverride("Join speed: " + draft.pacing.name()));
    }

    private void addProxyModeDropdown(int x, int y, int width) {
        dropdowns.add(new CompactDropdown(x, y, width, 18, PROXY_MODE_OPTIONS, draft.proxyMode.ordinal(), index -> {
            captureFields();
            if (index >= 0 && index < MultiProfile.ProxyMode.values().length) {
                draft.proxyMode = MultiProfile.ProxyMode.values()[index];
                commit();
                rebuildControls();
            }
        }).setButtonLabelOverride("Proxy: " + proxyModeLabel()));
    }

    private void pickAllMacro() {
        captureFields();
        minecraft.gui.setScreen(new AutismMultiMacroPickerScreen(this, draft.allMacroName, name -> {
            draft.allMacroName = name == null ? "" : name;
            commit();
        }));
    }

    private void toggleAccount(String accountId) {
        if (isActiveProfile(draft.id)) return;
        if (MultiManager.isCurrentRenderedAccount(accountId)) {

            toast("Current account cannot join Multi.", ERROR);
            return;
        }
        captureFields();
        MultiProfile.SessionSpec existing = selectedSpec(accountId);
        if (existing != null) {
            draft.sessions.remove(existing);
        } else if (draft.sessions.size() < MultiProfile.MAX_SESSIONS) {
            draft.sessions.add(new MultiProfile.SessionSpec(accountId, ""));
        } else {
            toast("Maximum " + MultiProfile.MAX_SESSIONS + " accounts.", ERROR);
        }
        commit();
        rebuildControls();
    }

    private void setSessionProxy(MultiProfile.SessionSpec existing, String proxyId) {
        if (isActiveProfile(draft.id)) return;
        int index = draft.sessions.indexOf(existing);
        if (index >= 0) draft.sessions.set(index,
            new MultiProfile.SessionSpec(existing.accountId(), proxyId, existing.macroName()));
        commit();
        rebuildControls();
    }

    private void openManualProxyPicker(String title, String selectedProxyId, java.util.function.Consumer<String> onPick) {
        captureFields();
        if (minecraft != null) {
            minecraft.gui.setScreen(new AutismMultiProxyPickerScreen(
                this, title, draft.serverAddress, selectedProxyId, manualProxyUsage(), onPick));
        }
    }

    private java.util.Map<String, Integer> manualProxyUsage() {
        java.util.Map<String, Integer> usage = new java.util.LinkedHashMap<>();
        for (MultiProfile.SessionSpec spec : draft.sessions) {
            if (spec.direct() || spec.bestProxy()) continue;
            usage.merge(spec.proxyId(), 1, Integer::sum);
        }
        return java.util.Map.copyOf(usage);
    }

    private void setAllProxies(String proxyId) {
        if (isActiveProfile(draft.id)) return;
        if (draft.sessions.isEmpty()) {
            toast("Select some accounts first.", ERROR);
            return;
        }
        String id = proxyId == null ? "" : proxyId;
        for (int i = 0; i < draft.sessions.size(); i++) {
            MultiProfile.SessionSpec spec = draft.sessions.get(i);
            draft.sessions.set(i, new MultiProfile.SessionSpec(spec.accountId(), id, spec.macroName()));
        }
        commit();
        rebuildControls();
        toast("Set proxy on " + draft.sessions.size() + " account" + (draft.sessions.size() == 1 ? "" : "s") + ".", SUCCESS);
    }

    private MultiProfile.SessionSpec selectedSpec(String accountId) {
        for (MultiProfile.SessionSpec spec : draft.sessions) {
            if (spec.accountId().equals(accountId)) return spec;
        }
        return null;
    }

    private String proxyLabel(String proxyId) {
        if (proxyId == null || proxyId.isBlank()) return "Proxy Off";
        if (MultiProfile.BEST_PROXY_ID.equals(proxyId)) return "Best Proxy";
        AutismProxy proxy = AutismProxyManager.get().findById(proxyId);
        return proxy == null ? "Missing" : proxy.displayName();
    }

    private String proxyModeLabel() {
        return switch (draft.proxyMode) {
            case Off -> "Off";
            case Auto -> "Auto";
            case Manual -> "Manual";
        };
    }

    private String commonManualProxyId() {
        if (draft.sessions.isEmpty()) return "";
        String common = draft.sessions.getFirst().proxyId();
        for (int i = 1; i < draft.sessions.size(); i++) {
            if (!java.util.Objects.equals(common, draft.sessions.get(i).proxyId())) return null;
        }
        return common == null ? "" : common;
    }

    private List<AccountChoice> accountChoices() {
        List<AccountChoice> choices = new ArrayList<>();
        Set<String> known = new HashSet<>();
        choices.add(new AccountChoice(MultiProfile.DEFAULT_ACCOUNT_ID,
            AutismAccountSessionSwitcher.getOriginalUser().getName() + " (Default)",
            MultiManager.isCurrentRenderedAccount(MultiProfile.DEFAULT_ACCOUNT_ID)));
        known.add(MultiProfile.DEFAULT_ACCOUNT_ID);
        String query = accountSearchText().toLowerCase(Locale.ROOT);
        for (AutismAccount account : AutismAccountManager.get().all()) {
            known.add(account.stableId());
            AutismAccountType type = account.type == null ? AutismAccountType.Cracked : account.type;
            if (!typeFilters.contains(type)) continue;
            String label = account.displayName() + " (" + type.name() + ")";
            if (!query.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(query)) continue;
            choices.add(new AccountChoice(account.stableId(), label, MultiManager.isCurrentRenderedAccount(account.stableId())));
        }
        for (MultiProfile.SessionSpec spec : draft.sessions) {
            if (known.add(spec.accountId())) {
                choices.add(new AccountChoice(spec.accountId(), "Missing account: " + spec.accountId(), false));
            }
        }
        return choices;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (accountSearchField != null && accountSearchField.charTyped(event)) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && CompactDropdown.closeOpenMenu(dropdowns)) return true;
        if (accountSearchField != null && accountSearchField.keyPressed(event)) return true;
        return super.keyPressed(event);
    }

    private void ensureAccountSearchField() {
        if (accountSearchField == null) {
            accountSearchField = new autismclient.util.AutismChatField(
                net.minecraft.client.Minecraft.getInstance(), font, 0, 0, 20, 16, false);
            accountSearchField.setPlaceholder(Component.literal("Search accounts..."));
            accountSearchField.setMaxLength(48);
            accountSearchField.setChangedListener(value -> {
                accountScroll = 0;
                rebuildControls();
            });
        }
    }

    private String accountSearchText() {
        return accountSearchField == null ? "" : accountSearchField.getText();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double vx = AutismUiScale.toVirtual(mouseX);
        double vy = AutismUiScale.toVirtual(mouseY);
        if (CompactDropdown.mouseScrolled(dropdowns, vx, vy, vertical)) return true;
        if (vx < MARGIN + leftWidth()) {
            captureFields();
            profileScroll = Math.max(0, profileScroll + (vertical < 0 ? 1 : -1));
            rebuildControls();
            return true;
        }
        if (vx >= rightX() && vy >= accountsTop() - 4) {
            captureFields();
            accountScroll = Math.max(0, accountScroll + (vertical < 0 ? 1 : -1));
            rebuildControls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (CompactDropdown.mouseClicked(dropdowns, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
        if (accountSearchField != null && accountSearchField.mouseClicked(virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
            captureFields();
            clearInputFocus();
            return true;
        }
        return super.mouseClicked(virtualEvent, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (CompactDropdown.mouseReleased(dropdowns)) return true;
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (accountSearchField != null) accountSearchField.mouseReleased(virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
        return super.mouseReleased(virtualEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (CompactDropdown.mouseDragged(dropdowns, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
        double vdx = AutismUiScale.toVirtual(dragX);
        double vdy = AutismUiScale.toVirtual(dragY);
        if (accountSearchField != null && accountSearchField.mouseDragged(virtualEvent.x(), virtualEvent.y(), virtualEvent.button(), vdx, vdy)) return true;
        return super.mouseDragged(virtualEvent, vdx, vdy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
        UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), themeBg());
        int leftPanelHeight = Math.max(1, profileListBottom() + 6 - 14);
        int rightPanelHeight = Math.max(1, accountListBottom() + 6 - 14);
        UiRenderer.frame(graphics, UiBounds.of(MARGIN, 14, leftWidth(), leftPanelHeight), themePanelSoft(), themeBorder());
        UiRenderer.frame(graphics, UiBounds.of(rightX(), 14, rightWidth(), rightPanelHeight), themePanel(), themeBorder());
        drawText(graphics, "Profiles", MARGIN + 10, 24, themeText());
        drawText(graphics, profileStateLabel(), rightX() + 12, 24, themeText());

        int ix = rightX() + 12;
        int innerW = rightWidth() - 24;
        drawText(graphics, "Accounts", ix, accountsBaseY(), themeText());
        long selectedCount = draft.sessions.stream()
            .filter(s -> !MultiManager.isCurrentRenderedAccount(s.accountId())).count();
        drawRight(graphics, selectedCount + " selected", ix + innerW, accountsBaseY(), themeMuted());
        renderSearchBox(graphics, virtualMouseX, virtualMouseY, delta);

        boolean menuOpen = CompactDropdown.isMenuOpen(dropdowns);
        int mx = menuOpen ? Integer.MIN_VALUE : virtualMouseX;
        int my = menuOpen ? Integer.MIN_VALUE : virtualMouseY;
        super.extractRenderState(graphics, mx, my, delta);
        CompactDropdown.renderAll(graphics, font, dropdowns, virtualMouseX, virtualMouseY);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderSearchBox(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiBounds box = searchBox();
        ensureAccountSearchField();
        accountSearchField.setX(box.x());
        accountSearchField.setY(box.y());
        accountSearchField.setWidth(box.width());
        accountSearchField.setHeight(box.height());
        accountSearchField.render(graphics, mouseX, mouseY, delta);
    }

    private static final int MANAGE_BTN_W = 76;

    private UiBounds searchBox() {
        int ix = rightX() + 12;
        return UiBounds.of(ix, accountsBaseY() + 12, Math.max(20, rightWidth() - 24 - MANAGE_BTN_W - 4), 16);
    }

    private String profileStateLabel() {
        if (selectedProfileId == null) return "New profile";
        String name = MultiManager.singleLine(draft.name, 28);
        if (dirty) return "Editing " + name + " (changed)";

        if (System.currentTimeMillis() - savedFlashAt < 1500L) return "Editing " + name + " (saved)";
        return "Editing " + name;
    }

    private int accountsHeaderBaseY() {
        return 92 + (draft.pacing == MultiProfile.Pacing.Custom ? 26 : 0) + 52;
    }

    private int accountsBaseY() {

        return accountsHeaderBaseY() + (showBulkProxy() ? 22 : 0);
    }

    private boolean showBulkProxy() {
        return draft.proxyMode == MultiProfile.ProxyMode.Manual && !isActiveProfile(draft.id);
    }

    private int chipsY() {
        return accountsBaseY() + 32;
    }

    private int accountsTop() {
        return accountsBaseY() + (compactVertical() ? 32 : 52);
    }

    @Override
    public void onClose() {
        if (dirty) commit();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private AutismStyledButton addStyled(int x, int y, int w, int h, String text, Button.Tone tone,
                                         net.minecraft.client.gui.components.Button.OnPress press) {
        Button.Tone interactiveTone = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
        AutismStyledButton button = new AutismStyledButton(x, y, Math.max(1, w), h,
            Component.literal(fitLabel(text, w - 6)), interactiveTone, press);
        addRenderableWidget(button);
        return button;
    }

    private String fitLabel(String text, int width) {
        return UiText.trimToWidthEllipsis(font, MultiManager.singleLine(text, 72), Math.max(1, width),
            THEME.fontFor(UiTone.BODY), themeText());
    }

    private void clearInputFocus() {
        if (nameField != null) nameField.setFocused(false);
        if (serverField != null) serverField.setFocused(false);
        if (concurrencyField != null) concurrencyField.setFocused(false);
        if (delayField != null) delayField.setFocused(false);
        this.setFocused(null);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        UiText.draw(graphics, font, MultiManager.singleLine(text, 120), fontId, color, x, y, false);
    }

    private void drawRight(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        int w = UiText.width(font, text, fontId, color);
        UiText.draw(graphics, font, text, fontId, color, right - w, y, false);
    }

    private static int themeBg() {
        return AutismTheme.recolor(BG, Channel.BACKDROP);
    }

    private static int themePanel() {
        return AutismTheme.recolor(PANEL_BG, Channel.BUTTON);
    }

    private static int themePanelSoft() {
        return AutismTheme.recolor(PANEL_BG_SOFT, Channel.BUTTON);
    }

    private static int themeBorder() {
        return AutismTheme.recolor(BORDER, Channel.OUTLINE);
    }

    private static int themeText() {
        return AutismTheme.recolor(TEXT, Channel.TEXT);
    }

    private static int themeMuted() {
        return AutismTheme.recolor(MUTED, Channel.TEXT);
    }

    private MultiProfile newProfile() {
        MultiProfile profile = new MultiProfile();
        profile.name = MultiProfileManager.get().nextAvailableName("New profile", profile.id);
        profile.serverAddress = prefillServer;

        if (!MultiManager.isCurrentRenderedAccount(MultiProfile.DEFAULT_ACCOUNT_ID)) {
            profile.sessions.add(new MultiProfile.SessionSpec(MultiProfile.DEFAULT_ACCOUNT_ID, ""));
        } else {
            for (AutismAccount account : AutismAccountManager.get().all()) {
                if (!MultiManager.isCurrentRenderedAccount(account.stableId())) {
                    profile.sessions.add(new MultiProfile.SessionSpec(account.stableId(), ""));
                    break;
                }
            }
        }
        return profile;
    }

    private boolean isActiveProfile(String profileId) {
        if (profileId == null || !MultiManager.get().isActive()) return false;
        MultiProfile active = MultiManager.get().activeProfile();
        return active != null && profileId.equals(active.id);
    }

    private int rightX() {
        return MARGIN + leftWidth() + GAP;
    }

    private int rightWidth() {
        return Math.max(1, screenWidth() - rightX() - MARGIN);
    }

    private int leftWidth() {
        int roomAfterMinimumEditor = screenWidth() - MARGIN * 2 - GAP - 180;
        return Math.max(90, Math.min(LEFT_WIDTH, roomAfterMinimumEditor));
    }

    private boolean compactFooter() {
        return screenWidth() < 620;
    }

    private boolean compactVertical() {
        return screenHeight() < 300;
    }

    private int profileListBottom() {
        return screenHeight() - (compactFooter() ? 70 : 48);
    }

    private int accountListBottom() {
        return screenHeight() - 48;
    }

    private static List<String> pingOptionLabels() {
        List<String> labels = new ArrayList<>(PING_OPTIONS.length);
        for (int option : PING_OPTIONS) labels.add(option + "ms");
        return labels;
    }

    private static int pingIndex(int value) {
        int index = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < PING_OPTIONS.length; i++) {
            int distance = Math.abs(PING_OPTIONS[i] - value);
            if (distance < best) {
                best = distance;
                index = i;
            }
        }
        return index;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record AccountChoice(String id, String label, boolean current) {
    }

}
