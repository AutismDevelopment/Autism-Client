package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.util.AutismNameScrape;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public final class NameScrapeCommand extends Command {
    public NameScrapeCommand() {
        super("namescrape", "Scrape player names to clipboard. [count] limits it; deep = exhaustive; pause/resume/stop.",
            "scrapenames");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> apply(0, false));
        root.then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("count", IntegerArgumentType.integer(1))
            .executes(ctx -> apply(IntegerArgumentType.getInteger(ctx, "count"), false)));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("deep")
            .executes(ctx -> apply(0, true))
            .then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("count", IntegerArgumentType.integer(1))
                .executes(ctx -> apply(IntegerArgumentType.getInteger(ctx, "count"), true))));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("pause").executes(ctx -> {
            AutismNameScrape.pause();
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("resume").executes(ctx -> {
            AutismNameScrape.resume();
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("stop").executes(ctx -> {
            AutismNameScrape.stop();
            return SUCCESS;
        }));
    }

    private static int apply(int count, boolean deep) {
        AutismNameScrape.start(count, deep);
        return SUCCESS;
    }
}
