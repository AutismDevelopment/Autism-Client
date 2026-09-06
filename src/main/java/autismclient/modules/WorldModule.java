package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.util.AutismWorldHighlightRenderer;

public final class WorldModule extends Module {
    private boolean lastDarken;
    private String lastMode = "";
    private int lastDarkness = Integer.MIN_VALUE;
    private int lastTint = Integer.MIN_VALUE;
    private String lastBlocks = "";

    public WorldModule() {
        super("world", "World", ModuleCategory.RENDER, "World render tweaks.");
        add(new BoolSetting("darken-blocks", "Tint Blocks", true)
            .description("Tint block textures.").group("Block Tint").build());
        add(new ColorSetting("tint-color", "Color", 0xFF000000)
            .description("Tint color.").group("Block Tint").visibleWhen(() -> bool("darken-blocks")).build());
        add(new IntSetting("darkness", "Strength", 50, 0, 100, 1)
            .description("Tint strength, percent.").group("Block Tint").visibleWhen(() -> bool("darken-blocks")).build());
        add(new ChoiceSetting("mode", "Mode", "Blacklist", "Blacklist", "Whitelist")
            .description("List spares or targets.").group("Block Tint").visibleWhen(() -> bool("darken-blocks")).build());
        add(RegistryListSetting.blocks("blocks", "Blocks")
            .description("Blacklist spares, whitelist targets.").group("Block Tint")
            .visibleWhen(() -> bool("darken-blocks")).build());

        add(new BoolSetting("block-highlight", "Block Highlight", false)
            .description("Custom block outline.").group("Block Highlight").build());
        add(new ColorSetting("highlight-color", "Color", 0xFFFFFFFF)
            .description("Outline color.").group("Block Highlight").visibleWhen(() -> bool("block-highlight")).build());
        add(new DoubleSetting("highlight-width", "Line Width", 2.0, 0.5, 6.0, 0.1)
            .description("Outline thickness.").group("Block Highlight").visibleWhen(() -> bool("block-highlight")).build());
        add(new ChoiceSetting("highlight-style", "Style", "Full", "Full", "Corners")
            .description("Full cube or corners.").group("Block Highlight").visibleWhen(() -> bool("block-highlight")).build());
        add(new BoolSetting("highlight-fill", "Fill", false)
            .description("Fill the box faintly.").group("Block Highlight").visibleWhen(() -> bool("block-highlight")).build());
        add(new BoolSetting("highlight-progress", "Show Progress", true)
            .description("Damage color shift.").group("Block Highlight").visibleWhen(() -> bool("block-highlight")).build());

        add(new BoolSetting("skybox", "Skybox", false)
            .description("Replace the sky with the AUTISM panorama.").group("Skybox").build());
        add(new BoolSetting("skybox-spin", "Spin", true)
            .description("Slowly spin the skybox panorama.").group("Skybox").visibleWhen(() -> bool("skybox")).build());
        add(new DoubleSetting("skybox-speed", "Spin Speed", 1.0, 0.5, 3.0, 0.1)
            .description("Rotation speed.").group("Skybox")
            .visibleWhen(() -> bool("skybox") && bool("skybox-spin")).build());
        add(new BoolSetting("skybox-recolor", "Theme Recolor", false)
            .description("Theme-recolor the skybox panorama.").group("Skybox").visibleWhen(() -> bool("skybox")).build());
        add(new ColorSetting("skybox-color", "Color", 0xFFFF3B3B)
            .description("Custom panorama color.").group("Skybox")
            .visibleWhen(() -> bool("skybox") && !bool("skybox-recolor")).build());
    }

    @Override
    public void onEnable() {
        ModuleRenderUtil.refreshWorldRenderer();
        pushHighlight();
    }

    @Override
    public void onDisable() {
        ModuleRenderUtil.refreshWorldRenderer();
        AutismWorldHighlightRenderer.disable();
    }

    @Override
    public void tick() {

        boolean darken = bool("darken-blocks");
        String mode = choice("mode");
        int darkness = integer("darkness");
        int tint = ModuleRenderUtil.color(this, "tint-color", 0xFF000000);
        String blocks = value("blocks");
        if (darken != lastDarken || !mode.equals(lastMode) || darkness != lastDarkness || tint != lastTint || !blocks.equals(lastBlocks)) {
            lastDarken = darken;
            lastMode = mode;
            lastDarkness = darkness;
            lastTint = tint;
            lastBlocks = blocks;
            ModuleRenderUtil.refreshWorldRenderer();
        }

        pushHighlight();
    }

    private void pushHighlight() {
        boolean active = isEnabled() && bool("block-highlight");
        int color = ModuleRenderUtil.color(this, "highlight-color", 0xFFFFFFFF);
        float width = (float) decimal("highlight-width");
        boolean corners = "Corners".equals(choice("highlight-style"));
        boolean fill = bool("highlight-fill");
        int fillColor = (color & 0x00FFFFFF) | 0x40000000;
        AutismWorldHighlightRenderer.push(active, color, width, corners, fill, fillColor);
        AutismWorldHighlightRenderer.pushProgress(active && bool("highlight-progress"));
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if (isEnabled()) pushHighlight();
    }

    @Override
    public boolean ticksWhenDisabled() {
        return false;
    }
}
