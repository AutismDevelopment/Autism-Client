package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismPlayerScanner;
import autismclient.util.AutismRemoteView;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class RemoteViewCommand extends Command {
    public RemoteViewCommand() {
        super("rv", "Watch a loaded player's POV.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> openLookedAt());
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("stop")
            .executes(ctx -> {
                if (!AutismRemoteView.isActive()) {
                    AutismClientMessaging.sendPrefixed("§7Remote view is not active.");
                } else {
                    AutismRemoteView.stop(false);
                    AutismClientMessaging.sendPrefixed("§aRemote view stopped.");
                }
                return SUCCESS;
            }));
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("player", StringArgumentType.greedyString())
            .suggests(RemoteViewCommand::suggestPlayers)
            .executes(ctx -> openNamed(StringArgumentType.getString(ctx, "player"))));
    }

    private static int openLookedAt() {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc == null ? null : mc.crosshairPickEntity;
        if (!(entity instanceof Player) && mc != null && mc.hitResult instanceof EntityHitResult hit) {
            entity = hit.getEntity();
        }
        if (!(entity instanceof Player player)) {
            AutismClientMessaging.sendPrefixed("§cLook at a loaded player first.");
            return SUCCESS;
        }
        report(AutismRemoteView.start(player));
        return SUCCESS;
    }

    private static int openNamed(String requested) {
        Player player = findLoadedPlayer(Minecraft.getInstance(), requested);
        if (player == null) {
            AutismClientMessaging.sendPrefixed("§cPlayer is not loaded: §f" + requested.trim());
            return SUCCESS;
        }
        report(AutismRemoteView.start(player));
        return SUCCESS;
    }

    static Player findLoadedPlayer(Minecraft mc, String requested) {
        if (mc == null || mc.level == null || requested == null) return null;
        String wanted = requested.trim();
        if (wanted.isEmpty()) return null;
        for (Player player : mc.level.players()) {
            if (player == null) continue;
            String profile = player.getGameProfile() == null ? "" : player.getGameProfile().name();
            String shown = player.getName().getString();
            if (wanted.equalsIgnoreCase(profile) || wanted.equalsIgnoreCase(shown)) return player;
        }
        return null;
    }

    private static void report(AutismRemoteView.Result result) {
        AutismClientMessaging.sendPrefixed((result.ok() ? "§a" : "§c") + result.message());
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<AutismCommandSource> ignored,
                                                                  SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (AutismPlayerScanner.ScannedPlayer player : AutismPlayerScanner.scan(Minecraft.getInstance())) {
            if (player.name().toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(player.name());
        }
        return builder.buildFuture();
    }
}
