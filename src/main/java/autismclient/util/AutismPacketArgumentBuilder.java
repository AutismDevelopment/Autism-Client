package autismclient.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.LocalChatSession;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetGameRulePacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;

public final class AutismPacketArgumentBuilder {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final List<String> RAW_BODY_ARG_NAMES = List.of("raw", "rawHex", "body", "bodyHex", "hex", "bytes");
    private static final List<String> RAW_PACKET_ARG_NAMES = List.of("packet", "packetHex", "full", "fullHex", "plaintext", "packetBytes");
    private static final List<String> RAW_BASE64_ARG_NAMES = List.of("base64", "b64", "bodyBase64");
    private static final List<String> RAW_ARG_NAMES;

    static {
        List<String> names = new ArrayList<>();
        names.addAll(RAW_BODY_ARG_NAMES);
        names.addAll(RAW_PACKET_ARG_NAMES);
        names.addAll(RAW_BASE64_ARG_NAMES);
        RAW_ARG_NAMES = List.copyOf(names);
    }

    private AutismPacketArgumentBuilder() {
    }

    public static PreparedArgs prepare(String rawArgs) {
        List<String> tokens = tokenize(rawArgs == null ? "" : rawArgs);
        boolean dryRun = false;
        List<String> kept = new ArrayList<>();
        for (String token : tokens) {
            String normalized = normalizeName(token);
            int eq = token.indexOf('=');
            String key = eq >= 0 ? normalizeName(token.substring(0, eq)) : normalized;
            if (key.equals("dry") || key.equals("dryrun") || key.equals("preview") || key.equals("nosend")) {
                dryRun = eq < 0 || parseBoolean(key, token.substring(eq + 1));
                continue;
            }
            kept.add(token);
        }
        return new PreparedArgs(joinTokens(kept), dryRun);
    }

