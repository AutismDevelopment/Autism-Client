package autismclient.modules;

import autismclient.api.module.ActionSetting;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.IntSetting;
import autismclient.gui.screen.AutismWaypointsScreen;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismWaypoints;

public final class WaypointsModule extends Module {
    private final BoolSetting onDeath;
    private final BoolSetting trackDeaths;
    private final IntSetting deathMemory;
    private final ColorSetting deathColor;
    private final ColorSetting defaultColor;
    private final IntSetting dotSize;

    private boolean wasAlive = true;

    public WaypointsModule() {
        super("waypoints", "Waypoints", ModuleCategory.RENDER, "Save and render named destinations.");
        onDeath = add(new BoolSetting("on-death", "Waypoint On Death", true)
            .description("Save a waypoint where you die.").group("Waypoints").build());
        trackDeaths = add(new BoolSetting("track-deaths", "Track Deaths", true)
            .description("Save deaths even when off.").group("Waypoints").build());
        deathMemory = add(new IntSetting("death-memory", "Death Memory", 10, 1, 50, 1)
            .description("Max deaths to keep.").group("Waypoints").build());
        deathColor = add(new ColorSetting("death-color", "Death Color", 0xFFFF3B3B)
            .description("Color for death waypoints.").group("Waypoints").build());
        defaultColor = add(new ColorSetting("default-color", "Default Color", 0xFF3BD7FF)
            .description("Color for new waypoints.").group("Waypoints").build());
        dotSize = add(new IntSetting("dot-size", "Dot Size", 3, 1, 10, 1)
            .description("Waypoint dot size.").group("Waypoints").build());
        add(new BoolSetting("show-distance", "Show Distance", true)
            .description("Show your distance below waypoints.").group("Waypoints").build());
        add(new ActionSetting("manage-waypoints", "Manage Waypoints", this::openManager)
            .availableOffline().buttonLabel("Open").description("Open the waypoint manager.").group("Waypoints").build());
    }

    public int defaultColor() {
        return defaultColor.get();
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public void onGameJoin() {
        if (MC == null) return;
        AutismWaypoints.get().pruneDeaths(AutismWaypoints.scopeKey(MC), deathMemory.get());
    }

    @Override
    public void tick() {
        if (MC == null || MC.player == null) {
            wasAlive = true;
            return;
        }
        boolean alive = MC.player.getHealth() > 0.0F;
        if (wasAlive && !alive) onDeath();
        wasAlive = alive;
    }

    private void onDeath() {
        boolean enabled = isEnabled();

        if (!(enabled ? onDeath.get() : trackDeaths.get())) return;
        int x = (int) Math.floor(MC.player.getX());
        int y = (int) Math.floor(MC.player.getY());
        int z = (int) Math.floor(MC.player.getZ());
        AutismWaypoints.Waypoint added = AutismWaypoints.get().addDeath(
            AutismWaypoints.scopeKey(MC), x, y, z, deathColor.get(), System.currentTimeMillis(), deathMemory.get());
        if (added != null && enabled) {
            AutismClientMessaging.sendPrefixed("§aDeath waypoint saved: §f" + added.name()
                + " §7(" + x + " " + y + " " + z + ")");
        }
    }

    private void openManager() {
        if (MC == null || MC.gui == null) return;
        MC.gui.setScreen(new AutismWaypointsScreen(MC.gui.screen()));
    }
}
