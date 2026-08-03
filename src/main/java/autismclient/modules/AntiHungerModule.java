package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.mixin.accessor.AutismMovePlayerPacketAccessor;
import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class AntiHungerModule extends Module {
    private static final Minecraft MC = Minecraft.getInstance();

    public AntiHungerModule() {
        super("anti-hunger", "AntiHunger", ModuleCategory.MISC,
            "Prevents hunger from decreasing. Will flag anticheats.");
        add(new BoolSetting("no-sprint", "NoSprint", true)
            .description("Hide sprint from server."));
        add(new BoolSetting("sprint-while-swimming", "While Swimming", false)
            .description("Keep sprinting while swimming."));
        add(new BoolSetting("keep-floating", "KeepFloating", true)
            .description("Spoof onGround as false."));
    }

    boolean noSprintActive() {
        return bool("no-sprint") && (!MC.player.isSwimming() || bool("sprint-while-swimming"));
    }

    public static boolean noSprintRequested() {
        Module module = ModuleRegistry.get("anti-hunger");
        if (module instanceof AntiHungerModule antiHunger && antiHunger.isEnabled()
            && MC.player != null && antiHunger.noSprintActive()) {
            return true;
        }
        Module flight = ModuleRegistry.get("flight");
        if (flight instanceof BuiltinModules.FlightModule flightModule
            && flightModule.isEnabled() && flightModule.antiHungerToggle()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (MC.player == null || MC.getConnection() == null) return false;
        if (packet instanceof ServerboundMovePlayerPacket move) {

            if (!bool("keep-floating") || MC.player.isPassenger() || isDestroyingBlock()) return false;
            if (MC.player.isInWater() || MC.player.isSwimming() || MC.player.isUnderWater()) return false;
            if (move.isOnGround() && MC.player.fallDistance <= 0.0F) {
                ((AutismMovePlayerPacketAccessor) move).autism$setOnGround(false);
            }
        }
        return false;
    }

    private static boolean isDestroyingBlock() {
        return MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor
            && accessor.autism$isDestroying();
    }
}
