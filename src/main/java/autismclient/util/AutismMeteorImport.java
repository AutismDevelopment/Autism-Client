package autismclient.util;

import autismclient.AutismClientAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AutismMeteorImport {
    private static final String STATE_FILE = "autism-meteor-import.nbt";
    private static final String METEOR_DIR = "meteor-client";

    private static final int PROXY_IMPORT_VERSION = 1;

    private static volatile boolean started;

    private AutismMeteorImport() {
    }

    public static void ensureImported() {
        if (started) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return;
        synchronized (AutismMeteorImport.class) {
            if (started) return;
            started = true;
        }
        try {
            runImport(mc.gameDirectory);
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("Meteor import failed", t);
        }
    }

    private static void runImport(File gameDir) {
        File meteorDir = new File(gameDir, METEOR_DIR);
        if (!meteorDir.isDirectory()) return;

        State state = loadState(gameDir);

        boolean upgradeProxyImport = state.proxyImportVersion < PROXY_IMPORT_VERSION;
        if (upgradeProxyImport) state.proxiesSig = "";

        boolean changed = importAccounts(new File(meteorDir, "accounts.nbt"), state);
        changed |= importProxies(new File(meteorDir, "proxies.nbt"), state);

        if (upgradeProxyImport) {
            state.proxyImportVersion = PROXY_IMPORT_VERSION;
            changed = true;
        }
        if (changed) saveState(gameDir, state);
    }

    private static boolean importAccounts(File file, State state) {
        if (!file.isFile()) return false;
        String signature = fileSignature(file);
        if (signature.equals(state.accountsSig)) return false;

        try {
            CompoundTag root = NbtIo.read(file.toPath());
            if (root != null) {
                for (Tag element : root.getListOrEmpty("accounts")) {
                    if (!(element instanceof CompoundTag tag)) continue;
                    AutismAccount account = mapAccount(tag);
                    if (account == null) continue;
                    if (!state.importedAccounts.add(accountKey(account))) continue;
                    AutismAccountManager.get().add(account);
                }
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("Failed to read Meteor accounts", e);
            return !state.importedAccounts.isEmpty();
        }
        state.accountsSig = signature;
        return true;
    }

    private static AutismAccount mapAccount(CompoundTag tag) {
        String type = tag.getStringOr("type", "");
        String name = tag.getStringOr("name", "");
        String token = tag.getStringOr("token", "");
        String cacheUser = "";
        String cacheUuid = "";
        Optional<CompoundTag> cache = tag.getCompound("cache");
        if (cache.isPresent()) {
            cacheUser = cache.get().getStringOr("username", "");
            cacheUuid = cache.get().getStringOr("uuid", "");
        }

        AutismAccount account = new AutismAccount();
        account.uuid = cacheUuid;
        account.username = cacheUser;
        switch (type) {
            case "Cracked" -> {
                account.type = AutismAccountType.Cracked;
                account.label = name;
                if (account.username.isBlank()) account.username = name;
            }
            case "Microsoft" -> {
                account.type = AutismAccountType.Microsoft;
                account.label = name;
            }
            case "Session" -> {
                account.type = AutismAccountType.Session;
                account.label = name;
                account.token = token;
            }
            case "TheAltening" -> {
                account.type = AutismAccountType.TheAltening;
                account.label = name;
                account.token = token.isBlank() ? name : token;
            }
            default -> {
                return null;
            }
        }

        boolean usable = !account.label.isBlank() || !account.username.isBlank() || !account.token.isBlank();
        return usable ? account : null;
    }

    private static String accountKey(AutismAccount account) {
        String uuid = account.uuid == null ? "" : account.uuid.trim().toLowerCase(Locale.ROOT);
        if (!uuid.isEmpty()) return account.type.name() + "|u|" + uuid;
        String identity = account.username != null && !account.username.isBlank() ? account.username : account.label;
        return account.type.name() + "|n|" + (identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean importProxies(File file, State state) {
        if (!file.isFile()) return false;
        String signature = fileSignature(file);
        if (signature.equals(state.proxiesSig)) return false;

        try {
            CompoundTag root = NbtIo.read(file.toPath());
            if (root != null) {
                for (Tag element : root.getListOrEmpty("proxies")) {
                    if (!(element instanceof CompoundTag tag)) continue;
                    AutismProxy proxy = mapProxy(tag);
                    if (proxy == null || !proxy.isValid()) continue;
                    if (!state.importedProxies.add(proxyKey(proxy))) continue;
                    AutismProxyManager.get().add(proxy);
                }
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("Failed to read Meteor proxies", e);
            return !state.importedProxies.isEmpty();
        }
        state.proxiesSig = signature;
        return true;
    }

    private static AutismProxy mapProxy(CompoundTag proxyTag) {

        Optional<CompoundTag> settings = proxyTag.getCompound("settings");
        return settings.isPresent() ? mapProxyFromSettings(settings.get()) : mapProxyFlat(proxyTag);
    }

    private static AutismProxy mapProxyFromSettings(CompoundTag settings) {

        Map<String, CompoundTag> byName = new HashMap<>();
        for (Tag groupTag : settings.getListOrEmpty("groups")) {
            if (!(groupTag instanceof CompoundTag group)) continue;
            for (Tag settingTag : group.getListOrEmpty("settings")) {
                if (!(settingTag instanceof CompoundTag setting)) continue;
                String settingName = setting.getStringOr("name", "");
                if (!settingName.isEmpty()) byName.put(settingName, setting);
            }
        }
        return buildImportedProxy(
            settingString(byName, "address", ""),
            settingInt(byName, "port", 0),
            settingString(byName, "name", ""),
            settingString(byName, "username", ""),
            settingString(byName, "password", ""),
            settingString(byName, "type", "Socks5"));
    }

    private static AutismProxy mapProxyFlat(CompoundTag tag) {
        return buildImportedProxy(
            tag.getStringOr("address", ""),
            tag.getIntOr("port", 0),
            tag.getStringOr("name", ""),
            tag.getStringOr("username", ""),
            tag.getStringOr("password", ""),
            tag.getStringOr("type", "Socks5"));
    }

    private static AutismProxy buildImportedProxy(String address, int port, String name, String username, String password, String type) {
        if (address == null || address.isBlank() || port <= 0) return null;
        AutismProxy proxy = new AutismProxy();
        proxy.address = address.trim();
        proxy.port = port;
        proxy.name = name == null ? "" : name;
        proxy.username = username == null ? "" : username;
        proxy.password = password == null ? "" : password;

        proxy.type = type != null && type.toLowerCase(Locale.ROOT).contains("4") ? AutismProxyType.Socks4 : AutismProxyType.Socks5;
        proxy.enabled = false;
        return proxy;
    }

    private static String settingString(Map<String, CompoundTag> byName, String key, String def) {
        CompoundTag setting = byName.get(key);
        return setting == null ? def : setting.getStringOr("value", def);
    }

    private static int settingInt(Map<String, CompoundTag> byName, String key, int def) {
        CompoundTag setting = byName.get(key);
        return setting == null ? def : setting.getIntOr("value", def);
    }

    private static String proxyKey(AutismProxy proxy) {
        return proxy.type.name() + "|" + proxy.address.trim().toLowerCase(Locale.ROOT) + "|" + proxy.port;
    }

    private static final class State {
        String accountsSig = "";
        String proxiesSig = "";
        int proxyImportVersion = 0;
        final Set<String> importedAccounts = new LinkedHashSet<>();
        final Set<String> importedProxies = new LinkedHashSet<>();
    }

    private static State loadState(File gameDir) {
        State state = new State();
        File file = new File(gameDir, STATE_FILE);
        if (!file.isFile()) return state;
        try {
            CompoundTag tag = NbtIo.read(file.toPath());
            if (tag == null) return state;
            state.accountsSig = tag.getStringOr("accountsSig", "");
            state.proxiesSig = tag.getStringOr("proxiesSig", "");
            state.proxyImportVersion = tag.getIntOr("proxyImportVersion", 0);
            readKeys(tag.getListOrEmpty("importedAccounts"), state.importedAccounts);
            readKeys(tag.getListOrEmpty("importedProxies"), state.importedProxies);
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("Failed to read Meteor import state", e);
        }
        return state;
    }

    private static void saveState(File gameDir, State state) {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("accountsSig", state.accountsSig);
            tag.putString("proxiesSig", state.proxiesSig);
            tag.putInt("proxyImportVersion", state.proxyImportVersion);
            tag.put("importedAccounts", writeKeys(state.importedAccounts));
            tag.put("importedProxies", writeKeys(state.importedProxies));
            NbtIo.write(tag, new File(gameDir, STATE_FILE).toPath());
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("Failed to save Meteor import state", e);
        }
    }

    private static void readKeys(ListTag list, Set<String> into) {
        for (Tag element : list) {
            String value = element.asString().orElse("");
            if (!value.isEmpty()) into.add(value);
        }
    }

    private static ListTag writeKeys(Set<String> keys) {
        ListTag list = new ListTag();
        for (String key : keys) list.add(StringTag.valueOf(key));
        return list;
    }

    private static String fileSignature(File file) {
        return file.lastModified() + ":" + file.length();
    }
}
