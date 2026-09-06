package autismclient.mixin.accessor;

import net.minecraft.client.CommandHistory;
import net.minecraft.util.ArrayListDeque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CommandHistory.class)
public interface AutismCommandHistoryAccessor {
    @Accessor("lastCommands")
    ArrayListDeque<String> autism$getLastCommands();

    @Invoker("save")
    void autism$save();
}
