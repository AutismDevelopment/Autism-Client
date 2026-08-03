package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;

public final class ChamsModule extends Module {
    public ChamsModule() {
        super("chams", "Chams", ModuleCategory.RENDER, "Entities through walls.");
        add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player")
            .description("Entities to render.").group("General").build());
        add(new ChoiceSetting("style", "Style", "Colored", "Colored", "Texture")
            .description("Colour or texture.").group("General").build());
        add(new DoubleSetting("max-distance", "Max Distance", 128.0, 0.0, 512.0, 4.0)
            .description("Range, 0 unlimited.").group("General").build());
        add(new BoolSetting("draw-armor", "Draw Armor", true)
            .description("Real armor on top.").group("General").build());
        add(new ColorSetting("visible-color", "Visible", 0xFF35FF5B)
            .description("Colour when visible.")
            .group("Colors").visibleWhen(() -> "Colored".equals(choice("style"))).build());
        add(new ColorSetting("occluded-color", "Behind Walls", 0xFFB030FF)
            .description("Colour behind walls.")
            .group("Colors").visibleWhen(() -> "Colored".equals(choice("style"))).build());
        add(new IntSetting("opacity", "Opacity", 100, 0, 100, 1)
            .description("Solid colour opacity.")
            .group("Colors").visibleWhen(() -> "Colored".equals(choice("style"))).build());
        add(new BoolSetting("hit-color", "Hit Color", true)
            .description("Flash on hit.").group("Hit Color").build());
        add(new ColorSetting("hit-color-value", "Color", 0xFFFF3B3B)
            .description("Flash colour.")
            .group("Hit Color").visibleWhen(() -> bool("hit-color")).build());
    }

    @Override
    public void onEnable() {
        ModuleRenderUtil.refreshFastFlags();
    }

    @Override
    public void onDisable() {
        ModuleRenderUtil.refreshFastFlags();
    }

    @Override
    public void onOptionValueChanged(String settingId) {
        ModuleRenderUtil.refreshFastFlags();
    }
}
