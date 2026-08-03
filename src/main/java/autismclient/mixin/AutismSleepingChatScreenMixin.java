package autismclient.mixin;

import autismclient.gui.screen.AutismStyledButton;
import autismclient.gui.vanillaui.components.Button;
import autismclient.modules.AutismModule;
import autismclient.util.AutismClientWake;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InBedChatScreen.class)
public abstract class AutismSleepingChatScreenMixin extends Screen {
    protected AutismSleepingChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void autism$init(CallbackInfo ci) {
        AutismModule module = AutismModule.get();
        if (module == null || !module.isActive()) return;

        this.addRenderableWidget(new AutismStyledButton(
            5, 5, 140, 20,
            Component.literal("Client wake up"),
            Button.Tone.PRIMARY,
            button -> AutismClientWake.wake()
        ));
    }
}
