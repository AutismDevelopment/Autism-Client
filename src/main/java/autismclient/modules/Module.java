package autismclient.modules;

import autismclient.api.module.Setting;
import autismclient.api.module.SettingOwner;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class Module implements SettingOwner {
    protected static final Minecraft MC = Minecraft.getInstance();

    private final String id;
    private final String name;
    private ModuleCategory category;
    private final String description;
    private final List<Setting<?, ?>> settings = new ArrayList<>();
    private String replacementToggleMessage;
    private boolean addon;

    protected Module(String id, String name, ModuleCategory category, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
    }

    protected Module(String id, String name, String description) {
        this(id, name, null, description);
    }

    final void assignCategory(ModuleCategory category) {
        if (this.category == null) this.category = category;
    }

    public final String id() {
        return id;
    }

    public final boolean isAddon() {
        return addon;
    }

    final void markAddon() {
        this.addon = true;
    }

    public final String name() {
        return name;
    }

    public final ModuleCategory category() {
        return category;
    }

    public final String description() {
        return description;
    }

    public final boolean isEnabled() {
        return state().enabled;
    }

    public final void setEnabled(boolean enabled) {
        if (enabled && PackHideState.blocksEnable(this)) return;
        boolean wasEnabled = isEnabled();
        if (wasEnabled == enabled) {
            replacementToggleMessage = null;
            return;
        }
        state().enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
        ModuleRegistry.markModuleEnabledChanged();
        save();
        String message = replacementToggleMessage;
        replacementToggleMessage = null;
        if (emitsToggleMessage() && !PackHideState.isSilenced()) {
            AutismClientMessaging.sendPrefixed(message == null || message.isBlank()
                ? name + ": " + (isEnabled() ? "enabled" : "disabled")
                : message);
        }
    }

    public final void setConfiguredEnabled(boolean enabled) {
        if (enabled && PackHideState.blocksEnable(this)) return;
        boolean wasEnabled = isEnabled();
        if (wasEnabled == enabled) return;
        state().enabled = enabled;
        ModuleRegistry.recordOfflineConfiguredState(this, wasEnabled, enabled);
        ModuleRegistry.markModuleEnabledChanged();
        save();
    }

    protected final void setEnabledSilently(boolean enabled) {
        if (enabled && PackHideState.blocksEnable(this)) return;
        boolean wasEnabled = isEnabled();
        if (wasEnabled == enabled) return;
        state().enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
        ModuleRegistry.markModuleEnabledChanged();
        save();
    }

    protected final void replaceNextToggleMessage(String message) {
        replacementToggleMessage = message;
    }

    protected final void disableSilentlyWithToggleMessage(String message) {
        replaceNextToggleMessage(message);
        setEnabledSilently(false);
    }

    protected final void disableWithToggleMessage(String message) {
        replaceNextToggleMessage(message);
        setEnabled(false);
    }

    public final void toggle() {
        setEnabled(!isEnabled());
    }

    protected int defaultKeybind() {
        return -1;
    }

    public final int keybind() {
        return state().keybind;
    }

    public final void setKeybind(int keybind) {
        state().keybind = keybind;
        ModuleRegistry.markModuleSettingsChanged();
        save();
    }

    public final List<Setting<?, ?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public final List<Setting<?, ?>> visibleSettings() {
        List<Setting<?, ?>> visible = new ArrayList<>();
        for (Setting<?, ?> setting : settings) {
            if (setting.isVisible()) visible.add(setting);
        }
        return visible;
    }

    protected final <T, S extends Setting<T, S>> S add(S setting) {
        if (setting == null) throw new IllegalArgumentException("Module setting cannot be null");
        if (setting.id() == null || setting.id().isBlank()) {
            throw new IllegalArgumentException("Module setting id cannot be blank for " + id);
        }
        if (setting(setting.id()) != null) {
            throw new IllegalStateException("Duplicate setting '" + setting.id() + "' in module '" + id + "'");
        }
        setting.attach(this);
        settings.add(setting);
        state().settings.putIfAbsent(setting.id(), setting.defaultValue());
        ModuleRegistry.markModuleSettingsChanged();
        return setting;
    }

    public String info() {
        return "";
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void tick() {
    }

    public boolean ticksWhenDisabled() {
        return false;
    }

    public boolean hasDisabledTickWork() {
        return ticksWhenDisabled();
    }

    public boolean opensSettingsOnClick() {
        return false;
    }

    public boolean hasActivationToggle() {
        return true;
    }

    public boolean holdToActivate() {
        return false;
    }

    public boolean showInModuleMenu() {
        return true;
    }

    public boolean settingsShareable() {
        return true;
    }

    public boolean showInArrayList() {
        return true;
    }

    public boolean emitsToggleMessage() {
        return true;
    }

    public void preMovementTick() {
    }

    public void onNetworkMovementTickPre() {
    }

    public void onPacketProcessFrame() {
    }

    public boolean shouldCancelPlayerTick() {
        return false;
    }

    public void onRenderLevel(float partialTick) {
    }

    public void onMouseRotation(double deltaYaw, double deltaPitch) {
    }

    public Vec3 onPlayerMove(MoverType type, Vec3 movement) {
        return movement;
    }

    public boolean shouldApplySpeedTimer() {
        return false;
    }

    public float speedTimerMultiplier() {
        return 1.0f;
    }

    public void onGameJoin() {
    }

    public void onGameLeft() {
    }

    public boolean onPacketSend(Packet<?> packet) {
        return false;
    }

    public boolean onPacketReceive(Packet<?> packet) {
        return false;
    }

    public void onSoundPacket(ClientboundSoundPacket packet) {
    }

    public void appendTooltip(ItemStack stack, List<?> lines) {
    }

    public boolean shouldCancelAttack(HitResult hitResult) {
        return false;
    }

    public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
        return false;
    }

    public void onStartBreakingBlock(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
    }

    public boolean onStartDestroyBlock(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        return false;
    }

    public void onBlockBreakingProgress(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
    }

    public boolean shouldCancelStartBreakingBlock(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        return false;
    }

    public boolean shouldTraceEntity(Entity entity) {
        return false;
    }

    public int traceColor(Entity entity) {
        return 0x80FFFFFF;
    }

    protected final boolean bool(String settingId) {
        return Boolean.parseBoolean(value(settingId));
    }

    protected final int integer(String settingId) {
        try {
            return Integer.parseInt(value(settingId));
        } catch (NumberFormatException ignored) {
            Setting<?, ?> setting = setting(settingId);
            try {
                return setting == null ? 0 : Integer.parseInt(setting.defaultValue());
            } catch (NumberFormatException stillBad) {
                return 0;
            }
        }
    }

    protected final double decimal(String settingId) {
        try {
            return Double.parseDouble(value(settingId));
        } catch (NumberFormatException ignored) {
            Setting<?, ?> setting = setting(settingId);
            try {
                return setting == null ? 0.0 : Double.parseDouble(setting.defaultValue());
            } catch (NumberFormatException stillBad) {
                return 0.0;
            }
        }
    }

    protected final String text(String settingId) {
        return value(settingId);
    }

    protected final String choice(String settingId) {
        return value(settingId);
    }

    protected final List<String> list(String settingId) {
        String value = value(settingId);
        if (value == null || value.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String raw : value.split("\\|")) {
            String item = raw.trim();
            if (!item.isEmpty()) out.add(item);
        }
        return out;
    }

    public final String value(String settingId) {
        String external = externalSettingValue(settingId);
        if (external != null) return external;
        Setting<?, ?> setting = setting(settingId);
        String fallback = setting == null ? "" : setting.defaultValue();
        return state().settings.getOrDefault(settingId, fallback);
    }

    @Override
    public final String settingValue(String name) {
        String external = externalSettingValue(name);
        if (external != null) return external;
        return state().settings.get(name);
    }

    @Override
    public final void putSettingValue(String name, String value) {
        setValue(name, value);
    }

    public final void setValue(String settingId, String value) {
        setValueTransient(settingId, value);
        save();
    }

    public final void setValueTransient(String settingId, String value) {
        String sanitized = sanitize(setting(settingId), value);
        if (!setExternalSettingValue(settingId, sanitized)) state().settings.put(settingId, sanitized);
        onOptionValueChanged(settingId);
        ModuleRegistry.markModuleSettingsChanged();
    }

    public final void setConfiguredValue(String settingId, String value) {
        setConfiguredValueTransient(settingId, value);
        save();
    }

    public final void setConfiguredValueTransient(String settingId, String value) {
        String sanitized = sanitize(setting(settingId), value);
        if (!setExternalSettingValue(settingId, sanitized)) state().settings.put(settingId, sanitized);
        ModuleRegistry.recordOfflineConfiguredOption(this, settingId, false);
        ModuleRegistry.markModuleSettingsChanged();
    }

    public final void persistConfiguredState() {
        save();
    }

    public final void resetValue(String settingId) {
        Setting<?, ?> setting = setting(settingId);
        if (setting == null) return;
        if (!setExternalSettingValue(settingId, setting.defaultValue())) {
            state().settings.put(settingId, setting.defaultValue());
        }
        onOptionValueChanged(settingId);
        ModuleRegistry.markModuleSettingsChanged();
        save();
    }

    public final void resetConfiguredValue(String settingId) {
        Setting<?, ?> setting = setting(settingId);
        if (setting == null) return;
        if (!setExternalSettingValue(settingId, setting.defaultValue())) {
            state().settings.put(settingId, setting.defaultValue());
        }
        ModuleRegistry.recordOfflineConfiguredOption(this, settingId, false);
        ModuleRegistry.markModuleSettingsChanged();
        save();
    }

    public final void resetSettings() {
        for (Setting<?, ?> setting : settings) {

            if (setting.isKeptOnReset()) continue;
            if (!setExternalSettingValue(setting.id(), setting.defaultValue())) {
                state().settings.put(setting.id(), setting.defaultValue());
            }
        }
        onSettingsReset();
        ModuleRegistry.markModuleSettingsChanged();
        save();
    }

    public final void resetConfiguredSettings() {
        for (Setting<?, ?> setting : settings) {

            if (setting.isKeptOnReset()) continue;
            if (!setExternalSettingValue(setting.id(), setting.defaultValue())) {
                state().settings.put(setting.id(), setting.defaultValue());
            }
        }
        ModuleRegistry.recordOfflineConfiguredOption(this, "", true);
        ModuleRegistry.markModuleSettingsChanged();
        save();
    }

    final void applyConfiguredSettings(Set<String> settingIds, boolean reset) {
        if (reset) onSettingsReset();
        if (settingIds == null) return;
        for (String settingId : settingIds) {
            if (settingId != null && !settingId.isBlank()) onOptionValueChanged(settingId);
        }
    }

    protected void onSettingsReset() {
    }

    protected void onOptionValueChanged(String settingId) {
    }

    protected String externalSettingValue(String settingId) {
        return null;
    }

    protected boolean setExternalSettingValue(String settingId, String value) {
        return false;
    }

    public final void adjustOption(Setting<?, ?> setting, int direction) {
        if (setting == null) return;
        switch (setting.kind()) {
            case BOOLEAN -> setValue(setting.id(), Boolean.toString(!bool(setting.id())));
            case INTEGER -> {
                int value = integer(setting.id());
                int adjusted = (int) clamp(value + (int) setting.step() * direction, setting.min(), setting.max());
                setValue(setting.id(), Integer.toString(adjusted));
            }
            case DOUBLE -> {
                double value = decimal(setting.id());
                double adjusted = clamp(value + setting.step() * direction, setting.min(), setting.max());
                setValue(setting.id(), String.format(Locale.ROOT, "%.2f", adjusted));
            }
            case ENUM -> {
                List<String> choices = setting.choices();
                if (!choices.isEmpty()) {
                    int index = choices.indexOf(value(setting.id()));
                    if (index < 0) index = 0;
                    int next = Math.floorMod(index + direction, choices.size());
                    setValue(setting.id(), choices.get(next));
                }
            }
            case ACTION -> {
                if (setting.action() != null) setting.action().run();
            }
            default -> {
            }
        }
    }

    public final void adjustConfiguredOption(Setting<?, ?> setting, int direction) {
        if (setting == null) return;
        switch (setting.kind()) {
            case BOOLEAN -> setConfiguredValue(setting.id(), Boolean.toString(!bool(setting.id())));
            case INTEGER -> {
                int adjusted = (int) clamp(integer(setting.id()) + (int) setting.step() * direction, setting.min(), setting.max());
                setConfiguredValue(setting.id(), Integer.toString(adjusted));
            }
            case DOUBLE -> {
                double adjusted = clamp(decimal(setting.id()) + setting.step() * direction, setting.min(), setting.max());
                setConfiguredValue(setting.id(), String.format(Locale.ROOT, "%.2f", adjusted));
            }
            case ENUM -> {
                List<String> choices = setting.choices();
                if (!choices.isEmpty()) {
                    int index = Math.max(0, choices.indexOf(value(setting.id())));
                    setConfiguredValue(setting.id(), choices.get(Math.floorMod(index + direction, choices.size())));
                }
            }
            case ACTION -> {
                if (setting.isAvailable(false, false) && setting.action() != null) setting.action().run();
            }
            default -> {
            }
        }
    }

    public final String displayValue(Setting<?, ?> setting) {
        if (setting == null) return "";
        String override = displayValueOverride(setting);
        if (override != null) return override;
        return switch (setting.kind()) {
            case BOOLEAN -> bool(setting.id()) ? "ON" : "OFF";
            case ACTION -> "RUN";
            default -> setting.format(value(setting.id()));
        };
    }

    protected String displayValueOverride(Setting<?, ?> setting) {
        return null;
    }

    private static String sanitize(Setting<?, ?> setting, String value) {
        if (setting == null) return value == null ? "" : value;
        return setting.sanitizeUiString(value);
    }

    public final Setting<?, ?> setting(String settingId) {
        for (Setting<?, ?> setting : settings) {
            if (setting.id().equals(settingId)) return setting;
        }
        return null;
    }

    protected final boolean isAdminContext() {
        return MC != null && MC.player != null && MC.player.canUseGameMasterBlocks();
    }

    protected final boolean isCreativeContext() {
        return MC != null && MC.player != null && MC.player.hasInfiniteMaterials();
    }

    protected final void sendCommand(String command) {
        if (PackHideState.isHardLocked()) return;
        if (MC == null || MC.getConnection() == null || command == null || command.isBlank()) return;
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        MC.getConnection().sendCommand(normalized);
    }

    private AutismConfig.ModuleState state() {
        AutismConfig config = AutismConfig.getGlobal();
        return config.modules.computeIfAbsent(id, ignored -> {
            AutismConfig.ModuleState created = new AutismConfig.ModuleState();
            created.keybind = defaultKeybind();
            return created;
        });
    }

    private void save() {
        AutismConfig.getGlobal().save();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
