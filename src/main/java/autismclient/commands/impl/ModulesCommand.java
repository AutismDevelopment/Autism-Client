package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class ModulesCommand extends Command {
    public ModulesCommand() { super("modules", "List installed modules.", "features"); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            java.util.List<String> names = ModuleRegistry.names();
            AutismClientMessaging.sendPrefixed("§e" + names.size() + " modules:");
            StringBuilder line = new StringBuilder();
            for (String n : names) {
                if (line.length() > 0) line.append("§7, ");
                line.append("§f").append(n);
                if (line.length() > 200) { AutismClientMessaging.sendPrefixed(line.toString()); line.setLength(0); }
            }
            if (line.length() > 0) AutismClientMessaging.sendPrefixed(line.toString());
            return SUCCESS;
        });
    }
}
