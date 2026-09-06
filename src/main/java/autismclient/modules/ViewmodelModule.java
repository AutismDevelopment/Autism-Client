package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;

public final class ViewmodelModule extends Module {
    public ViewmodelModule() {
        super("viewmodel", "Viewmodel", ModuleCategory.RENDER,
            "Customize first-person hand position and animations.");

        add(new BoolSetting("main-hand", "Main Hand", false)
            .description("Move the main hand item.").group("Main Hand").build());
        add(new DoubleSetting("main-hand-scale", "Item Scale", 0.0, -5.0, 5.0, 0.05)
            .group("Main Hand").visibleWhen(() -> bool("main-hand")).build());
        add(new DoubleSetting("main-hand-x", "X", 0.0, -5.0, 5.0, 0.05)
            .group("Main Hand").visibleWhen(() -> bool("main-hand")).build());
        add(new DoubleSetting("main-hand-y", "Y", 0.0, -5.0, 5.0, 0.05)
            .group("Main Hand").visibleWhen(() -> bool("main-hand")).build());
        add(new DoubleSetting("main-hand-rot-x", "Rotation X", 0.0, -50.0, 50.0, 1.0)
            .group("Main Hand").visibleWhen(() -> bool("main-hand")).build());
        add(new DoubleSetting("main-hand-rot-y", "Rotation Y", 0.0, -50.0, 50.0, 1.0)
            .group("Main Hand").visibleWhen(() -> bool("main-hand")).build());
        add(new DoubleSetting("main-hand-rot-z", "Rotation Z", 0.0, -50.0, 50.0, 1.0)
            .group("Main Hand").visibleWhen(() -> bool("main-hand")).build());

        add(new BoolSetting("off-hand", "Off Hand", false)
            .description("Move the off hand item.").group("Off Hand").build());
        add(new DoubleSetting("off-hand-scale", "Item Scale", 0.0, -5.0, 5.0, 0.05)
            .group("Off Hand").visibleWhen(() -> bool("off-hand")).build());
        add(new DoubleSetting("off-hand-x", "X", 0.0, -1.0, 1.0, 0.02)
            .group("Off Hand").visibleWhen(() -> bool("off-hand")).build());
        add(new DoubleSetting("off-hand-y", "Y", 0.0, -1.0, 1.0, 0.02)
            .group("Off Hand").visibleWhen(() -> bool("off-hand")).build());
        add(new DoubleSetting("off-hand-rot-x", "Rotation X", 0.0, -50.0, 50.0, 1.0)
            .group("Off Hand").visibleWhen(() -> bool("off-hand")).build());
        add(new DoubleSetting("off-hand-rot-y", "Rotation Y", 0.0, -50.0, 50.0, 1.0)
            .group("Off Hand").visibleWhen(() -> bool("off-hand")).build());
        add(new DoubleSetting("off-hand-rot-z", "Rotation Z", 0.0, -50.0, 50.0, 1.0)
            .group("Off Hand").visibleWhen(() -> bool("off-hand")).build());

        add(new IntSetting("swing-duration", "Swing Duration", 6, 1, 20, 1)
            .description("Attack animation length (ticks)."));
        add(new ChoiceSetting("blocking-animation", "Blocking Animation", "1.7", "1.7", "Pushdown")
            .description("Sword swing animation.").group("Blocking Animation").build());
        add(new DoubleSetting("one-seven-y", "1.7 Y", 0.1, 0.05, 0.3, 0.01)
            .group("Blocking Animation").visibleWhen(() -> "1.7".equals(choice("blocking-animation"))).build());
        add(new DoubleSetting("one-seven-swing-scale", "1.7 Swing Scale", 0.9, 0.1, 1.0, 0.05)
            .group("Blocking Animation").visibleWhen(() -> "1.7".equals(choice("blocking-animation"))).build());

        add(new BoolSetting("equip-offset", "Equip Offset", true)
            .description("Item lower/raise animation.").group("Equip Offset").build());
        add(new BoolSetting("ignore-blocking", "Ignore Blocking", true)
            .description("Skip the blocking offset.").group("Equip Offset").visibleWhen(() -> bool("equip-offset")).build());
        add(new BoolSetting("ignore-place", "Ignore Place", true)
            .description("Skip the place bump.").group("Equip Offset").visibleWhen(() -> bool("equip-offset")).build());
        add(new BoolSetting("ignore-amount", "Ignore Amount", false)
            .description("Skip on count change.").group("Equip Offset").visibleWhen(() -> bool("equip-offset")).build());

        add(new BoolSetting("air-walker", "Air Walker", false)
            .description("Keep the walk bob in the air."));
    }

    @Override
    public void onEnable() {
        push();
    }

    @Override
    public void onDisable() {
        ViewmodelState.disable();
    }

    @Override
    public void tick() {
        push();
    }

    @Override
    public boolean ticksWhenDisabled() {
        return false;
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if (isEnabled()) push();
    }

    private void push() {
        ViewmodelState.mainHandOn = bool("main-hand");
        ViewmodelState.mainHandScale = (float) decimal("main-hand-scale");
        ViewmodelState.mainHandX = (float) decimal("main-hand-x");
        ViewmodelState.mainHandY = (float) decimal("main-hand-y");
        ViewmodelState.mainHandRotX = (float) decimal("main-hand-rot-x");
        ViewmodelState.mainHandRotY = (float) decimal("main-hand-rot-y");
        ViewmodelState.mainHandRotZ = (float) decimal("main-hand-rot-z");

        ViewmodelState.offHandOn = bool("off-hand");
        ViewmodelState.offHandScale = (float) decimal("off-hand-scale");
        ViewmodelState.offHandX = (float) decimal("off-hand-x");
        ViewmodelState.offHandY = (float) decimal("off-hand-y");
        ViewmodelState.offHandRotX = (float) decimal("off-hand-rot-x");
        ViewmodelState.offHandRotY = (float) decimal("off-hand-rot-y");
        ViewmodelState.offHandRotZ = (float) decimal("off-hand-rot-z");

        ViewmodelState.swingDuration = integer("swing-duration");
        ViewmodelState.blockAnim = "Pushdown".equals(choice("blocking-animation")) ? 1 : 0;
        ViewmodelState.oneSevenY = (float) decimal("one-seven-y");
        ViewmodelState.oneSevenSwingScale = (float) decimal("one-seven-swing-scale");

        ViewmodelState.equipOffsetOn = bool("equip-offset");
        ViewmodelState.ignoreBlocking = bool("ignore-blocking");
        ViewmodelState.ignorePlace = bool("ignore-place");
        ViewmodelState.ignoreAmount = bool("ignore-amount");

        ViewmodelState.airWalker = bool("air-walker");

        ViewmodelState.enable();
    }
}
