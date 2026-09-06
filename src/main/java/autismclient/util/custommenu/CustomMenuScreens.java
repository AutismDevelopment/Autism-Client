package autismclient.util.custommenu;

import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.mixin.accessor.AutismDialogScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class CustomMenuScreens {

    private static final long ADVANCE_TIMEOUT_MS = 500L;

    private CustomMenuScreens() {}

    public static CustomMenuSnapshot openScreenSnapshot(Minecraft mc) {
        Dialog dialog = openDialog(mc);
        if (dialog == null) return null;

        return VanillaDialogAdapter.snapshotOf(dialog, mc.getConnection() != null ? "PLAY" : "CONFIGURATION");
    }

    public static Screen openScreen(Minecraft mc) {
        return mc == null || mc.gui == null ? null : mc.gui.screen();
    }

    public static void advanceAfterSubmit(Minecraft mc, ClickEvent clientAction, Screen answered) {
        if (mc == null || !(answered instanceof DialogScreen<?>)) return;
        CompletableFuture<Void> applied;
        try {
            applied = mc.submit(() -> {
                if (mc.gui.screen() != answered || !(mc.gui.screen() instanceof DialogScreen<?> screen)) return;
                Dialog dialog = ((AutismDialogScreenAccessor) screen).autism$dialog();
                DialogAction after = dialog == null ? DialogAction.CLOSE : dialog.common().afterAction();

                if (clientAction == null && after == DialogAction.NONE) return;
                screen.runAction(Optional.ofNullable(clientAction));
            });
        } catch (RuntimeException clientGone) {
            return;
        }
        try {
            applied.get(ADVANCE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {

        }
    }

    private static Dialog openDialog(Minecraft mc) {
        if (mc == null || mc.gui == null) return null;
        return mc.gui.screen() instanceof DialogScreen<?> screen
            ? ((AutismDialogScreenAccessor) screen).autism$dialog()
            : null;
    }
}
