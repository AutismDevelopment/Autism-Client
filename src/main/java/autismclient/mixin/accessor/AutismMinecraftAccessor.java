package autismclient.mixin.accessor;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface AutismMinecraftAccessor {
    @Mutable
    @Accessor("user")
    void autism$setUser(User user);

    @Mutable
    @Accessor("profileKeyPairManager")
    void autism$setProfileKeyPairManager(ProfileKeyPairManager manager);

    @Mutable
    @Accessor("userApiService")
    void autism$setUserApiService(UserApiService service);

    @Mutable
    @Accessor("skinManager")
    void autism$setSkinManager(SkinManager manager);

    @Mutable
    @Accessor("playerSocialManager")
    void autism$setPlayerSocialManager(PlayerSocialManager manager);

    @Mutable
    @Accessor("remoteFriendListUpdateHandler")
    void autism$setRemoteFriendListUpdateHandler(RemoteFriendListUpdateHandler handler);

    @Mutable
    @Accessor("reportingContext")
    void autism$setReportingContext(ReportingContext context);

    @Mutable
    @Accessor("profileFuture")
    void autism$setProfileFuture(CompletableFuture<ProfileResult> future);

    @Mutable
    @Accessor("services")
    void autism$setServices(Services services);

    @Accessor("rightClickDelay")
    int autism$getRightClickDelay();

    @Accessor("rightClickDelay")
    void autism$setRightClickDelay(int delay);
}
