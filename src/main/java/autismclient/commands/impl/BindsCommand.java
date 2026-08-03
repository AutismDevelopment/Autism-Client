package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.args.KeyArgumentType;
import autismclient.modules.AutismModule;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.Map;

public class BindsCommand extends Command {
    public BindsCommand() { super("binds", "List command keybinds (set with `bind`)."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            Map<Integer, String> binds = AutismModule.get().getCommandBinds();
            if (binds.isEmpty()) { AutismClientMessaging.sendPrefixed("§eNo command keybinds set."); return SUCCESS; }
            AutismClientMessaging.sendPrefixed("§e" + binds.size() + " command keybinds:");
            for (Map.Entry<Integer, String> e : binds.entrySet()) {
                AutismClientMessaging.sendPrefixed("§b" + KeyArgumentType.keyName(e.getKey()) + "§7 → §f" + e.getValue());
            }
            return SUCCESS;
        });
    }
}
