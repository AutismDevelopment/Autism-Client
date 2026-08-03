package autismclient.util;

import java.util.Locale;

public final class AntiVanishHeuristics {
    private AntiVanishHeuristics() {
    }

    public static boolean suspiciousSound(String id) {
        String path = path(id);

        return path.contains("chest.open") || path.contains("chest.close") || path.contains("chest.locked")
            || path.contains("barrel.open") || path.contains("barrel.close")
            || path.contains("shulker_box.open") || path.contains("shulker_box.close")
            || path.contains("door.open") || path.contains("door.close")
            || path.contains("fence_gate.open") || path.contains("fence_gate.close")
            || path.contains("button.click") || path.contains("lever.click");
    }

    public static boolean suspiciousParticle(String id) {
        String path = path(id);
        return path.contains("crit") || path.contains("enchanted_hit") || path.contains("damage_indicator")
            || path.contains("smoke") || path.equals("block");
    }

    public static boolean blockEventInteraction(String id) {
        String path = path(id);
        return path.contains("chest") || path.contains("barrel") || path.contains("shulker");
    }

    public static boolean blockStateInteraction(String id) {
        String path = path(id);
        return path.contains("door") || path.contains("trapdoor") || path.contains("button")
            || path.contains("lever") || path.contains("barrel");
    }

    public static boolean potentialInteractiveBlock(String id) {
        return blockEventInteraction(id) || blockStateInteraction(id);
    }

    public static boolean crediblePlacementTransition(String previousId, String nextId, boolean previousReplaceable) {
        String previous = path(previousId);
        String next = path(nextId);
        return previousReplaceable
            && !previous.equals(next)
            && !isAir(nextId)
            && !naturalBlockNoise(nextId);
    }

    public static boolean credibleAnonymousPlacementTransition(String previousId, String nextId) {
        return isAir(previousId)
            && stableAnonymousBlock(nextId);
    }

    public static boolean matchingPlaceSound(String expectedId, String actualId) {
        String expected = path(expectedId);
        String actual = path(actualId);
        return !expected.isBlank() && expected.endsWith(".place") && expected.equals(actual);
    }

    public static boolean sameEvidenceWindow(long firstMs, long secondMs, long maxGapMs) {
        if (maxGapMs < 0L) return false;
        long gap = firstMs >= secondMs ? firstMs - secondMs : secondMs - firstMs;
        return gap >= 0L && gap <= maxGapMs;
    }

    public static boolean matchingBreakEvidence(String eventBlockId, long eventMs,
                                                String removedBlockId, long removalMs, long maxGapMs) {
        return credibleBreakBlock(eventBlockId)
            && matchingBreakEffect(eventBlockId, eventMs, removedBlockId, removalMs, maxGapMs);
    }

    public static boolean matchingBreakEffect(String eventBlockId, long eventMs,
                                              String removedBlockId, long removalMs, long maxGapMs) {
        return eventBlockId != null && !eventBlockId.isBlank()
            && removedBlockId != null && !removedBlockId.isBlank()
            && path(eventBlockId).equals(path(removedBlockId))
            && sameEvidenceWindow(eventMs, removalMs, maxGapMs);
    }

    public static boolean credibleBreakBlock(String id) {
        return id != null && !id.isBlank() && !isAir(id)
            && !naturalBlockNoise(id);
    }

    public static boolean credibleAnonymousBreakTransition(String previousId, String nextId) {
        return isAir(nextId) && stableAnonymousBlock(previousId);
    }

    public static boolean stableAnonymousBlock(String id) {
        if (id == null || id.isBlank()) return false;
        String path = path(id);
        if (isAir(path)) return false;
        if (path.equals("dirt") || path.equals("grass_block") || path.equals("mycelium")
            || path.equals("podzol") || path.endsWith("_nylium")) return true;

        if (path.contains("sandstone") || path.equals("soul_sand") || path.equals("soul_soil")
            || path.startsWith("mossy_") || path.equals("moss_block")
            || path.equals("redstone_block") || path.equals("redstone_lamp") || path.endsWith("redstone_ore")
            || path.equals("observer") || path.equals("piston") || path.equals("sticky_piston")) return true;
        if (naturalBlockNoise(path)) return false;

        return !path.contains("pumpkin") && !path.contains("melon")
            && !path.contains("anvil") && !path.equals("dragon_egg")
            && !path.contains("scaffolding");
    }

    public static boolean isAir(String id) {
        String path = path(id);
        return path.equals("air") || path.equals("cave_air") || path.equals("void_air");
    }

    public static boolean naturalBlockNoise(String id) {
        String path = path(id);
        return path.equals("dirt")
            || path.equals("farmland") || path.equals("dirt_path")
            || path.contains("mycelium") || path.contains("podzol") || path.contains("nylium")
            || path.contains("water") || path.contains("lava") || path.contains("bubble")
            || path.contains("fire") || path.contains("leaves") || path.contains("snow")
            || path.contains("ice") || path.contains("grass") || path.contains("fern")
            || path.contains("seagrass") || path.contains("kelp") || path.contains("vine")
            || path.contains("coral") || path.contains("sculk") || path.contains("redstone")
            || path.contains("piston") || path.contains("sapling") || path.contains("mushroom")
            || path.contains("fungus") || path.contains("roots") || path.contains("sprouts")
            || path.contains("flower") || path.contains("wheat") || path.contains("carrot")
            || path.contains("potato") || path.contains("beetroot") || path.contains("stem")
            || path.contains("cane") || path.contains("cactus") || path.contains("bamboo")
            || path.contains("chorus") || path.contains("berr") || path.contains("moss")
            || path.contains("azalea") || path.contains("dripleaf") || path.contains("dripstone")
            || path.contains("torch") || path.contains("candle") || path.contains("repeater")
            || path.contains("comparator") || path.contains("observer") || path.contains("nether_wart")
            || path.contains("cocoa") || path.contains("spore") || path.contains("lichen")
            || path.contains("turtle_egg") || path.contains("sniffer_egg") || path.contains("frogspawn")
            || path.contains("amethyst_bud") || path.contains("amethyst_cluster")
            || path.contains("infested_") || path.contains("moving_piston") || path.contains("piston_head")
            || path.contains("portal") || path.contains("end_gateway")
            || path.contains("resin")
            || path.contains("sand") || path.contains("gravel") || path.contains("concrete_powder");
    }

    public static String path(String id) {
        if (id == null) return "unknown";
        int split = id.indexOf(':');
        return (split >= 0 && split + 1 < id.length() ? id.substring(split + 1) : id).toLowerCase(Locale.ROOT);
    }
}
