package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class CommandsCommand extends Command {
    public CommandsCommand() { super("commands", "List all available commands.", "cmds"); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            String prefix = AutismCommands.effectivePrefix();
            AutismClientMessaging.sendPrefixed("§e" + AutismCommands.all().size() + " commands (prefix §f" + prefix + "§e):");
            StringBuilder line = new StringBuilder();
            for (Command c : AutismCommands.all()) {
                if (line.length() > 0) line.append("§7, ");
                line.append("§f").append(c.name());
                if (line.length() > 200) { AutismClientMessaging.sendPrefixed(line.toString()); line.setLength(0); }
            }
            if (line.length() > 0) AutismClientMessaging.sendPrefixed(line.toString());
            AutismClientMessaging.sendPrefixed("§7Type §f" + prefix + "help <command>§7 for details.");
            return SUCCESS;
        });
    }
}
