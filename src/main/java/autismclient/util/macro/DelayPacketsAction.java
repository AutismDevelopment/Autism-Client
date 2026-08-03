package autismclient.util.macro;

import autismclient.modules.AutismModule;
import autismclient.util.AutismPacketNamer;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismPacketRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DelayPacketsAction implements MacroAction {

    public enum DelayMode { ENABLE, DISABLE }

    public DelayMode mode           = DelayMode.ENABLE;
    public boolean   flushOnDisable = true;

    public List<String> c2sPacketNames = new ArrayList<>();

    public List<String> s2cPacketNames = new ArrayList<>();

    private boolean enabled = true;

    public DelayPacketsAction() {}

    public void cycleMode() {
        mode = DelayMode.values()[(mode.ordinal() + 1) % DelayMode.values().length];
    }

    public void cycleModeBackwards() {
        mode = DelayMode.values()[(mode.ordinal() - 1 + DelayMode.values().length) % DelayMode.values().length];
    }

    @SuppressWarnings("unchecked")
    public void applyDefaultPreset() {
        c2sPacketNames.clear();
        s2cPacketNames.clear();

        String[] defaultNames = {
            "ServerboundContainerClickPacket", "ServerboundContainerButtonClickPacket",
            "ServerboundSetCreativeModeSlotPacket", "ServerboundPlayerActionPacket",
            "ServerboundUseItemPacket"
        };
        for (String target : defaultNames) {
            String targetNoSuffix = target.endsWith("Packet") ? target.substring(0, target.length() - 6) : target;
            for (Class<?> clz : AutismPacketRegistry.getC2SPackets()) {
                String registryName = AutismPacketRegistry.getName((Class<? extends Packet<?>>) clz);
                if (registryName != null && (registryName.equals(target) || registryName.equals(targetNoSuffix))) {
                    c2sPacketNames.add(AutismPacketNamer.getFriendlyName((Class<? extends Packet<?>>) clz));
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void applyModulePreset() {
        c2sPacketNames.clear();
        s2cPacketNames.clear();
        AutismSharedState shared = AutismSharedState.get();
        for (Class<?> clz : shared.getC2SPackets())
            c2sPacketNames.add(AutismPacketNamer.getFriendlyName((Class<? extends Packet<?>>) clz));
        for (Class<?> clz : shared.getS2CPackets())
            s2cPacketNames.add(AutismPacketNamer.getFriendlyName((Class<? extends Packet<?>>) clz));
    }

    @Override
    public void execute(Minecraft mc) {
        AutismSharedState shared = AutismSharedState.get();
        AutismModule module = AutismModule.get();
        boolean shouldEnable = (mode == DelayMode.ENABLE);
        int flushed = 0;

        if (!shouldEnable && flushOnDisable && mc.getConnection() != null) {
            flushed = shared.flushDelayedPackets(mc.getConnection());
        }

        if (c2sPacketNames.isEmpty() && s2cPacketNames.isEmpty()) {
            shared.setC2SPackets(Set.of());
            shared.setS2CPackets(Set.of());
            shared.setUseCustomPackets(false);
            shared.setDelayGuiPackets(false);
            module.notifyDelayPacketsUiResult(false, flushed);
            return;
        }

        Set<Class<? extends Packet<?>>> resolvedC2S = resolvePackets(c2sPacketNames, true);
        Set<Class<? extends Packet<?>>> resolvedS2C = resolvePackets(s2cPacketNames, false);
        shared.setC2SPackets(resolvedC2S);
        shared.setS2CPackets(resolvedS2C);
        shared.setUseCustomPackets(true);
        shared.setDelayGuiPackets(shouldEnable);
        module.notifyDelayPacketsUiResult(shouldEnable, flushed);
    }

    @SuppressWarnings("unchecked")
    private Set<Class<? extends Packet<?>>> resolvePackets(List<String> names, boolean isC2S) {
        Set<Class<? extends Packet<?>>> result = new HashSet<>();
        Set<Class<? extends Packet<?>>> pool = isC2S
                ? (Set<Class<? extends Packet<?>>>) (Set<?>) AutismPacketRegistry.getC2SPackets()
                : (Set<Class<? extends Packet<?>>>) (Set<?>) AutismPacketRegistry.getS2CPackets();
        for (String name : names) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;

            String withSuffix = trimmed.endsWith("Packet") ? trimmed : trimmed + "Packet";
            for (Class<? extends Packet<?>> clazz : pool) {
                String friendlyName = AutismPacketNamer.getFriendlyName(clazz);
                if (friendlyName.equalsIgnoreCase(trimmed) || friendlyName.equalsIgnoreCase(withSuffix)
                        || clazz.getSimpleName().equalsIgnoreCase(trimmed)
                        || clazz.getSimpleName().equalsIgnoreCase(withSuffix)) {
                    result.add(clazz);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public MacroActionType getType() { return MacroActionType.DELAY_PACKETS; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "DELAY_PACKETS");
        tag.putString("mode", mode.name());
        tag.putBoolean("flushOnDisable", flushOnDisable);

        ListTag c2sList = new ListTag();
        for (String n : c2sPacketNames) c2sList.add(StringTag.valueOf(n));
        tag.put("c2sPackets", c2sList);

        ListTag s2cList = new ListTag();
        for (String n : s2cPacketNames) s2cList.add(StringTag.valueOf(n));
        tag.put("s2cPackets", s2cList);

        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("mode")) {
            try { mode = DelayMode.valueOf(tag.getStringOr("mode", "ENABLE")); }
            catch (IllegalArgumentException ignored) { mode = DelayMode.ENABLE; }
        }
        flushOnDisable = tag.getBooleanOr("flushOnDisable", true);

        c2sPacketNames.clear();
        if (tag.contains("c2sPackets")) {
            ListTag c2sList = (ListTag) tag.get("c2sPackets");
            if (c2sList != null) for (int i = 0; i < c2sList.size(); i++) c2sPacketNames.add(c2sList.getString(i).orElse(""));
        }
        s2cPacketNames.clear();
        if (tag.contains("s2cPackets")) {
            ListTag s2cList = (ListTag) tag.get("s2cPackets");
            if (s2cList != null) for (int i = 0; i < s2cList.size(); i++) s2cPacketNames.add(s2cList.getString(i).orElse(""));
        }
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        String base = mode == DelayMode.ENABLE ? "Delay Pkts: ON" : "Delay Pkts: OFF";
        int total = c2sPacketNames.size() + s2cPacketNames.size();
        if (mode == DelayMode.ENABLE && total > 0) base += " (" + total + " pkts)";
        return base;
    }

    @Override public String getIcon()           { return "D"; }
    @Override public boolean isEnabled()        { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
