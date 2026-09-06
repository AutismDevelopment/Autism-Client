package autismclient.api.macro;

import net.minecraft.client.Minecraft;

import java.util.function.Predicate;

public final class SimpleCondition extends AddonContextAction {
    private final String displayName;
    private final String status;
    private final String icon;
    private final Predicate<Minecraft> predicate;

    public SimpleCondition(String typeId, String displayName, String status, String icon, Predicate<Minecraft> predicate) {
        super(typeId);
        this.displayName = displayName == null || displayName.isBlank() ? typeId : displayName;
        this.status = status == null || status.isBlank() ? this.displayName : status;
        this.icon = icon == null || icon.isBlank() ? "?" : icon;
        this.predicate = predicate;
    }

    @Override
    public void run(MacroExecutionContext ctx) {
        ctx.setStatus(status);
        ctx.awaitCondition(new AddonCondition() {
            @Override
            public boolean check(Minecraft mc) {
                return predicate != null && predicate.test(mc);
            }
        });
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
