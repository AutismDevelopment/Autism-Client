package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.commands.args.MacroArgumentType;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismLANSync;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public final class SyncCommand extends Command {
    public SyncCommand() {
        super("sync", "Synchronize a chat message, server command, or macro across the LAN session.",
            "lan-sync", "lansync");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(context -> usage());

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("send")
            .executes(context -> {
                AutismClientMessaging.sendPrefixed("§eUsage: §f" + AutismCommands.effectivePrefix()
                    + "sync send <message or /command>");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument(
                    "message", StringArgumentType.greedyString())
                .executes(context -> send(StringArgumentType.getString(context, "message")))));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("macro")
            .executes(context -> {
                AutismClientMessaging.sendPrefixed("§eUsage: §f" + AutismCommands.effectivePrefix()
                    + "sync macro <name>");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument(
                    "name", MacroArgumentType.macroName())
                .executes(context -> macro(MacroArgumentType.get(context, "name")))));
    }

    private static int usage() {
        String prefix = AutismCommands.effectivePrefix();
        AutismClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "sync send <message or /command>");
        AutismClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "sync macro <name>");
        return SUCCESS;
    }

    private static int send(String message) {
        AutismLANSync sync = AutismLANSync.getInstance();
        if (!sync.isInSession()) {
            AutismClientMessaging.sendPrefixed("§cJoin a LAN Sync session first.");
            return SUCCESS;
        }
        sync.sendChatMessage(message);
        return SUCCESS;
    }

    private static int macro(String name) {
        AutismLANSync sync = AutismLANSync.getInstance();
        if (!sync.isInSession()) {
            AutismClientMessaging.sendPrefixed("§cJoin a LAN Sync session first.");
            return SUCCESS;
        }
        AutismMacro macro = AutismMacroManager.get().get(name);
        if (macro == null) {
            AutismClientMessaging.sendPrefixed("§cMacro not found: §f" + name);
            return SUCCESS;
        }

        sync.executeMacroSynchronized(macro.name);
        return SUCCESS;
    }
}