    public static Result build(Class<? extends Packet<?>> packetClass, String rawArgs) {
        if (packetClass == null) return Result.error("Packet class is missing.");
        Map<String, String> args;
        try {
            args = parseArgs(rawArgs);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        if (args.containsKey("help") || args.containsKey("?")) {
            return Result.help(help(packetClass));
        }

        Result rawCodec = tryRawCodec(packetClass, args);
        if (rawCodec != null) return rawCodec;

        Result special = trySpecial(packetClass, args);
        if (special != null) return special;

        if (args.isEmpty()) {
            Packet<?> noArg = tryNoArg(packetClass);
            if (noArg != null) return Result.ok(noArg, "no-arg");
            return Result.error("Packet needs arguments. " + help(packetClass));
        }

        if (packetClass.isRecord()) {
            Result record = tryRecord(packetClass, args);
            if (record != null) return record;
        }

        Result schema = trySchemaConstructor(packetClass, args);
        if (schema != null) return schema;

        return Result.error("Could not build " + displayName(packetClass) + ". " + help(packetClass));
    }

    public static String help(Class<? extends Packet<?>> packetClass) {
        if (packetClass == null) return "";
        AutismPacketSchemaRegistry.PacketSchema schema = AutismPacketSchemaRegistry.find(packetClass);
        List<String> fields = new ArrayList<>();
        if (schema != null) {
            for (AutismPacketSchemaRegistry.FieldSchema field : schema.fields()) {
                fields.add(field.name() + "=" + exampleFor(field.javaType(), field.valueKind()) + " (" + shortType(field.javaType(), field.valueKind()) + ")");
            }
        }
        if (fields.isEmpty()) {
            for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 0) continue;
                fields.add("ctor(" + constructor.getParameterCount() + " args)");
            }
        }
        String usage = fields.isEmpty()
            ? ".send " + displayName(packetClass)
            : ".send " + displayName(packetClass) + " " + String.join(" ", compactUsageFields(fields));
        StringBuilder out = new StringBuilder("Usage: ").append(usage);
        if (!fields.isEmpty()) {
            out.append("\nFields: ");
            out.append(String.join(", ", fields));
        }
        List<String> examples = examplesFor(packetClass);
        if (!examples.isEmpty()) {
            out.append("\nExamples: ");
            out.append(String.join(" | ", examples));
        }
        out.append("\nRaw: rawHex=<packet body>, base64=<packet body>, or packetHex=<packet id + body> decodes through Minecraft's own STREAM_CODEC.");
        out.append("\nTip: Tab completes fields and enum values. Use current for live container/player defaults where offered.");
        return out.toString();
    }

    public static CompletableFuture<Suggestions> suggest(Class<? extends Packet<?>> packetClass, SuggestionsBuilder builder) {
        if (packetClass == null) return builder.buildFuture();
        String remaining = builder.getRemaining();
        int tokenStart = lastTokenStart(remaining);
        String current = remaining.substring(tokenStart);
        SuggestionsBuilder tokenBuilder = builder.createOffset(builder.getStart() + tokenStart);
        int eq = current.indexOf('=');
        if (eq >= 0) {
            String field = current.substring(0, eq);
            String valuePrefix = current.substring(eq + 1);
            for (String value : valueSuggestions(packetClass, field)) {
                if (value.toLowerCase(Locale.ROOT).startsWith(valuePrefix.toLowerCase(Locale.ROOT))) {
                    tokenBuilder.suggest(field + "=" + quoteSuggestionValue(value));
                }
            }
            return tokenBuilder.buildFuture();
        }

        Set<String> used = parsedKeysBeforeCurrent(remaining, tokenStart);
        String currentLower = current.toLowerCase(Locale.ROOT);
        for (String field : suggestionFields(packetClass)) {
            if (used.contains(normalizeName(field))) continue;
            String suggestion = field + "=" + suggestionExample(packetClass, field);
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(currentLower) || field.toLowerCase(Locale.ROOT).startsWith(currentLower)) {
                tokenBuilder.suggest(suggestion);
            }
        }
        for (String control : List.of("dry", "preview", "noSend")) {
            if (control.toLowerCase(Locale.ROOT).startsWith(currentLower)) tokenBuilder.suggest(control);
        }
        if ("help".startsWith(currentLower)) tokenBuilder.suggest("help");
        return tokenBuilder.buildFuture();
    }

    private static Packet<?> tryNoArg(Class<? extends Packet<?>> packetClass) {
        for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != 0) continue;
            try {
                constructor.setAccessible(true);
                return (Packet<?>) constructor.newInstance();
            } catch (Throwable ignored) {  }
        }
        return null;
    }

    private static Result tryRawCodec(Class<? extends Packet<?>> packetClass, Map<String, String> args) {
        String bodyHex = firstArg(args, RAW_BODY_ARG_NAMES.toArray(new String[0]));
        String fullPacketHex = firstArg(args, RAW_PACKET_ARG_NAMES.toArray(new String[0]));
        String bodyBase64 = firstArg(args, RAW_BASE64_ARG_NAMES.toArray(new String[0]));
        int provided = (bodyHex == null ? 0 : 1) + (fullPacketHex == null ? 0 : 1) + (bodyBase64 == null ? 0 : 1);
        if (provided == 0) return null;
        if (provided > 1) return Result.error("Use only one raw packet source: rawHex, packetHex, or base64.");
        try {
            requireOnly(args, RAW_ARG_NAMES.toArray(new String[0]));
            byte[] sourceBytes;
            boolean fullPacket = fullPacketHex != null;
            if (bodyBase64 != null) {
                sourceBytes = Base64.getDecoder().decode(bodyBase64.trim());
            } else {
                sourceBytes = parseBytes(fullPacket ? fullPacketHex : bodyHex);
            }
            Packet<?> packet = fullPacket
                ? AutismPacketCodecIo.decodeFullPacket(packetClass, sourceBytes, null).packet()
                : AutismPacketCodecIo.decodeBody(packetClass, sourceBytes, null);
            return Result.ok(packet, fullPacket ? "minecraft packet STREAM_CODEC" : "minecraft body STREAM_CODEC");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Throwable t) {
            return Result.error("Minecraft codec decode failed: " + safeMessage(t));
        }
    }

    private static Result trySpecial(Class<? extends Packet<?>> packetClass, Map<String, String> args) {
        String name = packetClass.getName();
        try {
            if (packetClass == ServerboundContainerClosePacket.class) {
                requireOnly(args, "containerId", "id");
                return Result.ok(new ServerboundContainerClosePacket(intArg(args, AutismPacketArgumentBuilder::currentContainerId, "containerId", "id")), "typed constructor");
            }
            if (packetClass == ServerboundSetCarriedItemPacket.class) {
                requireOnly(args, "slot");
                return Result.ok(new ServerboundSetCarriedItemPacket(intArg(args, AutismPacketArgumentBuilder::currentSelectedSlot, "slot")), "typed constructor");
            }
            if (packetClass == ServerboundSelectBundleItemPacket.class) {
                requireOnly(args, "slot", "selectedItemIndex", "index");
                return Result.ok(new ServerboundSelectBundleItemPacket(intArg(args, "slot"), intArg(args, "selectedItemIndex", "index")), "typed constructor");
            }
            if (packetClass == ServerboundSwingPacket.class) {
                requireOnly(args, "hand");
                return Result.ok(new ServerboundSwingPacket(enumArgOrDefault(InteractionHand.class, args, InteractionHand.MAIN_HAND, "hand")), "typed constructor");
            }
            if (packetClass == ServerboundUseItemPacket.class) {
                requireOnly(args, "hand", "sequence", "yRot", "xRot");
                InteractionHand hand = enumArgOrDefault(InteractionHand.class, args, InteractionHand.MAIN_HAND, "hand");
                int sequence = intArg(args, 0, "sequence");
                float yRot = floatArg(args, currentYRot(), "yRot", "yaw");
                float xRot = floatArg(args, currentXRot(), "xRot", "pitch");
                return Result.ok(new ServerboundUseItemPacket(hand, sequence, yRot, xRot), "typed constructor");
            }
            if (packetClass == ServerboundUseItemOnPacket.class) {
                requireOnly(args, "hand", "sequence", "pos", "blockPos", "x", "y", "z", "direction", "dir", "face", "hit", "hitX", "hitY", "hitZ", "inside");
                InteractionHand hand = enumArgOrDefault(InteractionHand.class, args, InteractionHand.MAIN_HAND, "hand");
                BlockHitResult hitResult = blockHitArg(args);
                int sequence = intArg(args, 0, "sequence");
                return Result.ok(new ServerboundUseItemOnPacket(hand, hitResult, sequence), "typed constructor");
            }
            if (packetClass == ServerboundClientCommandPacket.class) {
                requireOnly(args, "action");
                return Result.ok(new ServerboundClientCommandPacket(enumArg(ServerboundClientCommandPacket.Action.class, args, "action")), "typed constructor");
            }
            if (packetClass == ServerboundChatCommandPacket.class) {
                requireOnly(args, "command", "cmd");
                return Result.ok(new ServerboundChatCommandPacket(normalizeChatCommand(requireString(args, firstArg(args, "command") != null ? "command" : "cmd"))), "typed constructor");
            }
            if (packetClass == ServerboundChatPacket.class) {
                requireOnly(args, "message", "timeStamp", "timestamp", "time", "salt", "signature", "lastSeenMessages", "lastSeen");
                String message = requireString(args, "message");
                Instant time = instantArg(args, Instant.now(), "timeStamp", "timestamp", "time");
                long salt = longArg(args, 0L, "salt");
                MessageSignature signature = parseMessageSignature(firstArg(args, "signature"));
                LastSeenMessages.Update lastSeen = parseLastSeenUpdate(firstArg(args, "lastSeenMessages", "lastSeen"));
                return Result.ok(new ServerboundChatPacket(message, time, salt, signature, lastSeen), "typed constructor");
            }
            if (packetClass == ServerboundChatCommandSignedPacket.class) {
                requireOnly(args, "command", "timeStamp", "timestamp", "time", "salt", "argumentSignatures", "signatures", "lastSeenMessages", "lastSeen");
                String command = requireString(args, "command");
                Instant time = instantArg(args, Instant.now(), "timeStamp", "timestamp", "time");
                long salt = longArg(args, 0L, "salt");
                ArgumentSignatures signatures = parseArgumentSignatures(firstArg(args, "argumentSignatures", "signatures"));
                LastSeenMessages.Update lastSeen = parseLastSeenUpdate(firstArg(args, "lastSeenMessages", "lastSeen"));
                return Result.ok(new ServerboundChatCommandSignedPacket(command, time, salt, signatures, lastSeen), "typed constructor");
            }
            if (packetClass == ServerboundChatSessionUpdatePacket.class) {
                requireOnly(args, "current", "default");
                RemoteChatSession.Data data = currentChatSessionData();
                if (data == null) throw new IllegalArgumentException("No current local chat session is available.");
                return Result.ok(new ServerboundChatSessionUpdatePacket(data), "current chat session");
            }
            if (packetClass == ServerboundClientInformationPacket.class) {
                requireOnly(args, "default", "information");
                return Result.ok(new ServerboundClientInformationPacket(ClientInformation.createDefault()), "current client information");
            }
            if (packetClass == ServerboundCustomPayloadPacket.class) {
                requireOnly(args, "brand", "payload", "channel");
                String channel = firstArg(args, "channel");
                String brand = firstArg(args, "brand", "payload");
                if (brand != null && (channel == null || normalizeIdentifier(channel).equals("minecraft:brand") || normalizeIdentifier(channel).equals("brand"))) {
                    return Result.ok(new ServerboundCustomPayloadPacket(new BrandPayload(brand)), "brand payload");
                }
                return Result.error("Use Packet Logger > Payload Sender for arbitrary plugin payload bytes. .send can build minecraft:brand only.");
            }
            if (packetClass == ServerboundCustomQueryAnswerPacket.class) {
                requireOnly(args, "transactionId", "id", "payload");
                int id = intArg(args, "transactionId", "id");
                String payload = firstArg(args, "payload");
                if (payload == null || isNone(payload)) return Result.ok(new ServerboundCustomQueryAnswerPacket(id, null), "typed constructor");
                if (normalizeName(payload).equals("discarded")) {
                    return Result.ok(new ServerboundCustomQueryAnswerPacket(id, DiscardedQueryAnswerPayload.INSTANCE), "typed constructor");
                }
                return Result.error("Login custom query answers support payload=empty/null/discarded only from .send.");
            }
            if (packetClass == ServerboundEditBookPacket.class) {
                requireOnly(args, "slot", "pages", "page", "title");
                int slot = intArg(args, "slot");
                String pagesValue = firstArg(args, "pages", "page");
                if (pagesValue == null) throw new IllegalArgumentException("Missing required argument: pages");
                Optional<String> title = optionalString(firstArg(args, "title"));
                return Result.ok(new ServerboundEditBookPacket(slot, splitList(pagesValue, "\\|", 100), title), "typed constructor");
            }
            if (packetClass == ServerboundSignUpdatePacket.class) {
                requireOnly(args, "pos", "blockPos", "x", "y", "z", "front", "isFrontText", "line0", "line1", "line2", "line3", "l0", "l1", "l2", "l3", "lines");
                BlockPos pos = blockPosArg(args);
                boolean front = boolArg(args, true, "front", "isFrontText");
                String[] lines = lines4(args);
                return Result.ok(new ServerboundSignUpdatePacket(pos, front, lines[0], lines[1], lines[2], lines[3]), "typed constructor");
            }
            if (packetClass == ServerboundInteractPacket.class) {
                requireOnly(args, "entityId", "id", "entity", "hand", "location", "pos", "x", "y", "z", "secondary", "usingSecondaryAction");
                int entityId = intArg(args, AutismPacketArgumentBuilder::currentEntityId, "entityId", "id", "entity");
                InteractionHand hand = enumArgOrDefault(InteractionHand.class, args, InteractionHand.MAIN_HAND, "hand");
                Vec3 location = firstArg(args, "location", "pos") != null
                    ? parseVec3(firstArg(args, "location", "pos"))
                    : currentEntityHitLocation(new Vec3(doubleArg(args, 0.0D, "x"), doubleArg(args, 0.0D, "y"), doubleArg(args, 0.0D, "z")));
                boolean secondary = boolArg(args, false, "secondary", "usingSecondaryAction");
                return Result.ok(new ServerboundInteractPacket(entityId, hand, location, secondary), "typed constructor");
            }
            if (packetClass == ServerboundContainerClickPacket.class) {
                requireOnly(args, "containerId", "id", "stateId", "slotNum", "slot", "buttonNum", "button", "containerInput", "input", "click", "changedSlots", "changed", "carriedItem", "carried");
                int containerId = intArg(args, AutismPacketArgumentBuilder::currentContainerId, "containerId", "id");
                int stateId = intArg(args, AutismPacketArgumentBuilder::currentContainerStateId, "stateId");
                short slot = (short) intArg(args, "slotNum", "slot");
                byte button = (byte) intArg(args, 0, "buttonNum", "button");
                ContainerInput input = enumArg(ContainerInput.class, args, "containerInput", "input", "click");
                Int2ObjectMap<HashedStack> changed = parseHashedSlotMap(firstArg(args, "changedSlots", "changed"));
                HashedStack carried = parseHashedStack(firstArg(args, "carriedItem", "carried"));
                return Result.ok(new ServerboundContainerClickPacket(containerId, stateId, slot, button, input, changed, carried), "typed constructor");
            }
            if (packetClass == ServerboundContainerButtonClickPacket.class) {
                requireOnly(args, "containerId", "id", "buttonId", "button");
                return Result.ok(new ServerboundContainerButtonClickPacket(
                    intArg(args, AutismPacketArgumentBuilder::currentContainerId, "containerId", "id"),
                    intArg(args, "buttonId", "button")
                ), "typed constructor");
            }
            if (packetClass == ServerboundContainerSlotStateChangedPacket.class) {
                requireOnly(args, "slotId", "slot", "containerId", "id", "newState", "state", "enabled");
                return Result.ok(new ServerboundContainerSlotStateChangedPacket(
                    intArg(args, "slotId", "slot"),
                    intArg(args, AutismPacketArgumentBuilder::currentContainerId, "containerId", "id"),
                    boolArg(args, false, "newState", "state", "enabled")
                ), "typed constructor");
            }
            if (packetClass == ServerboundSetCreativeModeSlotPacket.class) {
                requireOnly(args, "slotNum", "slot", "itemStack", "item");
                int slot = intArg(args, "slotNum", "slot");
                ItemStack stack = parseItemStack(firstArg(args, "itemStack", "item"));
                return Result.ok(new ServerboundSetCreativeModeSlotPacket(slot, stack), "typed constructor");
            }
            if (packetClass == ServerboundPlayerCommandPacket.class) {
                requireOnly(args, "action", "data");
                if (MC.player == null) throw new IllegalArgumentException("Player is required for this packet.");
                ServerboundPlayerCommandPacket.Action action = enumArg(ServerboundPlayerCommandPacket.Action.class, args, "action");
                int data = intArg(args, 0, "data");
                return Result.ok(new ServerboundPlayerCommandPacket(MC.player, action, data), "current player constructor");
            }
            if (packetClass == ServerboundPlayerInputPacket.class) {
                requireOnly(args, "forward", "backward", "left", "right", "jump", "shift", "sneak", "sprint", "empty");
                if (boolArg(args, false, "empty")) return Result.ok(new ServerboundPlayerInputPacket(Input.EMPTY), "typed constructor");
                return Result.ok(new ServerboundPlayerInputPacket(new Input(
                    boolArg(args, false, "forward"),
                    boolArg(args, false, "backward"),
                    boolArg(args, false, "left"),
                    boolArg(args, false, "right"),
                    boolArg(args, false, "jump"),
                    boolArg(args, false, "shift", "sneak"),
                    boolArg(args, false, "sprint")
                )), "typed constructor");
            }
            if (packetClass == ServerboundPaddleBoatPacket.class) {
                requireOnly(args, "left", "right");
                return Result.ok(new ServerboundPaddleBoatPacket(boolArg(args, false, "left"), boolArg(args, false, "right")), "typed constructor");
            }
            if (packetClass == ServerboundSetCommandBlockPacket.class) {
                requireOnly(args, "pos", "blockPos", "x", "y", "z", "command", "mode", "trackOutput", "conditional", "automatic");
                return Result.ok(new ServerboundSetCommandBlockPacket(
                    blockPosArg(args),
                    requireString(args, "command"),
                    enumArg(CommandBlockEntity.Mode.class, args, "mode"),
                    boolArg(args, false, "trackOutput"),
                    boolArg(args, false, "conditional"),
                    boolArg(args, false, "automatic")
                ), "typed constructor");
            }
            if (packetClass == ServerboundSetGameRulePacket.class) {
                requireOnly(args, "entries", "rules", "rule", "value");
                return Result.ok(new ServerboundSetGameRulePacket(gameRuleEntries(args)), "typed constructor");
            }
            if (packetClass == ServerboundSeenAdvancementsPacket.class) {
                requireOnly(args, "action", "tab");
                ServerboundSeenAdvancementsPacket.Action action = enumArg(ServerboundSeenAdvancementsPacket.Action.class, args, "action");
                Identifier tab = action == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB
                    ? Identifier.parse(requireString(args, "tab"))
                    : null;
                return Result.ok(new ServerboundSeenAdvancementsPacket(action, tab), "typed constructor");
            }
            if (packetClass == ServerboundTestInstanceBlockActionPacket.class) {
                requireOnly(args, "pos", "blockPos", "x", "y", "z", "action", "size", "rotation", "ignoreEntities", "ignore");
                return Result.ok(new ServerboundTestInstanceBlockActionPacket(
                    blockPosArg(args),
                    enumArg(ServerboundTestInstanceBlockActionPacket.Action.class, args, "action"),
                    Optional.empty(),
                    vec3iArg(args, Vec3i.ZERO, "size"),
                    enumArgOrDefault(Rotation.class, args, Rotation.NONE, "rotation"),
                    boolArg(args, false, "ignoreEntities", "ignore")
                ), "typed constructor");
            }
            if (packetClass == ServerboundPlayerActionPacket.class) {
                requireOnly(args, "action", "pos", "x", "y", "z", "direction", "sequence");
                ServerboundPlayerActionPacket.Action action = enumArg(ServerboundPlayerActionPacket.Action.class, args, "action");
                BlockPos pos = blockPosArg(args);
                Direction direction = enumArg(Direction.class, args, "direction", "dir", "face");
                int sequence = intArg(args, 0, "sequence");
                return Result.ok(new ServerboundPlayerActionPacket(action, pos, direction, sequence), "typed constructor");
            }
            if (packetClass == ServerboundMovePlayerPacket.Pos.class || name.endsWith("ServerboundMovePlayerPacket$Pos")) {
                requireOnly(args, "x", "y", "z", "onGround", "ground", "horizontalCollision", "collision");
                return Result.ok(new ServerboundMovePlayerPacket.Pos(
                    doubleArg(args, currentX(), "x"),
                    doubleArg(args, currentY(), "y"),
                    doubleArg(args, currentZ(), "z"),
                    boolArg(args, currentOnGround(), "onGround", "ground"),
                    boolArg(args, currentHorizontalCollision(), "horizontalCollision", "collision")
                ), "typed constructor");
            }
            if (packetClass == ServerboundMovePlayerPacket.PosRot.class || name.endsWith("ServerboundMovePlayerPacket$PosRot")) {
                requireOnly(args, "x", "y", "z", "yRot", "yaw", "xRot", "pitch", "onGround", "ground", "horizontalCollision", "collision");
                return Result.ok(new ServerboundMovePlayerPacket.PosRot(
                    doubleArg(args, currentX(), "x"),
                    doubleArg(args, currentY(), "y"),
                    doubleArg(args, currentZ(), "z"),
                    floatArg(args, currentYRot(), "yRot", "yaw"),
                    floatArg(args, currentXRot(), "xRot", "pitch"),
                    boolArg(args, currentOnGround(), "onGround", "ground"),
                    boolArg(args, currentHorizontalCollision(), "horizontalCollision", "collision")
                ), "typed constructor");
            }
            if (packetClass == ServerboundMovePlayerPacket.Rot.class || name.endsWith("ServerboundMovePlayerPacket$Rot")) {
                requireOnly(args, "yRot", "yaw", "xRot", "pitch", "onGround", "ground", "horizontalCollision", "collision");
                return Result.ok(new ServerboundMovePlayerPacket.Rot(
                    floatArg(args, currentYRot(), "yRot", "yaw"),
                    floatArg(args, currentXRot(), "xRot", "pitch"),
                    boolArg(args, currentOnGround(), "onGround", "ground"),
                    boolArg(args, currentHorizontalCollision(), "horizontalCollision", "collision")
                ), "typed constructor");
            }
            if (packetClass == ServerboundMovePlayerPacket.StatusOnly.class || name.endsWith("ServerboundMovePlayerPacket$StatusOnly")) {
                requireOnly(args, "onGround", "ground", "horizontalCollision", "collision");
                return Result.ok(new ServerboundMovePlayerPacket.StatusOnly(
                    boolArg(args, currentOnGround(), "onGround", "ground"),
                    boolArg(args, currentHorizontalCollision(), "horizontalCollision", "collision")
                ), "typed constructor");
            }
            if (packetClass == ServerboundMoveVehiclePacket.class) {
                requireOnly(args, "position", "pos", "x", "y", "z", "yRot", "yaw", "xRot", "pitch", "onGround", "ground");
                if (args.isEmpty() && MC.player != null && MC.player.getVehicle() != null) {
                    return Result.ok(ServerboundMoveVehiclePacket.fromEntity(MC.player.getVehicle()), "current vehicle");
                }
                Vec3 position = firstArg(args, "position", "pos") != null
                    ? parseVec3(firstArg(args, "position", "pos"))
                    : new Vec3(doubleArg(args, currentX(), "x"), doubleArg(args, currentY(), "y"), doubleArg(args, currentZ(), "z"));
                return Result.ok(new ServerboundMoveVehiclePacket(
                    position,
                    floatArg(args, currentYRot(), "yRot", "yaw"),
                    floatArg(args, currentXRot(), "xRot", "pitch"),
                    boolArg(args, currentOnGround(), "onGround", "ground")
                ), "typed constructor");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Throwable t) {
            return Result.error("Special builder failed: " + safeMessage(t));
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Result tryRecord(Class<? extends Packet<?>> packetClass, Map<String, String> args) {
        RecordComponent[] components = packetClass.getRecordComponents();
        if (components == null || components.length == 0) return null;
        Constructor<?> constructor;
        try {
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) types[i] = components[i].getType();
            constructor = packetClass.getDeclaredConstructor(types);
        } catch (Throwable ignored) {
            return null;
        }
        try {
            Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                values[i] = parseNamedValue(args, components[i].getName(), components[i].getType(), components[i].getGenericType());
            }
            ensureNoUnknown(args, expandableAllowedNames(componentNames(components)));
            constructor.setAccessible(true);
            return Result.ok((Packet<?>) constructor.newInstance(values), "record constructor");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Throwable t) {
            return Result.error("Record builder failed: " + safeMessage(t));
        }
    }

    private static Result trySchemaConstructor(Class<? extends Packet<?>> packetClass, Map<String, String> args) {
        AutismPacketSchemaRegistry.PacketSchema schema = AutismPacketSchemaRegistry.find(packetClass);
        if (schema == null || schema.fields().isEmpty()) return null;
        String firstError = null;
        for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != schema.fields().size()) continue;
            if (isCodecConstructor(constructor)) continue;
            try {
                Object[] values = new Object[schema.fields().size()];
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                List<String> names = new ArrayList<>();
                for (int i = 0; i < schema.fields().size(); i++) {
                    String fieldName = schema.fields().get(i).name();
                    names.add(fieldName);
                    values[i] = parseNamedValue(args, fieldName, parameterTypes[i], parameterTypes[i]);
                }
                ensureNoUnknown(args, expandableAllowedNames(names));
                constructor.setAccessible(true);
                return Result.ok((Packet<?>) constructor.newInstance(values), "schema constructor");
            } catch (IllegalArgumentException e) {
                if (firstError == null) firstError = e.getMessage();
            } catch (Throwable ignored) {  }
        }
        return firstError == null ? null : Result.error(firstError);
    }

    private static boolean isCodecConstructor(Constructor<?> constructor) {
        for (Class<?> type : constructor.getParameterTypes()) {
            String name = type.getName();
            if (name.contains("ByteBuf")) return true;
        }
        return false;
    }

    private static Map<String, String> parseArgs(String rawArgs) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rawArgs == null || rawArgs.isBlank()) return out;
        for (String token : tokenize(rawArgs)) {
            if (token.isBlank()) continue;
            int eq = token.indexOf('=');
            if (eq < 0) {
                String key = normalizeName(token);
                out.put(key, "true");
                continue;
            }
            String key = normalizeName(token.substring(0, eq));
            String value = token.substring(eq + 1);
            if (key.isBlank()) throw new IllegalArgumentException("Blank argument name in: " + token);
            out.put(key, value);
        }
        return out;
    }

    private static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                token.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (quoted) {
                if (c == quote) quoted = false;
                else token.append(c);
                continue;
            }
            if (c == '"' || c == '\'') {
                quoted = true;
                quote = c;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!token.isEmpty()) {
                    out.add(token.toString());
                    token.setLength(0);
                }
                continue;
            }
            token.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Unclosed quote in arguments.");
        if (!token.isEmpty()) out.add(token.toString());
        return out;
    }

    private static String joinTokens(List<String> tokens) {
        List<String> out = new ArrayList<>();
        for (String token : tokens) out.add(quoteToken(token));
        return String.join(" ", out);
    }

    private static String quoteToken(String token) {
        if (token == null) return "";
        boolean needsQuote = token.isBlank();
        for (int i = 0; i < token.length() && !needsQuote; i++) {
            char c = token.charAt(i);
            needsQuote = Character.isWhitespace(c) || c == '"' || c == '\'';
        }
        if (!needsQuote) return token;
        return "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static int lastTokenStart(String text) {
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (quoted) {
                if (c == quote) quoted = false;
                continue;
            }
            if (c == '"' || c == '\'') {
                quoted = true;
                quote = c;
                continue;
            }
            if (Character.isWhitespace(c)) start = i + 1;
        }
        return start;
    }

    private static Set<String> parsedKeysBeforeCurrent(String text, int tokenStart) {
        Set<String> out = new java.util.HashSet<>();
        String prefix = tokenStart <= 0 ? "" : text.substring(0, tokenStart);
        try {
            out.addAll(parseArgs(prefix).keySet());
        } catch (IllegalArgumentException ignored) {  }
        return out;
    }

    private static List<String> suggestionFields(Class<? extends Packet<?>> packetClass) {
        List<String> fields = specialSuggestionFields(packetClass);
        if (!fields.isEmpty()) return withRawSuggestions(fields);
        AutismPacketSchemaRegistry.PacketSchema schema = AutismPacketSchemaRegistry.find(packetClass);
        if (schema == null || schema.fields().isEmpty()) return withRawSuggestions(List.of("help"));
        List<String> out = new ArrayList<>();
        for (AutismPacketSchemaRegistry.FieldSchema field : schema.fields()) {
            out.add(field.name());
        }
        return withRawSuggestions(out);
    }

    private static List<String> withRawSuggestions(List<String> fields) {
        List<String> out = new ArrayList<>(fields);
        out.add("rawHex");
        out.add("packetHex");
        out.add("base64");
        return out;
    }

    private static List<String> specialSuggestionFields(Class<? extends Packet<?>> packetClass) {
        String name = packetClass.getName();
        if (packetClass == ServerboundUseItemOnPacket.class) return List.of("hand", "pos", "direction", "sequence", "hit", "inside");
        if (packetClass == ServerboundContainerClickPacket.class) return List.of("slot", "input", "button", "containerId", "stateId", "changed", "carried");
        if (packetClass == ServerboundSignUpdatePacket.class) return List.of("pos", "front", "lines", "line0", "line1", "line2", "line3");
        if (packetClass == ServerboundEditBookPacket.class) return List.of("slot", "pages", "title");
        if (packetClass == ServerboundSetGameRulePacket.class) return List.of("rule", "value", "entries");
        if (packetClass == ServerboundSetCreativeModeSlotPacket.class) return List.of("slot", "item");
        if (packetClass == ServerboundPlayerInputPacket.class) return List.of("forward", "backward", "left", "right", "jump", "shift", "sprint");
        if (packetClass == ServerboundInteractPacket.class) return List.of("entityId", "hand", "location", "secondary");
        if (packetClass == ServerboundTestInstanceBlockActionPacket.class) return List.of("pos", "action", "size", "rotation", "ignore");
        if (packetClass == ServerboundChatCommandPacket.class) return List.of("command");
        if (packetClass == ServerboundChatPacket.class) return List.of("message", "time", "salt", "signature", "lastSeen");
        if (packetClass == ServerboundChatCommandSignedPacket.class) return List.of("command", "time", "salt", "signatures", "lastSeen");
        if (packetClass == ServerboundCustomPayloadPacket.class) return List.of("brand", "channel");
        if (packetClass == ServerboundCustomQueryAnswerPacket.class) return List.of("transactionId", "payload");
        if (packetClass == ServerboundMovePlayerPacket.Pos.class || name.endsWith("ServerboundMovePlayerPacket$Pos")) return List.of("x", "y", "z", "onGround", "collision");
        if (packetClass == ServerboundMovePlayerPacket.PosRot.class || name.endsWith("ServerboundMovePlayerPacket$PosRot")) return List.of("x", "y", "z", "yaw", "pitch", "onGround", "collision");
        if (packetClass == ServerboundMovePlayerPacket.Rot.class || name.endsWith("ServerboundMovePlayerPacket$Rot")) return List.of("yaw", "pitch", "onGround", "collision");
        if (packetClass == ServerboundMovePlayerPacket.StatusOnly.class || name.endsWith("ServerboundMovePlayerPacket$StatusOnly")) return List.of("onGround", "collision");
        return List.of();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<String> valueSuggestions(Class<? extends Packet<?>> packetClass, String rawField) {
        String field = normalizeName(rawField);
        if (field.equals("rawhex") || field.equals("raw") || field.equals("body") || field.equals("bodyhex") || field.equals("hex") || field.equals("bytes")) {
            return List.of("hex:010203", "utf8:text");
        }
        if (field.equals("packethex") || field.equals("packet") || field.equals("full") || field.equals("fullhex") || field.equals("plaintext") || field.equals("packetbytes")) {
            return List.of("hex:010203");
        }
        if (field.equals("base64") || field.equals("b64") || field.equals("bodybase64")) {
            return List.of("AAECAw==");
        }
        Class<? extends Enum<?>> enumClass = enumSuggestionType(packetClass, field);
        if (enumClass != null) {
            List<String> values = new ArrayList<>();
            for (Enum<?> constant : enumClass.getEnumConstants()) values.add(constant.name());
            return values;
        }
        if (field.equals("hand")) return List.of("MAIN_HAND", "OFF_HAND");
        if (field.equals("direction") || field.equals("dir") || field.equals("face")) return List.of("UP", "DOWN", "NORTH", "SOUTH", "WEST", "EAST");
        if (field.equals("input") || field.equals("click") || field.equals("containerinput")) return List.of("PICKUP", "QUICK_MOVE", "SWAP", "THROW", "QUICK_CRAFT", "PICKUP_ALL");
        if (field.equals("containerid") || field.equals("id") || field.equals("stateid")) return List.of("current", "0");
        if (field.equals("slot") || field.equals("slotnum") || field.equals("slotid")) return List.of("current", "0");
        if (field.equals("x")) return List.of(formatDouble(currentX()));
        if (field.equals("y")) return List.of(formatDouble(currentY()));
        if (field.equals("z")) return List.of(formatDouble(currentZ()));
        if (field.equals("yaw") || field.equals("yrot")) return List.of(formatFloat(currentYRot()));
        if (field.equals("pitch") || field.equals("xrot")) return List.of(formatFloat(currentXRot()));
        if (field.equals("pos") || field.equals("blockpos") || field.equals("hit") || field.equals("size")) return List.of("0,64,0");
        if (field.equals("item") || field.equals("itemstack") || field.equals("carried")) return List.of("empty", "held", "offhand", "cursor");
        if (field.equals("payload")) return List.of("empty", "discarded");
        if (field.equals("time") || field.equals("timestamp") || field.equals("timestamp")) return List.of("now");
        if (field.equals("signature") || field.equals("signatures") || field.equals("lastseen")) return List.of("empty");
        if (field.equals("brand")) return List.of("AUTISM");
        if (field.equals("channel")) return List.of("minecraft:brand");
        if (field.equals("command") || field.equals("cmd")) return List.of("help", "shop");
        return List.of("true", "false", "0", "empty");
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Enum<?>> enumSuggestionType(Class<? extends Packet<?>> packetClass, String field) {
        if (field.equals("action")) {
            if (packetClass == ServerboundResourcePackPacket.class) return ServerboundResourcePackPacket.Action.class;
            if (packetClass == ServerboundClientCommandPacket.class) return ServerboundClientCommandPacket.Action.class;
            if (packetClass == ServerboundPlayerActionPacket.class) return ServerboundPlayerActionPacket.Action.class;
            if (packetClass == ServerboundPlayerCommandPacket.class) return ServerboundPlayerCommandPacket.Action.class;
            if (packetClass == ServerboundSeenAdvancementsPacket.class) return ServerboundSeenAdvancementsPacket.Action.class;
            if (packetClass == ServerboundTestInstanceBlockActionPacket.class) return ServerboundTestInstanceBlockActionPacket.Action.class;
        }
        if (field.equals("mode") && packetClass == ServerboundSetCommandBlockPacket.class) return CommandBlockEntity.Mode.class;
        if (field.equals("rotation")) return Rotation.class;
        if (field.equals("hand")) return InteractionHand.class;
        if (field.equals("direction") || field.equals("dir") || field.equals("face")) return Direction.class;
        if (field.equals("input") || field.equals("click") || field.equals("containerinput")) return ContainerInput.class;
        AutismPacketSchemaRegistry.PacketSchema schema = AutismPacketSchemaRegistry.find(packetClass);
        if (schema == null) return null;
        for (AutismPacketSchemaRegistry.FieldSchema schemaField : schema.fields()) {
            if (!normalizeName(schemaField.name()).equals(field)) continue;
            try {
                Class<?> cls = Class.forName(enumClassName(schemaField.javaType()));
                return cls.isEnum() ? (Class<? extends Enum<?>>) cls : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String enumClassName(String javaType) {
        if (javaType == null) return "";
        if (javaType.indexOf('.') >= 0) return javaType;
        return switch (javaType) {
            case "Difficulty" -> "net.minecraft.world.Difficulty";
            case "GameType" -> "net.minecraft.world.level.GameType";
            case "RecipeBookType" -> "net.minecraft.world.inventory.RecipeBookType";
            case "ContainerInput" -> "net.minecraft.world.inventory.ContainerInput";
            case "InteractionHand" -> "net.minecraft.world.InteractionHand";
            case "Direction" -> "net.minecraft.core.Direction";
            case "Mirror" -> "net.minecraft.world.level.block.Mirror";
            case "Rotation" -> "net.minecraft.world.level.block.Rotation";
            case "StructureMode" -> "net.minecraft.world.level.block.state.properties.StructureMode";
            case "StructureBlockEntity.UpdateType" -> "net.minecraft.world.level.block.entity.StructureBlockEntity$UpdateType";
            case "CommandBlockEntity.Mode" -> "net.minecraft.world.level.block.entity.CommandBlockEntity$Mode";
            case "JigsawBlockEntity.JointType" -> "net.minecraft.world.level.block.entity.JigsawBlockEntity$JointType";
            case "TestBlockMode" -> "net.minecraft.world.level.block.state.properties.TestBlockMode";
            case "ServerboundResourcePackPacket.Action" -> "net.minecraft.network.protocol.common.ServerboundResourcePackPacket$Action";
            case "ServerboundClientCommandPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundClientCommandPacket$Action";
            case "ServerboundPlayerActionPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action";
            case "ServerboundPlayerCommandPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket$Action";
            case "ServerboundSeenAdvancementsPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket$Action";
            case "ServerboundTestInstanceBlockActionPacket.Action" -> "net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket$Action";
            default -> javaType.replace('.', '$');
        };
    }

    private static String suggestionExample(Class<? extends Packet<?>> packetClass, String field) {
        List<String> values = valueSuggestions(packetClass, field);
        if (!values.isEmpty()) return quoteSuggestionValue(values.get(0));
        return "0";
    }

    private static String quoteSuggestionValue(String value) {
        if (value == null) return "";
        return value.indexOf(' ') >= 0 ? "\"" + value.replace("\"", "\\\"") + "\"" : value;
    }

    private static List<String> examplesFor(Class<? extends Packet<?>> packetClass) {
        String prefix = ".send " + displayName(packetClass) + " ";
        String name = packetClass.getName();
        if (packetClass == ServerboundUseItemPacket.class) return List.of(prefix + "hand=MAIN_HAND");
        if (packetClass == ServerboundUseItemOnPacket.class) return List.of(prefix + "hand=MAIN_HAND", prefix + "pos=0,64,0 direction=UP");
        if (packetClass == ServerboundSwingPacket.class) return List.of(prefix + "hand=MAIN_HAND");
        if (packetClass == ServerboundContainerClickPacket.class) return List.of(prefix + "slot=0 input=PICKUP", prefix + "slot=0 input=QUICK_MOVE");
        if (packetClass == ServerboundContainerClosePacket.class) return List.of(prefix + "containerId=current");
        if (packetClass == ServerboundContainerButtonClickPacket.class) return List.of(prefix + "button=0 containerId=current");
        if (packetClass == ServerboundSetCarriedItemPacket.class) return List.of(prefix + "slot=current", prefix + "slot=0");
        if (packetClass == ServerboundMovePlayerPacket.Pos.class || name.endsWith("ServerboundMovePlayerPacket$Pos")) return List.of(prefix + "x=current y=current z=current");
        if (packetClass == ServerboundMovePlayerPacket.PosRot.class || name.endsWith("ServerboundMovePlayerPacket$PosRot")) return List.of(prefix + "x=current y=current z=current yaw=current pitch=current");
        if (packetClass == ServerboundMovePlayerPacket.Rot.class || name.endsWith("ServerboundMovePlayerPacket$Rot")) return List.of(prefix + "yaw=current pitch=current");
        if (packetClass == ServerboundChatCommandPacket.class) return List.of(prefix + "command=shop", prefix + "command=\"/warp spawn\"");
        if (packetClass == ServerboundChatPacket.class) return List.of(prefix + "message=\"hello\" dry");
        if (packetClass == ServerboundEditBookPacket.class) return List.of(prefix + "slot=0 pages=\"page one|page two\" title=\"title\"");
        if (packetClass == ServerboundSignUpdatePacket.class) return List.of(prefix + "pos=0,64,0 lines=\"one|two|three|four\"");
        if (packetClass == ServerboundSetCreativeModeSlotPacket.class) return List.of(prefix + "slot=0 item=held", prefix + "slot=0 item=empty");
        if (packetClass == ServerboundSetGameRulePacket.class) return List.of(prefix + "rule=doDaylightCycle value=false");
        if (packetClass == ServerboundCustomPayloadPacket.class) return List.of(prefix + "brand=vanilla");
        if (packetClass == ServerboundMoveVehiclePacket.class) return List.of(prefix + "dry", prefix + "pos=current yaw=current pitch=current");
        return List.of(prefix + "dry", prefix + "rawHex=hex:00 dry", prefix + "packetHex=hex:0000 dry");
    }

    private static Object parseNamedValue(Map<String, String> args, String fieldName, Class<?> targetType, Type genericType) {
        String value = firstArg(args, aliasNames(fieldName));
        if (value == null) throw new IllegalArgumentException("Missing required argument: " + fieldName);
        return parseValue(fieldName, value, targetType, genericType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseValue(String fieldName, String value, Class<?> targetType, Type genericType) {
        if (isCurrent(value)) return currentValueFor(fieldName, targetType);
        if (targetType == String.class) return value;
        if (targetType == int.class || targetType == Integer.class) return parseInt(fieldName, value);
        if (targetType == short.class || targetType == Short.class) return (short) parseInt(fieldName, value);
        if (targetType == byte.class || targetType == Byte.class) return (byte) parseInt(fieldName, value);
        if (targetType == long.class || targetType == Long.class) return parseLong(fieldName, value);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(value);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
        if (targetType == boolean.class || targetType == Boolean.class) return parseBoolean(fieldName, value);
        if (targetType.isEnum()) return enumValue((Class<? extends Enum>) targetType, value);
        if (targetType == Identifier.class) return Identifier.parse(value);
        if (targetType == BlockPos.class) return parseBlockPos(value);
        if (targetType == Vec3i.class) return parseVec3i(value);
        if (targetType == Vec3.class) return parseVec3(value);
        if (targetType == UUID.class) return UUID.fromString(value);
        if (targetType == Instant.class) return parseInstant(value);
        if (targetType == byte[].class) return parseBytes(value);
        if (targetType == String[].class) return splitList(value, "\\|", 64).toArray(new String[0]);
        if (targetType == Optional.class) return parseOptional(fieldName, value, genericType);
        if (targetType == List.class) return parseList(fieldName, value, genericType);
        if (targetType == Set.class) return parseEmptySet(fieldName, value);
        if (targetType == Map.class) return parseEmptyMap(fieldName, value);
        if (targetType == Int2ObjectMap.class) return parseHashedSlotMap(value);
        if (targetType == HashedStack.class) return parseHashedStack(value);
        if (targetType == ItemStack.class) return parseItemStack(value);
        if (targetType == Input.class) return parseInput(value);
        if (targetType == ClientInformation.class) return ClientInformation.createDefault();
        if (targetType == RecipeDisplayId.class) return new RecipeDisplayId(parseInt(fieldName, value));
        if (targetType == ArgumentSignatures.class) return parseArgumentSignatures(value);
        if (targetType == LastSeenMessages.Update.class) return parseLastSeenUpdate(value);
        if (targetType == MessageSignature.class) return parseMessageSignature(value);
        throw new IllegalArgumentException("Argument '" + fieldName + "' uses unsupported type " + targetType.getSimpleName()
            + ". Capture/resend or packet macros are still needed for complex packet objects.");
    }

    private static Object currentValueFor(String fieldName, Class<?> targetType) {
        String normalized = normalizeName(fieldName);
        if (targetType == int.class || targetType == Integer.class) {
            if (normalized.equals("containerid")) return currentContainerId();
            if (normalized.equals("stateid")) return currentContainerStateId();
            if (normalized.equals("slot") || normalized.equals("slotid") || normalized.equals("slotnum")) return currentSelectedSlot();
            if (normalized.equals("id") || normalized.equals("entityid")) {
                if (MC != null && MC.player != null) return MC.player.getId();
            }
        }
        if (targetType == short.class || targetType == Short.class) {
            if (normalized.equals("slot") || normalized.equals("slotid") || normalized.equals("slotnum")) return (short) currentSelectedSlot();
        }
        if (targetType == byte.class || targetType == Byte.class) {
            if (normalized.equals("buttonnum") || normalized.equals("button")) return (byte) 0;
        }
        if (targetType == double.class || targetType == Double.class) {
            if (normalized.equals("x")) return currentX();
            if (normalized.equals("y")) return currentY();
            if (normalized.equals("z")) return currentZ();
        }
        if (targetType == float.class || targetType == Float.class) {
            if (normalized.equals("yrot") || normalized.equals("yaw")) return currentYRot();
            if (normalized.equals("xrot") || normalized.equals("pitch")) return currentXRot();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (normalized.equals("onground") || normalized.equals("ground")) return currentOnGround();
            if (normalized.equals("horizontalcollision") || normalized.equals("collision")) return currentHorizontalCollision();
        }
        if (targetType == BlockPos.class) {
            BlockHitResult hit = currentBlockHit();
            if (hit != null) return hit.getBlockPos();
        }
        if (targetType == Vec3.class) {
            if (normalized.equals("position") || normalized.equals("pos")) return new Vec3(currentX(), currentY(), currentZ());
            BlockHitResult hit = currentBlockHit();
            if (hit != null) return hit.getLocation();
        }
        if (targetType == ItemStack.class) {
            return parseItemStack("held");
        }
        if (targetType == ClientInformation.class) {
            return ClientInformation.createDefault();
        }
        if (targetType == Instant.class) {
            return Instant.now();
        }
        throw new IllegalArgumentException("current is not available for " + fieldName + " (" + targetType.getSimpleName() + ").");
    }

    private static int intArg(Map<String, String> args, String... names) {
        String value = firstArg(args, names);
        if (value == null) throw new IllegalArgumentException("Missing required argument: " + names[0]);
        return parseInt(names[0], value);
    }

    private static int intArg(Map<String, String> args, int fallback, String... names) {
        String value = firstArg(args, names);
        return value == null || isCurrent(value) ? fallback : parseInt(names[0], value);
    }

    private static int intArg(Map<String, String> args, IntSupplier fallback, String... names) {
        String value = firstArg(args, names);
        return value == null || isCurrent(value) ? fallback.getAsInt() : parseInt(names[0], value);
    }

    private static long longArg(Map<String, String> args, long fallback, String... names) {
        String value = firstArg(args, names);
        return value == null ? fallback : parseLong(names[0], value);
    }

    private static double doubleArg(Map<String, String> args, String name) {
        String value = firstArg(args, name);
        if (value == null) throw new IllegalArgumentException("Missing required argument: " + name);
        return Double.parseDouble(value);
    }

    private static double doubleArg(Map<String, String> args, double fallback, String name) {
        String value = firstArg(args, name);
        return value == null || isCurrent(value) ? fallback : Double.parseDouble(value);
    }

    private static float floatArg(Map<String, String> args, String... names) {
        String value = firstArg(args, names);
        if (value == null) throw new IllegalArgumentException("Missing required argument: " + names[0]);
        return Float.parseFloat(value);
    }

    private static float floatArg(Map<String, String> args, float fallback, String... names) {
        String value = firstArg(args, names);
        return value == null || isCurrent(value) ? fallback : Float.parseFloat(value);
    }

    private static boolean boolArg(Map<String, String> args, boolean fallback, String... names) {
        String value = firstArg(args, names);
        return value == null || isCurrent(value) ? fallback : parseBoolean(names[0], value);
    }

    private static <E extends Enum<E>> E enumArg(Class<E> enumClass, Map<String, String> args, String... names) {
        String value = firstArg(args, names);
        if (value == null) throw new IllegalArgumentException("Missing required argument: " + names[0]);
        return enumValue(enumClass, value);
    }

    private static <E extends Enum<E>> E enumArgOrDefault(Class<E> enumClass, Map<String, String> args, E fallback, String... names) {
        String value = firstArg(args, names);
        return value == null ? fallback : enumValue(enumClass, value);
    }

    private static BlockPos blockPosArg(Map<String, String> args) {
        String packed = firstArg(args, "pos", "blockPos");
        if (packed != null) return parseBlockPos(packed);
        return new BlockPos(intArg(args, "x"), intArg(args, "y"), intArg(args, "z"));
    }

    private static BlockHitResult blockHitArg(Map<String, String> args) {
        BlockHitResult current = currentBlockHit();
        boolean hasExplicitPos = firstArg(args, "pos", "blockPos") != null || firstArg(args, "x") != null || firstArg(args, "y") != null || firstArg(args, "z") != null;
        boolean hasExplicitDirection = firstArg(args, "direction", "dir", "face") != null;
        boolean hasExplicitHit = firstArg(args, "hit") != null || firstArg(args, "hitX") != null || firstArg(args, "hitY") != null || firstArg(args, "hitZ") != null;
        if (!hasExplicitPos && !hasExplicitDirection && !hasExplicitHit && current != null) return current;
        BlockPos pos = hasExplicitPos ? blockPosArg(args) : current != null ? current.getBlockPos() : null;
        if (pos == null) throw new IllegalArgumentException("Missing required block position. Use pos=x,y,z or look at a block.");
        Direction direction = hasExplicitDirection ? enumArg(Direction.class, args, "direction", "dir", "face") : current != null ? current.getDirection() : Direction.UP;
        Vec3 hit = vec3Arg(args, current != null ? current.getLocation() : blockCenter(pos), "hit");
        boolean inside = boolArg(args, current != null && current.isInside(), "inside");
        return new BlockHitResult(hit, direction, pos, inside);
    }

    private static Vec3i vec3iArg(Map<String, String> args, Vec3i fallback, String packedName) {
        String packed = firstArg(args, packedName);
        return packed == null ? fallback : parseVec3i(packed);
    }

    private static BlockPos parseBlockPos(String value) {
        if (isCurrent(value)) {
            BlockHitResult hit = currentBlockHit();
            if (hit != null) return hit.getBlockPos();
            if (MC != null && MC.player != null) return MC.player.blockPosition();
            throw new IllegalArgumentException("current BlockPos needs a player or block hit.");
        }
        String[] parts = value.trim().split("[,;]");
        if (parts.length != 3) throw new IllegalArgumentException("BlockPos must be x,y,z.");
        return new BlockPos(parseInt("x", parts[0].trim()), parseInt("y", parts[1].trim()), parseInt("z", parts[2].trim()));
    }

    private static Vec3 vec3Arg(Map<String, String> args, Vec3 fallback, String packedName) {
        String packed = firstArg(args, packedName);
        if (packed != null) return parseVec3(packed);
        String hx = firstArg(args, packedName + "X");
        String hy = firstArg(args, packedName + "Y");
        String hz = firstArg(args, packedName + "Z");
        if (hx != null || hy != null || hz != null) {
            if (hx == null || hy == null || hz == null) throw new IllegalArgumentException(packedName + "X/Y/Z must all be provided.");
            return new Vec3(Double.parseDouble(hx), Double.parseDouble(hy), Double.parseDouble(hz));
        }
        return fallback;
    }

    private static Vec3 blockCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private static Vec3 parseVec3(String value) {
        if (isCurrent(value)) return new Vec3(currentX(), currentY(), currentZ());
        String[] parts = value.trim().split("[,;]");
        if (parts.length != 3) throw new IllegalArgumentException("Vec3 must be x,y,z.");
        return new Vec3(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()));
    }

    private static Vec3i parseVec3i(String value) {
        if (isCurrent(value)) {
            if (MC != null && MC.player != null) return MC.player.blockPosition();
            return Vec3i.ZERO;
        }
        String[] parts = value.trim().split("[,;]");
        if (parts.length != 3) throw new IllegalArgumentException("Vec3i must be x,y,z.");
        return new Vec3i(parseInt("x", parts[0].trim()), parseInt("y", parts[1].trim()), parseInt("z", parts[2].trim()));
    }

    private static byte[] parseBytes(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || isNone(trimmed)) return new byte[0];
        if (trimmed.regionMatches(true, 0, "base64:", 0, 7)) {
            return Base64.getDecoder().decode(trimmed.substring(7).trim());
        }
        if (trimmed.regionMatches(true, 0, "hex:", 0, 4)) {
            return parseHex(trimmed.substring(4));
        }
        String compactHex = trimmed.replace(" ", "").replace("_", "");
        if (compactHex.length() % 2 == 0 && compactHex.matches("(?i)[0-9a-f]+")) {
            return parseHex(compactHex);
        }
        if (trimmed.regionMatches(true, 0, "utf8:", 0, 5)) {
            return trimmed.substring(5).getBytes(StandardCharsets.UTF_8);
        }
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] parseHex(String value) {
        String compact = value.replaceAll("[^0-9A-Fa-f]", "");
        if (compact.length() % 2 != 0) throw new IllegalArgumentException("Hex byte string must have an even length.");
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < compact.length(); i += 2) {
            out[i / 2] = (byte) Integer.parseInt(compact.substring(i, i + 2), 16);
        }
        return out;
    }

    private static Object parseOptional(String fieldName, String value, Type genericType) {
        if (value == null || isNone(value)) return Optional.empty();
        Type inner = genericArgument(genericType);
        if (inner instanceof Class<?> innerClass) {
            return Optional.of(parseValue(fieldName, value, innerClass, innerClass));
        }
        throw new IllegalArgumentException("Optional argument '" + fieldName + "' only supports empty/null here.");
    }

    private static Object parseList(String fieldName, String value, Type genericType) {
        Type inner = genericArgument(genericType);
        if (!(inner instanceof Class<?> innerClass)) {
            if (isNone(value)) return List.of();
            throw new IllegalArgumentException("List argument '" + fieldName + "' needs a supported element type.");
        }
        List<String> parts = splitList(value, "\\|", 256);
        List<Object> values = new ArrayList<>(parts.size());
        for (String part : parts) values.add(parseValue(fieldName, part, innerClass, innerClass));
        return List.copyOf(values);
    }

    private static Set<?> parseEmptySet(String fieldName, String value) {
        if (isNone(value)) return Set.of();
        throw new IllegalArgumentException("Set argument '" + fieldName + "' only supports empty/null from .send.");
    }

    private static Map<?, ?> parseEmptyMap(String fieldName, String value) {
        if (isNone(value)) return Map.of();
        throw new IllegalArgumentException("Map argument '" + fieldName + "' only supports empty/null from .send.");
    }

    private static Type genericArgument(Type genericType) {
        if (genericType instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > 0) {
            return parameterized.getActualTypeArguments()[0];
        }
        return Object.class;
    }

    private static Int2ObjectMap<HashedStack> parseHashedSlotMap(String value) {
        Int2ObjectOpenHashMap<HashedStack> out = new Int2ObjectOpenHashMap<>();
        if (value == null || isNone(value)) return out;
        for (String entry : splitList(value, "\\|", 128)) {
            if (entry.isBlank()) continue;
            int eq = entry.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("changedSlots entries must be slot=empty.");
            int slot = parseInt("changedSlots", entry.substring(0, eq).trim());
            HashedStack stack = parseHashedStack(entry.substring(eq + 1).trim());
            out.put(slot, stack);
        }
        return out;
    }

    private static HashedStack parseHashedStack(String value) {
        if (value == null || isNone(value)) return HashedStack.EMPTY;
        throw new IllegalArgumentException("HashedStack from .send currently supports only empty/null. Capture/resend for real hashed item data.");
    }

    private static ItemStack parseItemStack(String value) {
        if (value == null || isNone(value)) return ItemStack.EMPTY;
        String normalized = normalizeName(value);
        if (MC.player != null) {
            if ("held".equals(normalized) || "mainhand".equals(normalized)) return MC.player.getMainHandItem().copy();
            if ("offhand".equals(normalized)) return MC.player.getOffhandItem().copy();
            if ("cursor".equals(normalized) && MC.player.containerMenu != null) return MC.player.containerMenu.getCarried().copy();
        }
        throw new IllegalArgumentException("ItemStack supports empty, held/mainhand, offhand, or cursor.");
    }

    private static Input parseInput(String value) {
        if (value == null || isNone(value)) return Input.EMPTY;
        boolean forward = false;
        boolean backward = false;
        boolean left = false;
        boolean right = false;
        boolean jump = false;
        boolean shift = false;
        boolean sprint = false;
        for (String part : value.split("[,;|+]")) {
            switch (normalizeName(part)) {
                case "", "empty" -> {
                }
                case "forward", "w" -> forward = true;
                case "backward", "back", "s" -> backward = true;
                case "left", "a" -> left = true;
                case "right", "d" -> right = true;
                case "jump", "space" -> jump = true;
                case "shift", "sneak" -> shift = true;
                case "sprint" -> sprint = true;
                default -> throw new IllegalArgumentException("Unknown input flag: " + part);
            }
        }
        return new Input(forward, backward, left, right, jump, shift, sprint);
    }

    private static String[] lines4(Map<String, String> args) {
        String packed = firstArg(args, "lines");
        String[] lines = {"", "", "", ""};
        if (packed != null) {
            List<String> parts = splitList(packed, "\\|", 4);
            for (int i = 0; i < Math.min(4, parts.size()); i++) lines[i] = parts.get(i);
        }
        for (int i = 0; i < 4; i++) {
            String value = firstArg(args, "line" + i, "l" + i);
            if (value != null) lines[i] = value;
        }
        return lines;
    }

    private static Optional<String> optionalString(String value) {
        return value == null || isNone(value) ? Optional.empty() : Optional.of(value);
    }

    private static List<ServerboundSetGameRulePacket.Entry> gameRuleEntries(Map<String, String> args) {
        String packed = firstArg(args, "entries", "rules");
        if (packed == null) {
            String rule = firstArg(args, "rule");
            String value = firstArg(args, "value");
            if (rule == null || value == null) throw new IllegalArgumentException("Provide entries=rule=value|rule2=value2 or rule=... value=...");
            packed = rule + "=" + value;
        }
        List<ServerboundSetGameRulePacket.Entry> entries = new ArrayList<>();
        for (String entry : splitList(packed, "\\|", 256)) {
            int eq = entry.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("Game rule entries must be rule=value.");
            ResourceKey<GameRule<?>> key = ResourceKey.create(Registries.GAME_RULE, parseIdentifierDefault(entry.substring(0, eq).trim()));
            entries.add(new ServerboundSetGameRulePacket.Entry(key, entry.substring(eq + 1)));
        }
        return List.copyOf(entries);
    }

    private static Identifier parseIdentifierDefault(String value) {
        return value.contains(":") ? Identifier.parse(value) : Identifier.withDefaultNamespace(value);
    }

    private static String normalizeIdentifier(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireString(Map<String, String> args, String name) {
        String value = firstArg(args, name);
        if (value == null) throw new IllegalArgumentException("Missing required argument: " + name);
        return value;
    }

    private static String normalizeChatCommand(String command) {
        String trimmed = command == null ? "" : command.trim();
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        if (trimmed.isBlank()) throw new IllegalArgumentException("Command cannot be blank.");
        return trimmed;
    }

    private static List<String> splitList(String value, String delimiterRegex, int max) {
        if (value == null || isNone(value)) return List.of();
        String[] parts = value.split(delimiterRegex, -1);
        if (parts.length > max) throw new IllegalArgumentException("Too many list values; max " + max + ".");
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) out.add(part);
        return out;
    }

    private static boolean isNone(String value) {
        if (value == null) return true;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.equals("empty") || normalized.equals("null") || normalized.equals("none") || normalized.equals("{}") || normalized.equals("[]");
    }

    private static boolean isCurrent(String value) {
        return value != null && value.trim().equalsIgnoreCase("current");
    }

    private static int parseInt(String name, String value) {
        try {
            return Integer.decode(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + name + ": " + value);
        }
    }

    private static long parseLong(String name, String value) {
        try {
            return Long.decode(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long for " + name + ": " + value);
        }
    }

    private static Instant instantArg(Map<String, String> args, Instant fallback, String... names) {
        String value = firstArg(args, names);
        return value == null ? fallback : parseInstant(value);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank() || "now".equalsIgnoreCase(value.trim())) return Instant.now();
        String trimmed = value.trim();
        if (trimmed.matches("-?\\d+")) return Instant.ofEpochMilli(Long.parseLong(trimmed));
        return Instant.parse(trimmed);
    }

    private static ArgumentSignatures parseArgumentSignatures(String value) {
        if (value == null || isNone(value)) return ArgumentSignatures.EMPTY;
        throw new IllegalArgumentException("ArgumentSignatures supports only empty/null from .send.");
    }

    private static LastSeenMessages.Update parseLastSeenUpdate(String value) {
        if (value == null || isNone(value)) return new LastSeenMessages.Update(0, new BitSet(20), (byte) 0);
        String[] parts = value.split("[,;]");
        if (parts.length == 1) {
            return new LastSeenMessages.Update(parseInt("lastSeenOffset", parts[0].trim()), new BitSet(20), (byte) 0);
        }
        throw new IllegalArgumentException("lastSeen supports empty/null or an offset integer.");
    }

    private static MessageSignature parseMessageSignature(String value) {
        if (value == null || isNone(value)) return null;
        byte[] bytes = parseBytes(value);
        if (bytes.length != MessageSignature.BYTES) {
            throw new IllegalArgumentException("MessageSignature must be exactly 256 bytes.");
        }
        return new MessageSignature(bytes);
    }

    private static RemoteChatSession.Data currentChatSessionData() {
        if (MC == null || MC.getConnection() == null) return null;
        try {
            Field field = MC.getConnection().getClass().getDeclaredField("chatSession");
            field.setAccessible(true);
            Object value = field.get(MC.getConnection());
            if (value instanceof LocalChatSession localChatSession) {
                return localChatSession.asRemote().asData();
            }
        } catch (Throwable ignored) {  }
        for (Field field : MC.getConnection().getClass().getDeclaredFields()) {
            try {
                if (field.getType() != LocalChatSession.class) continue;
                field.setAccessible(true);
                Object value = field.get(MC.getConnection());
                if (value instanceof LocalChatSession localChatSession) {
                    return localChatSession.asRemote().asData();
                }
            } catch (Throwable ignored) {  }
        }
        return null;
    }

    private static boolean parseBoolean(String name, String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean for " + name + ": " + value);
        };
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumClass, String value) {
        String wanted = normalizeName(value);
        for (E constant : enumClass.getEnumConstants()) {
            if (normalizeName(constant.name()).equals(wanted)) return constant;
        }
        throw new IllegalArgumentException("Invalid " + enumClass.getSimpleName() + ": " + value
            + ". Options: " + enumOptions(enumClass));
    }

    private static String enumOptions(Class<? extends Enum<?>> enumClass) {
        List<String> names = new ArrayList<>();
        for (Enum<?> constant : enumClass.getEnumConstants()) names.add(constant.name());
        return String.join(", ", names);
    }

    private static void requireOnly(Map<String, String> args, String... allowed) {
        Set<String> normalized = new java.util.HashSet<>();
        for (String name : allowed) normalized.add(normalizeName(name));
        for (String key : args.keySet()) {
            if (!normalized.contains(key)) throw new IllegalArgumentException("Unknown argument: " + key);
        }
    }

    private static void ensureNoUnknown(Map<String, String> args, List<String> allowed) {
        Set<String> normalized = new java.util.HashSet<>();
        for (String name : allowed) normalized.add(normalizeName(name));
        for (String key : args.keySet()) {
            if (!normalized.contains(key)) throw new IllegalArgumentException("Unknown argument: " + key);
        }
    }

    private static List<String> componentNames(RecordComponent[] components) {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : components) names.add(component.getName());
        return names;
    }

    private static List<String> expandableAllowedNames(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String name : names) {
            for (String alias : aliasNames(name)) out.add(alias);
        }
        return out;
    }

    private static String[] aliasNames(String name) {
        String normalized = normalizeName(name);
        return switch (normalized) {
            case "slotnum", "slotid" -> new String[]{name, "slot"};
            case "buttonnum", "buttonid" -> new String[]{name, "button"};
            case "selecteditemindex" -> new String[]{name, "index"};
            case "containerid" -> new String[]{name, "id"};
            case "containerinput" -> new String[]{name, "input", "click"};
            case "transactionid" -> new String[]{name, "id", "tx"};
            case "entityid" -> new String[]{name, "id", "entity"};
            case "yrot" -> new String[]{name, "yaw"};
            case "xrot" -> new String[]{name, "pitch"};
            case "position", "blockpos" -> new String[]{name, "pos"};
            case "uuid" -> new String[]{name, "id"};
            case "profileid" -> new String[]{name, "uuid", "id"};
            case "isopen" -> new String[]{name, "open"};
            case "isfiltering" -> new String[]{name, "filtering"};
            case "isflying" -> new String[]{name, "flying"};
            case "includedata" -> new String[]{name, "data"};
            case "usemaxitems" -> new String[]{name, "max"};
            case "isfronttext" -> new String[]{name, "front"};
            case "itemstack" -> new String[]{name, "item"};
            case "carrieditem" -> new String[]{name, "carried"};
            case "changedslots" -> new String[]{name, "changed"};
            case "keybytes" -> new String[]{name, "key"};
            case "encryptedchallenge" -> new String[]{name, "challenge"};
            default -> new String[]{name};
        };
    }

    private static String getArg(Map<String, String> args, String name) {
        return args.get(normalizeName(name));
    }

    private static String firstArg(Map<String, String> args, String... names) {
        for (String name : names) {
            String value = getArg(args, name);
            if (value != null) return value;
        }
        return null;
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim()
            .replace("-", "")
            .replace("_", "")
            .replace(".", "")
            .toLowerCase(Locale.ROOT);
    }

    private static String exampleFor(String javaType, String kind) {
        String lower = (javaType == null ? "" : javaType).toLowerCase(Locale.ROOT);
        if ("boolean".equals(kind)) return "true";
        if ("enum".equals(kind)) return "VALUE";
        if ("identifier".equals(kind)) return "minecraft:stone";
        if ("string".equals(kind)) return "\"text\"";
        if (lower.contains("blockpos")) return "0,64,0";
        if (lower.contains("float") || lower.contains("double")) return "0.0";
        if ("number".equals(kind)) return "0";
        return "<" + (javaType == null || javaType.isBlank() ? "value" : javaType) + ">";
    }

    private static List<String> compactUsageFields(List<String> fields) {
        List<String> out = new ArrayList<>();
        for (String field : fields) {
            int typeStart = field.indexOf(" (");
            out.add(typeStart >= 0 ? field.substring(0, typeStart) : field);
        }
        return out;
    }

    private static String shortType(String javaType, String kind) {
        if (kind != null && !kind.isBlank() && !"object".equals(kind)) return kind;
        if (javaType == null || javaType.isBlank()) return "value";
        int generic = javaType.indexOf('<');
        String base = generic >= 0 ? javaType.substring(0, generic) : javaType;
        int dot = Math.max(base.lastIndexOf('.'), base.lastIndexOf('$'));
        return dot >= 0 ? base.substring(dot + 1) : base;
    }

    private static String displayName(Class<?> packetClass) {
        @SuppressWarnings("unchecked")
        String known = AutismPacketRegistry.getName((Class<? extends Packet<?>>) packetClass);
        return known != null ? known : packetClass.getSimpleName().replace('$', '.');
    }

    private static float currentYRot() {
        return MC != null && MC.player != null ? MC.player.getYRot() : 0.0F;
    }

    private static float currentXRot() {
        return MC != null && MC.player != null ? MC.player.getXRot() : 0.0F;
    }

    private static double currentX() {
        return MC != null && MC.player != null ? MC.player.getX() : 0.0D;
    }

    private static double currentY() {
        return MC != null && MC.player != null ? MC.player.getY() : 0.0D;
    }

    private static double currentZ() {
        return MC != null && MC.player != null ? MC.player.getZ() : 0.0D;
    }

    private static boolean currentOnGround() {
        return MC != null && MC.player != null && MC.player.onGround();
    }

    private static boolean currentHorizontalCollision() {
        return MC != null && MC.player != null && MC.player.horizontalCollision;
    }

    private static int currentSelectedSlot() {
        if (MC == null || MC.player == null) throw new IllegalArgumentException("Player is required for current selected slot.");
        return MC.player.getInventory().getSelectedSlot();
    }

    private static int currentContainerId() {
        if (MC == null || MC.player == null || MC.player.containerMenu == null) {
            throw new IllegalArgumentException("Open/current container is required.");
        }
        return MC.player.containerMenu.containerId;
    }

    private static int currentContainerStateId() {
        if (MC == null || MC.player == null || MC.player.containerMenu == null) return 0;
        return MC.player.containerMenu.getStateId();
    }

    private static int currentEntityId() {
        if (MC == null || !(MC.hitResult instanceof EntityHitResult entityHit) || entityHit.getType() == HitResult.Type.MISS) {
            throw new IllegalArgumentException("Look at an entity or provide entityId=...");
        }
        return entityHit.getEntity().getId();
    }

    private static Vec3 currentEntityHitLocation(Vec3 fallback) {
        if (MC != null && MC.hitResult instanceof EntityHitResult entityHit && entityHit.getType() != HitResult.Type.MISS) {
            return entityHit.getLocation();
        }
        return fallback;
    }

    private static BlockHitResult currentBlockHit() {
        if (MC == null || !(MC.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() == HitResult.Type.MISS) return null;
        return blockHit;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    public record Result(Packet<?> packet, boolean ok, boolean help, String message, String source) {
        static Result ok(Packet<?> packet, String source) {
            return new Result(packet, true, false, "", source == null ? "builder" : source);
        }

        static Result error(String message) {
            return new Result(null, false, false, message == null ? "Packet build failed." : message, "");
        }

        static Result help(String message) {
            return new Result(null, false, true, message == null ? "" : message, "");
        }
    }

    public record PreparedArgs(String args, boolean dryRun) {
    }
}
