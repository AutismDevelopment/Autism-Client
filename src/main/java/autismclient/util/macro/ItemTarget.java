package autismclient.util.macro;

import autismclient.util.AutismRegistryLabels;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ItemTarget {
    public static final String TAG_SLOT = "slot";
    public static final String TAG_REGISTRY_ID = "registryId";
    public static final String TAG_DISPLAY = "display";
    public static final String TAG_TEXT_JSON = "textJson";
    public static final String TAG_MANUAL = "manual";
    public static final String TAG_TEMPLATE = "template";

    public int slot = -1;
    public String registryId = "";
    public String display = "";
    public String textJson = "";
    public boolean manual = false;
    public String template = "";

    public ItemTarget() {
    }

    public ItemTarget copy() {
        ItemTarget copy = new ItemTarget();
        copy.slot = slot;
        copy.registryId = registryId;
        copy.display = display;
        copy.textJson = textJson;
        copy.manual = manual;
        copy.template = template;
        return copy;
    }

    public boolean hasSlot() {
        return slot >= 0;
    }

    public boolean hasRegistryId() {
        return registryId != null && !registryId.isBlank();
    }

    public boolean hasDisplay() {
        return display != null && !display.isBlank();
    }

    public boolean hasRichText() {
        return textJson != null && !textJson.isBlank();
    }

    public boolean hasIdentity() {
        return hasRegistryId() || hasDisplay() || hasRichText();
    }

    public Component displayComponent() {
        Component rich = MacroExecutor.deserializeTextComponent(textJson);
        if (rich != null) return rich.copy();
        return Component.literal(editorText());
    }

    public Component editorComponent(String editedValue) {
        String safeValue = editedValue == null ? "" : editedValue;
        Component rich = MacroExecutor.deserializeTextComponent(textJson);
        if (rich == null) return Component.literal(safeValue);
        return rebuildStyledComponent(rich, safeValue);
    }

    public ItemTarget withEditedDisplay(String editedValue) {
        ItemTarget target = copy();
        String safeValue = manualDisplayText(editedValue);
        target.display = safeValue;
        target.manual = true;
        target.textJson = "";
        target.template = safeValue.contains("{") ? safeValue : "";
        if (safeValue.isEmpty()) {
            target.registryId = "";
            return target;
        }
        return target;
    }

    public ItemTarget withEditedRichDisplay(String editedValue) {
        String safeValue = editedValue == null ? "" : editedValue;
        ItemTarget target = copy();
        if (safeValue.isEmpty()) {
            target.display = "";
            target.textJson = "";
            target.registryId = "";
            target.manual = true;
            target.template = "";
            return target;
        }
        Component styled = editorComponent(safeValue);
        target.display = safeValue;
        target.textJson = styled == null ? "" : MacroExecutor.serializeTextComponent(styled);
        target.manual = false;
        target.template = safeValue.contains("{") ? safeValue : "";
        return target;
    }

    public String editorText() {
        if (hasDisplay()) return display;
        if (hasRegistryId()) return registryId;
        return "";
    }

    public String summaryText() {
        String name = displayLabel();
        if (hasSlot() && !name.isBlank()) return "Slot " + slot + ": " + name;
        if (hasSlot()) return "Slot " + slot;
        return name;
    }

    public String displayLabel() {
        if (hasRichText()) {
            Component rich = MacroExecutor.deserializeTextComponent(textJson);
            if (rich != null && !rich.getString().isBlank()) return rich.getString();
        }

        if (hasDisplay()) {
            if (!hasRegistryId()) return display;

            String registryLabel = AutismRegistryLabels.item(registryId);
            if (!AutismRegistryLabels.looksLikeRawIdentifierLabel(display, registryId)
                    && !display.equalsIgnoreCase(registryLabel))
            {
                return display;
            }
            return registryLabel;
        }

        if (hasRegistryId()) return AutismRegistryLabels.item(registryId);
        return "";
    }

    public Component listComponent() {
        if (hasRichText()) {
            Component rich = MacroExecutor.deserializeTextComponent(textJson);
            if (rich != null) return rich.copy();
        }
        return Component.literal(displayLabel());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (hasSlot()) tag.putInt(TAG_SLOT, slot);
        if (hasRegistryId()) tag.putString(TAG_REGISTRY_ID, registryId);
        if (hasDisplay()) tag.putString(TAG_DISPLAY, display);
        if (hasRichText()) tag.putString(TAG_TEXT_JSON, textJson);
        if (template != null && !template.isBlank()) tag.putString(TAG_TEMPLATE, template);
        tag.putBoolean(TAG_MANUAL, manual);
        return tag;
    }

    public static ItemTarget fromTag(CompoundTag tag) {
        ItemTarget target = new ItemTarget();
        if (tag == null) return target;
        if (tag.contains(TAG_SLOT)) target.slot = tag.getIntOr(TAG_SLOT, -1);
        if (tag.contains(TAG_REGISTRY_ID)) target.registryId = tag.getStringOr(TAG_REGISTRY_ID, "");
        if (tag.contains(TAG_DISPLAY)) target.display = tag.getStringOr(TAG_DISPLAY, "");
        if (tag.contains(TAG_TEXT_JSON)) target.textJson = tag.getStringOr(TAG_TEXT_JSON, "");
        if (tag.contains(TAG_MANUAL)) target.manual = tag.getBooleanOr(TAG_MANUAL, false);
        target.template = tag.getStringOr(TAG_TEMPLATE, "");
        if (target.manual) {
            target.display = manualDisplayText(target.display);
            target.textJson = "";
        } else if (!target.hasRichText() && target.hasDisplay()) {
            target.display = manualDisplayText(target.display);
        }
        return target;
    }

    public static ItemTarget fromElement(Tag element) {
        if (element instanceof CompoundTag compound) return fromTag(compound);
        return fromLegacyEntry(element == null ? "" : element.asString().orElse(""));
    }

    public static List<ItemTarget> fromElementList(ListTag list) {
        List<ItemTarget> targets = new ArrayList<>();
        if (list == null) return targets;
        for (Tag element : list) {
            ItemTarget target = fromElement(element);
            if (target.hasSlot() || target.hasIdentity()) targets.add(target);
        }
        return targets;
    }

    public static ListTag toTagList(List<ItemTarget> targets) {
        ListTag list = new ListTag();
        if (targets == null) return list;
        for (ItemTarget target : targets) {
            if (target == null) continue;
            if (!target.hasSlot() && !target.hasIdentity()) continue;
            list.add(target.toTag());
        }
        return list;
    }

    public static List<ItemTarget> copyList(List<ItemTarget> targets) {
        List<ItemTarget> copies = new ArrayList<>();
        if (targets == null) return copies;
        for (ItemTarget target : targets) {
            if (target != null) copies.add(target.copy());
        }
        return copies;
    }

    public static ItemTarget manualText(String text) {
        ItemTarget target = new ItemTarget();
        target.display = manualDisplayText(text);
        target.manual = true;
        if (text != null && text.contains("{")) target.template = text;
        return target;
    }

    public static ItemTarget registry(String registryId) {
        ItemTarget target = new ItemTarget();
        target.registryId = registryId == null ? "" : registryId.trim();
        target.display = target.registryId;
        target.manual = false;
        return target;
    }

    public static ItemTarget slotOnly(int slot) {
        ItemTarget target = new ItemTarget();
        target.slot = slot;
        return target;
    }

    public static ItemTarget capture(ItemStack stack, int visibleSlot) {
        ItemTarget target = new ItemTarget();
        target.slot = visibleSlot;
        if (stack == null || stack.isEmpty()) return target;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        target.registryId = id == null ? "" : id.toString();
        target.display = stack.getHoverName().getString();
        target.manual = false;

        if (shouldCaptureRichText(stack)) {
            target.textJson = MacroExecutor.serializeTextComponent(stack.getHoverName());
        }

        return target;
    }

    public static ItemTarget fromLegacyEntry(String raw) {
        if (raw == null) return new ItemTarget();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return new ItemTarget();

        ItemTarget target = new ItemTarget();
        String body = trimmed;
        if (trimmed.startsWith("#")) {
            int separator = trimmed.indexOf('|');
            String slotPart = separator >= 0 ? trimmed.substring(1, separator).trim() : trimmed.substring(1).trim();
            try {
                int parsedSlot = Integer.parseInt(slotPart);
                if (parsedSlot >= 0) target.slot = parsedSlot;
            } catch (NumberFormatException ignored) {
                return classifyFreeform(trimmed);
            }
            body = separator >= 0 && separator + 1 < trimmed.length() ? trimmed.substring(separator + 1).trim() : "";
        }

        if (!body.isBlank()) {
            ItemTarget bodyTarget = classifyFreeform(body);
            target.registryId = bodyTarget.registryId;
            target.display = bodyTarget.display;
            target.textJson = bodyTarget.textJson;
            target.manual = bodyTarget.manual;
        }

        return target;
    }

    public String toLegacyEntry() {
        String body = editorText();
        if (hasSlot() && body.isBlank()) return "#" + slot;
        if (hasSlot()) return "#" + slot + "|" + body;
        return body;
    }

    public int score(ItemStack stack, int visibleSlot) {
        if (stack == null || stack.isEmpty()) return -1;
        if (hasSlot() && slot != visibleSlot) return -1;

        if (hasRichText()) {
            String stackJson = MacroExecutor.serializeTextComponent(stack.getHoverName());
            if (textJson.equals(stackJson)) return 400;
            if (hasDisplay() && display.equals(stack.getHoverName().getString())) return 350;
            return -1;
        }

        if (hasRegistryId()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) return -1;
            String fullId = id.toString();
            String rawPath = id.getPath();
            String spacedPath = rawPath.replace('_', ' ');
            if (registryId.equalsIgnoreCase(fullId)) return 320;
            if (registryId.equalsIgnoreCase(rawPath)) return 315;
            if (registryId.equalsIgnoreCase(spacedPath)) return 310;
            if (hasDisplay()) {
                String normalizedDisplay = normalize(display);
                if (normalizedDisplay.equals(normalize(fullId))) return 305;
                if (normalizedDisplay.equals(normalize(rawPath))) return 304;
                if (normalizedDisplay.equals(normalize(spacedPath))) return 303;
            }
            return -1;
        }

        if (hasDisplay()) {
            String normalizedTarget = normalize(display);
            if (normalizedTarget.isBlank()) return hasSlot() ? 100 : -1;

            String normalizedDisplay = normalize(stack.getHoverName().getString());
            if (normalizedTarget.equals(normalizedDisplay)) return manual ? 220 : 240;

            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) {
                String fullId = normalize(id.toString());
                String rawPath = normalize(id.getPath());
                String spacedPath = normalize(id.getPath().replace('_', ' '));
                if (normalizedTarget.equals(fullId)) return manual ? 210 : 230;
                if (normalizedTarget.equals(rawPath)) return manual ? 205 : 225;
                if (normalizedTarget.equals(spacedPath)) return manual ? 200 : 220;
            }
            return -1;
        }

        return hasSlot() ? 100 : -1;
    }

    public boolean matches(ItemStack stack, int visibleSlot) {
        return score(stack, visibleSlot) >= 0;
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) out.append(ch);
            else if (Character.isWhitespace(ch) || ch == '_' || ch == '-' || ch == ':' || ch == '/' || ch == '.') out.append(' ');
            else if (ch == '\'' || ch == '"' || ch == '`' || ch == '\u2019') {

            } else {
                out.append(' ');
            }
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    private static ItemTarget classifyFreeform(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return new ItemTarget();

        ItemTarget target = new ItemTarget();
        if (looksLikeRegistryId(trimmed)) {
            target.registryId = trimmed;
            target.display = trimmed;
            target.manual = false;
            return target;
        }

        target.display = manualDisplayText(trimmed);
        target.manual = true;
        if (trimmed.contains("{")) target.template = trimmed;
        return target;
    }

    public ItemTarget resolveTemplate(net.minecraft.client.Minecraft mc) {
        String source = template == null || template.isBlank() ? editorText() : template;
        if (source == null || source.indexOf('{') < 0) return copy();
        String trimmed = source.trim();
        if (trimmed.matches("\\{[A-Za-z_][A-Za-z0-9_.-]{0,63}}")) {
            String name = trimmed.substring(1, trimmed.length() - 1);
            java.util.Optional<MacroValue> value = MacroVariables.value(name);
            if (value.isPresent() && value.get().kind() == MacroValue.Kind.ITEM) {
                MacroValue item = value.get();
                ItemTarget resolved = new ItemTarget();
                resolved.slot = item.property("slot").map(v -> parseInt(v.value(), slot)).orElse(slot);
                resolved.registryId = item.property("id").map(MacroValue::value).orElse("");
                resolved.display = item.property("name").map(MacroValue::value).orElse(item.value());
                resolved.manual = false;
                return resolved;
            }
        }
        MacroTemplate.Resolution resolution = MacroVariables.resolve(source, mc);
        if (!resolution.success() || resolution.value().isBlank()) return null;
        ItemTarget resolved = fromLegacyEntry(resolution.value());
        if (hasSlot()) resolved.slot = slot;
        return resolved;
    }

    private static int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static String manualDisplayText(String text) {
        return MacroExecutor.normalizeManualText(text);
    }

    private static boolean looksLikeRegistryId(String text) {
        if (text == null || text.isBlank()) return false;
        if (text.contains(" ")) return false;
        return text.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    }

    private static int wholeWordScore(String normalizedTarget, String normalizedCandidate, int baseScore) {
        if (normalizedTarget.isBlank() || normalizedCandidate.isBlank()) return -1;
        String[] queryWords = normalizedTarget.split(" ");
        String[] candidateWords = normalizedCandidate.split(" ");
        int matches = 0;
        for (String queryWord : queryWords) {
            boolean found = false;
            for (String candidateWord : candidateWords) {
                if (queryWord.equals(candidateWord)) {
                    found = true;
                    matches++;
                    break;
                }
            }
            if (!found) return -1;
        }
        return baseScore + matches;
    }

    private static boolean shouldCaptureRichText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        Component stackName = stack.getHoverName();
        Component defaultName = stack.getItem().getName(stack);
        if (stackName != null && defaultName != null && !stackName.getString().equals(defaultName.getString())) return true;

        String json = MacroExecutor.serializeTextComponent(stackName);
        return json.contains("\"color\"")
                || json.contains("\"bold\"")
                || json.contains("\"italic\"")
                || json.contains("\"underlined\"")
                || json.contains("\"strikethrough\"")
                || json.contains("\"obfuscated\"");
    }

    private static Component rebuildStyledComponent(Component previousComponent, String editedValue) {
        String safeValue = editedValue == null ? "" : editedValue;
        if (previousComponent == null) return Component.literal(safeValue);

        String previousValue = previousComponent.getString();
        if (previousValue.equals(safeValue)) return previousComponent.copy();
        if (safeValue.isEmpty()) return Component.empty();
        if (previousValue.isEmpty()) return Component.literal(safeValue);

        List<Style> previousStyles = flattenStyles(previousComponent, previousValue.length());
        if (previousStyles.isEmpty()) return Component.literal(safeValue);

        int prefix = longestCommonPrefix(previousValue, safeValue);
        int suffix = longestCommonSuffix(previousValue, safeValue, prefix);
        int previousLength = previousValue.length();
        int nextLength = safeValue.length();

        MutableComponent rebuilt = Component.empty();
        StringBuilder segment = new StringBuilder();
        Style segmentStyle = null;
        for (int i = 0; i < nextLength; i++) {
            Style style = styleForEditedIndex(previousStyles, previousLength, nextLength, prefix, suffix, i);
            if (segmentStyle == null) {
                segmentStyle = style;
            } else if (!segmentStyle.equals(style)) {
                rebuilt.append(Component.literal(segment.toString()).setStyle(segmentStyle));
                segment.setLength(0);
                segmentStyle = style;
            }
            segment.append(safeValue.charAt(i));
        }
        if (!segment.isEmpty()) {
            rebuilt.append(Component.literal(segment.toString()).setStyle(segmentStyle == null ? Style.EMPTY : segmentStyle));
        }
        return rebuilt;
    }

    private static List<Style> flattenStyles(Component text, int expectedLength) {
        if (text == null || expectedLength <= 0) return Collections.emptyList();
        List<Style> rawStyles = new ArrayList<>(expectedLength);
        text.visit((style, part) -> {
            if (part != null && !part.isEmpty()) {
                Style safeStyle = style == null ? Style.EMPTY : style;
                for (int i = 0; i < part.length(); i++) rawStyles.add(safeStyle);
            }
            return Optional.empty();
        }, Style.EMPTY);
        List<Style> styles = new ArrayList<>(rawStyles);
        if (styles.isEmpty()) {
            for (int i = 0; i < expectedLength; i++) styles.add(Style.EMPTY);
        } else if (styles.size() < expectedLength) {
            Style fill = styles.get(styles.size() - 1);
            while (styles.size() < expectedLength) styles.add(fill);
        } else if (styles.size() > expectedLength) {
            styles = new ArrayList<>(styles.subList(0, expectedLength));
        }
        return styles;
    }

    private static int longestCommonPrefix(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int index = 0;
        while (index < max && left.charAt(index) == right.charAt(index)) index++;
        return index;
    }

    private static int longestCommonSuffix(String previous, String next, int prefixLength) {
        int previousRemaining = previous.length() - prefixLength;
        int nextRemaining = next.length() - prefixLength;
        int max = Math.min(previousRemaining, nextRemaining);
        int suffix = 0;
        while (suffix < max
                && previous.charAt(previous.length() - 1 - suffix) == next.charAt(next.length() - 1 - suffix)) {
            suffix++;
        }
        return suffix;
    }

    private static Style styleForEditedIndex(List<Style> previousStyles, int previousLength, int nextLength,
                                             int prefixLength, int suffixLength, int nextIndex) {
        if (previousStyles.isEmpty()) return Style.EMPTY;
        if (nextIndex < prefixLength) {
            return previousStyles.get(Math.min(nextIndex, previousStyles.size() - 1));
        }

        int nextSuffixStart = nextLength - suffixLength;
        int previousSuffixStart = previousLength - suffixLength;
        if (suffixLength > 0 && nextIndex >= nextSuffixStart) {
            int mappedIndex = previousSuffixStart + (nextIndex - nextSuffixStart);
            return previousStyles.get(Math.max(0, Math.min(mappedIndex, previousStyles.size() - 1)));
        }

        int anchorIndex;
        if (prefixLength > 0) {
            anchorIndex = prefixLength - 1;
        } else if (suffixLength > 0) {
            anchorIndex = previousSuffixStart;
        } else {
            anchorIndex = 0;
        }
        return previousStyles.get(Math.max(0, Math.min(anchorIndex, previousStyles.size() - 1)));
    }
}
