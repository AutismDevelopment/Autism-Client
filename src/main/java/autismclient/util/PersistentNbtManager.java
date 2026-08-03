package autismclient.util;

import autismclient.AutismClientAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public abstract class PersistentNbtManager<T> {
    protected final List<T> items = new ArrayList<>();
    private boolean loaded;
    private long changeRevision;

    public final synchronized long changeRevision() {
        return changeRevision;
    }

    protected abstract File saveFile();

    protected abstract String listKey();

    protected abstract T fromTag(CompoundTag tag);

    protected abstract CompoundTag toTag(T item);

    protected void readExtra(CompoundTag root) {
    }

    protected void writeExtra(CompoundTag root) {
    }

    protected abstract String describe();

    protected final synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        changeRevision++;
        File file = saveFile();
        if (!file.exists()) return;
        try {
            CompoundTag tag = NbtIo.read(file.toPath());
            if (tag == null) return;
            readExtra(tag);
            items.clear();
            ListTag list = tag.getListOrEmpty(listKey());
            for (Tag element : list) {
                if (element instanceof CompoundTag compoundTag) items.add(fromTag(compoundTag));
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.error("Failed to load " + describe(), e);
        }
    }

    public void save() {
        CompoundTag tag = new CompoundTag();
        List<T> snapshot;
        File target;
        synchronized (this) {
            changeRevision++;
            writeExtra(tag);

            snapshot = new ArrayList<>(items);
            target = saveFile();
        }
        ListTag list = new ListTag();
        for (T item : snapshot) list.add(toTag(item));
        tag.put(listKey(), list);
        String key = "nbt:" + target.getAbsolutePath();
        File destination = target;
        SaveCoordinator.enqueueLatest(key, () -> writeSnapshot(tag, destination));
    }

    private void writeSnapshot(CompoundTag tag, File target) {
        File parent = target.getParentFile();
        if (parent == null) parent = new File(".");
        if (parent != null) parent.mkdirs();
        File tmp = new File(parent, target.getName() + ".tmp");
        File backup = new File(parent, target.getName() + ".bak");
        try {
            NbtIo.write(tag, tmp.toPath());
        } catch (Exception e) {
            AutismClientAddon.LOG.error("Failed to save " + describe(), e);
            tmp.delete();
            return;
        }
        try {
            if (target.exists()) Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("Failed to back up " + describe(), e);
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFailed) {
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                AutismClientAddon.LOG.error("Failed to swap in " + describe(), e);
            }
        }
    }

    public synchronized List<T> all() {
        return new ArrayList<>(items);
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized boolean contains(T item) {
        return items.contains(item);
    }
}
