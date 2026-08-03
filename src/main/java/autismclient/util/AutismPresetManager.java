package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.modules.AutismModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.protocol.Packet;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AutismPresetManager {
    private static final File PRESETS_FOLDER = new File(AutismClientAddon.FOLDER, "presets");
    private static final AutismPresetManager INSTANCE = new AutismPresetManager();
    private static final String USER_PRESET_PREFIX = "User Preset ";
    private static final Map<String, String> LEGACY_PACKET_NAME_ALIASES = createLegacyPacketNameAliases();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private List<PresetEntry> cachedBuiltInPresetEntries = null;
    private List<PresetEntry> cachedUserPresetEntries = null;
    private String cachedUserPresetSignature = "";

    public record PresetEntry(AutismPacketPreset preset, boolean builtIn) {
        public String name() {
            return preset != null && preset.name != null ? preset.name : "";
        }

        public int c2sCount() {
            return preset != null && preset.c2sPackets != null ? preset.c2sPackets.size() : 0;
        }

        public int s2cCount() {
            return preset != null && preset.s2cPackets != null ? preset.s2cPackets.size() : 0;
        }

        public boolean deletable() {
            return !builtIn;
        }
    }

    private AutismPresetManager() {
        if (!PRESETS_FOLDER.exists()) {
            PRESETS_FOLDER.mkdirs();
        }
    }

    public static AutismPresetManager get() {
        return INSTANCE;
    }

    public boolean savePreset(String name) {
        String sanitized = sanitizePresetName(name);
        if (sanitized == null) {
            AutismClientMessaging.sendPrefixed("§ePreset name cannot be empty.");
            return false;
        }
        if (isReservedPresetName(sanitized)) {
            AutismClientMessaging.sendPrefixed("§cThat preset name is reserved for developer templates.");
            return false;
        }

        savePresetObject(captureCurrentPreset(sanitized), true);
        invalidateUserPresetCache();
        return true;
    }

    public String saveCurrentAsUserPreset() {
        String name = nextAvailableUserPresetName();
        savePresetObject(captureCurrentPreset(name), true);
        invalidateUserPresetCache();
        return name;
    }

    public String savePacketCancellerPreset(Set<String> c2sPackets, Set<String> s2cPackets) {
        String name = nextAvailableUserPresetName();
        savePresetObject(new AutismPacketPreset(name,
            c2sPackets == null ? new LinkedHashSet<>() : new LinkedHashSet<>(c2sPackets),
            s2cPackets == null ? new LinkedHashSet<>() : new LinkedHashSet<>(s2cPackets)), true);
        invalidateUserPresetCache();
        return name;
    }

    public boolean overwriteUserPreset(String name) {
        String sanitized = sanitizePresetName(name);
        if (sanitized == null) {
            AutismClientMessaging.sendPrefixed("§eSelect a user preset first.");
            return false;
        }
        if (isReservedPresetName(sanitized)) {
            AutismClientMessaging.sendPrefixed("§cDeveloper presets cannot be overwritten.");
            return false;
        }

        File file = new File(PRESETS_FOLDER, sanitized + ".json");
        if (!file.exists()) {
            AutismClientMessaging.sendPrefixed("§cUser preset not found: " + sanitized);
            return false;
        }

        savePresetObject(captureCurrentPreset(sanitized), true);
        invalidateUserPresetCache();
        return true;
    }

    public boolean loadPreset(String name) {
        PresetEntry entry = getPresetEntry(name);
        if (entry == null) {
            AutismClientMessaging.sendPrefixed("§cPreset not found: " + name);
            return false;
        }

        applyPreset(entry.preset());
        AutismClientMessaging.sendPrefixed("§aLoaded preset: " + entry.name());
        return true;
    }

    public boolean deleteUserPreset(String name) {
        String sanitized = sanitizePresetName(name);
        if (sanitized == null) {
            AutismClientMessaging.sendPrefixed("§eSelect a user preset first.");
            return false;
        }
        if (isReservedPresetName(sanitized)) {
            AutismClientMessaging.sendPrefixed("§cDeveloper presets cannot be deleted.");
            return false;
        }

        File file = new File(PRESETS_FOLDER, sanitized + ".json");
        if (!file.exists()) {
            AutismClientMessaging.sendPrefixed("§cUser preset not found: " + sanitized);
            return false;
        }
        if (!file.delete()) {
            AutismClientMessaging.sendPrefixed("§cFailed to delete preset: " + sanitized);
            return false;
        }

        invalidateUserPresetCache();
        AutismClientMessaging.sendPrefixed("§aDeleted preset: " + sanitized);
        return true;
    }

    public boolean importSharedPreset(String presetName, String presetJson, String sender) {
        String sanitized = sanitizePresetName(presetName);
        if (sanitized == null || presetJson == null || presetJson.isBlank()) return false;
        if (isReservedPresetName(sanitized)) sanitized = sanitized + " (LAN)";

        try {
            AutismPacketPreset preset = gson.fromJson(presetJson, AutismPacketPreset.class);
            if (preset == null) return false;
            preset.name = createUniquePresetName(sanitized);
            savePresetObject(preset, false);
            invalidateUserPresetCache();
            AutismClientMessaging.sendPrefixed("§aReceived preset from " + sender + ": " + preset.name);
            return true;
        } catch (Exception e) {
            AutismClientAddon.LOG.error("Failed to import shared preset", e);
            AutismClientMessaging.sendPrefixed("§cFailed to import preset: " + e.getMessage());
            return false;
        }
    }

    public boolean isReservedPresetName(String name) {
        String normalized = normalizePresetName(name);
        if (normalized.isEmpty()) return false;
        for (PresetEntry entry : getBuiltInPresetEntries()) {
            if (normalizePresetName(entry.name()).equals(normalized)) return true;
        }
        return false;
    }

    public PresetEntry getPresetEntry(String name) {
        String normalized = normalizePresetName(name);
        if (normalized.isEmpty()) return null;

        for (PresetEntry entry : getBuiltInPresetEntries()) {
            if (normalizePresetName(entry.name()).equals(normalized)) return entry;
        }
        for (PresetEntry entry : getUserPresetEntries()) {
            if (normalizePresetName(entry.name()).equals(normalized)) return entry;
        }
        return null;
    }

    public List<PresetEntry> getBuiltInPresetEntries() {
        if (cachedBuiltInPresetEntries != null) return cachedBuiltInPresetEntries;

        AutismModule module = AutismModule.get();
        List<PresetEntry> entries = new ArrayList<>();
        entries.add(new PresetEntry(
            presetFromClasses("Default", module.defaultC2SPackets(), module.defaultS2CPackets()),
            true
        ));
        entries.add(new PresetEntry(
            presetFromPacketNames("Movement & World",
                Set.of(
                    "ServerboundSwingPacket",
                    "ServerboundPlayerActionPacket",
                    "ServerboundUseItemOnPacket",
                    "ServerboundInteractPacket",
                    "ServerboundUseItemPacket",
                    "ServerboundMovePlayerPacket.Pos",
                    "ServerboundMovePlayerPacket.PosRot",
                    "ServerboundMovePlayerPacket.Rot",
                    "ServerboundMovePlayerPacket.StatusOnly",
                    "ServerboundAcceptTeleportationPacket",
                    "ServerboundMoveVehiclePacket"
                ),
                Set.of(
                    "ClientboundBlockEventPacket",
                    "ClientboundBlockUpdatePacket",
                    "ClientboundEntityPositionSyncPacket",
                    "ClientboundSetEntityMotionPacket",
                    "ClientboundPlayerPositionPacket",
                    "ClientboundPlayerRotationPacket",
                    "ClientboundMoveVehiclePacket",
                    "ClientboundLevelEventPacket"
                )
            ),
            true
        ));
        entries.add(new PresetEntry(
            presetFromPacketNames("Inventory, Chat & Commands",
                Set.of(
                    "ServerboundContainerButtonClickPacket",
                    "ServerboundChatPacket",
                    "ServerboundChatCommandPacket",
                    "ServerboundPlaceRecipePacket",
                    "ServerboundContainerClickPacket",
                    "ServerboundSetCreativeModeSlotPacket",
                    "ServerboundPickItemFromBlockPacket",
                    "ServerboundPickItemFromEntityPacket",
                    "ServerboundPlayerActionPacket",
                    "ServerboundRecipeBookChangeSettingsPacket",
                    "ServerboundRecipeBookSeenRecipePacket",
                    "ServerboundRenameItemPacket",
                    "ServerboundCommandSuggestionPacket",
                    "ServerboundChatCommandSignedPacket",
                    "ServerboundSignUpdatePacket"
                ),
                Set.of(
                    "ClientboundPlayerChatPacket",
                    "ClientboundContainerClosePacket",
                    "ClientboundCommandSuggestionsPacket",
                    "ClientboundCommandsPacket",
                    "ClientboundSystemChatPacket",
                    "ClientboundContainerSetContentPacket",
                    "ClientboundMountScreenOpenPacket",
                    "ClientboundOpenScreenPacket",
                    "ClientboundContainerSetDataPacket",
                    "ClientboundContainerSetSlotPacket",
                    "ClientboundSetCursorItemPacket",
                    "ClientboundMerchantOffersPacket"
                )
            ),
            true
        ));
        entries.add(new PresetEntry(
            presetFromPacketNames("Chunk Freeze & Blocks",
                Set.of(
                    "ServerboundChunkBatchReceivedPacket",
                    "ServerboundClientCommandPacket",
                    "ServerboundSwingPacket",
                    "ServerboundPlayerActionPacket",
                    "ServerboundPlayerInputPacket",
                    "ServerboundUseItemOnPacket",
                    "ServerboundUseItemPacket",
                    "ServerboundMovePlayerPacket.PosRot"
                ),
                Set.of(
                    "ClientboundBlockEventPacket",
                    "ClientboundBlockUpdatePacket",
                    "ClientboundLevelChunkWithLightPacket",
                    "ClientboundSectionBlocksUpdatePacket",
                    "ClientboundSetChunkCacheRadiusPacket",
                    "ClientboundSetChunkCacheCenterPacket",
                    "ClientboundGameEventPacket",
                    "ClientboundLightUpdatePacket",
                    "ClientboundChunkBatchStartPacket",
                    "ClientboundForgetLevelChunkPacket",
                    "ClientboundLevelEventPacket"
                )
            ),
            true
        ));
        cachedBuiltInPresetEntries = List.copyOf(entries);
        return cachedBuiltInPresetEntries;
    }

    public List<PresetEntry> getUserPresetEntries() {
        String signature = userPresetFilesSignature();
        if (cachedUserPresetEntries != null && cachedUserPresetSignature.equals(signature)) {
            return cachedUserPresetEntries;
        }

        List<PresetEntry> entries = new ArrayList<>();
        File[] files = PRESETS_FOLDER.listFiles((dir, fileName) -> fileName.endsWith(".json"));
        if (files == null) {
            cachedUserPresetSignature = signature;
            cachedUserPresetEntries = List.of();
            return cachedUserPresetEntries;
        }

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                AutismPacketPreset preset = gson.fromJson(reader, AutismPacketPreset.class);
                if (preset == null) continue;
                if (preset.name == null || preset.name.isBlank()) {
                    preset.name = file.getName().replace(".json", "");
                }
                if (isReservedPresetName(preset.name)) {
                    AutismClientAddon.LOG.warn("Ignoring user preset with reserved built-in name: {}", preset.name);
                    continue;
                }
                if (preset.c2sPackets == null) preset.c2sPackets = new LinkedHashSet<>();
                if (preset.s2cPackets == null) preset.s2cPackets = new LinkedHashSet<>();
                entries.add(new PresetEntry(preset, false));
            } catch (IOException e) {
                AutismClientAddon.LOG.error("Failed to read preset file {}", file.getName(), e);
            }
        }

        entries.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        cachedUserPresetSignature = signature;
        cachedUserPresetEntries = List.copyOf(entries);
        return cachedUserPresetEntries;
    }

    public List<String> getPresetNames() {
        List<String> names = new ArrayList<>();
        for (PresetEntry entry : getBuiltInPresetEntries()) names.add(entry.name());
        for (PresetEntry entry : getUserPresetEntries()) names.add(entry.name());
        return names;
    }

    private AutismPacketPreset captureCurrentPreset(String name) {
        AutismSharedState shared = AutismSharedState.get();
        Set<String> c2s = encodePacketNames(shared.getC2SPackets());
        Set<String> s2c = encodePacketNames(shared.getS2CPackets());
        return new AutismPacketPreset(name, c2s, s2c);
    }

    private AutismPacketPreset presetFromClasses(String name,
                                                   Collection<Class<? extends Packet<?>>> c2sPackets,
                                                   Collection<Class<? extends Packet<?>>> s2cPackets) {
        return new AutismPacketPreset(name, encodePacketNames(c2sPackets), encodePacketNames(s2cPackets));
    }

    private AutismPacketPreset presetFromPacketNames(String name, Set<String> c2sNames, Set<String> s2cNames) {
        return new AutismPacketPreset(
            name,
            resolvePacketNames(c2sNames, true).stream().map(this::encodePacketName).collect(Collectors.toCollection(LinkedHashSet::new)),
            resolvePacketNames(s2cNames, false).stream().map(this::encodePacketName).collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }

    private Set<String> encodePacketNames(Collection<Class<? extends Packet<?>>> packets) {
        return packets.stream()
            .map(this::encodePacketName)
            .filter(packetName -> packetName != null && !packetName.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String encodePacketName(Class<? extends Packet<?>> packetClass) {
        String name = AutismPacketRegistry.getName(packetClass);
        return name != null ? name : packetClass.getName();
    }

    @SuppressWarnings("unchecked")
    private Set<Class<? extends Packet<?>>> resolvePacketNames(Collection<String> names, boolean c2s) {
        Set<Class<? extends Packet<?>>> resolved = new LinkedHashSet<>();
        Set<Class<? extends Packet<?>>> pool = c2s ? AutismPacketRegistry.getC2SPackets() : AutismPacketRegistry.getS2CPackets();
        for (String packetName : names) {
            if (packetName == null || packetName.isBlank()) continue;

            String resolvedName = normalizePresetPacketName(packetName);
            Class<? extends Packet<?>> packetClass = AutismPacketRegistry.getPacket(resolvedName);
            if (packetClass == null) {
                packetClass = AutismPacketRegistry.getPacket(AutismPacketNamer.getFriendlyName(resolvedName));
            }

            if (packetClass != null && pool.contains(packetClass)) {
                resolved.add(packetClass);
            } else {
                AutismClientAddon.LOG.warn("Unknown packet name in preset: {}", packetName);
            }
        }
        return resolved;
    }

    private void savePresetObject(AutismPacketPreset preset, boolean notify) {
        String safeName = sanitizePresetName(preset.name);
        if (safeName == null) {
            AutismClientMessaging.sendPrefixed("§cInvalid preset name.");
            return;
        }
        File file = new File(PRESETS_FOLDER, safeName + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(preset, writer);
            if (notify) AutismClientMessaging.sendPrefixed("§aSaved preset: " + preset.name);
        } catch (IOException e) {
            AutismClientAddon.LOG.error("Failed to save preset", e);
            AutismClientMessaging.sendPrefixed("§cFailed to save preset: " + e.getMessage());
        }
    }

    private void applyPreset(AutismPacketPreset preset) {
        AutismModule module = AutismModule.get();
        module.setC2SPackets(resolvePacketNames(preset.c2sPackets, true));
        module.setS2CPackets(resolvePacketNames(preset.s2cPackets, false));
    }

    private String nextAvailableUserPresetName() {
        int index = 1;
        while (true) {
            String candidate = USER_PRESET_PREFIX + index;
            if (getPresetEntry(candidate) == null) return candidate;
            index++;
        }
    }

    private String createUniquePresetName(String preferredName) {
        String baseName = sanitizePresetName(preferredName);
        if (baseName == null) baseName = USER_PRESET_PREFIX + "LAN";
        String candidate = baseName;
        int suffix = 1;
        while (getPresetEntry(candidate) != null || isReservedPresetName(candidate)) {
            candidate = baseName + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private String sanitizePresetName(String name) {
        if (name == null) return null;
        String cleaned = name.trim()
            .replace('\\', ' ').replace('/', ' ')
            .replaceAll("[\\x00-\\x1f]", "")
            .replace("..", "")
            .replaceAll("[:*?\"<>|]", "")
            .trim();
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1).trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalizePresetName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePresetPacketName(String packetName) {
        if (packetName == null) return "";
        String trimmed = packetName.trim();
        String aliased = LEGACY_PACKET_NAME_ALIASES.get(trimmed);
        return aliased != null ? aliased : trimmed;
    }

    private void invalidateUserPresetCache() {
        cachedUserPresetEntries = null;
        cachedUserPresetSignature = "";
    }

    private String userPresetFilesSignature() {
        File[] files = PRESETS_FOLDER.listFiles((dir, fileName) -> fileName.endsWith(".json"));
        if (files == null || files.length == 0) return "empty";

        List<File> orderedFiles = new ArrayList<>(List.of(files));
        orderedFiles.sort(Comparator.comparing(File::getName));
        StringBuilder signature = new StringBuilder();
        for (File file : orderedFiles) {
            signature.append(file.getName())
                .append(':')
                .append(file.lastModified())
                .append(':')
                .append(file.length())
                .append(';');
        }
        return signature.toString();
    }

    private static Map<String, String> createLegacyPacketNameAliases() {
        return Map.ofEntries(
            Map.entry("HandSwingC2SPacket", "ServerboundSwingPacket"),
            Map.entry("PlayerMoveC2SPacket", "ServerboundMovePlayerPacket.PosRot"),
            Map.entry("PlayerMoveC2SPacket.Full", "ServerboundMovePlayerPacket.PosRot"),
            Map.entry("PlayerMoveC2SPacket.LookAndOnGround", "ServerboundMovePlayerPacket.Rot"),
            Map.entry("PlayerMoveC2SPacket.OnGroundOnly", "ServerboundMovePlayerPacket.StatusOnly"),
            Map.entry("PlayerMoveC2SPacket.PositionAndOnGround", "ServerboundMovePlayerPacket.Pos"),
            Map.entry("TeleportConfirmC2SPacket", "ServerboundAcceptTeleportationPacket"),
            Map.entry("VehicleMoveC2SPacket", "ServerboundMoveVehiclePacket"),
            Map.entry("AcknowledgeChunksC2SPacket", "ServerboundChunkBatchReceivedPacket"),
            Map.entry("PlayerInputC2SPacket", "ServerboundPlayerInputPacket"),
            Map.entry("ChatMessageC2SPacket", "ServerboundChatPacket"),
            Map.entry("CommandExecutionC2SPacket", "ServerboundChatCommandPacket"),
            Map.entry("PickItemFromBlockC2SPacket", "ServerboundPickItemFromBlockPacket"),
            Map.entry("PickItemFromEntityC2SPacket", "ServerboundPickItemFromEntityPacket"),
            Map.entry("RecipeBookDataC2SPacket", "ServerboundRecipeBookChangeSettingsPacket"),
            Map.entry("RenameItemC2SPacket", "ServerboundRenameItemPacket"),
            Map.entry("RequestCommandCompletionsC2SPacket", "ServerboundCommandSuggestionPacket"),
            Map.entry("BlockEventS2CPacket", "ClientboundBlockEventPacket"),
            Map.entry("BlockUpdateS2CPacket", "ClientboundBlockUpdatePacket"),
            Map.entry("PlayerRotationS2CPacket", "ClientboundPlayerRotationPacket"),
            Map.entry("VehicleMoveS2CPacket", "ClientboundMoveVehiclePacket"),
            Map.entry("WorldEventS2CPacket", "ClientboundLevelEventPacket"),
            Map.entry("ChatMessageS2CPacket", "ClientboundPlayerChatPacket"),
            Map.entry("CloseScreenS2CPacket", "ClientboundContainerClosePacket"),
            Map.entry("CommandTreeS2CPacket", "ClientboundCommandsPacket"),
            Map.entry("GameMessageS2CPacket", "ClientboundSystemChatPacket"),
            Map.entry("OpenMountScreenS2CPacket", "ClientboundMountScreenOpenPacket"),
            Map.entry("AbstractContainerMenuPropertyUpdateS2CPacket", "ClientboundContainerSetDataPacket"),
            Map.entry("SetCursorItemS2CPacket", "ClientboundSetCursorItemPacket"),
            Map.entry("SetClientboundContainerSetContentPacket", "ClientboundContainerSetContentPacket"),
            Map.entry("SetTradeOffersS2CPacket", "ClientboundMerchantOffersPacket"),
            Map.entry("ChunkDeltaUpdateS2CPacket", "ClientboundSectionBlocksUpdatePacket"),
            Map.entry("ChunkLoadDistanceS2CPacket", "ClientboundSetChunkCacheRadiusPacket"),
            Map.entry("ChunkRenderDistanceCenterS2CPacket", "ClientboundSetChunkCacheCenterPacket"),
            Map.entry("ChunkSentS2CPacket", "ClientboundLevelChunkWithLightPacket"),
            Map.entry("LightUpdateS2CPacket", "ClientboundLightUpdatePacket"),
            Map.entry("StartChunkSendS2CPacket", "ClientboundChunkBatchStartPacket"),
            Map.entry("UnloadChunkS2CPacket", "ClientboundForgetLevelChunkPacket")
        );
    }
}
