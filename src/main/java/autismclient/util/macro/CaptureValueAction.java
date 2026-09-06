package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

public class CaptureValueAction implements MacroAction, MacroCaptureOutput {
    private static final Pattern OBVIOUS_DYNAMIC_VALUE = Pattern.compile(
        "(?i)(?<![a-z0-9_])[-+]?\\d[\\d,._]*(?:[.,]\\d+)?\\s*(?:k|m|b|t|q|thousand|million|billion|trillion)?(?![a-z0-9_])");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Comparator<PlayerScoreEntry> SCOREBOARD_DISPLAY_ORDER = Comparator
        .comparing(PlayerScoreEntry::value).reversed()
        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    public enum Source { GUI_TITLE, RECENT_CHAT, SCOREBOARD, GUI_ITEM, PLAYER_ITEM, CURSOR_ITEM, HELD_ITEM,
                         COMMAND_AUTOFILL, TABLIST, NAMESCRAPE }
    public enum ItemText { NAME, ID, LORE }

    public enum NumberMode { OFF, SUFFIX_KMB, DROP_CENTS }
    public enum NumberModifier { NONE, PLUS, MINUS, MULTIPLY, DIVIDE, PLUS_PERCENT, MINUS_PERCENT }

    public Source source = Source.GUI_TITLE;
    public String saveAs = "value";
    public MacroCapturePattern.Mode matchMode = MacroCapturePattern.Mode.MATCH;
    public String pattern = "";
    public String exampleText = "";
    public String selectedText = "";
    public String itemFilter = "";
    public String scoreboardRow = "";
    public int scoreboardRowIndex = -1;
    public String scoreboardObjective = "";
    public int slot = -1;
    public ItemText itemText = ItemText.NAME;
    public NumberMode numberMode = NumberMode.OFF;
    public NumberModifier numberModifier = NumberModifier.NONE;
    public double numberModifierAmount = 1.0;
    public boolean waitForTrigger = defaultWaitForTrigger(Source.GUI_TITLE);
    public String autofillCommand = "";
    public int autofillTimeoutMs = 5000;
    public boolean autofillCacheList = true;
    public CaptureListSelector.Selection listSelection = CaptureListSelector.Selection.RANDOM;
    public CaptureListSelector.Filter listFilter = CaptureListSelector.Filter.NONE;
    public String listFilterText = "";
    public String listExcludeText = "";
    public int listPickPosition = 1;
    public boolean listStripPrefix;
    public boolean excludeSelf = true;
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        run(mc);
    }

    public Preview run(Minecraft mc) {
        if (mc == null) return Preview.unavailable("Game unavailable");
        clearOutputs();
        Preview preview = preview(mc);
        if (preview.success()) MacroVariables.setAll(preview.values());
        return preview;
    }

    public Preview preview(Minecraft mc) {
        if (mc == null) return Preview.unavailable("Game unavailable");
        if (source == Source.RECENT_CHAT) {
            List<MacroExecutor.RecentChatMessage> messages = MacroExecutor.getRecentChatMessages();
            if (messages.isEmpty()) return Preview.unavailable("No recent chat");
            for (MacroExecutor.RecentChatMessage chat : messages) {
                Preview candidate = evaluateCaptured(chatValue(chat));
                if (candidate.success()) return candidate;
            }
            return Preview.unavailable("No recent message matches");
        }
        if (source == Source.GUI_TITLE) {
            Preview current = previewCurrent(mc);
            if (current.success()) return current;
            List<MacroExecutor.RecentGuiTitle> titles = MacroExecutor.getRecentGuiTitles();
            for (MacroExecutor.RecentGuiTitle title : titles) {
                Preview candidate = evaluateCaptured(guiValue(title));
                if (candidate.success()) return candidate;
            }
            return titles.isEmpty() ? current : Preview.unavailable("No recent GUI matches");
        }
        return previewCurrent(mc);
    }

    public Preview previewCurrent(Minecraft mc) {
        if (mc == null) return Preview.unavailable("Game unavailable");
        if (isListSource()) {
            String filterError = CaptureListSelector.filterError(listFilter, listFilterText);
            if (!filterError.isBlank()) return Preview.unavailable(filterError);
            if (source == Source.COMMAND_AUTOFILL || source == Source.NAMESCRAPE) {
                return Preview.unavailable("Fetched when macro runs");
            }
        }
        Captured captured = capture(mc);
        return evaluateCaptured(captured);
    }

    public Preview previewSuggestions(List<String> suggestions, String query) {
        Captured captured = pickFromList(suggestions, Map.of());
        if (captured != null && query != null && !query.isBlank()) {
            Map<String, MacroValue> props = new LinkedHashMap<>(captured.value().properties());
            props.put("query", MacroValue.text(query));
            captured = new Captured(captured.text(),
                MacroValue.structured(MacroValue.Kind.TEXT, captured.value().value(), props));
        }
        return evaluateCaptured(captured);
    }

    private boolean isListSource() {
        return source == Source.COMMAND_AUTOFILL || source == Source.TABLIST || source == Source.NAMESCRAPE;
    }

    public Preview previewChat(MacroExecutor.RecentChatMessage chat) {
        return evaluateCaptured(chatValue(chat));
    }

    public Preview previewScoreboardLine(ScoreboardLine line) {
        return evaluateCaptured(scoreboardValue(line));
    }

    public ScoreboardLine selectedScoreboardLine(Minecraft mc) {
        return resolveScoreboardLine(mc, scoreboardRow, scoreboardRowIndex, scoreboardObjective);
    }

    public ScoreboardLine resolveScoreboardLine(Minecraft mc, String preferredKey, int preferredRow, String preferredObjective) {
        List<ScoreboardLine> lines = scoreboardLines(mc);
        if (lines.isEmpty()) return null;
        String key = preferredKey == null ? "" : preferredKey;
        for (ScoreboardLine line : lines) {
            if (!key.isBlank() && key.equals(line.key())) return line;
        }

        ScoreboardLine rowCandidate = null;
        for (ScoreboardLine line : lines) {
            if (!sameObjective(line, preferredObjective)) continue;
            if (line.row() == preferredRow) {
                rowCandidate = line;
                break;
            }
        }
        if (rowCandidate != null && previewScoreboardLine(rowCandidate).success()) return rowCandidate;

        ScoreboardLine best = bestMatchingScoreboardLine(lines, preferredRow, preferredObjective, true);
        if (best != null) return best;

        return bestMatchingScoreboardLine(lines, preferredRow, preferredObjective, false);
    }

    private ScoreboardLine bestMatchingScoreboardLine(List<ScoreboardLine> lines, int preferredRow,
                                                       String preferredObjective, boolean requireObjective) {
        ScoreboardLine best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ScoreboardLine line : lines) {
            if (requireObjective && !sameObjective(line, preferredObjective)) continue;
            if (!previewScoreboardLine(line).success()) continue;
            int distance = preferredRow < 0 ? line.row() : Math.abs(line.row() - preferredRow);
            if (best == null || distance < bestDistance) {
                best = line;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean sameObjective(ScoreboardLine line, String objective) {
        return line != null && (objective == null || objective.isBlank() || objective.equals(line.objective()));
    }

    public static List<ScoreboardLine> scoreboardLines(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) return List.of();
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = sidebarObjective(scoreboard, mc);
        if (objective == null) return List.of();
        NumberFormat scoreFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
            .filter(entry -> !entry.isHidden())
            .sorted(SCOREBOARD_DISPLAY_ORDER)
            .limit(15L)
            .toList();
        List<ScoreboardLine> lines = new java.util.ArrayList<>(entries.size());
        String objectiveTitle = objective.getDisplayName() == null ? "" : objective.getDisplayName().getString();
        for (int i = 0; i < entries.size(); i++) {
            PlayerScoreEntry entry = entries.get(i);
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component nameComponent = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            Component scoreComponent = entry.formatValue(scoreFormat);
            String name = nameComponent == null ? "" : nameComponent.getString();
            String score = scoreComponent == null ? "" : scoreComponent.getString();
            String text = score.isEmpty() ? name : name + ": " + score;
            String key = objective.getName() + "\u001F" + entry.owner();
            lines.add(new ScoreboardLine(key, i, objective.getName(), objectiveTitle,
                entry.owner(), name, score, text));
        }
        return List.copyOf(lines);
    }

    private static Objective sidebarObjective(Scoreboard scoreboard, Minecraft mc) {
        if (scoreboard == null || mc == null || mc.player == null) return null;
        Objective teamObjective = null;
        PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (team != null) {
            Optional<TeamColor> color = team.getColor();
            if (color.isPresent()) teamObjective = scoreboard.getDisplayObjective(color.get().displaySlot());
        }
        return teamObjective != null ? teamObjective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    public Preview previewExample() {
        if (exampleText == null || exampleText.isBlank()) return Preview.unavailable("Enter an example message");
        return evaluateCaptured(new Captured(exampleText, MacroValue.text(exampleText)));
    }

    private Preview evaluateCaptured(Captured captured) {
        if (captured == null || captured.value == null) return Preview.unavailable("Nothing available now");
        Map<String, MacroValue> additions = new LinkedHashMap<>();

        if (isListSource()) {
            additions = new LinkedHashMap<>(combineCapturedOutputs(additions, saveAs, captured.value));
            if (additions.isEmpty()) return Preview.unavailable("Enter a variable name");
            return new Preview(true, captured.text, additions, "Ready");
        }
        if (matchMode != MacroCapturePattern.Mode.MATCH && pattern != null && !pattern.isBlank()) {
            Optional<MacroCapturePattern.Result> result = MacroCapturePattern.match(matchMode, pattern, captured.text);
            if (result.isEmpty()) return Preview.unavailable("Pattern does not match");
            additions.putAll(result.get().values());
        } else if (pattern != null && !pattern.isBlank()
                && !captured.text.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT))) {
            return Preview.unavailable("Text does not match");
        }
        additions = new LinkedHashMap<>(combineCapturedOutputs(additions, saveAs, captured.value));
        if (mode() != NumberMode.OFF) {
            additions = new LinkedHashMap<>(normalizeCapturedOutputs(additions, saveAs, mode()));
        }
        NumberModification modification = modifyCapturedOutputs(additions);
        if (!modification.valid()) return Preview.unavailable(modification.message());
        additions = new LinkedHashMap<>(modification.values());
        if (additions.isEmpty()) return Preview.unavailable("Enter a variable name");
        return new Preview(true, captured.text, additions, "Ready");
    }

    public static boolean defaultWaitForTrigger(Source source) {
        return source == Source.RECENT_CHAT;
    }

    public String numberModifierError() {
        if (isListSource()) return "";
        NumberModifier modifier = numberModifier == null ? NumberModifier.NONE : numberModifier;
        if (modifier == NumberModifier.NONE) return "";
        if (!Double.isFinite(numberModifierAmount)) return "Enter a valid modifier";
        if (modifier == NumberModifier.DIVIDE && numberModifierAmount == 0.0) return "Cannot divide by zero";
        return "";
    }

    private NumberMode mode() {
        return numberMode == null ? NumberMode.OFF : numberMode;
    }

    private MacroTemplate.NumberStyle style() {
        return mode() == NumberMode.DROP_CENTS ? MacroTemplate.NumberStyle.WHOLE : MacroTemplate.NumberStyle.AUTO;
    }

    private NumberModification modifyCapturedOutputs(Map<String, MacroValue> values) {
        String error = numberModifierError();
        if (!error.isBlank()) return NumberModification.invalid(error);
        NumberModifier modifier = numberModifier == null ? NumberModifier.NONE : numberModifier;
        if (modifier == NumberModifier.NONE || values == null || values.isEmpty()) {
            return NumberModification.valid(values == null ? Map.of() : values);
        }

        BigDecimal operand = BigDecimal.valueOf(numberModifierAmount);

        String outputName = MacroVariableContext.cleanRootName(saveAs);
        boolean targetTracked = !outputName.isBlank() && values.containsKey(outputName);
        Map<String, MacroValue> modified = new LinkedHashMap<>();
        boolean ok = false;
        for (Map.Entry<String, MacroValue> entry : values.entrySet()) {
            boolean target = targetTracked && entry.getKey().equals(outputName);
            MacroValue original = entry.getValue();
            MacroValue result = modifyCapturedNumber(original, modifier, operand, target, style());
            modified.put(entry.getKey(), result);

            if (result != original && (target || !targetTracked)) ok = true;
        }
        return ok
            ? NumberModification.valid(modified)
            : NumberModification.invalid("Not a number");
    }

    private static MacroValue modifyCapturedNumber(MacroValue value, NumberModifier modifier,
                                                    BigDecimal operand, boolean embedded,
                                                    MacroTemplate.NumberStyle style) {
        BigDecimal number = capturedNumber(value, embedded, style);
        if (number == null) return value;
        try {
            BigDecimal result = switch (modifier) {
                case PLUS -> number.add(operand);
                case MINUS -> number.subtract(operand);
                case MULTIPLY -> number.multiply(operand);
                case DIVIDE -> divideForDisplay(number, operand);

                case PLUS_PERCENT -> percentOfForDisplay(number, BigDecimal.ONE.add(percentFactor(operand)));
                case MINUS_PERCENT -> percentOfForDisplay(number, BigDecimal.ONE.subtract(percentFactor(operand)));
                case NONE -> number;
            };
            return rebuildNumber(value, result);
        } catch (ArithmeticException ignored) {
            return value;
        }
    }

    private static BigDecimal divideForDisplay(BigDecimal number, BigDecimal operand) {
        BigDecimal exact = number.divide(operand,
            new MathContext(Math.max(16, number.precision() + 8), RoundingMode.HALF_UP));
        return roundForDisplay(exact, number);
    }

    public static BigDecimal percentFactor(BigDecimal percent) {
        return percent.divide(ONE_HUNDRED, new MathContext(34, RoundingMode.HALF_UP));
    }

    private static BigDecimal percentOfForDisplay(BigDecimal number, BigDecimal factor) {
        return roundForDisplay(number.multiply(factor), number);
    }

    private static BigDecimal roundForDisplay(BigDecimal result, BigDecimal source) {
        int scale = Math.max(2, source.scale() + 2);
        int adjustedExponent = result.precision() - result.scale() - 1;
        if (result.signum() != 0 && adjustedExponent < 0) scale = Math.max(scale, 1 - adjustedExponent);
        return result.setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal capturedNumber(MacroValue value, boolean embedded,
                                             MacroTemplate.NumberStyle style) {
        if (value == null || value.kind() == MacroValue.Kind.SLOT
                || value.kind() == MacroValue.Kind.IDENTIFIER) {
            return null;
        }
        try {
            return new BigDecimal(embedded
                ? MacroTemplate.parseCaptureNumber(value.value(), style)
                : MacroTemplate.parseCaptureNumberStrict(value.value(), style));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static Map<String, MacroValue> combineCapturedOutputs(Map<String, MacroValue> patternValues,
                                                           String saveAs, MacroValue sourceValue) {
        Map<String, MacroValue> outputs = new LinkedHashMap<>();
        if (patternValues != null) outputs.putAll(patternValues);
        String outputName = MacroVariableContext.cleanRootName(saveAs);

        if (!outputName.isBlank() && sourceValue != null) outputs.putIfAbsent(outputName, sourceValue);
        return outputs;
    }

    static Map<String, MacroValue> normalizeCapturedOutputs(Map<String, MacroValue> values) {
        return normalizeCapturedOutputs(values, "", NumberMode.SUFFIX_KMB);
    }

    static Map<String, MacroValue> normalizeCapturedOutputs(Map<String, MacroValue> values, String saveAs) {
        return normalizeCapturedOutputs(values, saveAs, NumberMode.SUFFIX_KMB);
    }

    static Map<String, MacroValue> normalizeCapturedOutputs(Map<String, MacroValue> values, String saveAs,
                                                            NumberMode mode) {
        if (values == null || values.isEmpty()) return Map.of();
        MacroTemplate.NumberStyle style = mode == NumberMode.DROP_CENTS
            ? MacroTemplate.NumberStyle.WHOLE : MacroTemplate.NumberStyle.AUTO;
        String outputName = MacroVariableContext.cleanRootName(saveAs);
        Map<String, MacroValue> normalized = new LinkedHashMap<>();
        values.forEach((name, value) -> normalized.put(name,
            normalizeCapturedNumber(value, !outputName.isBlank() && outputName.equals(name), style)));
        return normalized;
    }

    private static MacroValue normalizeCapturedNumber(MacroValue value, boolean embedded,
                                                      MacroTemplate.NumberStyle style) {
        BigDecimal number = capturedNumber(value, embedded, style);
        return number == null ? value : rebuildNumber(value, number);
    }

    private static MacroValue rebuildNumber(MacroValue source, BigDecimal number) {
        return source.properties().isEmpty()
            ? MacroValue.number(number)
            : MacroValue.structured(source.kind(), MacroTemplate.renderNumber(number), source.properties());
    }

    private Captured capture(Minecraft mc) {
        return switch (source == null ? Source.GUI_TITLE : source) {
            case GUI_TITLE -> {
                var screen = mc.gui.screen();
                if (screen != null && !MacroGuiMatcher.isOwnScreen(screen)) {
                    String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
                    yield new Captured(title, MacroValue.structured(MacroValue.Kind.GUI, title, Map.of(
                        "title", MacroValue.text(title), "type", MacroValue.text(MacroGuiMatcher.semanticName(screen)))));
                }
                autismclient.api.custommenu.CustomMenuSnapshot menu =
                    autismclient.util.custommenu.CustomMenuTracker.current();
                if (menu == null) yield null;
                String title = menu.title();
                yield new Captured(title, MacroValue.structured(MacroValue.Kind.GUI, title, Map.of(
                    "title", MacroValue.text(title), "type", MacroValue.text("CUSTOM_MENU"))));
            }
            case RECENT_CHAT -> {
                List<MacroExecutor.RecentChatMessage> messages = MacroExecutor.getRecentChatMessages();
                if (messages.isEmpty()) yield null;
                yield chatValue(messages.get(0));
            }
            case SCOREBOARD -> scoreboardValue(selectedScoreboardLine(mc));
            case GUI_ITEM -> captureMenuItem(mc, false);
            case PLAYER_ITEM -> capturePlayerItem(mc);
            case CURSOR_ITEM -> mc.player == null || mc.player.containerMenu == null
                ? null : itemValue(mc.player.containerMenu.getCarried(), -1);
            case HELD_ITEM -> mc.player == null
                ? null : itemValue(mc.player.getMainHandItem(), mc.player.getInventory().getSelectedSlot());
            case TABLIST -> tablistValue(mc);
            case COMMAND_AUTOFILL, NAMESCRAPE -> null;
        };
    }

    private Captured tablistValue(Minecraft mc) {
        List<autismclient.util.AutismPlayerScanner.ScannedPlayer> players = autismclient.util.AutismPlayerScanner.scan(mc);
        List<String> names = new java.util.ArrayList<>(players.size() + 1);
        Map<String, String> prefixes = new java.util.HashMap<>();
        for (autismclient.util.AutismPlayerScanner.ScannedPlayer player : players) {
            names.add(player.name());
            if (player.hasPrefix()) prefixes.put(player.name(), player.prefix());
        }

        if (!excludeSelf && mc.player != null && mc.player.getGameProfile() != null) {
            names.add(mc.player.getGameProfile().name());
            names.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return pickFromList(names, prefixes);
    }

    public List<String> filterCandidates(List<String> candidates) {
        if (candidates == null) return List.of();
        List<String> pool = CaptureListSelector.filter(candidates, listFilter, listFilterText);
        return CaptureListSelector.exclude(pool, listExcludeText);
    }

    private Captured pickFromList(List<String> candidates, Map<String, String> prefixByValue) {
        if (candidates == null) return null;
        List<String> pool = filterCandidates(candidates);
        CaptureListSelector.State state = MacroExecutor.captureSelectionState(this);
        Optional<CaptureListSelector.Pick> pick = CaptureListSelector.pick(pool, listSelection, listPickPosition, state);
        if (pick.isEmpty()) return null;
        CaptureListSelector.Pick picked = pick.get();
        String value = listStripPrefix
            ? CaptureListSelector.stripMatch(picked.value(), listFilter, listFilterText)
            : picked.value();
        Map<String, MacroValue> props = new LinkedHashMap<>();
        props.put("index", MacroValue.number(picked.index() + 1));
        props.put("count", MacroValue.number(pool.size()));
        props.put("total", MacroValue.number(candidates.size()));
        props.put("list", MacroValue.text(String.join(", ", pool)));
        String prefix = prefixByValue == null ? null : prefixByValue.get(picked.value());
        if (prefix != null && !prefix.isBlank()) props.put("prefix", MacroValue.text(prefix));
        return new Captured(value, MacroValue.structured(MacroValue.Kind.TEXT, value, props));
    }

    private Captured chatValue(MacroExecutor.RecentChatMessage chat) {
        if (chat == null) return null;
        String display = chat.displayText() == null ? "" : chat.displayText();
        Map<String, MacroValue> props = new LinkedHashMap<>();
        props.put("sender", MacroValue.text(chat.sender()));
        props.put("message", MacroValue.text(chat.message()));
        props.put("display", MacroValue.text(display));
        props.put("source", MacroValue.text(chat.source() == null ? "" : chat.source().name()));
        return new Captured(display, MacroValue.structured(MacroValue.Kind.TEXT, display, props));
    }

    private Captured guiValue(MacroExecutor.RecentGuiTitle gui) {
        if (gui == null) return null;
        String title = gui.title() == null ? "" : gui.title();
        return new Captured(title, MacroValue.structured(MacroValue.Kind.GUI, title, Map.of(
            "title", MacroValue.text(title),
            "type", MacroValue.text(gui.type() == null ? "" : gui.type())
        )));
    }

    private Captured scoreboardValue(ScoreboardLine line) {
        if (line == null) return null;
        Map<String, MacroValue> props = new LinkedHashMap<>();
        props.put("line", MacroValue.text(line.text()));
        props.put("name", MacroValue.text(line.name()));
        props.put("score", MacroValue.text(line.score()));
        props.put("row", MacroValue.number(BigDecimal.valueOf(line.row() + 1L)));
        props.put("title", MacroValue.text(line.objectiveTitle()));
        props.put("objective", MacroValue.text(line.objective()));
        return new Captured(line.text(), MacroValue.structured(MacroValue.Kind.TEXT, line.text(), props));
    }

    private Captured captureMenuItem(Minecraft mc, boolean playerOnly) {
        if (mc.player == null || mc.player.containerMenu == null) return null;
        FilterResolution filterResolution = resolveFilter(mc);
        if (!filterResolution.valid()) return null;
        ItemTarget filter = filterResolution.target();
        for (Slot candidate : mc.player.containerMenu.slots) {
            if (candidate == null || candidate.getItem().isEmpty()) continue;
            boolean playerSlot = AutismInventoryHelper.isInventorySlot(mc, candidate);
            if (playerOnly != playerSlot) continue;
            int visible = AutismInventoryHelper.toUserVisibleSlot(mc, candidate.index);
            if (slot >= 0 && slot != visible && slot != candidate.index) continue;
            if (filter != null && filter.hasIdentity() && filter.score(candidate.getItem(), visible) < 0) continue;
            return itemValue(candidate.getItem(), visible);
        }
        return null;
    }

    private Captured capturePlayerItem(Minecraft mc) {
        Captured menu = captureMenuItem(mc, true);
        if (menu != null || mc.player == null) return menu;
        FilterResolution filterResolution = resolveFilter(mc);
        if (!filterResolution.valid()) return null;
        ItemTarget filter = filterResolution.target();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (slot >= 0 && slot != i) continue;
            if (filter != null && filter.hasIdentity() && filter.score(stack, i) < 0) continue;
            return itemValue(stack, i);
        }
        return null;
    }

    private FilterResolution resolveFilter(Minecraft mc) {
        if (itemFilter == null || itemFilter.isBlank()) return new FilterResolution(true, null);
        MacroTemplate.Resolution resolution = MacroVariables.resolve(itemFilter, mc);
        return resolution.success()
            ? new FilterResolution(true, ItemTarget.fromLegacyEntry(resolution.value()))
            : new FilterResolution(false, null);
    }

    private Captured itemValue(ItemStack stack, int visibleSlot) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = id == null ? "" : id.toString();
        String name = stack.getHoverName().getString();
        ItemLore lore = stack.get(DataComponents.LORE);
        String loreText = lore == null ? "" : lore.lines().stream().map(line -> line.getString()).reduce((a, b) -> a + "\n" + b).orElse("");
        Map<String, MacroValue> props = new LinkedHashMap<>();
        props.put("name", MacroValue.text(name));
        props.put("id", MacroValue.identifier(itemId));
        props.put("count", MacroValue.number(stack.getCount()));
        props.put("slot", MacroValue.slot(visibleSlot));
        props.put("lore", MacroValue.text(loreText));
        if (lore != null) {
            for (int i = 0; i < lore.lines().size(); i++) props.put("lore" + (i + 1), MacroValue.text(lore.lines().get(i).getString()));
        }
        String text = switch (itemText == null ? ItemText.NAME : itemText) {
            case NAME -> name;
            case ID -> itemId;
            case LORE -> loreText;
        };

        return new Captured(text, MacroValue.structured(MacroValue.Kind.ITEM, text, props));
    }

    private void clearOutputs() {
        if (saveAs != null && !saveAs.isBlank()) MacroVariables.remove(saveAs);
        for (String name : MacroCapturePattern.declaredNames(matchMode, pattern)) MacroVariables.remove(name);
    }

    public static String suggestDynamicPart(String example) {
        if (example == null || example.isBlank()) return "";
        Matcher matcher = OBVIOUS_DYNAMIC_VALUE.matcher(example);
        return matcher.find() ? matcher.group().trim() : "";
    }

    public static String buildCapturePattern(String example, String selected, String variableName) {
        if (example == null || selected == null || selected.isBlank()) return "";
        String root = MacroVariableContext.cleanRootName(variableName);

        if (root.isBlank()) {
            if (variableName != null && !variableName.isBlank()) return "";
            root = "value";
        }
        int index = example.indexOf(selected);
        if (index < 0) index = example.toLowerCase(Locale.ROOT).indexOf(selected.toLowerCase(Locale.ROOT));
        if (index < 0) return "";
        String before = escapeCaptureLiteral(example.substring(0, index));
        String after = escapeCaptureLiteral(example.substring(index + selected.length()));
        return before + "{" + root + "}" + after;
    }

    private static String escapeCaptureLiteral(String value) {
        return value == null ? "" : value.replace("{", "{{").replace("}", "}}");
    }

    @Override public MacroActionType getType() { return MacroActionType.CAPTURE_VALUE; }
    @Override public String getDisplayName() { return "Capture " + (saveAs == null || saveAs.isBlank() ? "Value" : saveAs); }
    @Override public String getIcon() { return "{}"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("source", (source == null ? Source.GUI_TITLE : source).name());
        tag.putString("saveAs", saveAs == null ? "" : saveAs);
        tag.putString("matchMode", (matchMode == null ? MacroCapturePattern.Mode.MATCH : matchMode).name());
        tag.putString("pattern", pattern == null ? "" : pattern);
        tag.putString("exampleText", exampleText == null ? "" : exampleText);
        tag.putString("selectedText", selectedText == null ? "" : selectedText);
        tag.putString("itemFilter", itemFilter == null ? "" : itemFilter);
        tag.putString("scoreboardRow", scoreboardRow == null ? "" : scoreboardRow);
        tag.putInt("scoreboardRowIndex", scoreboardRowIndex);
        tag.putString("scoreboardObjective", scoreboardObjective == null ? "" : scoreboardObjective);
        tag.putInt("slot", slot);
        tag.putString("itemText", (itemText == null ? ItemText.NAME : itemText).name());
        tag.putString("numberMode", mode().name());
        tag.putString("numberModifier", (numberModifier == null ? NumberModifier.NONE : numberModifier).name());
        tag.putDouble("numberModifierAmount", numberModifierAmount);
        tag.putBoolean("waitForTrigger", waitForTrigger);
        tag.putString("autofillCommand", autofillCommand == null ? "" : autofillCommand);
        tag.putInt("autofillTimeoutMs", autofillTimeoutMs);
        tag.putBoolean("autofillCacheList", autofillCacheList);
        tag.putString("listSelection", (listSelection == null ? CaptureListSelector.Selection.RANDOM : listSelection).name());
        tag.putString("listFilter", (listFilter == null ? CaptureListSelector.Filter.NONE : listFilter).name());
        tag.putString("listFilterText", listFilterText == null ? "" : listFilterText);
        tag.putString("listExcludeText", listExcludeText == null ? "" : listExcludeText);
        tag.putInt("listPickPosition", listPickPosition);
        tag.putBoolean("listStripPrefix", listStripPrefix);
        tag.putBoolean("excludeSelf", excludeSelf);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        source = MacroStringList.enumValue(Source.class, tag.getStringOr("source", "GUI_TITLE"), Source.GUI_TITLE);
        saveAs = tag.getStringOr("saveAs", "value");
        matchMode = MacroStringList.enumValue(MacroCapturePattern.Mode.class, tag.getStringOr("matchMode", "MATCH"), MacroCapturePattern.Mode.MATCH);
        pattern = tag.getStringOr("pattern", "");
        exampleText = tag.getStringOr("exampleText", "");
        selectedText = tag.getStringOr("selectedText", "");
        itemFilter = tag.getStringOr("itemFilter", "");
        scoreboardRow = tag.getStringOr("scoreboardRow", "");
        scoreboardRowIndex = tag.getIntOr("scoreboardRowIndex", -1);
        scoreboardObjective = tag.getStringOr("scoreboardObjective", "");
        slot = tag.getIntOr("slot", -1);
        itemText = MacroStringList.enumValue(ItemText.class, tag.getStringOr("itemText", "NAME"), ItemText.NAME);

        numberMode = tag.contains("numberMode")
            ? MacroStringList.enumValue(NumberMode.class, tag.getStringOr("numberMode", "OFF"), NumberMode.OFF)
            : tag.getBooleanOr("normalizeNumbers", false) ? NumberMode.SUFFIX_KMB : NumberMode.OFF;
        numberModifier = MacroStringList.enumValue(NumberModifier.class,
            tag.getStringOr("numberModifier", "NONE"), NumberModifier.NONE);
        numberModifierAmount = tag.getDoubleOr("numberModifierAmount", 1.0);
        waitForTrigger = tag.getBooleanOr("waitForTrigger", defaultWaitForTrigger(source));
        autofillCommand = tag.getStringOr("autofillCommand", "");
        autofillTimeoutMs = tag.getIntOr("autofillTimeoutMs", 5000);
        autofillCacheList = tag.getBooleanOr("autofillCacheList", true);
        listSelection = MacroStringList.enumValue(CaptureListSelector.Selection.class,
            tag.getStringOr("listSelection", "RANDOM"), CaptureListSelector.Selection.RANDOM);
        listFilter = MacroStringList.enumValue(CaptureListSelector.Filter.class,
            tag.getStringOr("listFilter", "NONE"), CaptureListSelector.Filter.NONE);
        listFilterText = tag.getStringOr("listFilterText", "");
        listExcludeText = tag.getStringOr("listExcludeText", "");
        listPickPosition = tag.getIntOr("listPickPosition", 1);
        listStripPrefix = tag.getBooleanOr("listStripPrefix", false);
        excludeSelf = tag.getBooleanOr("excludeSelf", true);
        enabled = tag.getBooleanOr("enabled", true);
    }

    private record Captured(String text, MacroValue value) {}
    private record NumberModification(boolean valid, Map<String, MacroValue> values, String message) {
        private static NumberModification valid(Map<String, MacroValue> values) {
            return new NumberModification(true, values == null ? Map.of() : values, "");
        }

        private static NumberModification invalid(String message) {
            return new NumberModification(false, Map.of(), message == null ? "Invalid number modifier" : message);
        }
    }
    public record ScoreboardLine(String key, int row, String objective, String objectiveTitle,
                                 String owner, String name, String score, String text) {}
    private record FilterResolution(boolean valid, ItemTarget target) {}

    public record Preview(boolean success, String sourceText, Map<String, MacroValue> values, String message) {
        public Preview {
            sourceText = sourceText == null ? "" : sourceText;
            values = values == null ? Map.of() : Map.copyOf(values);
            message = message == null ? "" : message;
        }

        public static Preview unavailable(String message) {
            return new Preview(false, "", Map.of(), message);
        }

        public String value(String variableName) {
            String clean = MacroVariableContext.cleanRootName(variableName);
            MacroValue value = clean.isBlank() ? null : values.get(clean);
            return value == null ? "" : value.value();
        }
    }
}
