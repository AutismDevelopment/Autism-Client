package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismWaypoints;
import autismclient.util.multi.PacketTeleportController;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

import java.util.Locale;

public final class TpCommand extends Command {
    public TpCommand() {
        super("tp", "Ground-first HClip teleport.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            show(PacketTeleportController.executeMain(""));
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument(
                "arguments", StringArgumentType.greedyString())
            .suggests((ctx, suggestions) -> {
                String remaining = suggestions.getRemaining();

                String bare = remaining.startsWith("\"") ? remaining.substring(1) : remaining;
                String lower = bare.toLowerCase(Locale.ROOT);
                for (String value : new String[]{"~ ~ ~", "stop", "status", "config 20 500", "reset"}) {
                    if (value.startsWith(remaining)) suggestions.suggest(value);
                }

                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    for (AutismWaypoints.Waypoint wp : AutismWaypoints.get().list(AutismWaypoints.scopeKey(mc))) {
                        String name = wp.name();
                        if (!name.toLowerCase(Locale.ROOT).startsWith(lower)) continue;
                        suggestions.suggest(name.contains(" ") ? "\"" + name + "\"" : name);
                    }
                }
                return suggestions.buildFuture();
            })
            .executes(ctx -> {
                show(PacketTeleportController.executeMain(resolveWaypoint(StringArgumentType.getString(ctx, "arguments"))));
                return SUCCESS;
            }));
    }

    private static String resolveWaypoint(String args) {
        String trimmed = args == null ? "" : args.trim();
        if (trimmed.isEmpty()) return args;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return args;
        String scope = AutismWaypoints.scopeKey(mc);

        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return toCoords(AutismWaypoints.get().find(scope, trimmed.substring(1, trimmed.length() - 1)), args);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("stop") || lower.equals("status") || lower.equals("reset")
            || lower.startsWith("config") || looksLikeCoords(trimmed)) return args;

        return toCoords(AutismWaypoints.get().find(scope, trimmed), args);
    }

    private static String toCoords(AutismWaypoints.Waypoint wp, String fallback) {
        return wp == null ? fallback : wp.x() + " " + wp.y() + " " + wp.z();
    }

    private static boolean looksLikeCoords(String value) {
        String[] parts = value.split("\\s+");
        if (parts.length < 3) return false;
        for (int i = 0; i < 3; i++) {
            if (parts[i].startsWith("~")) continue;
            try {
                Double.parseDouble(parts[i]);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static void show(String result) {
        String text = result == null || result.isBlank()
            ? "Usage: " + AutismCommands.effectivePrefix()
                + "tp <x> <y> <z> [maxPackets] [pauseMs]"
            : result;
        String color = text.startsWith("TP started") || text.startsWith("TP defaults") ? "§a"
            : text.startsWith("Usage") || text.startsWith("No active") ? "§e" : "§7";
        AutismClientMessaging.sendPrefixed(color + text);
    }
}
