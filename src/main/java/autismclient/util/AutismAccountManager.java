package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.io.File;
import java.util.Iterator;
import java.util.List;

public final class AutismAccountManager extends PersistentNbtManager<AutismAccount> implements Iterable<AutismAccount> {
    private static final AutismAccountManager INSTANCE = new AutismAccountManager();

    private AutismAccountManager() {
    }

    public static AutismAccountManager get() {
        INSTANCE.ensureLoaded();
        AutismMeteorImport.ensureImported();
        INSTANCE.ensureStableIds();
        return INSTANCE;
    }

    private synchronized void ensureStableIds() {
        boolean changed = false;
        for (AutismAccount account : items) {
            if (account == null) continue;
            account.stableId();
            changed |= account.generatedStableId;
            account.generatedStableId = false;
        }
        if (changed) save();
    }

    public synchronized AutismAccount findById(String id) {
        if (id == null || id.isBlank()) return null;
        for (AutismAccount account : items) {
            if (id.equals(account.stableId())) return account;
        }
        return null;
    }

    public synchronized void applyResolvedCredentials(AutismAccount resolved) {
        if (resolved == null || resolved.id == null) return;
        AutismAccount stored = findById(resolved.id);
        if (stored == null) return;
        stored.label = resolved.label;
        stored.token = resolved.token;
        stored.sessionToken = resolved.sessionToken;
        stored.username = resolved.username;
        stored.uuid = resolved.uuid;
        stored.sessionTokenExpiresAt = resolved.sessionTokenExpiresAt;
        save();
    }

    public synchronized boolean rename(String accountId, String newLabel) {
        if (newLabel == null || newLabel.isBlank()) return false;
        AutismAccount stored = findById(accountId);
        if (stored == null || stored.type != AutismAccountType.Cracked) return false;
        String trimmed = newLabel.trim();
        if (trimmed.equals(stored.label)) return true;
        for (AutismAccount account : items) {
            if (account != stored && trimmed.equalsIgnoreCase(account.label)) return false;
        }
        stored.label = trimmed;
        stored.username = trimmed;
        stored.uuid = net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(trimmed).toString();
        save();
        return true;
    }

    public synchronized void invalidateSessionToken(String accountId) {
        AutismAccount stored = findById(accountId);
        if (stored == null || stored.type != AutismAccountType.Microsoft) return;
        stored.token = "";
        stored.sessionTokenExpiresAt = 0L;
        save();
    }

    @Override
    protected File saveFile() {
        return new File(Minecraft.getInstance().gameDirectory, "autism-accounts.nbt");
    }

    @Override
    protected String listKey() {
        return "accounts";
    }

    @Override
    protected AutismAccount fromTag(CompoundTag tag) {
        return new AutismAccount().fromTag(tag);
    }

    @Override
    protected CompoundTag toTag(AutismAccount item) {
        return item.toTag();
    }

    @Override
    protected String describe() {
        return "Autism accounts";
    }

    public synchronized void add(AutismAccount account) {
        if (account == null) return;
        items.add(account);
        save();
    }

    public synchronized int addAll(List<AutismAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) return 0;
        int added = 0;
        for (AutismAccount account : accounts) {
            if (account != null) {
                items.add(account);
                added++;
            }
        }
        if (added > 0) save();
        return added;
    }

    public synchronized void remove(AutismAccount account) {
        if (items.remove(account)) save();
    }

    public synchronized int removeExpired() {
        int removed = 0;
        Iterator<AutismAccount> iterator = items.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().checkStatus == AutismAccount.CheckStatus.EXPIRED) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) save();
        return removed;
    }

    public void login(AutismAccount account) {
        if (account == null) return;
        Thread thread = new Thread(() -> {
            if (account.fetchInfo() && account.login()) {
                save();
                AutismClientMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
            } else {
                AutismClientMessaging.sendPrefixed("Failed to login account: " + account.displayName() + account.failureSuffix());
            }
        }, "Autism-Account-Login");
        thread.setDaemon(true);
        thread.start();
    }

    public void loginMicrosoft(AutismAccount account) {
        if (account == null || account.type != AutismAccountType.Microsoft) return;
        AutismMicrosoftLogin.getRefreshToken(refreshToken -> {
            if (refreshToken == null) {
                AutismClientMessaging.sendPrefixed("Microsoft login cancelled or failed.");
                return;
            }
            account.label = refreshToken;
            Thread thread = new Thread(() -> {
                if (account.fetchInfo() && account.login()) {
                    synchronized (this) {
                        if (!items.contains(account)) items.add(account);
                    }
                    save();
                    AutismClientMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
                } else {
                    AutismClientMessaging.sendPrefixed("Failed to login Microsoft account" + account.failureSuffix() + ".");
                }
            }, "Autism-Microsoft-Login");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public Iterator<AutismAccount> iterator() {
        return all().iterator();
    }
}
