package autismclient.mixin.security;

import autismclient.AutismClientAddon;
import autismclient.security.AutismProtectorServerPackFailureGuard;
import autismclient.security.AutismProtectorPackStrip;
import autismclient.util.AutismNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Mixin(ServerPackManager.class)
public abstract class AutismProtectorServerPackManagerMixin {

    @Unique private static long autism$lastRecoveryReloadMs;
    @Unique private static long autism$lastRecoveryToastMs;

    @Shadow public abstract void popAll();

    @Inject(method = "onDownload", at = @At("HEAD"))
    private void autism$makeFailedServerPackBatchAtomic(Collection<?> data, DownloadQueue.BatchResult result, CallbackInfo ci) {
        if (result == null || result.failed().isEmpty()) return;
        AutismProtectorServerPackFailureGuard.suppressServerPacksTemporarily();

        try {
            Map<UUID, ?> downloaded = result.downloaded();
            if (downloaded != null) downloaded.clear();
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("[AutismProtector] Failed to clear partial server-pack batch.", error);
        }
    }

    @Inject(method = "onDownload", at = @At("TAIL"))
    private void autism$recoverFromFailedServerPackDownload(Collection<?> data, DownloadQueue.BatchResult result, CallbackInfo ci) {
        if (result == null || result.failed().isEmpty()) return;

        AutismProtectorServerPackFailureGuard.suppressServerPacksTemporarily();
        AutismProtectorPackStrip.clearAll();

        try {
            popAll();
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("[AutismProtector] Failed to clear server packs after download failure.", error);
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                try {
                    client.getDownloadedPackSource().popAll();
                    long now = System.currentTimeMillis();
                    if (now - autism$lastRecoveryToastMs > 5000L) {
                        autism$lastRecoveryToastMs = now;
                        AutismNotifications.warning("Server resource pack failed. Restored client resources.");
                    }
                    if (now - autism$lastRecoveryReloadMs > 1000L) {
                        autism$lastRecoveryReloadMs = now;
                        client.reloadResourcePacks();
                    }
                } catch (Throwable error) {
                    AutismClientAddon.LOG.warn("[AutismProtector] Failed to clear downloaded pack source after download failure.", error);
                }
            });
        }
    }
}
