package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;

public final class NameTagsModule extends Module {
    public NameTagsModule() {
        super("nametags", "Nametags", ModuleCategory.RENDER, "Labels above entities.");
        add(new BoolSetting("players", "Players", true).description("Tag players"));
        add(new BoolSetting("mobs", "Mobs", false).description("Tag mobs"));
        add(new BoolSetting("items", "Items", true).description("Tag dropped items"));
        add(new BoolSetting("show-health", "Show Health", true).group("Info").description("Show health"));
        add(new BoolSetting("show-distance", "Show Distance", false).group("Info").description("Show distance"));
        add(new DoubleSetting("scale", "Scale", 1.0, 0.5, 4.0, 0.05).group("Display").description("Label size"));
        add(new BoolSetting("distance-scale", "Distance Scaling", true).group("Display").description("Shrink with distance"));
        add(new DoubleSetting("max-distance", "Max Distance", 64.0, 0.0, 256.0, 1.0).group("Display").description("0 = unlimited"));
        add(new BoolSetting("group-items", "Group Items", true).group("Items")
            .description("Merge nearby items")
            .visibleWhen(() -> bool("items")));
        add(new DoubleSetting("group-radius", "Group Radius", 5.0, 0.5, 16.0, 0.5).group("Items")
            .description("Item merge radius")
            .visibleWhen(() -> bool("items") && bool("group-items")));
    }
}
