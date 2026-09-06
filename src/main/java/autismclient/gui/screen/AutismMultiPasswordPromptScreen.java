package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismTheme;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class AutismMultiPasswordPromptScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    public static final int MAX_PASSWORD_CHARS = 16;
    public static final int MIN_GENERATED_CHARS = 9;

    private final Screen parent;
    private EditBox passwordField;
    private boolean reveal = true;
    private String status = "";
    private int statusColor = AutismScreenPalette.MUTED;

    public AutismMultiPasswordPromptScreen(Screen parent) {
        super(Component.literal("Multi Login Password"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int w = panelW();
        int x = (screenWidth() - w) / 2;
        int inner = w - 24;
        int fieldY = panelY() + 100;

        passwordField = new EditBox(font, x + 12, fieldY, inner - 62, 18, Component.literal("Password"));
        passwordField.setMaxLength(MAX_PASSWORD_CHARS);
        passwordField.setHint(Component.literal("Password for all accounts"));
        applyMaskFormatter();
        addRenderableWidget(passwordField);
        addButton(x + 12 + inner - 58, fieldY, 58, reveal ? "Hide" : "Reveal", Button.Tone.SECONDARY, b -> {
            reveal = !reveal;
            applyMaskFormatter();
        });
        int y = fieldY + 24;
        addButton(x + 12, y, inner, "Use This Password for All Accounts", Button.Tone.SUCCESS, b -> applyManual());
        y += 22;
        addButton(x + 12, y, inner, "Generate Random for Each Account", Button.Tone.PRIMARY, b -> applyGenerated());
        y += 22;
        String macroName = MultiManager.get().allMacroName();
        String macroLabel = "Login Macro: " + (macroName == null || macroName.isBlank() ? "none" : macroName);
        addButton(x + 12, y, inner, macroLabel,
            macroName == null || macroName.isBlank() ? Button.Tone.SECONDARY : Button.Tone.PRIMARY, b -> pickLoginMacro());
        y += 22;
        boolean autoCaptcha = autismclient.util.AutismConfig.getGlobal().multiAutoSolveCaptcha;
        addButton(x + 12, y, inner, "Auto-solve Captchas: " + (autoCaptcha ? "On" : "Off"),
            autoCaptcha ? Button.Tone.SUCCESS : Button.Tone.SECONDARY, b -> {
                autismclient.util.AutismConfig config = autismclient.util.AutismConfig.getGlobal();
                config.multiAutoSolveCaptcha = !config.multiAutoSolveCaptcha;
                config.save();
                init();
            });
        y += 22;
        addButton(x + 12, y, inner, "Not Now", Button.Tone.SECONDARY, b -> onClose());
        setFocused(passwordField);
        passwordField.setFocused(true);
    }

    private void pickLoginMacro() {
        if (minecraft == null) return;
        minecraft.gui.setScreen(new AutismMultiMacroPickerScreen(this, MultiManager.get().allMacroName(), name -> {
            MultiManager.get().assignAllMacro(name == null ? "" : name);
            init();
        }));
    }

    private void applyMaskFormatter() {
        if (passwordField == null) return;
        passwordField.addFormatter((value, offset) -> FormattedCharSequence.forward(
            reveal ? value : "*".repeat(value.length()), Style.EMPTY));
    }

    private void applyManual() {
        String password = passwordField == null ? "" : passwordField.getValue();
        if (password.isBlank()) {
            status("Type a password first (or generate random ones).", AutismScreenPalette.ERROR);
            return;
        }
        if (password.length() > MAX_PASSWORD_CHARS) {
            status("Password must be at most " + MAX_PASSWORD_CHARS + " characters.", AutismScreenPalette.ERROR);
            return;
        }
        int updated = MultiManager.get().applyPasswordToAllAccounts(password);
        if (updated <= 0) {
            status("No accounts to store the password for.", AutismScreenPalette.ERROR);
            return;
        }
        finish("Password set for " + updated + " account(s).", password.length() < MIN_GENERATED_CHARS
            ? " Note: it is shorter than " + MIN_GENERATED_CHARS + " characters; some servers may reject it." : "");
    }

    private void applyGenerated() {
        int updated = MultiManager.get().applyGeneratedPasswords();
        if (updated <= 0) {
            status("No accounts to generate passwords for.", AutismScreenPalette.ERROR);
            return;
        }
        startLoginMacroIfAssigned();
        autismclient.util.AutismNotifications.show("Generated a random password for " + updated + " account(s).", AutismTheme.recolor(AutismScreenPalette.SUCCESS, AutismTheme.Channel.SUCCESS));

        MultiProfile snapshot = MultiManager.get().activeProfile();
        if (snapshot != null && minecraft != null) {
            minecraft.gui.setScreen(new AutismFormValuesScreen(parent, snapshot, null,
                saved -> MultiManager.get().updateActiveFormValues(saved), true));
        } else {
            onClose();
        }
    }

    private void finish(String message, String note) {
        startLoginMacroIfAssigned();
        autismclient.util.AutismNotifications.show(message + note, AutismTheme.recolor(AutismScreenPalette.SUCCESS, AutismTheme.Channel.SUCCESS));
        onClose();
    }

    private void startLoginMacroIfAssigned() {
        MultiManager manager = MultiManager.get();
        if (manager.isActive() && manager.hasAnyAssignedMacro()) {
            manager.runMacroOnScope(java.util.Set.of(), true);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {

            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()),
                AutismTheme.recolor(AutismScreenPalette.BG, AutismTheme.Channel.BACKDROP));
            int w = panelW();
            int x = (screenWidth() - w) / 2;
            int y = panelY();

            UiRenderer.frame(graphics, UiBounds.of(x, y, w, panelH()),
                AutismTheme.recolor(AutismScreenPalette.PANEL_BG, AutismTheme.Channel.BUTTON),
                AutismTheme.recolor(AutismScreenPalette.BORDER, AutismTheme.Channel.OUTLINE));
            int textColor = AutismTheme.recolor(AutismScreenPalette.TEXT, AutismTheme.Channel.TEXT);
            int mutedColor = AutismTheme.recolor(AutismScreenPalette.MUTED, AutismTheme.Channel.TEXT);
            Identifier fontId = THEME.fontFor(UiTone.BODY);
            UiText.draw(graphics, font, "Multi: login password needed", fontId, textColor, x + 12, y + 10, false);

            String message = "A server login screen appeared but no password is stored for this profile. "
                + "Set one password for every account, or generate a different random one per account "
                + "(" + MIN_GENERATED_CHARS + "-" + MAX_PASSWORD_CHARS + " characters). Waiting login macros continue automatically.";
            int ty = y + 24;
            List<FormattedCharSequence> lines = font.split(net.minecraft.network.chat.FormattedText.of(message), w - 24);
            for (FormattedCharSequence line : lines) {
                graphics.text(font, line, x + 12, ty, mutedColor, false);
                ty += 10;
            }
            if (!status.isBlank()) {
                for (FormattedCharSequence line : font.split(net.minecraft.network.chat.FormattedText.of(status), w - 24)) {
                    graphics.text(font, line, x + 12, y + panelH() - 26, themedStatus(), false);
                    break;
                }
            }
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private int themedStatus() {
        if (statusColor == AutismScreenPalette.ERROR) return AutismTheme.recolor(AutismScreenPalette.ERROR, AutismTheme.Channel.DANGER);
        if (statusColor == AutismScreenPalette.SUCCESS) return AutismTheme.recolor(AutismScreenPalette.SUCCESS, AutismTheme.Channel.SUCCESS);
        return AutismTheme.recolor(AutismScreenPalette.MUTED, AutismTheme.Channel.TEXT);
    }

    private int panelW() {
        return Math.min(320, Math.max(220, screenWidth() - 40));
    }

    private int panelH() {
        return 262;
    }

    private int panelY() {
        return Math.max(10, (screenHeight() - panelH()) / 2);
    }

    private void status(String text, int color) {
        status = text == null ? "" : text;
        statusColor = color;
    }

    private void addButton(int x, int y, int w, String label, Button.Tone tone,
                           net.minecraft.client.gui.components.Button.OnPress press) {
        addRenderableWidget(new AutismStyledButton(x, y, Math.max(1, w), 18,
            Component.literal(label), tone, press));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
