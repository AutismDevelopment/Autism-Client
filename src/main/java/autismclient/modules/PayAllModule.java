package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.StringListSetting;
import autismclient.api.module.StringSetting;
import autismclient.util.AutismBackgroundTasks;
import autismclient.util.AutismClientMessaging;
import autismclient.util.macro.PayAction;

import java.util.ArrayList;
import java.util.List;

public final class PayAllModule extends Module {
    private volatile int runGeneration;

    public PayAllModule() {
        super("pay-all", "PayAll", ModuleCategory.MISC, "Pay every listed player.");

        add(new StringListSetting("players", "Players", "").playerRankPicker().build());
        add(new BoolSetting("probe", "Probe Hidden", true).description("Include hidden players.").build());
        add(new StringSetting("amount", "Amount", "1000").description("Supports K, M, B.").build());
        add(new BoolSetting("divide", "Split Total", false).description("Split across players.").build());
        add(new IntSetting("delay", "Delay (ms)", 1000, 0, 10000, 100).description("Gap between payments.").build());
        add(new StringSetting("command", "Command", "/pay <player> <amount>").description("<player> and <amount>.").build());
        add(new StringSetting("confirm-macro", "Confirm Macro", "")
            .macroPicker().description("Run after every pay.").build());
    }

    @Override
    public void onEnable() {
        int generation = ++runGeneration;
        AutismBackgroundTasks.runTracked("pay-all", () -> runPayRound(generation));
    }

    @Override
    public void onDisable() {
        runGeneration++;
    }

    @Override
    public void onGameLeft() {
        if (isEnabled()) setEnabled(false);
    }

    private void runPayRound(int generation) {
        try {
            List<String> targets = new ArrayList<>();
            for (String player : list("players")) {
                if (player == null) continue;
                String trimmed = player.trim();
                if (!trimmed.isEmpty() && !targets.contains(trimmed)) targets.add(trimmed);
            }
            if (MC == null || MC.getConnection() == null) return;

            boolean everyone = targets.isEmpty();
            if (everyone) {
                if (bool("probe")) chat("§7Pay All: scanning players...");
                targets.addAll(autismclient.util.AutismPlayerProbe.everyone(MC, bool("probe"), () -> cancelled(generation)));
                if (targets.isEmpty()) {
                    chat("§cPay All: no players listed or visible.");
                    return;
                }
            }

            long amount = Math.max(0L, PayAction.parseAmount(text("amount")));
            long[] divided = bool("divide") ? PayAction.distribute(amount, targets.size()) : null;
            int sent = 0;
            for (int i = 0; i < targets.size(); i++) {
                if (cancelled(generation)) return;
                String player = targets.get(i);
                long perAmount = divided != null ? divided[i] : amount;
                if (perAmount <= 0L) continue;

                if (sent > 0 && integer("delay") > 0) Thread.sleep(integer("delay"));
                if (cancelled(generation)) return;
                String command = commandFor(player, perAmount);
                if (command.isEmpty()) continue;
                sent++;
                final int index = sent;
                final int total = targets.size();
                final String label = PayAction.formatAmount(perAmount);
                final String targetName = player;
                java.util.concurrent.CountDownLatch sendLatch = new java.util.concurrent.CountDownLatch(1);
                MC.execute(() -> {
                    try {
                        sendPayment(targetName, command, label, index, total);
                    } finally {
                        sendLatch.countDown();
                    }
                });

                sendLatch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (cancelled(generation)) return;
                String confirm = text("confirm-macro");
                if (confirm != null && !confirm.isBlank()) {
                    PayAction.runConfirmMacro(confirm, () -> cancelled(generation));
                }
            }
            int finalSent = sent;
            MC.execute(() -> chat("§aPay All done: §f" + finalSent + " payment" + (finalSent == 1 ? "" : "s")
                + (everyone ? " §7(everyone)" : "") + "."));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            finish(generation);
        }
    }

    private void sendPayment(String player, String command, String label, int index, int total) {
        try {
            if (PackHideState.isHardLocked() || MC == null || MC.getConnection() == null) return;
            if (command.startsWith("/") && command.length() > 1) {
                MC.getConnection().sendCommand(command.substring(1));
            } else {
                MC.getConnection().sendChat(command);
            }
            chat("§aPaid §f" + player + " §e" + label + " §7(" + index + "/" + total + ")");
        } catch (Exception e) {
            chat("§cPay failed: " + e.getMessage());
        }
    }

    private String commandFor(String player, long amount) {
        String template = text("command");
        if (template == null || template.isBlank()) template = "/pay <player> <amount>";
        String value = String.valueOf(amount);
        return template
            .replace("<player>", player).replace("{player}", player)
            .replace("<amount>", value).replace("{amount}", value)
            .trim();
    }

    private boolean cancelled(int generation) {
        return generation != runGeneration || !isEnabled();
    }

    private void finish(int generation) {
        if (MC == null) {
            if (generation == runGeneration) setEnabled(false);
            return;
        }
        MC.execute(() -> {
            if (generation == runGeneration && isEnabled()) setEnabled(false);
        });
    }

    private void chat(String message) {
        AutismClientMessaging.sendPrefixed(message);
    }
}
