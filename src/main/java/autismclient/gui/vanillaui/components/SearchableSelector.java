package autismclient.gui.vanillaui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class SearchableSelector<T> {
    private final Function<T, String> searchText;
    private List<T> source = List.of();
    private List<T> filtered = List.of();
    private String query = "";
    private boolean dirty = true;

    public SearchableSelector(Function<T, String> searchText) {
        this.searchText = Objects.requireNonNull(searchText);
    }

    public void setItems(List<T> items) {
        List<T> next = items == null || items.isEmpty() ? List.of() : List.copyOf(items);
        if (source.equals(next)) return;
        source = next;
        dirty = true;
    }

    public void setQuery(String query) {
        String next = normalize(query);
        if (this.query.equals(next)) return;
        this.query = next;
        dirty = true;
    }

    public List<T> items() {
        rebuildIfDirty();
        return filtered;
    }

    public int size() {
        return items().size();
    }

    private void rebuildIfDirty() {
        if (!dirty) return;
        if (query.isEmpty()) {
            filtered = source;
        } else {
            ArrayList<T> next = new ArrayList<>();
            for (T item : source) {
                String haystack = normalize(searchText.apply(item));
                if (haystack.contains(query)) next.add(item);
            }
            filtered = List.copyOf(next);
        }
        dirty = false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
