package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

public final class FreeLookModule extends Module {
    private float cameraYaw;
    private float cameraPitch;
    private CameraType previousPerspective;

    public FreeLookModule() {
        super("free-look", "FreeLook", ModuleCategory.RENDER, "Look around without rotating the player.");
        add(new ChoiceSetting("perspective", "Perspective", "Back", "Back", "Front").build());
        add(new BoolSetting("no-pitch-limit", "No Pitch Limit", true).build());
    }

    @Override
    public boolean holdToActivate() {
        return true;
    }

    @Override
    public void onEnable() {
        if (MC.player != null) {
            cameraYaw = MC.player.getYRot();
            cameraPitch = MC.player.getXRot();
        }
        if (MC.options != null) {
            previousPerspective = MC.options.getCameraType();
            MC.options.setCameraType(desiredPerspective());
        }
    }

    @Override
    public void onDisable() {
        if (MC.options != null && previousPerspective != null) {
            MC.options.setCameraType(previousPerspective);
        }
        previousPerspective = null;
    }

    @Override
    public void onGameLeft() {

        if (isEnabled()) setEnabled(false);
    }

    @Override
    public void preMovementTick() {

        if (MC.options != null && MC.options.getCameraType() != desiredPerspective()) {
            MC.options.setCameraType(desiredPerspective());
        }
    }

    private CameraType desiredPerspective() {
        return invertedView() ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK;
    }

    private boolean invertedView() {
        return "Front".equals(choice("perspective"));
    }

    private static FreeLookModule activeInstance() {
        Module module = ModuleRegistry.get("free-look");
        return module instanceof FreeLookModule freeLook && freeLook.isEnabled() ? freeLook : null;
    }

    public static FreeLookModule lookingInstance() {
        return MC == null || MC.player == null ? null : activeInstance();
    }

    public boolean isInvertedView() {
        return invertedView();
    }

    public float cameraYaw() {
        return cameraYaw;
    }

    public float cameraPitch() {
        return cameraPitch;
    }

    public static boolean consumeMouseTurn(double deltaX, double deltaY) {
        FreeLookModule freeLook = activeInstance();
        if (freeLook == null || MC.player == null) return false;
        freeLook.cameraYaw += (float) (deltaX * 0.15D);
        freeLook.cameraPitch += (float) (deltaY * 0.15D);
        if (!freeLook.bool("no-pitch-limit")) {
            freeLook.cameraPitch = Mth.clamp(freeLook.cameraPitch, -90.0F, 90.0F);
        }
        return true;
    }
}
