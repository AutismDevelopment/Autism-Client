package com.example.addon.commands;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

// A Brigadier command. Users run it with the command prefix, e.g. .example or .example hi.
public final class ExampleCommand extends Command {
    public ExampleCommand() {
        super("example", "Example addon command.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            AutismClientMessaging.sendPrefixed("\u00a7dExample addon command works!");
            return SUCCESS;
        });

        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("text", StringArgumentType.greedyString())
            .executes(ctx -> {
                AutismClientMessaging.sendPrefixed("\u00a7dYou said: \u00a7f" + StringArgumentType.getString(ctx, "text"));
                return SUCCESS;
            }));
    }
}
