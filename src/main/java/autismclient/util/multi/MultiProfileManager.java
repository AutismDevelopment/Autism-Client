package autismclient.util.multi;

import autismclient.util.PersistentNbtManager;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MultiProfileManager extends PersistentNbtManager<MultiProfile> implements Iterable<MultiProfile> {
    private static final MultiProfileManager INSTANCE = new MultiProfileManager();
    private static final int MAX_NAME_LENGTH = 64;
    private static final Pattern NUMBERED_NAME = Pattern.compile("^(.*) \\((\\d+)\\)$");
    private long revision;
    private boolean namesNormalized;
    private String selectedId = "";

    private MultiProfileManager() {
    }

    public static MultiProfileManager get() {
        INSTANCE.ensureLoaded();
        INSTANCE.ensureUniqueStoredNames();
        return INSTANCE;
    }

    @Override
    protected File saveFile() {
        return new File(Minecraft.getInstance().gameDirectory, "autism-multi.nbt");
    }

    @Override
    protected String listKey() {
        return "profiles";
    }

    @Override
    protected MultiProfile fromTag(CompoundTag tag) {
        return MultiProfile.fromTag(tag);
    }

    @Override
    protected CompoundTag toTag(MultiProfile item) {
        return item.toTag();
    }

    @Override
    protected String describe() {
        return "Multi profiles";
    }

    @Override
    protected void writeExtra(CompoundTag root) {
        root.putString("selected", selectedId == null ? "" : selectedId);
    }

    @Override
    protected void readExtra(CompoundTag root) {
        selectedId = root.getStringOr("selected", "");
    }

    public synchronized String selectedId() {
        if (selectedId != null && !selectedId.isBlank()) {
            for (MultiProfile profile : items) if (selectedId.equals(profile.id)) return selectedId;
        }
        return items.isEmpty() ? "" : items.get(0).id;
    }

    public synchronized void setSelectedId(String id) {
        String value = id == null ? "" : id;
        if (value.equals(selectedId)) return;
        selectedId = value;
        save();
    }

    public synchronized void put(MultiProfile profile) {
        if (profile == null) return;
        profile.normalize();
        profile.name = uniqueName(profile.name, profile.id, items);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(profile.id)) {
                items.set(i, new MultiProfile(profile));
                revision++;
                save();
                return;
            }
        }
        items.add(new MultiProfile(profile));
        revision++;
        save();
    }

    public synchronized void remove(String id) {
        if (id == null) return;
        if (items.removeIf(profile -> id.equals(profile.id))) {
            revision++;
            save();
        }
    }

    public synchronized MultiProfile find(String id) {
        if (id == null) return null;
        for (MultiProfile profile : items) {
            if (id.equals(profile.id)) return new MultiProfile(profile);
        }
        return null;
    }

    @Override
    public synchronized List<MultiProfile> all() {
        List<MultiProfile> copies = new ArrayList<>(items.size());
        for (MultiProfile profile : items) copies.add(new MultiProfile(profile));
        return copies;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized String nextAvailableName(String requestedName, String profileId) {
        return uniqueName(requestedName, profileId, items);
    }

    static String uniqueName(String requestedName, String profileId, List<MultiProfile> profiles) {
        String requested = normalizeName(requestedName);
        if (!isNameTaken(requested, profileId, profiles)) return requested;

        String base = requested;
        long number = 1;
        Matcher matcher = NUMBERED_NAME.matcher(requested);
        if (matcher.matches() && !matcher.group(1).isBlank()) {
            base = matcher.group(1).stripTrailing();
            try {
                number = Math.max(1L, Long.parseLong(matcher.group(2)) + 1L);
            } catch (NumberFormatException ignored) {
                number = 1L;
            }
        }

        while (true) {
            String suffix = " (" + number + ")";
            int baseLimit = Math.max(1, MAX_NAME_LENGTH - suffix.length());
            String fittedBase = base.length() > baseLimit ? base.substring(0, baseLimit).stripTrailing() : base;
            if (fittedBase.isBlank()) fittedBase = "Profile";
            String candidate = fittedBase + suffix;
            if (!isNameTaken(candidate, profileId, profiles)) return candidate;
            number++;
        }
    }

    private static String normalizeName(String requestedName) {
        String value = MultiManager.singleLine(requestedName == null ? "" : requestedName, MAX_NAME_LENGTH);
        return value.isBlank() ? "New profile" : value;
    }

    private static boolean isNameTaken(String name, String profileId, List<MultiProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return false;
        String key = name.toLowerCase(Locale.ROOT);
        for (MultiProfile profile : profiles) {
            if (profile == null || (profileId != null && profileId.equals(profile.id))) continue;
            if (normalizeName(profile.name).toLowerCase(Locale.ROOT).equals(key)) return true;
        }
        return false;
    }

    private synchronized void ensureUniqueStoredNames() {
        if (namesNormalized) return;
        namesNormalized = true;
        List<MultiProfile> accepted = new ArrayList<>(items.size());
        boolean changed = false;
        for (MultiProfile profile : items) {
            String unique = uniqueName(profile.name, null, accepted);
            if (!unique.equals(profile.name)) {
                profile.name = unique;
                changed = true;
            }
            accepted.add(profile);
        }
        if (changed) {
            revision++;
            save();
        }
    }

    public synchronized int replaceMacroReferences(String oldName, String newName) {
        String before = oldName == null ? "" : oldName.trim();
        if (before.isBlank()) return 0;
        String after = newName == null ? "" : newName.trim();
        int changed = 0;
        for (MultiProfile profile : items) {
            boolean touched = profile.replaceMacroReference(before, after);
            if (touched) {
                profile.normalize();
                changed++;
            }
        }
        if (changed > 0) {
            revision++;
            save();
        }
        return changed;
    }

    @Override
    public Iterator<MultiProfile> iterator() {
        return all().iterator();
    }
}
