package autismclient.mixin;

import autismclient.modules.NameCensorModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class AutismChatComponentCensorMixin {
    @ModifyVariable(method = "addClientSystemMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component autism$censorClientSystemChat(Component component) {
        return NameCensorModule.censorComponent(component);
    }

    @ModifyVariable(method = "addServerSystemMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component autism$censorServerSystemChat(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @ModifyVariable(method = "addPlayerMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component autism$censorPlayerChat(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component autism$censorPrivateChatFallback(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }
}
