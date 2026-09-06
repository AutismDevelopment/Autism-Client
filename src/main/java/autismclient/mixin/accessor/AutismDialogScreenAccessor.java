package autismclient.mixin.accessor;

import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.server.dialog.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DialogScreen.class)
public interface AutismDialogScreenAccessor {
    @Accessor("dialog")
    Dialog autism$dialog();
}
