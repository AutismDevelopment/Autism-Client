package autismclient.modules;
import autismclient.api.module.*;

public final class AntiBotModule extends Module {
    public AntiBotModule() {
        super("antibot", "AntiBot", ModuleCategory.MISC, "Ignores fake/bot players.");
        add(new ChoiceSetting("mode", "Mode", "Conservative", "Conservative", "Aggressive")
            .description("Detection strictness preset").build());
    }

    @Override
    public void tick() {
        AutismAntiBot.tick();
    }

    @Override
    public void onDisable() {
        AutismAntiBot.reset();
    }

    @Override
    public void onGameJoin() {
        AutismAntiBot.reset();
    }

    @Override
    public void onGameLeft() {
        AutismAntiBot.reset();
    }
}
