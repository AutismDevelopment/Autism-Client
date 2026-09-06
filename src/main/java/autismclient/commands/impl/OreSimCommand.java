package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.modules.Module;
import autismclient.modules.ModuleOreSim;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismWaypoints;
import autismclient.util.oresim.AutismOreSimEngine;
import autismclient.util.oresim.AutismOreSimSeedInput;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

public class OreSimCommand extends Command {

    private static final String G = "§7";
    private static final String W = "§f";
    private static final String R = "§c";
    private static final String Y = "§e";
    private static final String A = "§a";

    public OreSimCommand() {
        super("oresim", "Report why OreSim is or is not predicting.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            report();
            return SUCCESS;
        });
    }

    private static void report() {
        Module xray = ModuleRegistry.get("xray");
        if (xray == null) {
            AutismClientMessaging.sendPrefixed(R + "Xray module not registered.");
            return;
        }

        AutismClientMessaging.sendPrefixed(Y + "--- OreSim ---");
        AutismClientMessaging.sendPrefixed(G + "enabled: " + W + xray.isEnabled()
            + G + "  mode: " + W + xray.value("mode")
            + G + "  style: " + W + xray.value("render-style"));

        Long seed = ModuleOreSim.debugSeed(xray);
        AutismOreSimSeedInput.Status seedStatus = ModuleOreSim.seedInputStatus(xray);
        AutismClientMessaging.sendPrefixed(G + "seed: "
            + (seedStatus == AutismOreSimSeedInput.Status.INVALID ? R + "INVALID"
                : seed == null ? R + "NONE - enter a signed 64-bit value" : W + seed)
            + G + "  from: " + W + "the World Seed text field");
        AutismClientMessaging.sendPrefixed(G + "saved scope: " + W
            + AutismWaypoints.scopeKey(Minecraft.getInstance())
            + G + "; the same world/server scoping as waypoints. No world or server seed is read.");

        AutismClientMessaging.sendPrefixed(G + "ore list entries: " + W + ModuleOreSim.debugSelectionSize(xray)
            + G + "  families: " + W + Integer.bitCount(ModuleOreSim.debugEnabledMask(xray)));

        String worldgen = AutismOreSimEngine.failed() ? R + "FAILED"
            : AutismOreSimEngine.loading() ? Y + AutismOreSimEngine.status().name().toLowerCase(java.util.Locale.ROOT)
            : AutismOreSimEngine.ready() ? A + "ready" : R + AutismOreSimEngine.status().name().toLowerCase(java.util.Locale.ROOT);
        AutismClientMessaging.sendPrefixed(G + "local Minecraft 26.2 worldgen: " + worldgen);
        if (AutismOreSimEngine.failed()
            || AutismOreSimEngine.status() == AutismOreSimEngine.Status.UNVERIFIED_WORLDGEN) {
            AutismClientMessaging.sendPrefixed(R + AutismOreSimEngine.failureMessage());
        }

        AutismClientMessaging.sendPrefixed(G + "chunks simulated: " + W + AutismOreSimEngine.chunkCount()
            + G + "  positions: " + W + AutismOreSimEngine.storedPositions());

        AutismClientMessaging.sendPrefixed(G + "generation source: " + W
            + "local vanilla registries + terrain + carvers + biome decoration"
            + G + "; received server chunks are not inputs.");

        AutismClientMessaging.sendPrefixed(G + "nearby real ore: " + W + ModuleOreSim.nearDiagnostics());
        AutismClientMessaging.sendPrefixed(G + "matched = real ore within 5 blocks; touchingAir = of those,"
            + " exposed; drawn = also in line of sight.");
    }
}
