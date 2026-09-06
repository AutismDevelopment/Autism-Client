package autismclient.util.multi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MultiSharedGui {

    public record Group(String key, String label, List<String> accountIds) {
        public String representativeId() {
            return accountIds.getFirst();
        }

        public int size() {
            return accountIds.size();
        }
    }

    private MultiSharedGui() {
    }

    public static List<Group> groups() {
        Map<String, List<String>> byKey = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (MultiSession.Snapshot snapshot : MultiManager.get().snapshots()) {
            if (!snapshot.ready()) continue;
            String key;
            String name;
            if (snapshot.customMenuOpen()) {
                key = "custom";
                name = "CustomScreen";
            } else if (snapshot.openScreen() == null || snapshot.openScreen().isBlank()) {
                key = "inv";
                name = "Inventory";
            } else {
                key = "s:" + snapshot.openScreen();
                name = snapshot.openScreen();
            }
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(snapshot.accountId());
            names.putIfAbsent(key, name);
        }
        List<Group> out = new ArrayList<>(byKey.size());
        for (Map.Entry<String, List<String>> entry : byKey.entrySet()) {
            String name = MultiManager.singleLine(names.get(entry.getKey()), 40);
            out.add(new Group(entry.getKey(), name + " (" + entry.getValue().size() + ")",
                List.copyOf(entry.getValue())));
        }
        out.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return out;
    }

    public static Group pick(List<Group> groups, String preferredKey) {
        if (groups == null || groups.isEmpty()) return null;
        if (preferredKey != null && !preferredKey.isEmpty()) {
            for (Group group : groups) {
                if (group.key().equals(preferredKey)) return group;
            }
        }
        return groups.getFirst();
    }
}
