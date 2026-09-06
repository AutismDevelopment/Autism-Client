package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.commands.CommandSuggest;
import autismclient.modules.AutismModule;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismSharedState;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class DelayCommand extends Command {
    public DelayCommand() { super("delay", "Toggle the GUI packet delay queue."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> apply(null));
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("state", StringArgumentType.word())
            .suggests(CommandSuggest::state)
            .executes(ctx -> apply(StringArgumentType.getString(ctx, "state"))));
    }

    private static int apply(String state) {
        boolean current = AutismSharedState.get().shouldDelayGuiPackets();
        if (state != null) {
            String lower = state.toLowerCase();
            if (!lower.equals("on") && !lower.equals("enable") && !lower.equals("true") && !lower.equals("1")
                    && !lower.equals("off") && !lower.equals("disable") && !lower.equals("false") && !lower.equals("0")
                    && !lower.equals("toggle")) {
                AutismClientMessaging.sendPrefixed("Unknown delay state: " + state);
                AutismClientMessaging.sendPrefixed("Use on, off, or toggle.");
                return SUCCESS;
            }
        }

        boolean target = state == null ? !current
                : switch (state.toLowerCase()) {
                    case "on", "enable", "true", "1" -> true;
                    case "off", "disable", "false", "0" -> false;
                    case "toggle" -> !current;
                    default -> !current;
                };

        AutismModule module = AutismModule.get();
        int flushed = module.applyDelayGuiPacketsUiBehavior(target);
        module.notifyDelayPacketsUiResult(target, flushed);
        return SUCCESS;
    }
}
