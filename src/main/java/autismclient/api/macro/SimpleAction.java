package autismclient.api.macro;

import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public final class SimpleAction extends AddonAction {
    private final String displayName;
    private final String icon;
    private final Consumer<Minecraft> runner;

    public SimpleAction(String typeId, String displayName, String icon, Consumer<Minecraft> runner) {
        super(typeId);
        this.displayName = displayName == null || displayName.isBlank() ? typeId : displayName;
        this.icon = icon == null || icon.isBlank() ? "*" : icon;
        this.runner = runner;
    }

    @Override
    protected void run(Minecraft mc) {
        if (runner != null) runner.accept(mc);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getIcon() {
        return icon;
    }
}
