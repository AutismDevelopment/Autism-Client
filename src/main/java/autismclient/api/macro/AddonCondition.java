package autismclient.api.macro;

import net.minecraft.client.Minecraft;

public abstract class AddonCondition {

    public abstract boolean check(Minecraft mc);

    public void onComplete() {}
}
