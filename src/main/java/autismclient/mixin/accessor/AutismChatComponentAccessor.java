package autismclient.mixin.accessor;

import net.minecraft.client.CommandHistory;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface AutismChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> autism$getAllMessages();

    @Accessor("commandHistory")
    CommandHistory autism$getCommandHistory();

    @Invoker("refreshTrimmedMessages")
    void autism$refreshTrimmedMessages();
}
