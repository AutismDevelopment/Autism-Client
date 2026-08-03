package autismclient.util.multi;

import autismclient.util.AutismAccount;
import autismclient.util.AutismAccountManager;
import autismclient.util.AutismAccountSessionSwitcher;
import autismclient.util.AutismAccountType;
import autismclient.util.AutismAuthNetwork;
import com.mojang.authlib.Environment;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.Services;
import net.minecraft.util.SignatureValidator;

import java.util.Optional;

final class MultiIdentityResolver {
    private static final Environment ALTENING_ENVIRONMENT = new Environment(
        "http://sessionserver.thealtening.com",
        "http://authserver.thealtening.com",
        "https://api.mojang.com",
        "The Altening"
    );

    record Identity(
        String accountId,
        AutismAccountType type,
        User user,
        MinecraftSessionService sessionService,
        ProfileKeyPairManager keyPairManager,

        SignatureValidator profileKeyValidator
    ) {
    }

    private MultiIdentityResolver() {
    }

    static Identity resolve(String accountId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(accountId)) {
            User user = AutismAccountSessionSwitcher.getOriginalUser();
            YggdrasilAuthenticationService authentication =
                new YggdrasilAuthenticationService(AutismAuthNetwork.directProxy());
            Services services = Services.create(authentication, minecraft.gameDirectory);
            return new Identity(
                accountId,
                user.getAccessToken().isBlank() ? AutismAccountType.Cracked : AutismAccountType.Session,
                user,
                services.sessionService(),
                keyManager(minecraft, authentication, user),
                profileKeyValidator(services)
            );
        }

        AutismAccount stored = AutismAccountManager.get().findById(accountId);
        if (stored == null) throw new IllegalStateException("Account no longer exists");
        AutismAccount account = copy(stored);
        if (!account.fetchInfoSilently()) {
            throw new IllegalStateException(account.lastError().isBlank() ? "Account authentication failed" : account.lastError());
        }
        AutismAccountManager.get().applyResolvedCredentials(account);

        User user = new User(
            account.username,
            parseProfileId(account),
            accessToken(account),
            Optional.empty(),
            Optional.empty()
        );
        YggdrasilAuthenticationService authentication = account.type == AutismAccountType.TheAltening
            ? new YggdrasilAuthenticationService(minecraft.getProxy(), ALTENING_ENVIRONMENT)
            : new YggdrasilAuthenticationService(AutismAuthNetwork.directProxy());

        YggdrasilAuthenticationService mojangAuth =
            new YggdrasilAuthenticationService(AutismAuthNetwork.directProxy());
        Services services = Services.create(authentication, minecraft.gameDirectory);
        return new Identity(account.id, account.type, user, services.sessionService(),
            keyManager(minecraft, mojangAuth, user), profileKeyValidator(services));
    }

    private static ProfileKeyPairManager keyManager(Minecraft minecraft, YggdrasilAuthenticationService auth, User user) {
        if (user.getAccessToken() == null || user.getAccessToken().isBlank()) return ProfileKeyPairManager.EMPTY_KEY_MANAGER;
        try {
            UserApiService userApi = auth.createUserApiService(user.getAccessToken());
            return ProfileKeyPairManager.create(userApi, user, minecraft.gameDirectory.toPath());
        } catch (RuntimeException ignored) {
            return ProfileKeyPairManager.EMPTY_KEY_MANAGER;
        }
    }

    private static SignatureValidator profileKeyValidator(Services services) {
        try {
            return services.canValidateProfileKeys() ? services.profileKeySignatureValidator() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static java.util.UUID parseProfileId(AutismAccount account) {
        if (account.uuid != null && !account.uuid.isBlank()) {
            try {
                return UndashedUuid.fromStringLenient(account.uuid);
            } catch (RuntimeException ignored) {
                try {
                    return java.util.UUID.fromString(account.uuid);
                } catch (RuntimeException ignoredAgain) {
                }
            }
        }
        return UUIDUtil.createOfflinePlayerUUID(account.username);
    }

    private static String accessToken(AutismAccount account) {
        if (account.type == AutismAccountType.TheAltening) return safe(account.sessionToken);
        if (account.type == AutismAccountType.Cracked || account.type == AutismAccountType.Generated) return "";
        return safe(account.token);
    }

    private static AutismAccount copy(AutismAccount source) {
        AutismAccount copy = new AutismAccount();
        copy.id = source.stableId();
        copy.type = source.type;
        copy.label = safe(source.label);
        copy.token = safe(source.token);
        copy.sessionToken = safe(source.sessionToken);
        copy.username = safe(source.username);
        copy.uuid = safe(source.uuid);
        copy.sessionTokenExpiresAt = source.sessionTokenExpiresAt;
        return copy;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
