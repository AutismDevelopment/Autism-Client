package autismclient.util;

import autismclient.api.custommenu.CustomMenuAdapterRegistry;
import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.api.custommenu.CustomMenuSubmitResult;
import autismclient.util.custommenu.CustomMenuTracker;
import autismclient.util.macro.CustomMenuAction;
import autismclient.util.macro.CustomMenuActionSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.protocol.Packet;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AutismJoinMacroController {
    public enum Timing {
        JOINING("JOINING", "Joining"),
        WORLD("WORLD", "In World"),
        FIRST_TICK("FIRST_TICK", "First Tick"),
        INVENTORY_READY("INVENTORY_READY", "Inventory Ready"),
        PLAYABLE("PLAYABLE", "Playable"),
        FULLY_READY("FULLY_READY", "Fully Ready");

        private final String id;
        private final String label;

        Timing(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public Timing other() {
            Timing[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static Timing fromConfig(String value) {
            if ("JOINING".equalsIgnoreCase(value)) return JOINING;
            if ("FIRST_TICK".equalsIgnoreCase(value) || "FIRSTTICK".equalsIgnoreCase(value)) return FIRST_TICK;
            if ("INVENTORY_READY".equalsIgnoreCase(value) || "INVENTORY".equalsIgnoreCase(value)) return INVENTORY_READY;
            if ("PLAYABLE".equalsIgnoreCase(value)) return PLAYABLE;
            if ("FULLY_READY".equalsIgnoreCase(value) || "FULLYREADY".equalsIgnoreCase(value)) return FULLY_READY;
            return WORLD;
        }
    }

    public enum TriggerJoin {
        ANY("ANY", "Every Join", 0),
        FIRST("FIRST", "1st Join", 1),
        SECOND("SECOND", "2nd Join", 2),
        THIRD("THIRD", "3rd Join", 3),
        FOURTH("FOURTH", "4th Join", 4),
        FIFTH("FIFTH", "5th Join", 5),
        SIXTH_PLUS("SIXTH_PLUS", "6th+ Join", 6);

        private final String id;
        private final String label;
        private final int number;

        TriggerJoin(String id, String label, int number) {
            this.id = id;
            this.label = label;
            this.number = number;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public String displayLabel(boolean keepEnabled) {
            if (keepEnabled) {
                return switch (this) {
                    case ANY, FIRST -> "Every Join";
                    case SECOND -> "Every 2nd Join";
                    case THIRD -> "Every 3rd Join";
                    case FOURTH -> "Every 4th Join";
                    case FIFTH -> "Every 5th Join";
                    case SIXTH_PLUS -> "Every 6th Join";
                };
            }

            return switch (this) {
                case ANY, FIRST -> "Next Join";
                case SECOND -> "After 1 Transfer";
                case THIRD -> "After 2 Transfers";
                case FOURTH -> "After 3 Transfers";
                case FIFTH -> "After 4 Transfers";
                case SIXTH_PLUS -> "After 5+ Transfers";
            };
        }

        public boolean matches(int joinOrdinal) {
            if (this == ANY) return true;
            if (this == SIXTH_PLUS) return joinOrdinal >= number;
            return joinOrdinal == number;
        }

        public static TriggerJoin fromConfig(String value) {
            if (value != null) {
                for (TriggerJoin target : values()) {
                    if (target.id.equalsIgnoreCase(value)) return target;
                }
                try {
                    int ordinal = Integer.parseInt(value.trim());
                    return switch (ordinal) {
                        case 0 -> ANY;
                        case 2 -> SECOND;
                        case 3 -> THIRD;
                        case 4 -> FOURTH;
                        case 5 -> FIFTH;
                        default -> ordinal >= 6 ? SIXTH_PLUS : FIRST;
                    };
                } catch (NumberFormatException ignored) {  }
            }
            return FIRST;
        }
    }

    private static boolean executedThisConnection;
    private static boolean playJoinSeen;
    private static boolean worldReadySeen;
    private static int worldTicksSeen;
    private static int playableTicksSeen;
    private static int joinOrdinal;
    private static volatile AutismMacro armedRemainder;
    private static volatile boolean preJoinFailed;
    private static volatile net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl configurationListener;
    private static volatile Thread preJoinThread;

    private AutismJoinMacroController() {
    }

    public static Timing timing() {
        return Timing.fromConfig(AutismConfig.getGlobal().joinMacroTiming);
    }

    public static void setTiming(Timing timing) {
        AutismConfig config = AutismConfig.getGlobal();
        config.joinMacroTiming = (timing == null ? Timing.WORLD : timing).id();
        resetSequence();
        config.save();
    }

    public static void setSelectedMacro(String macroName) {
        AutismConfig config = AutismConfig.getGlobal();
        config.joinMacroName = macroName == null ? "" : macroName.trim();
        config.joinMacroEnabled = !config.joinMacroName.isBlank();
        resetSequence();
        config.save();
    }

    public static void setEnabled(boolean enabled) {
        AutismConfig config = AutismConfig.getGlobal();
        config.joinMacroEnabled = enabled && config.joinMacroName != null && !config.joinMacroName.isBlank();
        resetSequence();
        config.save();
    }

    public static TriggerJoin triggerJoin() {
        return normalizeTriggerJoin(TriggerJoin.fromConfig(AutismConfig.getGlobal().joinMacroTriggerJoin), keepEnabled());
    }

    public static void setTriggerJoin(TriggerJoin triggerJoin) {
        AutismConfig config = AutismConfig.getGlobal();
        config.joinMacroTriggerJoin = normalizeTriggerJoin(triggerJoin == null ? TriggerJoin.FIRST : triggerJoin, config.joinMacroKeepEnabled).id();
        resetSequence();
        config.save();
    }

    public static boolean keepEnabled() {
        return AutismConfig.getGlobal().joinMacroKeepEnabled;
    }

    public static void setKeepEnabled(boolean keepEnabled) {
        AutismConfig config = AutismConfig.getGlobal();
        config.joinMacroKeepEnabled = keepEnabled;
        config.joinMacroTriggerJoin = normalizeTriggerJoin(TriggerJoin.fromConfig(config.joinMacroTriggerJoin), keepEnabled).id();
        resetSequence();
        config.save();
    }

    public static String selectedMacroName() {
        String name = AutismConfig.getGlobal().joinMacroName;
        return name == null ? "" : name.trim();
    }

    public static boolean enabled() {
        return selectedMacroName().length() > 0;
    }

    public static void resetForJoin() {
        playJoinSeen = false;
        worldReadySeen = false;
        worldTicksSeen = 0;
        playableTicksSeen = 0;
    }

    public static void onPlayJoin() {
        resetForJoin();
        executedThisConnection = false;
        playJoinSeen = true;
        if (timing() == Timing.JOINING) {
            executeOnce(Timing.JOINING);
        }
    }

    public static void onWorldReady() {
        worldReadySeen = true;
        if (timing() == Timing.WORLD) {
            executeOnce(Timing.WORLD);
        }
    }

    public static void onClientTick(Minecraft mc) {
        if (!enabled() || executedThisConnection) return;
        if (mc == null) return;

        boolean worldReady = isWorldReady(mc);
        if (!worldReady) {
            worldReadySeen = false;
            worldTicksSeen = 0;
            playableTicksSeen = 0;
            return;
        }

        if (!worldReadySeen) {
            worldReadySeen = true;
            worldTicksSeen = 0;
        }
        worldTicksSeen++;

        Timing timing = timing();
        switch (timing) {
            case JOINING -> {
                if (playJoinSeen) executeOnce(Timing.JOINING);
            }
            case WORLD -> executeOnce(Timing.WORLD);
            case FIRST_TICK -> {
                if (worldTicksSeen >= 1) executeOnce(Timing.FIRST_TICK);
            }
            case INVENTORY_READY -> {
                if (mc.player != null && mc.player.inventoryMenu != null && mc.player.getInventory() != null) {
                    executeOnce(Timing.INVENTORY_READY);
                }
            }
            case PLAYABLE -> {
                if (isPlayable(mc)) executeOnce(Timing.PLAYABLE);
            }
            case FULLY_READY -> {
                if (isPlayable(mc)) {
                    playableTicksSeen++;
                    if (playableTicksSeen >= 2) executeOnce(Timing.FULLY_READY);
                } else {
                    playableTicksSeen = 0;
                }
            }
        }
    }

    public static void onGameLeft() {
        resetForJoin();
        executedThisConnection = false;
        armedRemainder = null;
        preJoinFailed = false;
        CustomMenuTracker.clear();
        configurationListener = null;
        Thread worker = preJoinThread;
        preJoinThread = null;
        if (worker != null) worker.interrupt();
    }

    public static void onConfigurationInit(net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl listener) {
        resetForJoin();
        executedThisConnection = false;
        preJoinFailed = false;
        armedRemainder = null;
        CustomMenuTracker.clear();
        configurationListener = listener;
        joinOrdinal++;
        if (!enabled() || !triggerJoin().matches(joinOrdinal)) return;
        AutismMacro selected = AutismMacroManager.get().get(selectedMacroName());
        if (selected == null) return;
        AutismMacro copy = selected.deepCopy();
        int prefix = 0;
        boolean sawCustomMenu = false;
        while (prefix < copy.actions.size()) {
            autismclient.util.macro.MacroAction action = copy.actions.get(prefix);
            if (action instanceof CustomMenuAction) {
                sawCustomMenu = true;
            } else if (!isPreJoinWait(action)) {
                break;
            }
            prefix++;
        }
        armedRemainder = copy.deepCopy();
        if (prefix > 0) armedRemainder.actions.subList(0, prefix).clear();

        if (prefix == 0 || !sawCustomMenu) return;
        java.util.List<autismclient.util.macro.MacroAction> leading =
            java.util.List.copyOf(copy.actions.subList(0, prefix));
        Thread worker = new Thread(() -> runPreJoinActions(leading), "AUTISM-Join-CustomMenu");
        worker.setDaemon(true);
        preJoinThread = worker;
        worker.start();
    }

    private static boolean isPreJoinWait(autismclient.util.macro.MacroAction action) {
        return action instanceof autismclient.util.macro.WaitForGuiAction
            || action instanceof autismclient.util.macro.WaitGuiTypeAction
            || action instanceof autismclient.util.macro.DelayAction;
    }

    public static boolean sendCommonPacket(Packet<?> packet) {
        if (packet == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getConnection() != null) { mc.getConnection().send(packet); return true; }
        net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl listener = configurationListener;
        if (listener != null) { listener.send(packet); return true; }
        return false;
    }

    public static void onConfigurationDisconnect() {
        onGameLeft();
    }

    private static void runPreJoinActions(java.util.List<autismclient.util.macro.MacroAction> actions) {
        for (autismclient.util.macro.MacroAction macroAction : actions) {
            if (macroAction == null || !macroAction.isEnabled()) continue;
            if (isPreJoinWait(macroAction)) {

                waitPreJoin(macroAction);
                if (preJoinFailed || Thread.currentThread().isInterrupted()) return;
                continue;
            }
            if (!(macroAction instanceof CustomMenuAction action)) continue;
            long deadline = System.currentTimeMillis() + action.boundedTimeout();
            CustomMenuSnapshot snapshot = null;
            while (System.currentTimeMillis() <= deadline && !Thread.currentThread().isInterrupted()) {
                CustomMenuSnapshot candidate = CustomMenuTracker.current();
                if (candidate != null) { snapshot = candidate; break; }
                java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
            }
            if (snapshot == null) { failPreJoin("Custom screen never appeared (timed out)"); return; }
            CustomMenuActionSupport.Prepared prepared = null;

            while (System.currentTimeMillis() <= deadline && !Thread.currentThread().isInterrupted()) {
                snapshot = CustomMenuTracker.current();
                if (snapshot == null) break;
                prepared = CustomMenuActionSupport.prepare(action, snapshot, AutismJoinMacroController::resolveFormTemplate);
                if (prepared.success() || prepared.error() == null || !prepared.error().contains("unavailable")) break;
                java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
            }
            if (snapshot == null) { failPreJoin("Custom screen closed before it could be filled"); return; }
            if (prepared == null || !prepared.success()) { failPreJoin(prepared == null ? "Custom screen failed" : prepared.error()); return; }
            CustomMenuSubmitResult result = CustomMenuAdapterRegistry.submit(snapshot, prepared.submission());
            if (!result.success()) { failPreJoin(result.error()); return; }
            for (Packet<?> packet : result.packets()) {
                if (!sendCommonPacket(packet)) { failPreJoin("Connection closed"); return; }
            }
            CustomMenuTracker.consume(snapshot, result.replacement());
            Minecraft mc = Minecraft.getInstance();
            if (result.clientAction() != null && mc != null) {
                mc.execute(() -> {
                    if (mc.gui.screen() instanceof DialogScreen<?> dialog) {
                        dialog.runAction(Optional.of(result.clientAction()));
                    }
                });
            }
        }
    }

    private static void waitPreJoin(autismclient.util.macro.MacroAction action) {
        long timeoutMs;
        if (action instanceof autismclient.util.macro.WaitForGuiAction w) {
            timeoutMs = w.timeoutMs > 0 ? w.timeoutMs : 30_000L;
        } else if (action instanceof autismclient.util.macro.WaitGuiTypeAction w) {
            timeoutMs = w.timeoutMs > 0 ? w.timeoutMs : 30_000L;
        } else if (action instanceof autismclient.util.macro.DelayAction d) {

            long ms = d.useTicks ? (long) d.delayTicks * 50L : d.delayMs;
            sleepBounded(Math.max(0L, Math.min(120_000L, ms)));
            return;
        } else {
            return;
        }

        long deadline = System.currentTimeMillis() + Math.max(100L, Math.min(120_000L, timeoutMs));
        while (System.currentTimeMillis() <= deadline && !Thread.currentThread().isInterrupted()) {
            if (CustomMenuTracker.current() != null) return;
            java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
        }
    }

    private static void sleepBounded(long ms) {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
        }
    }

    private static void failPreJoin(String reason) {
        preJoinFailed = true;
        armedRemainder = null;
        AutismNotifications.error(reason == null || reason.isBlank() ? "Join custom menu failed" : reason);
    }

    private static String resolveFormTemplate(String template) {
        return resolveStoredFormTemplate(template);
    }

    public static String resolveStoredFormTemplate(String template) {
        String out = template == null ? "" : template;
        for (Map.Entry<String, String> entry : openFormValues().entrySet()) {
            out = out.replace("{secret." + entry.getKey() + "}", entry.getValue());
        }
        if (out.matches("(?s).*\\{secret\\.[^}]+}.*")) return null;
        Minecraft mc = Minecraft.getInstance();
        String username = mc == null || mc.getUser() == null ? "" : mc.getUser().getName();
        String accountId = mc == null || mc.getUser() == null ? "" : mc.getUser().getProfileId().toString();
        return out.replace("{username}", username).replace("{account_id}", accountId)
            .replace("{profile_name}", "Rendered client");
    }

    public static Map<String, String> openFormValues() {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, String> stored = AutismConfig.getGlobal().joinMacroFormValues;
        if (stored == null) return result;
        stored.forEach((name, encoded) -> {
            try {
                byte[] plain = autismclient.util.mm.crypto.AtRestSeal.unseal(Base64.getDecoder().decode(encoded));
                if (plain != null) result.put(name, new String(plain, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {  }
        });
        return result;
    }

    public static boolean setFormValues(Map<String, String> values) {
        Map<String, String> sealed = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String name = autismclient.util.multi.MultiProfile.normalizeSecretName(entry.getKey());
                if (name.isEmpty()) continue;
                byte[] blob = autismclient.util.mm.crypto.AtRestSeal.seal(
                    (entry.getValue() == null ? "" : entry.getValue()).getBytes(StandardCharsets.UTF_8));
                sealed.put(name, Base64.getEncoder().encodeToString(blob));
            }
        }
        AutismConfig config = AutismConfig.getGlobal();
        config.joinMacroFormValues = sealed;
        config.save();
        return true;
    }

    private static void executeOnce(Timing timing) {
        if (!enabled() || executedThisConnection || !triggerJoin().matches(joinOrdinal)) return;
        executedThisConnection = true;

        String macroName = selectedMacroName();
        AutismMacro macro = armedRemainder != null ? armedRemainder : AutismMacroManager.get().get(macroName);
        if (macro == null) {
            AutismNotifications.warning("Join macro missing: " + macroName);
            setSelectedMacro("");
            return;
        }
        if (preJoinFailed) return;
        boolean keepEnabled = keepEnabled();
        if (!keepEnabled) {
            AutismConfig config = AutismConfig.getGlobal();
            config.joinMacroName = "";
            config.joinMacroEnabled = false;
            config.save();
        } else if (triggerJoin() != TriggerJoin.ANY) {
            joinOrdinal = 0;
        }

        Minecraft mc = Minecraft.getInstance();
        Runnable run = () -> {
            try {
                if (!macro.actions.isEmpty()) macro.execute();
                AutismNotifications.show("Join macro: " + macro.name, 0xFF35D873);
            } catch (Throwable t) {
                AutismNotifications.error("Join macro failed.");
            }
        };

        if (mc != null) mc.execute(run);
        else run.run();
    }

    private static boolean isWorldReady(Minecraft mc) {
        return mc.getConnection() != null && mc.player != null && mc.level != null;
    }

    private static boolean isPlayable(Minecraft mc) {
        return isWorldReady(mc) && mc.gui.screen() == null;
    }

    public static String modeSummary() {
        boolean keepEnabled = keepEnabled();
        return timing().label() + " / " + triggerJoin().displayLabel(keepEnabled) + " / " + (keepEnabled ? "Stays Enabled" : "Clears After Run");
    }

    private static void resetSequence() {
        joinOrdinal = 0;
        executedThisConnection = false;
        resetForJoin();
    }

    private static TriggerJoin normalizeTriggerJoin(TriggerJoin triggerJoin, boolean keepEnabled) {
        TriggerJoin value = triggerJoin == null ? TriggerJoin.FIRST : triggerJoin;
        if (keepEnabled && value == TriggerJoin.FIRST) return TriggerJoin.ANY;
        if (!keepEnabled && value == TriggerJoin.ANY) return TriggerJoin.FIRST;
        return value;
    }
}
