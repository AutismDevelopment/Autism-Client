package autismclient.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface AutismLocalPlayerAccessor {
    @Accessor("positionReminder")
    void autism$setPositionReminder(int ticks);

    @Invoker("isSlowDueToUsingItem")
    boolean autism$isSlowDueToUsingItem();
}
