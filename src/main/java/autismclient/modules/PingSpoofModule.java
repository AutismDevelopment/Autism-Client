package autismclient.modules;
import autismclient.api.module.*;

import autismclient.util.macro.PingSpoofController;

public final class PingSpoofModule extends Module {

    public PingSpoofModule() {
        super("ping-spoof", "PingSpoof", ModuleCategory.MISC, "Adds latency to your ping by delaying keep-alives.");
        add(new IntSetting("delay", "Delay (ms)", 250, 0, 500, 25)
            .description("Latency added to ping.").build());
        add(new BoolSetting("real-incoming", "Real: Incoming", false)
            .description("Delay packets you receive.").build());
        add(new BoolSetting("real-outgoing", "Real: Outgoing", false)
            .description("Delay packets you send.").build());
    }

    @Override
    public void onEnable() {
        pushOverride();
    }

    @Override
    public void onDisable() {
        PingSpoofController.clearModuleOverride();
    }

    @Override
    protected void onOptionValueChanged(String optionId) {
        if (isEnabled()) pushOverride();
    }

    private void pushOverride() {
        PingSpoofController.setModuleOverride(integer("delay"), bool("real-incoming"), bool("real-outgoing"));
    }
}
