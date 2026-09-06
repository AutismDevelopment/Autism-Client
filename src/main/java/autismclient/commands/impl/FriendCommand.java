package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.modules.TeamsModule;
import autismclient.util.AutismPlayerScanner;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class FriendCommand extends Command {
    public FriendCommand() {
        super("friend", "Manage the friends/teams list. add <name>, remove <name>, clear.", "team");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {

        root.executes(ctx -> listFriends());

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("add")
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("name", StringArgumentType.word())
                .suggests(FriendCommand::suggestServerPlayers)
                .executes(ctx -> TeamsModule.addFriend(StringArgumentType.getString(ctx, "name")) ? SUCCESS : 0)));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("remove")
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("name", StringArgumentType.word())
                .suggests(FriendCommand::suggestFriends)
                .executes(ctx -> TeamsModule.removeFriend(StringArgumentType.getString(ctx, "name")) ? SUCCESS : 0)));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("clear")
            .executes(ctx -> TeamsModule.clearFriends() ? SUCCESS : 0));
    }

    private static int listFriends() {
        List<String> friends = TeamsModule.storedFriendNames();
        if (friends.isEmpty()) {
            autismclient.util.AutismClientMessaging.sendPrefixed("§7Friend list is empty.");
            return SUCCESS;
        }
        autismclient.util.AutismClientMessaging.sendPrefixed("§7Friends (§f" + friends.size() + "§7): §f" + String.join("§7, §f", friends));
        return SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestServerPlayers(CommandContext<AutismCommandSource> ctx,
                                                                       SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (AutismPlayerScanner.ScannedPlayer player : AutismPlayerScanner.scan(Minecraft.getInstance())) {
            String name = player.name();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestFriends(CommandContext<AutismCommandSource> ctx,
                                                                 SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String name : TeamsModule.storedFriendNames()) {
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    }
}
