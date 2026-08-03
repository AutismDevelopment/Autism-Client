package autismclient.modules;

import net.minecraft.world.item.ItemStack;

public final class AutismAdminToolsBridge {
    private AutismAdminToolsBridge() {
    }

    public static boolean fillNbtEditorSilently(ItemStack stack) {
        Module module = ModuleRegistry.get("admin-tools");
        if (module instanceof BuiltinModules.AdminToolsModule adminTools) {
            return adminTools.fillItemEditorFromStack(stack, false);
        }
        return false;
    }

    public static boolean openFilledAdminEditor(ItemStack stack) {
        if (!fillNbtEditorSilently(stack)) return false;
        try {
            autismclient.util.AutismAdminToolsOverlay overlay =
                autismclient.util.AutismAdminToolsOverlay.getSharedOverlay();
            autismclient.util.AutismOverlayManager manager = autismclient.util.AutismOverlayManager.get();
            manager.register(overlay);
            overlay.setVisible(true);
            overlay.showRawItemEditor();
            manager.bringToFront(overlay);
        } catch (Throwable ignored) {  }
        return true;
    }
}
