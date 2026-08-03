package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.mixin.accessor.AutismMinecraftAccessor;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class AutismAccountSessionSwitcher {
    private static User originalUser;
    private static String lastError = "";

    private AutismAccountSessionSwitcher() {
    }

    public static User getOriginalUser() {
        if (originalUser == null) originalUser = Minecraft.getInstance().getUser();
        return originalUser;
    }

    public static boolean setSession(User user) {
        return setSession(user, new YggdrasilAuthenticationService(AutismAuthNetwork.directProxy()));
    }

    public static boolean setSession(User user, YggdrasilAuthenticationService authService) {
        lastError = "";
        try {
            Minecraft mc = Minecraft.getInstance();
            if (originalUser == null) originalUser = mc.getUser();
            AutismMinecraftAccessor accessor = (AutismMinecraftAccessor) mc;
            YggdrasilAuthenticationService userApiAuthService =
                new YggdrasilAuthenticationService(AutismAuthNetwork.directProxy());
            Services services = Services.create(authService, mc.gameDirectory);
            UserApiService apiService = userApiAuthService.createUserApiService(user.getAccessToken());
            FriendsService friendsService = userApiAuthService.createFriendsService(user.getAccessToken());
            RemoteFriendListUpdateHandler friendListUpdateHandler = new RemoteFriendListUpdateHandler(friendsService, mc);
            Path skinCachePath = mc.gameDirectory.toPath().resolve("assets").resolve("skins");

            accessor.autism$setServices(services);
            accessor.autism$setUser(user);
            accessor.autism$setUserApiService(apiService);
            accessor.autism$setRemoteFriendListUpdateHandler(friendListUpdateHandler);
            accessor.autism$setPlayerSocialManager(new PlayerSocialManager(mc, apiService, friendsService, friendListUpdateHandler));
            accessor.autism$setProfileKeyPairManager(ProfileKeyPairManager.create(apiService, user, mc.gameDirectory.toPath()));
            accessor.autism$setReportingContext(ReportingContext.create(ReportEnvironment.local(), apiService));
            accessor.autism$setProfileFuture(CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(mc.getUser().getProfileId(), true), Util.nonCriticalIoPool()));
            accessor.autism$setSkinManager(new SkinManager(skinCachePath, services,
                new SkinTextureDownloader(AutismAuthNetwork.directProxy(), mc.getTextureManager(), mc), mc));
            return true;
        } catch (Exception e) {
            lastError = shortError(e);
            AutismClientAddon.LOG.error("Failed to switch Autism account session", e);
            return false;
        }
    }

    public static String lastError() {
        return lastError == null ? "" : lastError;
    }

    private static String shortError(Throwable error) {
        if (error == null) return "unknown error";
        String name = error.getClass().getSimpleName();
        String message = error.getMessage();
        if (message == null || message.isBlank()) return name;
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (message.length() > 120) message = message.substring(0, 117) + "...";
        return name + ": " + message;
    }
}
