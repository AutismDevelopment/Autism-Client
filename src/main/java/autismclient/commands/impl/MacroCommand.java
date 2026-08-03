package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.args.MacroArgumentType;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismMacroManager;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiTakeoverState;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;

public class MacroCommand extends Command {
    private static final ConcurrentHashMap<String, AtomicBoolean> RUNNING_LOOPS = new ConcurrentHashMap<>();

    public MacroCommand() { super("macro", "Run a macro, optionally N times with a delay between starts."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            String prefix = AutismCommands.effectivePrefix();
            AutismClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "macro <name> [times] [delayTicks]");
            AutismClientMessaging.sendPrefixed("§7Also: §f" + prefix + "macro stop [name] §7or §f" + prefix + "macro clear");
            return SUCCESS;
        });

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("stop")
            .executes(ctx -> {
                String pov = MultiTakeoverState.activeAccountId();
                if (pov != null) {
                    stopPovLoops(pov);
                    MultiManager mgr = MultiManager.getIfInitialized();
                    MultiManager.BroadcastResult result = mgr == null
                        ? new MultiManager.BroadcastResult(0, 1, 0, java.util.List.of("Multi unavailable"))
                        : mgr.stopMacroOnScope(Set.of(pov));
                    AutismClientMessaging.sendPrefixed("§ePOV macro stop: §f" + result.summary());
                    return SUCCESS;
                }
                stopAllLoops();
                AutismMacroManager.get().stopMacro();
                AutismClientMessaging.sendPrefixed("§eStopped all macros.");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("name", MacroArgumentType.macroName())
                .executes(ctx -> {
                    String name = MacroArgumentType.get(ctx, "name");
                    String pov = MultiTakeoverState.activeAccountId();
                    if (pov != null) {
                        AtomicBoolean flag = RUNNING_LOOPS.remove(povLoopKey(pov, name));
                        if (flag != null) flag.set(false);
                        MultiManager mgr = MultiManager.getIfInitialized();
                        MultiManager.BroadcastResult result = mgr == null
                            ? new MultiManager.BroadcastResult(0, 1, 0, java.util.List.of("Multi unavailable"))
                            : mgr.stopMacroOnScope(Set.of(pov));
                        AutismClientMessaging.sendPrefixed("§eStopped POV macro §f" + name + "§e: " + result.summary());
                        return SUCCESS;
                    }
                    AtomicBoolean flag = RUNNING_LOOPS.remove(name.toLowerCase());
                    if (flag != null) flag.set(false);
                    MacroExecutor.stopMacro(name);
                    AutismClientMessaging.sendPrefixed("§eStopped: §f" + name);
                    return SUCCESS;
                })));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("clear")
            .executes(ctx -> {
                String pov = MultiTakeoverState.activeAccountId();
                if (pov != null) {
                    stopPovLoops(pov);
                    AutismClientMessaging.sendPrefixed("§eCleared POV macro loops for §f" + pov + "§e.");
                } else {
                    stopAllLoops();
                    AutismClientMessaging.sendPrefixed("§eCleared macro loops.");
                }
                return SUCCESS;
            }));

        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("name", MacroArgumentType.macroName())
            .executes(ctx -> startLoop(MacroArgumentType.get(ctx, "name"), 1, 0))
            .then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("times", IntegerArgumentType.integer(1, 100_000))
                .executes(ctx -> startLoop(MacroArgumentType.get(ctx, "name"), IntegerArgumentType.getInteger(ctx, "times"), 0))
                .then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("delayTicks", IntegerArgumentType.integer(0, 20 * 60 * 60))
                    .executes(ctx -> startLoop(
                        MacroArgumentType.get(ctx, "name"),
                        IntegerArgumentType.getInteger(ctx, "times"),
                        IntegerArgumentType.getInteger(ctx, "delayTicks"))))));
    }

    private static int startLoop(String name, int times, int delayTicks) {
        autismclient.util.AutismMacro macro = AutismMacroManager.get().get(name);
        if (macro == null) {
            AutismClientMessaging.sendPrefixed("§cMacro not found: §f" + name);
            return SUCCESS;
        }
        String pov = MultiTakeoverState.activeAccountId();
        if (pov != null) return startPovLoop(name, macro, times, delayTicks, pov);
        if (times <= 1 && delayTicks <= 0) {
            AutismMacroManager.get().executeMacro(name);
            return SUCCESS;
        }
        AtomicBoolean flag = new AtomicBoolean(true);
        RUNNING_LOOPS.put(name.toLowerCase(), flag);
        long sleepMs = Math.max(0L, delayTicks * 50L);
        final int totalTimes = Math.max(1, times);
        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < totalTimes && flag.get(); i++) {
                    AutismMacroManager.get().executeMacro(name);

                    long start = System.nanoTime();
                    long waitTimeoutNanos = 24L * 60L * 60L * 1_000_000_000L;
                    while (MacroExecutor.isMacroRunning(name) && flag.get()
                        && System.nanoTime() - start < waitTimeoutNanos) {
                        Thread.sleep(50);
                    }
                    if (!flag.get()) break;
                    if (i + 1 < totalTimes && sleepMs > 0) Thread.sleep(sleepMs);
                }
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { RUNNING_LOOPS.remove(name.toLowerCase(), flag); }
        }, "MacroLoop-" + name);
        t.setDaemon(true);
        t.start();
        AutismClientMessaging.sendPrefixed("§aQueued " + totalTimes + "x §f" + name + "§a (delay " + delayTicks + "t)");
        return SUCCESS;
    }

    private static int startPovLoop(String name, autismclient.util.AutismMacro macro, int times,
                                    int delayTicks, String accountId) {
        MultiManager mgr = MultiManager.getIfInitialized();
        if (mgr == null) {
            AutismClientMessaging.sendPrefixed("§cMulti is unavailable.");
            return SUCCESS;
        }
        Set<String> scope = Set.of(accountId);
        if (times <= 1 && delayTicks <= 0) {
            MultiManager.BroadcastResult result = mgr.runMacroDirect(macro, scope);
            AutismClientMessaging.sendPrefixed((result.sent() > 0 ? "§a" : "§c")
                + "POV macro §f" + name + "§a: " + result.summary());
            return SUCCESS;
        }

        String key = povLoopKey(accountId, name);
        AtomicBoolean flag = new AtomicBoolean(true);
        AtomicBoolean previous = RUNNING_LOOPS.put(key, flag);
        if (previous != null) previous.set(false);
        long sleepMs = Math.max(0L, delayTicks * 50L);
        int totalTimes = Math.max(1, times);
        Thread thread = new Thread(() -> {
            try {
                for (int i = 0; i < totalTimes && flag.get(); i++) {
                    mgr.runMacroDirect(macro, scope);
                    long start = System.nanoTime();
                    long timeout = 24L * 60L * 60L * 1_000_000_000L;
                    while (mgr.isMacroPlayingOnScope(name, scope) && flag.get()
                        && System.nanoTime() - start < timeout) {
                        Thread.sleep(50L);
                    }
                    if (!flag.get()) break;
                    if (i + 1 < totalTimes && sleepMs > 0L) Thread.sleep(sleepMs);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                RUNNING_LOOPS.remove(key, flag);
            }
        }, "PovMacroLoop-" + name);
        thread.setDaemon(true);
        thread.start();
        AutismClientMessaging.sendPrefixed("§aQueued POV §f" + totalTimes + "x " + name
            + "§a for §f" + accountId + "§a.");
        return SUCCESS;
    }

    private static String povLoopKey(String accountId, String name) {
        return "pov:" + accountId.toLowerCase(java.util.Locale.ROOT) + ":" + name.toLowerCase(java.util.Locale.ROOT);
    }

    private static void stopPovLoops(String accountId) {
        String prefix = "pov:" + accountId.toLowerCase(java.util.Locale.ROOT) + ":";
        RUNNING_LOOPS.forEach((key, flag) -> {
            if (key.startsWith(prefix) && RUNNING_LOOPS.remove(key, flag)) flag.set(false);
        });
    }

    private static void stopAllLoops() {
        for (AtomicBoolean flag : RUNNING_LOOPS.values()) flag.set(false);
        RUNNING_LOOPS.clear();
    }
}
