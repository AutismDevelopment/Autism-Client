package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.commands.CommandSuggest;
import autismclient.modules.AutismModule;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismCompatManager;
import autismclient.util.AutismNotifications;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class PrefixCommand extends Command {
    public PrefixCommand() {
        super("prefix", "Change the AUTISM command prefix.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            AutismClientMessaging.sendPrefixed("Current prefix: " + AutismCompatManager.effectiveCommandPrefix());
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("reset").executes(ctx -> {
            String prefix = AutismCompatManager.environmentDefaultCommandPrefix();
            AutismModule.get().setCommandPrefix(prefix);
            AutismClientMessaging.sendPrefixed("Prefix reset to " + prefix);
            return SUCCESS;
        }));
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("new", StringArgumentType.word())
            .suggests(CommandSuggest::prefixes)
            .executes(ctx -> {
                String requested = StringArgumentType.getString(ctx, "new");
                if (!AutismCompatManager.COMMAND_PREFIX_CHOICES.contains(requested)) {
                    AutismClientMessaging.sendPrefixed("Prefix must be one of: . % - _ * # @ & =");
                    return SUCCESS;
                }
                String selected = AutismCompatManager.normalizeStoredCommandPrefix(requested);
                AutismModule.get().setCommandPrefix(selected);
                if (!selected.equals(requested)) {
                    AutismNotifications.warning("Meteor uses '.'. Prefix kept as '%'.");
                }
                AutismClientMessaging.sendPrefixed("Prefix set to " + selected);
                return SUCCESS;
            }));
    }
}
