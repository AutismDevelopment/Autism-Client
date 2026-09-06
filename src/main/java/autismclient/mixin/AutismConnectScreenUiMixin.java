package autismclient.mixin;

import autismclient.gui.screen.AutismStyledButton;
import autismclient.gui.vanillaui.components.Button;
import autismclient.modules.PackAutoReconnectState;
import autismclient.modules.PackHideState;
import autismclient.util.AutismTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class AutismConnectScreenUiMixin extends Screen {

    private static final String[] STEP_KEYS = {
        "connect.connecting", "connect.authorizing", "connect.encrypting",
        "connect.negotiating", "connect.joining", null
    };
    private static final String[] STEP_LABELS = {
        "Connecting", "Authorizing", "Encrypting", "Negotiating", "Joining World", "Loading Terrain"
    };
    private static final int DONE_COLOR = 0xFF8A8A8A;
    private static final int UPCOMING_COLOR = 0xFF4A4A4A;
    private static final char[] SPINNER = {'|', '/', '-', '\\'};

    private int autism$step;
    @org.spongepowered.asm.mixin.Unique
    private AutismStyledButton autism$reconnectToggle;

    protected AutismConnectScreenUiMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void autism$addReconnectToggle(CallbackInfo ci) {

        if (PackHideState.isActive() || !PackAutoReconnectState.canShowToggle()) return;
        int w = 148;
        int h = 20;
        autism$reconnectToggle = new AutismStyledButton(
            this.width / 2 - w / 2, this.height - 38, w, h,
            Component.literal("Auto Reconnect"),
            Button.Tone.PRIMARY,
            PackAutoReconnectState::toggleLabel,
            button -> PackAutoReconnectState.toggle()
        );
        this.addRenderableWidget(autism$reconnectToggle);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void autism$hideToggleInPanic(CallbackInfo ci) {
        if (autism$reconnectToggle != null) {
            boolean show = !PackHideState.isActive();
            autism$reconnectToggle.visible = show;
            autism$reconnectToggle.active = show;
        }
    }

    @Inject(method = "updateStatus", at = @At("HEAD"))
    private void autism$trackStep(Component status, CallbackInfo ci) {
        if (!(status.getContents() instanceof TranslatableContents translatable)) return;
        String key = translatable.getKey();
        for (int i = 0; i < STEP_KEYS.length; i++) {
            if (STEP_KEYS[i] != null && STEP_KEYS[i].equals(key)) {
                if (i > autism$step) autism$step = i;
                return;
            }
        }
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void autism$renderJoinSteps(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {

        if (PackHideState.isActive()) {
            graphics.centeredText(font, text, x, y, color);
            return;
        }
        int accent = AutismTheme.recolor(0xFFFF3B3B, AutismTheme.Channel.ACCENT) & 0xFFFFFF;
        int rowHeight = 12;
        int top = y - (STEP_LABELS.length * rowHeight) / 2 + 1;
        char spin = SPINNER[(int) ((System.currentTimeMillis() / 100) % SPINNER.length)];
        for (int i = 0; i < STEP_LABELS.length; i++) {
            String label;
            int rgb;
            if (i < autism$step) {
                label = STEP_LABELS[i];
                rgb = DONE_COLOR;
            } else if (i == autism$step) {
                label = spin + " " + STEP_LABELS[i];
                rgb = 0xFF000000 | accent;
            } else {
                label = STEP_LABELS[i];
                rgb = UPCOMING_COLOR;
            }
            graphics.centeredText(font, Component.literal(label), x, top + i * rowHeight, rgb);
        }
    }
}
