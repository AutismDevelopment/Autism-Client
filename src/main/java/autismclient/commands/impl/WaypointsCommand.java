package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.gui.screen.AutismWaypointsScreen;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.WaypointsModule;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismWaypoints;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public final class WaypointsCommand extends Command {
    private static final int FALLBACK_DEFAULT_COLOR = 0xFF3BD7FF;

    public WaypointsCommand() {
        super("waypoints", "Save and manage waypoints.", "wp");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> addCurrentPosition());
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("gui").executes(ctx -> openGui()));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("list").executes(ctx -> openGui()));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("clear").executes(ctx -> clearScope()));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("del")
            .executes(ctx -> usage())
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("name", StringArgumentType.greedyString())
                .executes(ctx -> delete(StringArgumentType.getString(ctx, "name")))));
        root.then(RequiredArgumentBuilder.<AutismCommandSource, Double>argument("x", DoubleArgumentType.doubleArg())
            .then(RequiredArgumentBuilder.<AutismCommandSource, Double>argument("y", DoubleArgumentType.doubleArg())
                .then(RequiredArgumentBuilder.<AutismCommandSource, Double>argument("z", DoubleArgumentType.doubleArg())
                    .executes(ctx -> addAt(DoubleArgumentType.getDouble(ctx, "x"),
                        DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z"))))));

        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("args", StringArgumentType.greedyString())
            .executes(ctx -> fallback(StringArgumentType.getString(ctx, "args"))));
    }

    private int fallback(String args) {
        String trimmed = args == null ? "" : args.trim();
        if (trimmed.equalsIgnoreCase("gui") || trimmed.equalsIgnoreCase("list")) return openGui();
        if (trimmed.equalsIgnoreCase("clear")) return clearScope();
        if (trimmed.regionMatches(true, 0, "del ", 0, 4)) return delete(trimmed.substring(4));
        double[] coords = parseCoords(trimmed);
        if (coords != null) return addAt(coords[0], coords[1], coords[2]);
        return usage();
    }

    private int usage() {
        AutismClientMessaging.sendPrefixed("§eUsage: " + AutismCommands.effectivePrefix()
            + "wp [x y z] | gui | del <name> | clear");
        return SUCCESS;
    }

    private int addCurrentPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            AutismClientMessaging.sendPrefixed("§cNot in a world.");
            return SUCCESS;
        }
        AutismWaypoints store = AutismWaypoints.get();
        String scope = AutismWaypoints.scopeKey(mc);
        int x = (int) Math.floor(mc.player.getX());
        int y = (int) Math.floor(mc.player.getY());
        int z = (int) Math.floor(mc.player.getZ());
        String name = store.nextName(scope, "Waypoint");
        store.add(scope, new AutismWaypoints.Waypoint(name, x, y, z, defaultColor(), System.currentTimeMillis()));
        AutismClientMessaging.sendPrefixed("§aWaypoint saved: §f" + name + " §7(" + x + " " + y + " " + z + ")");
        return SUCCESS;
    }

    private int addAt(double dx, double dy, double dz) {
        Minecraft mc = Minecraft.getInstance();
        AutismWaypoints store = AutismWaypoints.get();
        String scope = AutismWaypoints.scopeKey(mc);
        int x = floorCoordinate(dx);
        int y = floorCoordinate(dy);
        int z = floorCoordinate(dz);
        String name = store.nextName(scope, "Waypoint");
        store.add(scope, new AutismWaypoints.Waypoint(name, x, y, z, defaultColor(), System.currentTimeMillis()));
        AutismClientMessaging.sendPrefixed("§aWaypoint saved: §f" + name + " §7(" + x + " " + y + " " + z + ")");
        return SUCCESS;
    }

    private int delete(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return usage();
        String scope = AutismWaypoints.scopeKey(Minecraft.getInstance());
        if (AutismWaypoints.get().remove(scope, trimmed)) {
            AutismClientMessaging.sendPrefixed("§aWaypoint deleted: §f" + trimmed);
        } else {
            AutismClientMessaging.sendPrefixed("§cNo waypoint named '" + trimmed + "'.");
        }
        return SUCCESS;
    }

    private int clearScope() {
        String scope = AutismWaypoints.scopeKey(Minecraft.getInstance());
        AutismWaypoints store = AutismWaypoints.get();
        int count = store.list(scope).size();
        store.clear(scope);
        AutismClientMessaging.sendPrefixed(count == 0
            ? "§7No waypoints to clear."
            : "§aCleared " + count + " waypoint" + (count == 1 ? "" : "s") + ".");
        return SUCCESS;
    }

    private int openGui() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return SUCCESS;

        mc.execute(() -> mc.gui.setScreen(new AutismWaypointsScreen(mc.gui.screen())));
        return SUCCESS;
    }

    private static int defaultColor() {
        Module module = ModuleRegistry.get("waypoints");
        return module instanceof WaypointsModule waypoints ? waypoints.defaultColor() : FALLBACK_DEFAULT_COLOR;
    }

    private static double[] parseCoords(String args) {
        String[] parts = args.split("\\s+");
        if (parts.length != 3) return null;
        double[] out = new double[3];
        try {
            for (int i = 0; i < 3; i++) {
                out[i] = Double.parseDouble(parts[i]);
                if (!Double.isFinite(out[i])) return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    private static int floorCoordinate(double value) {
        return Double.isFinite(value) ? (int) Math.floor(value) : 0;
    }
}
