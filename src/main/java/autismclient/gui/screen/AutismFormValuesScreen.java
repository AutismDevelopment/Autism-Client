package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismAccount;
import autismclient.util.AutismAccountManager;
import autismclient.util.AutismTheme;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class AutismFormValuesScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int MAX_PASSWORD_CHARS = 16;
    private static final int MIN_GENERATED_CHARS = 9;
    private static final int ROW_H = 28;

    private final Screen parent;
    private final Consumer<MultiProfile> onSave;
    private final MultiProfile draft;
    private final boolean showLoginMacro;

    private EditBox passwordField;
    private boolean hidden;
    private String selectedAccount;
    private int accountScroll;
    private final List<int[]> accountRowRects = new ArrayList<>();
    private String status = "";
    private int statusColor = AutismScreenPalette.MUTED;
    private int helpY = 124;

    public AutismFormValuesScreen(Screen parent, MultiProfile profile, Set<String> initiallySelected,
                                  Consumer<MultiProfile> onSave) {
        this(parent, profile, initiallySelected, onSave, true);
    }

    public AutismFormValuesScreen(Screen parent, MultiProfile profile, Set<String> initiallySelected,
                                  Consumer<MultiProfile> onSave, boolean showLoginMacro) {
        super(Component.literal("Login Passwords"));
        this.parent = parent;
        this.draft = new MultiProfile(profile);
        this.onSave = onSave == null ? ignored -> {} : onSave;
        this.showLoginMacro = showLoginMacro;
        List<String> ids = accountIds();
        if (initiallySelected != null) {
            for (String id : initiallySelected) {
                if (ids.contains(id)) { selectedAccount = id; break; }
            }
        }
        if (selectedAccount == null && !ids.isEmpty()) selectedAccount = ids.get(0);
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = 18;
        int leftW = Math.max(140, Math.min(240, screenWidth() / 3));
        int right = left + leftW + 14;
        int rightW = Math.max(150, screenWidth() - right - 18);
        int gap = 6;

        int fieldY = 46;
        int revealW = 54;
        passwordField = new EditBox(font, right, fieldY, rightW - revealW - gap, 20, Component.literal("Password"));
        passwordField.setMaxLength(MAX_PASSWORD_CHARS);
        passwordField.setHint(Component.literal("Type a password"));
        applyMask();

        if (selectedAccount != null) passwordField.setValue(storedPassword(selectedAccount));
        addRenderableWidget(passwordField);
        addButton(right + rightW - revealW, fieldY + 1, revealW, hidden ? "Show" : "Hide", Button.Tone.SECONDARY, b -> {
            hidden = !hidden;
            applyMask();
        });

        int half = Math.max(1, (rightW - gap) / 2);
        int y = fieldY + 26;
        addButton(right, y, half, "Set for Selected", Button.Tone.PRIMARY, b -> setForSelected());
        addButton(right + half + gap, y, rightW - half - gap, "Set for All", Button.Tone.SUCCESS, b -> setForAll());
        y += 24;
        addButton(right, y, rightW, "Generate Random for Each Account", Button.Tone.SECONDARY, b -> generateAll());
        y += 24;
        if (showLoginMacro) {

            boolean off = draft.loginMode == MultiProfile.LoginMode.Off;
            int modeH = off ? 32 : 18;
            addButton(right, y, rightW, modeH, "Login: " + loginModeLabel(draft.loginMode),
                off ? Button.Tone.DANGER : Button.Tone.NORMAL, b -> cycleLoginMode());
            y += modeH + 6;
            if (draft.loginMode == MultiProfile.LoginMode.Custom) {
                String macro = draft.allMacroName.isBlank() ? "none" : draft.allMacroName;
                addButton(right, y, rightW, "Login Macro: " + macro,
                    draft.allMacroName.isBlank() ? Button.Tone.SECONDARY : Button.Tone.PRIMARY, b -> pickLoginMacro());
                y += 24;
            }
        }

        if (!autismclient.util.AutismLiteVariant.enabled()) {
            boolean autoCaptcha = autismclient.util.AutismConfig.getGlobal().multiAutoSolveCaptcha;
            addButton(right, y, rightW, "Auto-solve Captchas: " + (autoCaptcha ? "On" : "Off"),
                autoCaptcha ? Button.Tone.SUCCESS : Button.Tone.SECONDARY, b -> {
                    autismclient.util.AutismConfig config = autismclient.util.AutismConfig.getGlobal();
                    config.multiAutoSolveCaptcha = !config.multiAutoSolveCaptcha;
                    config.save();
                    init();
                });
            y += 24;
        }
        helpY = y + 4;

        int footerW = Math.max(1, (rightW - gap) / 2);
        addButton(right, screenHeight() - 28, footerW, "Save", Button.Tone.SUCCESS, b -> saveAndClose());
        addButton(right + footerW + gap, screenHeight() - 28, rightW - footerW - gap, "Back", Button.Tone.SECONDARY, b -> onClose());
    }

    private void applyMask() {
        if (passwordField == null) return;
        passwordField.addFormatter((value, offset) -> FormattedCharSequence.forward(
            hidden ? "*".repeat(value.length()) : value, Style.EMPTY));
    }

    private void setForSelected() {
        if (selectedAccount == null) { status("Select an account on the left first.", AutismScreenPalette.ERROR); return; }
        String password = passwordField.getValue();
        if (!validate(password)) return;
        draft.setFormValue(selectedAccount, "password", password);
        status("Password set for " + accountLabel(selectedAccount) + ". Press Save to keep it.", AutismScreenPalette.SUCCESS);
    }

    private void setForAll() {
        String password = passwordField.getValue();
        if (!validate(password)) return;
        List<String> ids = accountIds();
        for (String id : ids) draft.setFormValue(id, "password", password);
        status("Password set for all " + ids.size() + " account(s). Press Save to keep it.", AutismScreenPalette.SUCCESS);
    }

    private boolean validate(String password) {
        if (password == null || password.isBlank()) { status("Type a password first.", AutismScreenPalette.ERROR); return false; }
        if (password.length() > MAX_PASSWORD_CHARS) {
            status("Password must be at most " + MAX_PASSWORD_CHARS + " characters.", AutismScreenPalette.ERROR);
            return false;
        }
        return true;
    }

    private void generateAll() {
        List<String> ids = accountIds();
        if (ids.isEmpty()) { status("No accounts.", AutismScreenPalette.ERROR); return; }
        for (String id : ids) draft.setFormValue(id, "password", MultiManager.generatePassword());

        hidden = false;
        if (selectedAccount != null && passwordField != null) passwordField.setValue(storedPassword(selectedAccount));
        applyMask();
        status("Generated a random password (" + MIN_GENERATED_CHARS + "-" + MAX_PASSWORD_CHARS
            + " chars) for each account - shown in the list. Press Save to keep them.", AutismScreenPalette.SUCCESS);
    }

    private void pickLoginMacro() {
        if (minecraft == null) return;

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        minecraft.gui.setScreen(new AutismMultiMacroPickerScreen(this, draft.allMacroName, name -> {
            draft.allMacroName = name == null ? "" : name.trim();
            status(draft.allMacroName.isBlank() ? "Login macro cleared. Press Save to keep it."
                : "Login macro set to \"" + draft.allMacroName + "\". Press Save to keep it.", AutismScreenPalette.SUCCESS);
        }));
    }

    private void cycleLoginMode() {
        MultiProfile.LoginMode[] modes = MultiProfile.LoginMode.values();
        draft.loginMode = modes[(draft.loginMode.ordinal() + 1) % modes.length];
        status("Login mode: " + loginModeLabel(draft.loginMode) + ". Press Save to keep it.", AutismScreenPalette.SUCCESS);
        rebuildWidgets();
    }

    private static String loginModeLabel(MultiProfile.LoginMode mode) {
        return switch (mode) {
            case Off -> "Off";
            case Auto -> "Auto (detect)";
            case Custom -> "Custom macro";
        };
    }

    private String loginHelp() {
        if (!showLoginMacro) {
            return "Type a password (you can see it), then Set for Selected or Set for All - or Generate a "
                + "random one per account. Used to fill custom login screens.";
        }
        return switch (draft.loginMode) {
            case Off -> "";
            case Auto -> "Auto: bots answer the usual login and register prompts on their own - AuthMe chat "
                + "commands and custom login screens - using the password set above. If no login shows up within "
                + "40 seconds, it stops.";
            case Custom -> "Custom: make a macro that logs in and put {password} wherever the password goes - for "
                + "example a command \"/login {password}\", or a login-screen field set to {password}. {password} "
                + "is the password you set above. Pick that macro below; it runs when the bot joins and stops after "
                + "40 seconds if no login was needed.";
        };
    }

    private void saveAndClose() {
        onSave.accept(new MultiProfile(draft));
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private String storedPassword(String id) {
        String stored = draft.openFormValues(id).getOrDefault("password", "");
        if (!stored.isBlank()) return stored;

        AutismAccount account = AutismAccountManager.get().findById(id);
        return account != null && account.password != null ? account.password : "";
    }

    private List<String> accountIds() {
        List<String> ids = new ArrayList<>();
        for (MultiProfile.SessionSpec spec : draft.sessions) ids.add(spec.accountId());
        return ids;
    }

    private String accountLabel(String id) {
        if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(id)) return "Current account";
        AutismAccount account = AutismAccountManager.get().findById(id);
        String label = account == null ? id : account.displayName();
        return MultiManager.singleLine(label == null || label.isBlank() ? id : label, 32);
    }

    private void status(String text, int color) {
        status = text == null ? "" : text;
        statusColor = color;
    }

    private void addButton(int x, int y, int w, int h, String label, Button.Tone tone,
                           net.minecraft.client.gui.components.Button.OnPress press) {
        addRenderableWidget(new AutismStyledButton(x, y, Math.max(1, w), h, Component.literal(label), tone, press));
    }

    private void addButton(int x, int y, int w, String label, Button.Tone tone,
                           net.minecraft.client.gui.components.Button.OnPress press) {
        addRenderableWidget(new AutismStyledButton(x, y, Math.max(1, w), 18, Component.literal(label), tone, press));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (virtualEvent.button() == 0) {
            for (int[] rect : accountRowRects) {
                if (virtualEvent.x() >= rect[0] && virtualEvent.x() < rect[0] + rect[2]
                    && virtualEvent.y() >= rect[1] && virtualEvent.y() < rect[1] + rect[3]) {
                    List<String> ids = accountIds();
                    if (rect[4] >= 0 && rect[4] < ids.size()) {
                        selectedAccount = ids.get(rect[4]);
                        if (passwordField != null) passwordField.setValue(storedPassword(selectedAccount));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(virtualEvent, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double vx = AutismUiScale.toVirtual(mouseX);
        double vy = AutismUiScale.toVirtual(mouseY);
        int left = 18;
        int leftW = Math.max(140, Math.min(240, screenWidth() / 3));
        if (vx >= left && vx < left + leftW && vy >= 46 && vy < screenHeight() - 28) {
            accountScroll = Math.max(0, accountScroll + (vertical < 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()),
                AutismTheme.recolor(AutismScreenPalette.BG, AutismTheme.Channel.BACKDROP));
            int left = 18;
            int leftW = Math.max(140, Math.min(240, screenWidth() / 3));
            int right = left + leftW + 14;
            int rightW = Math.max(150, screenWidth() - right - 18);
            int border = AutismTheme.recolor(AutismScreenPalette.BORDER, AutismTheme.Channel.OUTLINE);
            int textColor = AutismTheme.recolor(AutismScreenPalette.TEXT, AutismTheme.Channel.TEXT);
            int muted = AutismTheme.recolor(AutismScreenPalette.MUTED, AutismTheme.Channel.TEXT);

            UiRenderer.frame(graphics, UiBounds.of(left, 18, leftW, Math.max(1, screenHeight() - 46)),
                AutismTheme.recolor(AutismScreenPalette.PANEL_BG_SOFT, AutismTheme.Channel.BUTTON), border);
            UiRenderer.frame(graphics, UiBounds.of(right, 18, rightW, Math.max(1, screenHeight() - 46)),
                AutismTheme.recolor(AutismScreenPalette.PANEL_BG, AutismTheme.Channel.BUTTON), border);

            drawText(graphics, "Accounts (click to edit)", left + 8, 30, textColor);
            drawText(graphics, "Login Password", right + 8, 30, textColor);

            renderAccountRows(graphics, left + 4, leftW - 8, textColor, muted, border);

            int hy = helpY;
            for (FormattedCharSequence line : font.split(FormattedText.of(loginHelp()), rightW - 8)) {
                graphics.text(font, line, right, hy, muted, false);
                hy += 10;
            }
            if (!status.isBlank()) {
                int sy = screenHeight() - 52;
                for (FormattedCharSequence line : font.split(FormattedText.of(status), rightW - 8)) {
                    graphics.text(font, line, right, sy, statusColor, false);
                    sy += 10;
                }
            }
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderAccountRows(GuiGraphicsExtractor graphics, int x, int w, int textColor, int muted, int border) {
        accountRowRects.clear();
        List<String> ids = accountIds();
        int top = 46;
        int bottom = screenHeight() - 28;
        int visible = Math.max(1, (bottom - top) / ROW_H);
        accountScroll = Math.max(0, Math.min(accountScroll, Math.max(0, ids.size() - visible)));
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        int y = top;
        for (int i = accountScroll; i < Math.min(ids.size(), accountScroll + visible); i++) {
            String id = ids.get(i);
            boolean selected = id.equals(selectedAccount);
            int fill = selected ? (AutismScreenPalette.SUCCESS & 0x00FFFFFF) | 0x33000000 : 0x18000000;
            UiRenderer.rect(graphics, UiBounds.of(x, y, w, ROW_H - 3), fill);
            if (selected) UiRenderer.rect(graphics, UiBounds.of(x, y, 2, ROW_H - 3),
                AutismTheme.recolor(AutismScreenPalette.SUCCESS, AutismTheme.Channel.SUCCESS));
            String name = UiText.trimToWidthEllipsis(font, accountLabel(id), w - 10, fontId, textColor);
            UiText.draw(graphics, font, name, fontId, selected ? AutismTheme.recolor(AutismScreenPalette.SUCCESS, AutismTheme.Channel.SUCCESS) : textColor,
                x + 5, y + 4, false);
            String pass = storedPassword(id);
            String shown = pass.isEmpty() ? "(no password)" : (hidden ? "*".repeat(pass.length()) : pass);
            String passLine = UiText.trimToWidthEllipsis(font, shown, w - 10, fontId, muted);
            UiText.draw(graphics, font, passLine, fontId, pass.isEmpty() ? muted : textColor, x + 5, y + 14, false);
            accountRowRects.add(new int[]{x, y, w, ROW_H - 3, i});
            y += ROW_H;
        }
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        UiText.draw(graphics, font, MultiManager.singleLine(text, 100), fontId, color, x, y, false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
