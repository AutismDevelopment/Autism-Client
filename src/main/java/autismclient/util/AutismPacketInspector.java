package autismclient.util;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AutismPacketInspector {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Object INACCESSIBLE = new Object();
    private static final Pattern SIMPLE_CLASS_ALIAS_PATTERN = Pattern.compile("(?<![\\w$.])(class_\\d+)(?=[\\[{(:\\s]|$)");
    private static final Pattern QUALIFIED_CLASS_ALIAS_PATTERN = Pattern.compile("(net\\.minecraft(?:\\.[\\w$]+)*\\.class_\\d+)(?=[\\[{(:\\s]|$)");
    private static final Pattern IDENTIFIER_TOKEN_PATTERN = Pattern.compile("(?i)\\b[a-z0-9_.-]+:[a-z0-9_./-]+\\b");
    private static final Pattern READABLE_RUN_PATTERN = Pattern.compile("[\\x20-\\x7E]{4,}");
    private static final Pattern BASE64_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9+/=])(?:[A-Za-z0-9+/]{16,}={0,2})(?![A-Za-z0-9+/=])");
    private static final Pattern HEX_TOKEN_PATTERN = Pattern.compile("(?i)(?<![0-9a-f])(?:0x)?[0-9a-f]{16,}(?![0-9a-f])");
    private static final Pattern UUID_TEXT_PATTERN = Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern HOST_TOKEN_PATTERN = Pattern.compile("(?i)\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}(?::\\d{1,5})?\\b|\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b");
    private static final int MAX_DEPTH = 7;
    private static final int MAX_LINES = 900;
    private static final int MAX_COLLECTION_ITEMS = 64;
    private static final int MAX_ARRAY_ITEMS = 64;
    private static final int MAX_STRING_LENGTH = 320;
    private static final int MAX_GENERIC_SUMMARY_LINES = 8;
    private static final int MAX_DECOMPRESSED_PAYLOAD_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ANALYZER_ITEMS = 48;
    private static final Set<String> IGNORED_PROPERTY_METHODS = Set.of(
        "getClass",
        "getPacketType",
        "toString",
        "hashCode",
        "copy",
        "apply",
        "write",
        "read",
        "streamCodec",
        "codec",
        "isWritingErrorSkippable",
        "writingErrorSkippable",
        "WritingErrorSkippable"
    );
    private static final Set<String> IGNORED_PROPERTY_NORMALIZED = Set.of(
        "getclass",
        "getpackettype",
        "tostring",
        "hashcode",
        "copy",
        "apply",
        "write",
        "read",
        "streamcodec",
        "codec",
        "iswritingerrorskippable",
        "writingerrorskippable"
    );
    private static final Set<String> EXTRA_ACCESSOR_NAMES = Set.of(
        "id",
        "syncId",
        "revision",
        "slot",
        "contents",
        "cursorStack",
        "trackedValues",
        "values",
        "reason",
        "value",
        "hand",
        "sequence",
        "yaw",
        "pitch",
        "chunkX",
        "chunkZ",
        "chunkData",
        "lightData",
        "sound",
        "category",
        "x",
        "y",
        "z",
        "onGround",
        "name",
        "screenHandlerType",
        "type",
        "state",
        "mode",
        "seed"
    );
    private AutismPacketInspector() {
    }

    public static PacketInspection inspectSafe(AutismPacketLoggerOverlay.LogEntry entry) {
        return inspectSafe(entry, null, true);
    }

    public static PacketInspection inspectSafe(AutismPacketLoggerOverlay.LogEntry entry, PayloadAnalysisView payloadAnalysis) {
        return inspectSafe(entry, payloadAnalysis, true);
    }

    public static PacketInspection inspectSafe(AutismPacketLoggerOverlay.LogEntry entry, PayloadAnalysisView payloadAnalysis,
                                               boolean includePayloadAnalyzer) {
        try {
            return inspect(entry, payloadAnalysis, includePayloadAnalyzer);
        } catch (Throwable t) {
            InspectionBuilder builder = new InspectionBuilder(entry.shortName + " [" + entry.direction + "]");
            builder.section("Meta", AutismColors.packetLightYellow());
            builder.line("Name: " + entry.shortName, AutismColors.packetWhite());
            builder.line("Direction: " + entry.direction, directionColor(entry.direction));
            builder.line("Class: " + entry.packetClass.getName(), AutismColors.textSecondary());
            builder.line("Tick: " + entry.gameTick, AutismColors.textSecondary());
            builder.line("Time: " + Instant.ofEpochMilli(entry.timestampMs), AutismColors.textSecondary());
            builder.blank();
            builder.section("Inspect Error", AutismColors.dangerText());
            builder.line("The detailed inspector failed on this packet, so this fallback view was opened instead.", AutismColors.dangerText());
            builder.line("Reason: " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " - " + shorten(t.getMessage())),
                AutismColors.dangerText());
            if (entry.packetRef != null) {
                builder.blank();
                builder.section("Fallback", AutismColors.packetYellow());
                builder.line("Packet Type: " + safeLeafString(invokeNoArg(entry.packetRef, "getPacketType")), AutismColors.textSecondary());
                builder.line("toString(): " + shorten(String.valueOf(entry.packetRef)), AutismColors.textMuted());
            }
            return builder.build();
        }
    }

    public static PacketInspection inspect(AutismPacketLoggerOverlay.LogEntry entry) {
        return inspect(entry, null, true);
    }

    public static PacketInspection inspect(AutismPacketLoggerOverlay.LogEntry entry, PayloadAnalysisView payloadAnalysis) {
        return inspect(entry, payloadAnalysis, true);
    }

    public static PacketInspection inspect(AutismPacketLoggerOverlay.LogEntry entry, PayloadAnalysisView payloadAnalysis,
                                           boolean includePayloadAnalyzer) {
        String title = entry.shortName + " [" + entry.direction + "]";
        InspectionBuilder builder = new InspectionBuilder(title);
        AutismPayloadSupport.PayloadSnapshot payloadSnapshot = entry == null ? null : AutismPayloadSupport.snapshotFromEntry(entry);

        builder.section("Meta", AutismColors.packetLightYellow());
        builder.line("Name: " + entry.shortName, AutismColors.packetWhite());
        builder.line("Direction: " + entry.direction, directionColor(entry.direction));
        builder.line("Class: " + entry.packetClass.getName(), AutismColors.textSecondary());
        if (payloadSnapshot != null) {
            builder.line("Payload Channel: " + payloadSnapshot.channel(), AutismColors.successText());
            builder.line("Payload Size: " + payloadSnapshot.sizeBytes() + " bytes", AutismColors.textPrimary());
            AutismPayloadChannelListeners.Match filter = payloadFilterMatch(payloadSnapshot);
            if (filter != null) {
                builder.line("Matched Filter: " + filter.label() + " (" + filter.pattern() + ")", AutismColors.packetGreen());
            }
            String source = describePayloadChannel(payloadSnapshot.channel());
            if (source != null && !source.isBlank()) {
                builder.line("Likely Source: " + source, AutismColors.textSecondary());
            }
        }
        Object packetType = invokeNoArg(entry.packetRef, "getPacketType");
        if (packetType != null) {
            builder.line("Packet Type: " + safeLeafString(packetType), AutismColors.textSecondary());
        }
        builder.line("Tick: " + entry.gameTick, AutismColors.textSecondary());
        builder.line("Time: " + Instant.ofEpochMilli(entry.timestampMs), AutismColors.textSecondary());
        builder.line("Inventory: " + entry.isInventory + " | Movement: " + entry.isMovement,
            AutismColors.textMuted());
        AutismNormalPacketAnalyzer.Analysis normalAnalysis = AutismNormalPacketAnalyzer.analyze(entry);

        if (entry.packetRef == null) {
            builder.blank();
            builder.section("Reality", AutismColors.packetCyan());
            if (payloadSnapshot != null) {
                appendPayloadSnapshotSummary(payloadSnapshot, builder);
                builder.line("Packet Object: missing; showing preserved payload snapshot.", AutismColors.textMuted());
            } else {
                builder.line("Packet instance is missing, so only metadata is available.", AutismColors.dangerText());
            }
            if (normalAnalysis.hasContext()) {
                builder.blank();
                builder.section("Meaning", AutismColors.packetYellow());
                appendInspectionLines(builder, normalAnalysis.contextLines());
            }
            if (includePayloadAnalyzer && payloadSnapshot != null) {
                InspectionBuilder analyzerBuilder = new InspectionBuilder(title);
                if (appendPayloadAnalyzer(payloadSnapshot, analyzerBuilder, payloadAnalysis)) {
                    builder.blank();
                    builder.section("Analyzer", AutismColors.packetGreen());
                    builder.appendFrom(analyzerBuilder);
                }
            }
            if (normalAnalysis.hasWire()) {
                builder.blank();
                builder.section("Wire", AutismColors.packetBlue());
                appendInspectionLines(builder, normalAnalysis.wireLines());
            }
            return builder.build();
        }

        SummaryResult summary = SummaryResult.NONE;
        if (normalAnalysis.hasDecoded()) {
            builder.blank();
            builder.section("Reality", AutismColors.packetCyan());
            appendInspectionLines(builder, normalAnalysis.decodedLines());
        } else if (payloadSnapshot != null) {
            builder.blank();
            builder.section("Reality", AutismColors.packetCyan());
            appendPayloadSnapshotSummary(payloadSnapshot, builder);
        } else {
            InspectionBuilder summaryBuilder = new InspectionBuilder(title);
            summary = appendSummary(entry.packetRef, entry.shortName, entry, summaryBuilder);
            if (summary.wroteAny()) {
                builder.blank();
                builder.section("Reality", AutismColors.packetCyan());
                builder.appendFrom(summaryBuilder);
            }
        }

        if (normalAnalysis.hasContext()) {
            builder.blank();
            builder.section("Meaning", AutismColors.packetYellow());
            appendInspectionLines(builder, normalAnalysis.contextLines());
        }

        if (includePayloadAnalyzer && payloadSnapshot != null) {
            InspectionBuilder analyzerBuilder = new InspectionBuilder(title);
            if (appendPayloadAnalyzer(payloadSnapshot, analyzerBuilder, payloadAnalysis)) {
                builder.blank();
                builder.section("Analyzer", AutismColors.packetGreen());
                builder.appendFrom(analyzerBuilder);
            }
        }

        if (normalAnalysis.hasWire()) {
            builder.blank();
            builder.section("Wire", AutismColors.packetBlue());
            appendInspectionLines(builder, normalAnalysis.wireLines());
        }

        boolean semanticComplete = normalAnalysis.hasDecoded() ? normalAnalysis.complete() : summary.complete();
        if (!semanticComplete) {
            builder.blank();
            builder.section("Details", AutismColors.packetBlue());
            dumpRootFields(entry.packetRef, builder, new IdentityHashMap<>());
        }
        return builder.build();
    }

    public static PacketInspection payloadAnalyzerInspectionSafe(AutismPacketLoggerOverlay.LogEntry entry,
                                                                PayloadAnalysisView payloadAnalysis) {
        try {
            String title = entry == null ? "Payload Analyzer" : entry.shortName + " Analyzer";
            InspectionBuilder builder = new InspectionBuilder(title);
            AutismPayloadSupport.PayloadSnapshot snapshot = entry == null ? null : AutismPayloadSupport.snapshotFromEntry(entry);
            builder.section("Analyzer", AutismColors.packetGreen());
            if (snapshot == null) {
                builder.line("No payload snapshot is available for this packet.", AutismColors.dangerText());
                return builder.build();
            }
            if (payloadAnalysis == null) {
                builder.line("Channel: " + snapshot.channel(), AutismColors.successText());
                builder.line("Direction: " + snapshot.direction() + " | Protocol: " + emptyTo(snapshot.protocolPhase(), "unknown"),
                    directionColor(snapshot.direction()));
                builder.line("Body Size: " + snapshot.rawBytes().length + " bytes", AutismColors.textPrimary());
                String source = describePayloadChannel(snapshot.channel());
                if (source != null && !source.isBlank()) {
                    builder.line("What It Looks Like: " + source, AutismColors.textPrimary());
                }
                builder.blank();
                builder.line("Decoder Status: waiting for automatic payload analysis.", AutismColors.packetYellow());
                return builder.build();
            }
            appendPayloadAnalyzer(snapshot, builder, payloadAnalysis);
            return builder.build();
        } catch (Throwable t) {
            InspectionBuilder builder = new InspectionBuilder("Payload Analyzer");
            builder.section("Analyzer Error", AutismColors.dangerText());
            builder.line("Reason: " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " - " + shorten(t.getMessage())),
                AutismColors.dangerText());
            return builder.build();
        }
    }

    public static PayloadAnalysisView payloadAnalysisLoading(AutismPayloadSupport.PayloadSnapshot snapshot) {
        String channel = snapshot == null ? "payload" : emptyTo(snapshot.channel(), "payload");
        return new PayloadAnalysisView(false, false, 0.02D, "Preparing " + channel, List.of(
            new InspectionLine("Decoder Status: preparing automatic payload analysis", AutismColors.textSecondary())
        ));
    }

    public static PayloadAnalysisView payloadAnalysisFailed(String message) {
        String reason = message == null || message.isBlank() ? "unknown error" : message;
        return new PayloadAnalysisView(true, true, 1.0D, "Decoder failed", List.of(
            new InspectionLine("Decoder Status: failed", AutismColors.dangerText()),
            new InspectionLine("Reason: " + shorten(reason), AutismColors.dangerText())
        ));
    }

    public static PayloadAnalysisView analyzePayload(AutismPayloadSupport.PayloadSnapshot snapshot,
                                                     Consumer<PayloadAnalysisView> progressSink) {
        if (snapshot == null) {
            PayloadAnalysisView empty = new PayloadAnalysisView(true, false, 1.0D, "No payload body", List.of(
                new InspectionLine("No payload snapshot is available.", AutismColors.textMuted())
            ));
            if (progressSink != null) progressSink.accept(empty);
            return empty;
        }
        InspectionBuilder builder = new InspectionBuilder("Payload Analyzer");
        try {
            appendPayloadAnalyzer(snapshot, builder, progressSink);
            PayloadAnalysisView done = new PayloadAnalysisView(true, false, 1.0D, finalPayloadAnalysisStatus(builder.lines), builder.lines);
            if (progressSink != null) progressSink.accept(done);
            return done;
        } catch (Throwable t) {
            PayloadAnalysisView failed = payloadAnalysisFailed(t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            if (progressSink != null) progressSink.accept(failed);
            return failed;
        }
    }

    private static String finalPayloadAnalysisStatus(List<InspectionLine> lines) {
        if (lines != null) {
            for (InspectionLine line : lines) {
                String text = line == null ? "" : line.getText();
                if (text == null) continue;
                String lower = text.toLowerCase(Locale.ROOT);
                if (lower.contains("complete known-schema decode")) return "Known schema decoded";
                if (lower.contains("confidence: high")) return "High-confidence analysis";
                if (lower.contains("confidence: partial")) return "Partial analysis complete";
                if (lower.contains("binary payload")) return "Binary analysis complete";
            }
        }
        return "Analysis finished";
    }

    private static SummaryResult appendSummary(Packet<?> packet, String packetNameHint, AutismPacketLoggerOverlay.LogEntry entry,
                                               InspectionBuilder builder) {
        SummaryResult packetSpecific = appendPacketSpecificSummary(packet, packetNameHint, entry, builder);
        if (packetSpecific.wroteAny()) {
            return packetSpecific;
        }
        return appendGenericSummary(packet, builder);
    }

    private static boolean appendPayloadSnapshotSummary(AutismPayloadSupport.PayloadSnapshot snapshot, InspectionBuilder builder) {
        if (snapshot == null) return false;
        byte[] raw = snapshot.rawBytes();
        String channel = emptyTo(snapshot.channel(), "unknown");
        builder.line("Channel: " + channel, AutismColors.successText());
        AutismPayloadChannelListeners.Match filter = payloadFilterMatch(snapshot);
        if (filter != null) {
            builder.line("Matched Filter: " + filter.label() + " (" + filter.pattern() + ")", AutismColors.packetGreen());
        }
        String source = describePayloadChannel(channel);
        if (source != null && !source.isBlank()) {
            builder.line("Likely Source: " + source, AutismColors.textPrimary());
        }
        builder.line("Payload Direction: " + snapshot.direction() + " | Protocol: " + emptyTo(snapshot.protocolPhase(), "unknown"),
            directionColor(snapshot.direction()));
        if (snapshot.packetId() >= 0) {
            builder.line("Payload Packet ID: " + snapshot.packetId(), AutismColors.textSecondary());
        }
        builder.line("Payload Class: " + emptyTo(snapshot.payloadClassName(), "unknown"), AutismColors.textSecondary());
        builder.line("Payload Size: " + raw.length + " bytes", AutismColors.textPrimary());
        if (snapshot.commandApiValue() != null) {
            builder.line("CommandApi Value: " + snapshot.commandApiValue(), AutismColors.successText());
        }

        AutismPayloadTemplate.Preview preview = AutismPayloadTemplate.preview(raw);
        if (isRegisterChannel(channel)) {
            List<String> channels = extractIdentifierTokens(raw, 10);
            if (!channels.isEmpty()) {
                int total = identifierTokenCount(raw);
                builder.line((isUnregisterChannel(channel) ? "Unregistered" : "Registered")
                    + " Channels: " + total, AutismColors.successText());
                for (String value : channels) {
                    builder.line("  - " + value, AutismColors.textPrimary());
                }
                if (total > channels.size()) {
                    builder.line("  ... +" + (total - channels.size()) + " more", AutismColors.textMuted());
                }
            }
        }
        if (preview.minecraftString() != null && !preview.minecraftString().startsWith("<")) {
            builder.line("Minecraft String: " + quote(shorten(preview.minecraftString())), AutismColors.successText());
        }
        if (preview.javaWriteUtf() != null && !preview.javaWriteUtf().startsWith("<")) {
            builder.line("Java writeUTF: " + quote(shorten(preview.javaWriteUtf())), AutismColors.successText());
        }
        if (preview.utf8() != null && !preview.utf8().isBlank() && !"<binary>".equals(preview.utf8())) {
            builder.line("UTF-8 Guess: " + quote(shorten(cleanControlChars(preview.utf8()))), AutismColors.successText());
        }
        List<String> identifiers = extractIdentifierTokens(raw, 6);
        if (!identifiers.isEmpty() && !isRegisterChannel(channel)) {
            builder.line("Identifier Tokens: " + identifiers.size(), AutismColors.textPrimary());
            for (String identifier : identifiers) {
                builder.line("  - " + identifier, AutismColors.textSecondary());
            }
        }
        builder.line("Payload Hex: " + preview.hex(), AutismColors.textMuted());
        builder.line("Analyzer: automatic payload decoding runs when this packet is inspected.", AutismColors.textSecondary());
        return true;
    }

    private static void appendInspectionLines(InspectionBuilder builder, List<InspectionLine> lines) {
        if (builder == null || lines == null) return;
        for (InspectionLine line : lines) {
            if (line != null) builder.line(line.getText(), line.getColor());
        }
    }

    private static AutismPayloadChannelListeners.Match payloadFilterMatch(AutismPayloadSupport.PayloadSnapshot snapshot) {
        if (snapshot == null) return null;
        try {
            return new AutismPayloadChannelListeners().match(snapshot, snapshot.direction());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SummaryResult appendPacketSpecificSummary(Packet<?> packet, String packetNameHint,
                                                             AutismPacketLoggerOverlay.LogEntry entry,
                                                             InspectionBuilder builder) {
        boolean wrote = false;
        boolean complete = false;

        if (packet instanceof ServerboundPlayerActionPacket playerAction) {
            wrote = true;
            complete = true;
            builder.line("Action: " + playerAction.getAction(), AutismColors.textPrimary());
            builder.line("Meaning: " + describePlayerAction(playerAction.getAction()), AutismColors.textPrimary());
            builder.line("Block Pos: " + formatBlockPos(playerAction.getPos()), AutismColors.textPrimary());
            builder.line("Face: " + playerAction.getDirection(), AutismColors.textPrimary());
            builder.line("Sequence: " + playerAction.getSequence(), AutismColors.textSecondary());
            String capturedBlockState = entry == null ? null : entry.capturedBlockState;
            if (capturedBlockState != null && !capturedBlockState.isBlank()) {
                builder.line("Block: " + capturedBlockState, AutismColors.successText());
            } else {
                BlockState state = getWorldBlockState(playerAction.getPos());
                if (state != null) {
                    builder.line("Block: " + formatBlockState(state), AutismColors.successText());
                }
            }
            if (MC.player != null && (playerAction.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || playerAction.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                || playerAction.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM)) {
                builder.line("Held Item: " + formatItemStack(MC.player.getMainHandItem()), AutismColors.successText());
            }
        }

        if (packet instanceof ServerboundUseItemOnPacket interactBlock) {
            BlockHitResult hit = interactBlock.getHitResult();
            wrote = true;
            complete = true;
            builder.line("Interaction: Use Block (right click targeted block)", AutismColors.textPrimary());
            builder.line("Hand: " + interactBlock.getHand(), AutismColors.textPrimary());
            builder.line("Block Pos: " + formatBlockPos(hit.getBlockPos()), AutismColors.textPrimary());
            builder.line("Side: " + hit.getDirection(), AutismColors.textPrimary());
            builder.line("Hit Pos: " + formatVec3(hit.getLocation()), AutismColors.textPrimary());
            builder.line("Inside Block: " + hit.isInside(), AutismColors.textPrimary());
            builder.line("Sequence: " + interactBlock.getSequence(), AutismColors.textSecondary());
            if (MC.player != null) {
                builder.line("Held Item: " + formatItemStack(MC.player.getItemInHand(interactBlock.getHand())), AutismColors.successText());
            }
            String capturedBlockState = entry == null ? null : entry.capturedBlockState;
            if (capturedBlockState != null && !capturedBlockState.isBlank()) {
                builder.line("Block: " + capturedBlockState, AutismColors.successText());
            } else {
                BlockState state = getWorldBlockState(hit.getBlockPos());
                if (state != null) {
                    builder.line("Block: " + formatBlockState(state), AutismColors.successText());
                }
            }
        }

        if (packet instanceof ServerboundUseItemPacket interactItem) {
            wrote = true;
            complete = true;
            builder.line("Interaction: Use Item (right click without a targeted block)", AutismColors.textPrimary());
            builder.line("Hand: " + interactItem.getHand(), AutismColors.textPrimary());
            builder.line("Sequence: " + interactItem.getSequence(), AutismColors.textSecondary());
            builder.line("Yaw: " + interactItem.getYRot(), AutismColors.textSecondary());
            builder.line("Pitch: " + interactItem.getXRot(), AutismColors.textSecondary());
            if (MC.player != null) {
                Object selectedSlot = invokeFirstNoArg(MC.player.getInventory(), "getSelectedSlot", "selectedSlot");
                if (selectedSlot instanceof Number number) {
                    builder.line("Selected Slot: " + formatHotbarSlot(number.intValue()), AutismColors.textPrimary());
                }
                builder.line("Held Item: " + formatItemStack(MC.player.getItemInHand(interactItem.getHand())), AutismColors.successText());
            }
        }

        if (packet instanceof ServerboundInteractPacket interactEntity) {
            wrote = true;
            complete = true;
            int entityId = interactEntity.entityId();
            appendEntityIdLine(builder, "Entity Id", entityId);
            builder.line("Player Sneaking: " + interactEntity.usingSecondaryAction(), AutismColors.textPrimary());
            InteractionCapture capture = captureInteraction(interactEntity);
            if (capture.kind != null) {
                builder.line("Interaction: " + capture.kind, AutismColors.textPrimary());
            }
            if (capture.hand != null) {
                builder.line("Hand: " + capture.hand, AutismColors.textPrimary());
                if (MC.player != null) {
                    builder.line("Held Item: " + formatItemStack(MC.player.getItemInHand(capture.hand)), AutismColors.successText());
                }
            }
            if (capture.hitPos != null) {
                builder.line("Hit Pos: " + formatVec3(capture.hitPos), AutismColors.textPrimary());
            }
        }

        if (packet instanceof ClientboundOpenScreenPacket openScreen) {
            wrote = true;
            complete = true;
            builder.line("Sync Id: " + openScreen.getContainerId(), AutismColors.textPrimary());
            builder.line("Screen Type: " + safeLeafString(openScreen.getType()), AutismColors.textPrimary());
            builder.line("Title: " + formatSimpleValue(openScreen.getTitle()), AutismColors.successText());
        }

        if (packet instanceof ClientboundContainerSetSlotPacket slotUpdate) {
            wrote = true;
            complete = true;
            builder.line("Sync Id: " + slotUpdate.getContainerId(), AutismColors.textPrimary());
            builder.line("Revision: " + slotUpdate.getStateId(), AutismColors.textSecondary());
            builder.line("Slot: " + slotUpdate.getSlot(), AutismColors.textPrimary());
            builder.line("Stack: " + formatItemStack(slotUpdate.getItem()), AutismColors.successText());
        }

        if (packet instanceof ClientboundContainerSetContentPacket inventory) {
            wrote = true;
            builder.line("Sync Id: " + inventory.containerId(), AutismColors.textPrimary());
            builder.line("Revision: " + inventory.stateId(), AutismColors.textSecondary());
            builder.line("Items: " + inventory.items().size() + " slots (" + countNonEmpty(inventory.items()) + " non-empty)",
                AutismColors.textPrimary());
            builder.line("Cursor: " + formatItemStack(inventory.carriedItem()), AutismColors.successText());
        }

        if (packet instanceof ClientboundLevelChunkWithLightPacket chunkData) {
            wrote = true;
            builder.line("Chunk: " + chunkData.getX() + ", " + chunkData.getZ(), AutismColors.textPrimary());
            builder.line("Chunk Data: " + safeLeafString(chunkData.getChunkData()), AutismColors.textSecondary());
            builder.line("Light Data: " + safeLeafString(chunkData.getLightData()), AutismColors.textSecondary());
        }

        if (packet instanceof ClientboundEntityPositionSyncPacket positionSync) {
            wrote = true;
            appendEntityIdLine(builder, "Entity Id", positionSync.id());
            builder.line("Values: " + safeLeafString(positionSync.values()), AutismColors.textPrimary());
            builder.line("On Ground: " + positionSync.onGround(), AutismColors.textPrimary());
        }

        if (packet instanceof ClientboundSetEntityDataPacket trackerUpdate) {
            wrote = true;
            appendEntityIdLine(builder, "Entity Id", trackerUpdate.id());
            List<?> trackedValues = trackerUpdate.packedItems();
            builder.line("Tracked Values: " + (trackedValues == null ? 0 : trackedValues.size()), AutismColors.textPrimary());
        }

        if (packet instanceof ClientboundAddEntityPacket addEntity) {
            wrote = true;
            complete = true;
            appendEntityIdLine(builder, "Entity Id", addEntity.getId());
            builder.line("Entity Type: " + formatEntityType(addEntity.getType()), AutismColors.successText());
            builder.line("Uuid: " + addEntity.getUUID(), AutismColors.textSecondary());
            builder.line("Pos: " + formatVec3(new Vec3(addEntity.getX(), addEntity.getY(), addEntity.getZ())), AutismColors.textPrimary());
            builder.line("Movement: " + formatVec3(addEntity.getMovement()), AutismColors.textPrimary());
            builder.line("Rot: yaw=" + addEntity.getYRot() + ", pitch=" + addEntity.getXRot() + ", head=" + addEntity.getYHeadRot(),
                AutismColors.textPrimary());
            builder.line("Data: " + addEntity.getData(), AutismColors.textSecondary());
        }

        if (packet instanceof ClientboundSoundPacket soundPacket) {
            wrote = true;
            complete = true;
            builder.line("Sound: " + formatHolder(soundPacket.getSound()), AutismColors.successText());
            builder.line("Category: " + soundPacket.getSource(), AutismColors.textPrimary());
            builder.line("Pos: " + formatVec3(new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ())), AutismColors.textPrimary());
            builder.line("Volume: " + soundPacket.getVolume(), AutismColors.textSecondary());
            builder.line("Pitch: " + soundPacket.getPitch(), AutismColors.textSecondary());
            builder.line("Seed: " + soundPacket.getSeed(), AutismColors.textMuted());
        }

        if (packet instanceof ClientboundGameEventPacket gameState) {
            wrote = true;
            complete = true;
            builder.line("Reason: " + safeLeafString(gameState.getEvent()), AutismColors.textPrimary());
            builder.line("Value: " + gameState.getParam(), AutismColors.textPrimary());
        }

        SummaryResult broadKnown = appendBroadKnownPacketSummary(packet, builder);
        if (!wrote && broadKnown.wroteAny()) {
            return broadKnown;
        }
        if (broadKnown.wroteAny()) {
            wrote = true;
            complete = complete && broadKnown.complete();
        }

        AutismPayloadSupport.PayloadSnapshot payloadSnapshot = entry == null ? null : AutismPayloadSupport.snapshotFromEntry(entry);
        if (payloadSnapshot != null) {
            wrote = true;
            builder.line("Channel: " + payloadSnapshot.channel(), AutismColors.textPrimary());
            if (payloadSnapshot.protocolPhase() != null && !payloadSnapshot.protocolPhase().isBlank()) {
                builder.line("Payload Protocol: " + payloadSnapshot.protocolPhase(), AutismColors.textSecondary());
            }
            builder.line("Payload Class: " + payloadSnapshot.payloadClassName(), AutismColors.textSecondary());
            builder.line("Payload Size: " + payloadSnapshot.sizeBytes() + " bytes", AutismColors.textPrimary());
            if (payloadSnapshot.commandApiValue() != null) {
                builder.line("CommandApi Value: " + payloadSnapshot.commandApiValue(), AutismColors.successText());
            }
            AutismPayloadTemplate.Preview preview = AutismPayloadTemplate.preview(payloadSnapshot.rawBytes());
            if (preview.minecraftString() != null && !preview.minecraftString().startsWith("<")) {
                builder.line("Minecraft String: " + preview.minecraftString(), AutismColors.successText());
            }
            if (preview.javaWriteUtf() != null && !preview.javaWriteUtf().startsWith("<")) {
                builder.line("Java writeUTF: " + preview.javaWriteUtf(), AutismColors.successText());
            }
            if (preview.utf8() != null && !preview.utf8().isBlank() && !"<binary>".equals(preview.utf8())) {
                builder.line("UTF-8 Guess: " + preview.utf8(), AutismColors.successText());
            }
            builder.line("Payload Hex: " + preview.hex(), AutismColors.textMuted());
            builder.line("Payload Fields: raw bytes preserved; use Edit Payload for typed builder.", AutismColors.successText());
            complete = false;
        }

        SummaryResult reflective = appendReflectivePacketSummary(packet, packetNameHint, entry, builder);
        if (!wrote && reflective.wroteAny()) {
            return reflective;
        }
        return new SummaryResult(wrote, complete);
    }

    private static boolean appendPayloadAnalyzer(AutismPayloadSupport.PayloadSnapshot snapshot, InspectionBuilder builder,
                                                 PayloadAnalysisView payloadAnalysis) {
        if (payloadAnalysis != null) {
            for (InspectionLine line : payloadAnalysis.lines()) {
                builder.line(line.getText(), line.getColor());
            }
            return true;
        }
        return appendPayloadAnalyzer(snapshot, builder, (Consumer<PayloadAnalysisView>) null);
    }

    private static boolean appendPayloadAnalyzer(AutismPayloadSupport.PayloadSnapshot snapshot, InspectionBuilder builder,
                                                 Consumer<PayloadAnalysisView> progressSink) {
        if (snapshot == null) return false;
        byte[] raw = snapshot.rawBytes();
        boolean decodedStrongly = false;
        boolean decodedAnything = false;
        boolean textualPayload = false;
        boolean completeKnownSchema = false;
        String bestInterpretation = "";

        publishAnalysisProgress(builder, progressSink, 0.06D, "Reading payload metadata", false, false);
        builder.line("Channel: " + snapshot.channel(), AutismColors.successText());
        builder.line("Direction: " + snapshot.direction() + " | Protocol: " + emptyTo(snapshot.protocolPhase(), "unknown"),
            directionColor(snapshot.direction()));
        builder.line("Body Size: " + raw.length + " bytes", AutismColors.textPrimary());
        AutismPayloadChannelListeners.Match filter = payloadFilterMatch(snapshot);
        if (filter != null) {
            builder.line("Matched Filter: " + filter.label() + " (" + filter.pattern() + ")", AutismColors.packetGreen());
        }
        String source = describePayloadChannel(snapshot.channel());
        if (source != null && !source.isBlank()) {
            builder.line("What It Looks Like: " + source, AutismColors.textPrimary());
        }

        if (snapshot.commandApiValue() != null) {
            builder.line("CommandApi Int: " + snapshot.commandApiValue(), AutismColors.successText());
            decodedAnything = true;
        }
        appendCompressionHints(raw, builder);

        publishAnalysisProgress(builder, progressSink, 0.20D, "Checking plugin-channel structure", false, false);
        DecodeOutcome knownChannel = appendKnownChannelPayloadDetails(snapshot, raw, builder);
        decodedAnything |= knownChannel.anything();
        decodedStrongly |= knownChannel.strong();
        textualPayload |= knownChannel.textual();
        completeKnownSchema |= knownChannel.complete();
        if (knownChannel.anything()) {
            bestInterpretation = knownChannelInterpretation(snapshot.channel(), completeKnownSchema);
        }

        DecodeOutcome decompressed = DecodeOutcome.NONE;
        if (!completeKnownSchema) {
            decompressed = appendCompressedPayloadAnalysis(snapshot, raw, builder);
            decodedAnything |= decompressed.anything();
            decodedStrongly |= decompressed.strong();
            textualPayload |= decompressed.textual();
            completeKnownSchema |= decompressed.complete();
            if (decompressed.anything()) {
                bestInterpretation = "Compressed payload with nested decoded content";
            }
        }

        publishAnalysisProgress(builder, progressSink, 0.36D, "Trying JSON decoders", false, false);
        if (!completeKnownSchema) {
            JsonElement json = parsePayloadJson(raw);
            if (json != null) {
                appendJsonPayloadAnalysis(json, builder);
                decodedStrongly = true;
                decodedAnything = true;
                textualPayload = true;
                completeKnownSchema = true;
                bestInterpretation = "JSON payload";
            } else {
                JsonElement embeddedJson = findEmbeddedJson(raw);
                if (embeddedJson != null) {
                    builder.line("Decoded As: embedded JSON", AutismColors.successText());
                    appendJsonPayloadAnalysis(embeddedJson, builder);
                    decodedStrongly = true;
                    decodedAnything = true;
                    textualPayload = true;
                    bestInterpretation = "Payload containing embedded JSON";
                }
            }
        }

        publishAnalysisProgress(builder, progressSink, 0.52D, "Trying string codecs", false, false);
        String minecraftString = completeKnownSchema ? null : AutismPayloadSupport.decodeMinecraftStringPayload(raw);
        if (!completeKnownSchema) {
            if (minecraftString != null && !minecraftString.isBlank()) {
                builder.line("Decoded As: Minecraft String", AutismColors.successText());
                builder.line("Value: " + quote(shorten(minecraftString)), AutismColors.textPrimary());
                decodedStrongly = true;
                decodedAnything = true;
                textualPayload = true;
                if (bestInterpretation.isBlank()) bestInterpretation = "Single Minecraft ByteBuf string";
            }

            List<String> minecraftStrings = decodeMinecraftStringSequence(raw, 12);
            if (minecraftStrings.size() > 1 || (minecraftStrings.size() == 1 && !Objects.equals(minecraftStrings.get(0), minecraftString))) {
                builder.line("Minecraft String Sequence: " + minecraftStrings.size() + " value" + pluralSuffix(minecraftStrings.size()),
                    AutismColors.successText());
                for (String value : minecraftStrings) {
                    builder.line("  - " + quote(shorten(value)), AutismColors.textPrimary());
                }
                decodedStrongly = true;
                decodedAnything = true;
                textualPayload = true;
                if (bestInterpretation.isBlank()) bestInterpretation = "Sequence of Minecraft ByteBuf strings";
            }

            String javaUtf = AutismPayloadSupport.decodeJavaWriteUtfPayload(raw);
            if (javaUtf != null && !javaUtf.isBlank()) {
                builder.line("Decoded As: Java writeUTF", AutismColors.successText());
                builder.line("Value: " + quote(shorten(javaUtf)), AutismColors.textPrimary());
                decodedStrongly = true;
                decodedAnything = true;
                textualPayload = true;
                if (bestInterpretation.isBlank()) bestInterpretation = "Single Java DataOutput writeUTF string";
            }

            List<String> javaUtfStrings = decodeJavaWriteUtfSequence(raw, 12);
            if (javaUtfStrings.size() > 1 || (javaUtfStrings.size() == 1 && !Objects.equals(javaUtfStrings.get(0), javaUtf))) {
                builder.line("Java writeUTF Sequence: " + javaUtfStrings.size() + " value" + pluralSuffix(javaUtfStrings.size()),
                    AutismColors.successText());
                for (String value : javaUtfStrings) {
                    builder.line("  - " + quote(shorten(value)), AutismColors.textPrimary());
                }
                decodedStrongly = true;
                decodedAnything = true;
                textualPayload = true;
                if (bestInterpretation.isBlank()) bestInterpretation = "Sequence of Java DataOutput writeUTF strings";
            }

            if (appendNullSeparatedText(raw, builder)) {
                decodedAnything = true;
                textualPayload = true;
                if (bestInterpretation.isBlank()) bestInterpretation = "Null-separated text fields";
            }

            String utf8 = AutismPayloadSupport.decodeLikelyUtf8Text(raw);
            if (utf8 != null && !utf8.isBlank() && !Objects.equals(utf8, minecraftString) && !Objects.equals(utf8, javaUtf)) {
                builder.line("Readable UTF-8: " + quote(shorten(cleanControlChars(utf8))), AutismColors.successText());
                decodedAnything = true;
                textualPayload = true;
                if (bestInterpretation.isBlank()) bestInterpretation = "Readable UTF-8 text payload";
            }
        }

        publishAnalysisProgress(builder, progressSink, 0.70D, "Extracting identifiers and fragments", false, false);
        if (!completeKnownSchema) {
            DecodeOutcome byteBufScan = appendMinecraftByteBufScan(raw, builder);
            decodedAnything |= byteBufScan.anything();
            decodedStrongly |= byteBufScan.strong();
            textualPayload |= byteBufScan.textual();
            if (byteBufScan.anything() && bestInterpretation.isBlank()) {
                bestInterpretation = "Minecraft ByteBuf-style payload with embedded string fields";
            }

            List<String> identifiers = extractIdentifierTokens(raw, 16);
            if (!identifiers.isEmpty() && !isRegisterChannel(snapshot.channel())) {
                builder.line("Identifier Tokens: " + identifiers.size(), AutismColors.textPrimary());
                for (String identifier : identifiers) {
                    builder.line("  - " + identifier, AutismColors.textSecondary());
                }
                decodedAnything = true;
                textualPayload = true;
                if (bestInterpretation.isBlank()) bestInterpretation = "Identifier-bearing custom payload";
            }

            List<String> strings = extractReadableRuns(raw, 8);
            if (!strings.isEmpty()) {
                builder.line("Readable Fragments:", AutismColors.textPrimary());
                for (String string : strings) {
                    builder.line("  - " + quote(shorten(string)), AutismColors.textSecondary());
                }
                decodedAnything = true;
                textualPayload = true;
                if (bestInterpretation.isBlank()) bestInterpretation = "Payload with readable text fragments";
            }
        }

        publishAnalysisProgress(builder, progressSink, 0.78D, "Running deep nested probes", false, false);
        if (!completeKnownSchema) {
            DecodeOutcome deep = appendDeepPayloadProbes(raw, builder);
            decodedAnything |= deep.anything();
            decodedStrongly |= deep.strong();
            textualPayload |= deep.textual();
            completeKnownSchema |= deep.complete();
            if (deep.anything() && bestInterpretation.isBlank()) {
                bestInterpretation = deep.strong() ? "Nested or encoded payload container" : "Payload with recoverable embedded clues";
            }
        }

        publishAnalysisProgress(builder, progressSink, 0.84D, textualPayload ? "Checking confidence" : "Reading numeric and binary hints", false, false);
        if (!textualPayload) {
            appendNumericPayloadViews(raw, builder);
        }
        if (!completeKnownSchema) {
            appendBinaryStats(raw, builder);
        }

        if (raw.length == 0) {
            builder.line("Body is empty.", AutismColors.textMuted());
        } else if (!bestInterpretation.isBlank()) {
            builder.line("Best Interpretation: " + bestInterpretation,
                completeKnownSchema || decodedStrongly ? AutismColors.successText() : AutismColors.textPrimary());
        }
        if (raw.length == 0) {

        } else if (completeKnownSchema) {
            builder.line("Confidence: complete known-schema decode. Payload body is accounted for.", AutismColors.successText());
        } else if (decodedStrongly) {
            builder.line("Confidence: high. A full structured/text decoder matched this payload.", AutismColors.successText());
        } else if (!decodedAnything) {
            builder.line("Binary Payload: no safe schema/text decoder matched. Exact bytes are preserved.", AutismColors.textMuted());
        } else if (!decodedStrongly) {
            builder.line("Confidence: partial. Analyzer found readable hints, but no complete schema matched.", AutismColors.textMuted());
        }
        String finalStatus = completeKnownSchema ? "Known schema decoded"
            : decodedStrongly ? "High-confidence analysis"
            : decodedAnything ? "Partial analysis complete"
            : "Binary analysis complete";
        publishAnalysisProgress(builder, progressSink, 1.0D, finalStatus, true, false);
        return true;
    }

    private static void publishAnalysisProgress(InspectionBuilder builder, Consumer<PayloadAnalysisView> sink,
                                                double progress, String status, boolean done, boolean failed) {
        if (sink == null) return;
        sink.accept(new PayloadAnalysisView(done, failed, progress, status, builder.lines));
    }

    private static DecodeOutcome appendKnownChannelPayloadDetails(AutismPayloadSupport.PayloadSnapshot snapshot, byte[] raw,
                                                                  InspectionBuilder builder) {
        if (snapshot == null) return DecodeOutcome.NONE;
        String channel = normalizeChannel(snapshot.channel());
        if (isRegisterChannel(channel)) {
            boolean decodedRegister = appendRegisterPayloadDetails(snapshot, raw, builder);
            return decodedRegister ? DecodeOutcome.COMPLETE_TEXT : DecodeOutcome.NONE;
        }
        if ("minecraft:brand".equals(channel)) {
            String brand = AutismPayloadSupport.decodeMinecraftStringPayload(raw);
            String mode = "Minecraft String";
            if (brand == null || brand.isBlank()) {
                brand = AutismPayloadSupport.decodeLikelyUtf8Text(raw);
                mode = "raw UTF-8";
            }
            if (brand != null && !brand.isBlank()) {
                builder.line("Decoded As: Brand payload (" + mode + ")", AutismColors.successText());
                builder.line("Brand: " + quote(shorten(cleanControlChars(brand))), AutismColors.textPrimary());
                return DecodeOutcome.COMPLETE_TEXT;
            }
            return DecodeOutcome.NONE;
        }
        if ("vv:server_details".equals(channel)) {
            JsonElement json = parsePayloadJson(raw);
            if (json != null) {
                builder.line("Decoded As: ViaVersion server details JSON", AutismColors.successText());
                appendJsonPayloadAnalysis(json, builder);
                return DecodeOutcome.COMPLETE_TEXT;
            }
        }
        if ("bungeecord".equals(channel) || "bungeecord:main".equals(channel)) {
            return appendBungeeCordPayloadDetails(raw, builder);
        }
        if (channel.startsWith("fabric:")) {
            List<String> values = decodeMinecraftStringSequence(raw, 16);
            if (values.isEmpty()) values = decodeJavaWriteUtfSequence(raw, 16);
            if (!values.isEmpty()) {
                builder.line("Decoded As: Fabric readable payload fields", AutismColors.successText());
                for (String value : values) {
                    builder.line("  - " + quote(shorten(cleanControlChars(value))), AutismColors.textPrimary());
                }
                return new DecodeOutcome(true, true, true, false);
            }
            String utf8 = AutismPayloadSupport.decodeLikelyUtf8Text(raw);
            if (utf8 != null && !utf8.isBlank()) {
                builder.line("Decoded As: Fabric readable text payload", AutismColors.successText());
                builder.line("Text: " + quote(shorten(cleanControlChars(utf8))), AutismColors.textPrimary());
                return new DecodeOutcome(true, true, true, false);
            }
        }
        if (channel.startsWith("worldedit:")) {
            String utf8 = AutismPayloadSupport.decodeLikelyUtf8Text(raw);
            if (utf8 != null && !utf8.isBlank()) {
                builder.line("Decoded As: WorldEdit/CUI text payload", AutismColors.successText());
                builder.line("Message: " + quote(shorten(cleanControlChars(utf8))), AutismColors.textPrimary());
                return new DecodeOutcome(true, true, true, false);
            }
        }
        return DecodeOutcome.NONE;
    }

    private static DecodeOutcome appendBungeeCordPayloadDetails(byte[] raw, InspectionBuilder builder) {
        PayloadByteReader reader = new PayloadByteReader(raw);
        String subchannel;
        try {
            subchannel = reader.readJavaUtf();
        } catch (IOException ignored) {
            return DecodeOutcome.NONE;
        }
        if (subchannel == null || subchannel.isBlank()) return DecodeOutcome.NONE;
        builder.line("Decoded As: BungeeCord/DataOutput plugin message", AutismColors.successText());
        builder.line("Subchannel: " + subchannel, AutismColors.textPrimary());
        String normalized = subchannel.toLowerCase(Locale.ROOT);
        boolean known = true;
        try {
            switch (normalized) {
                case "connect" -> builder.line("Target Server: " + reader.readJavaUtf(), AutismColors.textPrimary());
                case "connectother" -> {
                    builder.line("Player: " + reader.readJavaUtf(), AutismColors.textPrimary());
                    builder.line("Target Server: " + reader.readJavaUtf(), AutismColors.textPrimary());
                }
                case "ip", "getserver", "getservers", "servername" -> {

                }
                case "ipother", "playercount", "playerlist", "uuidother", "serverip" ->
                    builder.line("Argument: " + reader.readJavaUtf(), AutismColors.textPrimary());
                case "message", "messageraw", "kickplayer" -> {
                    builder.line("Player: " + reader.readJavaUtf(), AutismColors.textPrimary());
                    builder.line("Message: " + quote(shorten(reader.readJavaUtf())), AutismColors.textPrimary());
                }
                case "forward" -> {
                    builder.line("Target Server: " + reader.readJavaUtf(), AutismColors.textPrimary());
                    String nestedChannel = reader.readJavaUtf();
                    builder.line("Nested Channel: " + nestedChannel, AutismColors.successText());
                    byte[] nested = reader.readShortBytes();
                    appendNestedPayloadQuickDecode("Nested Forward Payload", nestedChannel, nested, builder);
                }
                case "forwardtoplayer" -> {
                    builder.line("Target Player: " + reader.readJavaUtf(), AutismColors.textPrimary());
                    String nestedChannel = reader.readJavaUtf();
                    builder.line("Nested Channel: " + nestedChannel, AutismColors.successText());
                    byte[] nested = reader.readShortBytes();
                    appendNestedPayloadQuickDecode("Nested Forward Payload", nestedChannel, nested, builder);
                }
                default -> {
                    known = false;
                    List<String> values = new ArrayList<>();
                    while (reader.remaining() > 0 && values.size() < MAX_ANALYZER_ITEMS) {
                        int before = reader.position();
                        try {
                            String value = reader.readJavaUtf();
                            if (!isReadablePayloadString(value)) {
                                reader.seek(before);
                                break;
                            }
                            values.add(value);
                        } catch (IOException ex) {
                            reader.seek(before);
                            break;
                        }
                    }
                    if (!values.isEmpty()) {
                        builder.line("Additional UTF Fields: " + values.size(), AutismColors.textPrimary());
                        for (String value : values) {
                            builder.line("  - " + quote(shorten(value)), AutismColors.textSecondary());
                        }
                    }
                }
            }
        } catch (IOException ex) {
            builder.line("BungeeCord Parse Stopped: " + shorten(ex.getMessage()), AutismColors.packetYellow());
            return new DecodeOutcome(true, true, true, false);
        }
        if (reader.remaining() > 0) {
            builder.line("Remaining Bytes: " + reader.remaining() + "B " + AutismPayloadSupport.toCompactHex(reader.remainingBytes(), 48),
                AutismColors.textMuted());
        }
        return new DecodeOutcome(true, true, true, known && reader.remaining() == 0);
    }

    private static DecodeOutcome appendCompressedPayloadAnalysis(AutismPayloadSupport.PayloadSnapshot snapshot, byte[] raw,
                                                                InspectionBuilder builder) {
        CompressionKind kind = compressionKind(raw);
        if (kind == CompressionKind.NONE) return DecodeOutcome.NONE;
        byte[] decoded = decompressPayload(raw, kind);
        if (decoded == null || decoded.length == 0) {
            builder.line("Compression Decode: " + kind.label() + " header found, but decompression failed.", AutismColors.packetYellow());
            return new DecodeOutcome(true, false, false, false);
        }
        builder.line("Decoded As: " + kind.label() + " compressed payload", AutismColors.successText());
        builder.line("Uncompressed Size: " + decoded.length + " bytes", AutismColors.textPrimary());
        DecodeOutcome nested = appendNestedPayloadQuickDecode("Uncompressed Payload", snapshot == null ? "" : snapshot.channel(), decoded, builder);
        return nested.anything()
            ? new DecodeOutcome(true, true, nested.textual(), nested.complete())
            : new DecodeOutcome(true, true, false, false);
    }

    private static DecodeOutcome appendNestedPayloadQuickDecode(String title, String channel, byte[] bytes, InspectionBuilder builder) {
        if (bytes == null) bytes = new byte[0];
        builder.line(title + ": " + bytes.length + " bytes", AutismColors.textPrimary());
        if (bytes.length == 0) return DecodeOutcome.NONE;
        boolean anything = false;
        boolean strong = false;
        boolean textual = false;
        boolean complete = false;

        JsonElement json = parsePayloadJson(bytes);
        if (json != null) {
            builder.line("  Nested Decoded As: JSON", AutismColors.successText());
            appendJsonPayloadAnalysis(json, builder);
            return DecodeOutcome.COMPLETE_TEXT;
        }

        if (isRegisterChannel(channel)) {
            AutismPayloadSupport.PayloadSnapshot nestedSnapshot = new AutismPayloadSupport.PayloadSnapshot("", channel, bytes, bytes.length,
                null, "", false, false, "", "", -1, "");
            boolean register = appendRegisterPayloadDetails(nestedSnapshot, bytes, builder);
            if (register) return DecodeOutcome.COMPLETE_TEXT;
        }

        String mcString = AutismPayloadSupport.decodeMinecraftStringPayload(bytes);
        if (mcString != null && !mcString.isBlank()) {
            builder.line("  Nested Minecraft String: " + quote(shorten(mcString)), AutismColors.successText());
            anything = true;
            strong = true;
            textual = true;
            complete = true;
        }
        List<String> javaUtf = decodeJavaWriteUtfSequence(bytes, 8);
        if (!javaUtf.isEmpty()) {
            builder.line("  Nested Java UTF Fields: " + javaUtf.size(), AutismColors.successText());
            for (String value : javaUtf) {
                builder.line("    - " + quote(shorten(value)), AutismColors.textSecondary());
            }
            anything = true;
            strong = true;
            textual = true;
            complete = true;
        }
        List<String> ids = extractIdentifierTokens(bytes, 8);
        if (!ids.isEmpty()) {
            builder.line("  Nested Identifiers: " + ids.size(), AutismColors.textPrimary());
            for (String id : ids) {
                builder.line("    - " + id, AutismColors.textSecondary());
            }
            anything = true;
            textual = true;
        }
        if (!anything) {
            String utf8 = AutismPayloadSupport.decodeLikelyUtf8Text(bytes);
            if (utf8 != null && !utf8.isBlank()) {
                builder.line("  Nested UTF-8: " + quote(shorten(cleanControlChars(utf8))), AutismColors.textPrimary());
                anything = true;
                textual = true;
            } else {
                builder.line("  Nested Hex: " + AutismPayloadSupport.toCompactHex(bytes, 64), AutismColors.textMuted());
            }
        }
        return new DecodeOutcome(anything, strong, textual, complete);
    }

    private static DecodeOutcome appendMinecraftByteBufScan(byte[] raw, InspectionBuilder builder) {
        if (raw == null || raw.length < 2) return DecodeOutcome.NONE;
        List<ByteBufStringField> fields = scanMinecraftStringFields(raw, 16);
        if (fields.isEmpty()) return DecodeOutcome.NONE;
        builder.line("Minecraft ByteBuf String Fields: " + fields.size(), AutismColors.textPrimary());
        for (ByteBufStringField field : fields) {
            builder.line("  @" + field.offset() + " len=" + field.length() + ": " + quote(shorten(field.value())),
                AutismColors.textSecondary());
        }
        return new DecodeOutcome(true, false, true, false);
    }

    private static DecodeOutcome appendDeepPayloadProbes(byte[] raw, InspectionBuilder builder) {
        if (raw == null || raw.length == 0) return DecodeOutcome.NONE;
        boolean anything = false;
        boolean strong = false;
        boolean textual = false;

        DecodeOutcome nbt = appendNbtProbe(raw, builder, "Payload NBT");
        anything |= nbt.anything();
        strong |= nbt.strong();
        textual |= nbt.textual();

        String text = new String(raw, StandardCharsets.UTF_8);
        if (appendTextClueProbes(text, builder)) {
            anything = true;
            textual = true;
        }

        DecodeOutcome embedded = appendEmbeddedEncodedPayloads(text, builder);
        anything |= embedded.anything();
        strong |= embedded.strong();
        textual |= embedded.textual();

        DecodeOutcome lengthPrefixed = appendLengthPrefixedCandidates(raw, builder);
        anything |= lengthPrefixed.anything();
        strong |= lengthPrefixed.strong();
        textual |= lengthPrefixed.textual();

        DecodeOutcome rawUuid = appendRawUuidCandidates(raw, builder);
        anything |= rawUuid.anything();

        return new DecodeOutcome(anything, strong, textual, false);
    }

    private static DecodeOutcome appendNbtProbe(byte[] raw, InspectionBuilder builder, String label) {
        if (raw == null || raw.length < 3) return DecodeOutcome.NONE;
        CompoundTag compressed = readCompressedNbt(raw);
        if (compressed != null) {
            builder.line(label + ": compressed NBT compound", AutismColors.successText());
            appendNbtSummary(compressed, builder, "  ");
            return new DecodeOutcome(true, true, true, true);
        }
        return DecodeOutcome.NONE;
    }

    private static CompoundTag readCompressedNbt(byte[] raw) {
        if (compressionKind(raw) != CompressionKind.GZIP) return null;

        try (ByteArrayInputStream input = new ByteArrayInputStream(raw)) {
            CompoundTag tag = NbtIo.readCompressed(input, AutismClipboardHelper.safeNbtAccounter());
            return tag == null || tag.isEmpty() ? null : tag;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void appendNbtSummary(CompoundTag tag, InspectionBuilder builder, String indent) {
        if (tag == null) return;
        builder.line(indent + "Keys: " + tag.size(), AutismColors.textPrimary());
        int shown = 0;
        for (String key : tag.keySet()) {
            if (shown >= 16) {
                builder.line(indent + "... +" + (tag.size() - shown) + " more", AutismColors.textMuted());
                break;
            }
            Tag value = tag.get(key);
            builder.line(indent + key + ": " + summarizeNbtValue(value), AutismColors.textSecondary());
            shown++;
        }
    }

    private static String summarizeNbtValue(Tag tag) {
        if (tag == null) return "null";
        String value = tag.toString();
        return tag.getClass().getSimpleName() + " " + shorten(value);
    }

    private static boolean appendTextClueProbes(String text, InspectionBuilder builder) {
        if (text == null || text.isBlank()) return false;
        boolean wrote = false;
        List<String> uuids = regexTokens(UUID_TEXT_PATTERN, text, 8);
        if (!uuids.isEmpty()) {
            builder.line("UUID Tokens: " + uuids.size(), AutismColors.textPrimary());
            for (String uuid : uuids) {
                builder.line("  - " + uuid.toLowerCase(Locale.ROOT), AutismColors.textSecondary());
            }
            wrote = true;
        }
        List<String> hosts = regexTokens(HOST_TOKEN_PATTERN, text, 12);
        if (!hosts.isEmpty()) {
            builder.line("Host/IP Tokens: " + hosts.size(), AutismColors.textPrimary());
            for (String host : hosts) {
                builder.line("  - " + host, AutismColors.textSecondary());
            }
            wrote = true;
        }
        return wrote;
    }

    private static DecodeOutcome appendEmbeddedEncodedPayloads(String text, InspectionBuilder builder) {
        if (text == null || text.length() < 16) return DecodeOutcome.NONE;
        boolean anything = false;
        boolean strong = false;
        boolean textual = false;

        int shown = 0;
        Matcher base64 = BASE64_TOKEN_PATTERN.matcher(text);
        while (base64.find() && shown < 4) {
            String token = base64.group();
            byte[] decoded = decodeBase64Token(token);
            if (!isWorthNestedProbe(decoded)) continue;
            DecodeOutcome outcome = scoreNestedBytes(decoded);
            if (!outcome.anything()) continue;
            builder.line("Embedded Base64 Payload @" + base64.start() + ": " + decoded.length + " bytes", AutismColors.successText());
            DecodeOutcome nested = appendNestedPayloadQuickDecode("  Base64 decoded", "", decoded, builder);
            anything = true;
            strong |= nested.strong() || outcome.strong();
            textual |= nested.textual() || outcome.textual();
            shown++;
        }

        shown = 0;
        Matcher hex = HEX_TOKEN_PATTERN.matcher(text);
        while (hex.find() && shown < 4) {
            String token = hex.group();
            byte[] decoded = decodeHexToken(token);
            if (!isWorthNestedProbe(decoded)) continue;
            DecodeOutcome outcome = scoreNestedBytes(decoded);
            if (!outcome.anything()) continue;
            builder.line("Embedded Hex Payload @" + hex.start() + ": " + decoded.length + " bytes", AutismColors.successText());
            DecodeOutcome nested = appendNestedPayloadQuickDecode("  Hex decoded", "", decoded, builder);
            anything = true;
            strong |= nested.strong() || outcome.strong();
            textual |= nested.textual() || outcome.textual();
            shown++;
        }
        return new DecodeOutcome(anything, strong, textual, false);
    }

    private static DecodeOutcome appendLengthPrefixedCandidates(byte[] raw, InspectionBuilder builder) {
        if (raw == null || raw.length < 5) return DecodeOutcome.NONE;
        boolean anything = false;
        boolean strong = false;
        boolean textual = false;
        Set<String> seen = new LinkedHashSet<>();
        int shown = 0;
        for (int offset = 0; offset < raw.length - 2 && shown < 6; offset++) {
            VarIntRead varInt = readVarInt(raw, offset);
            if (varInt != null) {
                int length = varInt.value();
                int start = varInt.nextIndex();
                if (isReasonableNestedLength(length, start, raw.length)) {
                    String key = start + ":" + length;
                    if (seen.add(key)) {
                        byte[] slice = Arrays.copyOfRange(raw, start, start + length);
                        DecodeOutcome outcome = scoreNestedBytes(slice);
                        if (outcome.anything()) {
                            builder.line("Nested Candidate: VarInt length @" + offset + " -> " + length + " bytes", AutismColors.textPrimary());
                            DecodeOutcome nested = appendNestedPayloadQuickDecode("  Candidate Payload", "", slice, builder);
                            anything = true;
                            strong |= nested.strong() || outcome.strong();
                            textual |= nested.textual() || outcome.textual();
                            shown++;
                        }
                    }
                }
            }

            int shortLength = ((raw[offset] & 0xFF) << 8) | (raw[offset + 1] & 0xFF);
            int shortStart = offset + 2;
            if (isReasonableNestedLength(shortLength, shortStart, raw.length)) {
                String key = shortStart + ":" + shortLength;
                if (seen.add(key)) {
                    byte[] slice = Arrays.copyOfRange(raw, shortStart, shortStart + shortLength);
                    DecodeOutcome outcome = scoreNestedBytes(slice);
                    if (outcome.anything()) {
                        builder.line("Nested Candidate: unsigned-short length @" + offset + " -> " + shortLength + " bytes", AutismColors.textPrimary());
                        DecodeOutcome nested = appendNestedPayloadQuickDecode("  Candidate Payload", "", slice, builder);
                        anything = true;
                        strong |= nested.strong() || outcome.strong();
                        textual |= nested.textual() || outcome.textual();
                        shown++;
                    }
                }
            }
        }
        return new DecodeOutcome(anything, strong, textual, false);
    }

    private static DecodeOutcome appendRawUuidCandidates(byte[] raw, InspectionBuilder builder) {
        if (raw == null || raw.length < 16) return DecodeOutcome.NONE;
        List<String> uuids = new ArrayList<>();
        for (int offset = 0; offset + 16 <= raw.length && uuids.size() < 4; offset++) {
            if (looksLikeAsciiWindow(raw, offset, 16)) continue;
            ByteBuffer buffer = ByteBuffer.wrap(raw, offset, 16).order(ByteOrder.BIG_ENDIAN);
            UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
            if (uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L) continue;
            if (!looksPlausibleUuid(uuid)) continue;
            uuids.add("@" + offset + " " + uuid);
        }
        if (uuids.isEmpty()) return DecodeOutcome.NONE;
        builder.line("Raw UUID Candidates: " + uuids.size(), AutismColors.textPrimary());
        for (String uuid : uuids) {
            builder.line("  - " + uuid, AutismColors.textSecondary());
        }
        return new DecodeOutcome(true, false, false, false);
    }

    private static DecodeOutcome scoreNestedBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return DecodeOutcome.NONE;
        if (parsePayloadJson(bytes) != null) return DecodeOutcome.COMPLETE_TEXT;
        if (AutismPayloadSupport.decodeMinecraftStringPayload(bytes) != null) return DecodeOutcome.COMPLETE_TEXT;
        if (!decodeJavaWriteUtfSequence(bytes, 4).isEmpty()) return DecodeOutcome.COMPLETE_TEXT;
        if (!extractIdentifierTokens(bytes, 4).isEmpty()) return new DecodeOutcome(true, false, true, false);
        String utf8 = AutismPayloadSupport.decodeLikelyUtf8Text(bytes);
        if (utf8 != null && utf8.length() >= 4) return new DecodeOutcome(true, false, true, false);
        if (compressionKind(bytes) != CompressionKind.NONE) return new DecodeOutcome(true, true, false, false);
        return DecodeOutcome.NONE;
    }

    private static boolean isReasonableNestedLength(int length, int start, int totalLength) {
        if (length < 4 || length > MAX_DECOMPRESSED_PAYLOAD_BYTES) return false;
        if (start < 0 || start > totalLength) return false;
        if (start + length > totalLength) return false;
        return length <= totalLength - start;
    }

    private static boolean isWorthNestedProbe(byte[] bytes) {
        return bytes != null && bytes.length >= 4 && bytes.length <= MAX_DECOMPRESSED_PAYLOAD_BYTES;
    }

    private static List<String> regexTokens(Pattern pattern, String text, int max) {
        if (pattern == null || text == null || text.isBlank()) return List.of();
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find() && tokens.size() < Math.max(1, max)) {
            tokens.add(matcher.group());
        }
        return new ArrayList<>(tokens);
    }

    private static byte[] decodeBase64Token(String token) {
        if (token == null || token.isBlank()) return null;
        String normalized = token.strip();
        int remainder = normalized.length() % 4;
        if (remainder != 0) normalized += "=".repeat(4 - remainder);
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static byte[] decodeHexToken(String token) {
        if (token == null || token.isBlank()) return null;
        String normalized = token.strip().replaceFirst("(?i)^0x", "");
        if ((normalized.length() & 1) != 0 || normalized.length() < 8 || !normalized.matches("(?i)[0-9a-f]+")) return null;
        byte[] out = new byte[normalized.length() / 2];
        try {
            for (int i = 0; i < normalized.length(); i += 2) {
                out[i / 2] = (byte) Integer.parseInt(normalized.substring(i, i + 2), 16);
            }
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean looksLikeAsciiWindow(byte[] raw, int offset, int length) {
        if (raw == null || offset < 0 || length <= 0 || offset + length > raw.length) return false;
        int printable = 0;
        for (int i = offset; i < offset + length; i++) {
            int value = raw[i] & 0xFF;
            if (value >= 0x20 && value <= 0x7E) printable++;
        }
        return printable >= length * 3 / 4;
    }

    private static boolean looksPlausibleUuid(UUID uuid) {
        if (uuid == null) return false;
        long most = uuid.getMostSignificantBits();
        int version = (int) ((most >>> 12) & 0xF);
        return version >= 1 && version <= 7;
    }

    private static boolean appendRegisterPayloadDetails(AutismPayloadSupport.PayloadSnapshot snapshot, byte[] raw,
                                                        InspectionBuilder builder) {
        List<String> nullSeparated = splitNullSeparated(raw, 48);
        List<String> identifiers = extractIdentifierTokens(raw, 48);
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        channels.addAll(nullSeparated.stream()
            .filter(value -> IDENTIFIER_TOKEN_PATTERN.matcher(value).matches())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList());
        channels.addAll(identifiers);
        if (channels.isEmpty()) {
            builder.line("Register Payload: no channel identifiers were readable.", AutismColors.textMuted());
            return false;
        }
        int total = Math.max(identifierTokenCount(raw), channels.size());
        builder.line((isUnregisterChannel(snapshot.channel()) ? "Unregistered" : "Registered")
            + " Channels: " + channels.size() + (total > channels.size() ? "+ shown" : ""),
            AutismColors.successText());
        int shown = 0;
        for (String channel : channels) {
            if (shown >= 48) {
                builder.line("  ... +" + (channels.size() - shown) + " more", AutismColors.textMuted());
                break;
            }
            builder.line("  - " + channel, AutismColors.textPrimary());
            shown++;
        }
        return true;
    }

    private static JsonElement findEmbeddedJson(byte[] raw) {
        if (raw == null || raw.length < 2) return null;
        String text = new String(raw, StandardCharsets.UTF_8);
        int attempts = 0;
        for (int start = 0; start < text.length() && attempts < 20; start++) {
            char open = text.charAt(start);
            if (open != '{' && open != '[') continue;
            char close = open == '{' ? '}' : ']';
            int end = text.lastIndexOf(close);
            while (end > start && attempts < 20) {
                attempts++;
                String candidate = text.substring(start, end + 1).strip();
                if (candidate.length() >= 2) {
                    try {
                        JsonElement parsed = JsonParser.parseString(candidate);
                        if (parsed != null && (parsed.isJsonObject() || parsed.isJsonArray())) return parsed;
                    } catch (Throwable ignored) {  }
                }
                end = text.lastIndexOf(close, end - 1);
            }
        }
        return null;
    }

    private static boolean appendNullSeparatedText(byte[] raw, InspectionBuilder builder) {
        List<String> parts = splitNullSeparated(raw, 16);
        if (parts.size() <= 1) return false;
        builder.line("Null-Separated Strings: " + parts.size() + " value" + pluralSuffix(parts.size()),
            AutismColors.successText());
        for (String part : parts) {
            builder.line("  - " + quote(shorten(part)), AutismColors.textPrimary());
        }
        return true;
    }

    private static List<String> splitNullSeparated(byte[] raw, int max) {
        if (raw == null || raw.length == 0) return List.of();
        boolean hasNull = false;
        for (byte value : raw) {
            if (value == 0) {
                hasNull = true;
                break;
            }
        }
        if (!hasNull) return List.of();
        String text = new String(raw, StandardCharsets.UTF_8);
        List<String> values = new ArrayList<>();
        for (String part : text.split("\\u0000")) {
            if (values.size() >= Math.max(1, max)) break;
            String cleaned = cleanControlChars(part);
            if (isReadablePayloadString(cleaned)) values.add(cleaned);
        }
        return values;
    }

    private static List<String> decodeMinecraftStringSequence(byte[] raw, int max) {
        if (raw == null || raw.length == 0) return List.of();
        int index = 0;
        List<String> values = new ArrayList<>();
        while (index < raw.length && values.size() < Math.max(1, max)) {
            VarIntRead length = readVarInt(raw, index);
            if (length == null || length.value() < 0 || length.value() > 32767) return List.of();
            index = length.nextIndex();
            if (index + length.value() > raw.length) return List.of();
            String value = new String(raw, index, length.value(), StandardCharsets.UTF_8);
            if (!isReadablePayloadString(value)) return List.of();
            values.add(value);
            index += length.value();
        }
        return index == raw.length ? values : List.of();
    }

    private static List<String> decodeJavaWriteUtfSequence(byte[] raw, int max) {
        if (raw == null || raw.length < 2) return List.of();
        List<String> values = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
            while (input.available() > 0 && values.size() < Math.max(1, max)) {
                String value = input.readUTF();
                if (!isReadablePayloadString(value)) return List.of();
                values.add(value);
            }
            return input.available() == 0 ? values : List.of();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static VarIntRead readVarInt(byte[] bytes, int startIndex) {
        if (bytes == null || startIndex < 0 || startIndex >= bytes.length) return null;
        int value = 0;
        int shift = 0;
        int index = startIndex;
        while (index < bytes.length && shift < 35) {
            int current = bytes[index++] & 0xFF;
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) return new VarIntRead(value, index);
            shift += 7;
        }
        return null;
    }

    private static List<ByteBufStringField> scanMinecraftStringFields(byte[] raw, int max) {
        if (raw == null || raw.length < 2) return List.of();
        List<ByteBufStringField> fields = new ArrayList<>();
        Set<Integer> occupied = new LinkedHashSet<>();
        for (int offset = 0; offset < raw.length && fields.size() < Math.max(1, max); offset++) {
            if (occupied.contains(offset)) continue;
            VarIntRead length = readVarInt(raw, offset);
            if (length == null) continue;
            int valueLength = length.value();
            if (valueLength < 1 || valueLength > 32767) continue;
            int start = length.nextIndex();
            if (start + valueLength > raw.length) continue;
            String value = new String(raw, start, valueLength, StandardCharsets.UTF_8);
            if (!isReadablePayloadString(value)) continue;
            int fieldBytes = start + valueLength - offset;
            if (fieldBytes <= 1) continue;
            fields.add(new ByteBufStringField(offset, valueLength, value));
            for (int i = offset; i < start + valueLength; i++) {
                occupied.add(i);
            }
        }
        return fields;
    }

    private static CompressionKind compressionKind(byte[] raw) {
        if (raw == null || raw.length < 2) return CompressionKind.NONE;
        int b0 = raw[0] & 0xFF;
        int b1 = raw[1] & 0xFF;
        if (b0 == 0x1F && b1 == 0x8B) return CompressionKind.GZIP;
        if (b0 == 0x78 && (b1 == 0x01 || b1 == 0x5E || b1 == 0x9C || b1 == 0xDA)) return CompressionKind.ZLIB;
        return CompressionKind.NONE;
    }

    private static byte[] decompressPayload(byte[] raw, CompressionKind kind) {
        if (raw == null || raw.length == 0 || kind == null || kind == CompressionKind.NONE) return null;
        try {
            ByteArrayInputStream source = new ByteArrayInputStream(raw);
            java.io.InputStream input = kind == CompressionKind.GZIP ? new GZIPInputStream(source) : new InflaterInputStream(source);
            try (input; ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(raw.length * 2, 8192))) {
                byte[] buffer = new byte[4096];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    total += read;
                    if (total > MAX_DECOMPRESSED_PAYLOAD_BYTES) return null;
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void appendNumericPayloadViews(byte[] raw, InspectionBuilder builder) {
        if (raw == null || raw.length == 0) return;
        List<String> varInts = decodeVarIntPrefix(raw, 5);
        if (!varInts.isEmpty()) {
            builder.line("VarInt Prefix: " + String.join(", ", varInts), AutismColors.textSecondary());
        }
        if (raw.length >= 1) {
            builder.line("First Byte: unsigned=" + (raw[0] & 0xFF) + ", signed=" + raw[0], AutismColors.textMuted());
        }
        if (raw.length >= 2) {
            ByteBuffer be = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
            ByteBuffer le = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            builder.line("First Short: BE=" + be.getShort(0) + ", LE=" + le.getShort(0), AutismColors.textMuted());
        }
        if (raw.length >= 4) {
            ByteBuffer be = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
            ByteBuffer le = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            builder.line("First Int: BE=" + be.getInt(0) + ", LE=" + le.getInt(0), AutismColors.textMuted());
            float beFloat = be.getFloat(0);
            float leFloat = le.getFloat(0);
            if (isPlausibleFloat(beFloat) || isPlausibleFloat(leFloat)) {
                builder.line("First Float: BE=" + trimFloat(beFloat) + ", LE=" + trimFloat(leFloat), AutismColors.textMuted());
            }
        }
        if (raw.length >= 8) {
            ByteBuffer be = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
            ByteBuffer le = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            builder.line("First Long: BE=" + be.getLong(0) + ", LE=" + le.getLong(0), AutismColors.textMuted());
            double beDouble = be.getDouble(0);
            double leDouble = le.getDouble(0);
            if (isPlausibleDouble(beDouble) || isPlausibleDouble(leDouble)) {
                builder.line("First Double: BE=" + trimDouble(beDouble) + ", LE=" + trimDouble(leDouble), AutismColors.textMuted());
            }
        }
        if (raw.length >= 16) {
            ByteBuffer uuidBuffer = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
            UUID uuid = new UUID(uuidBuffer.getLong(0), uuidBuffer.getLong(8));
            builder.line("First 16 Bytes As UUID: " + uuid, AutismColors.textMuted());
        }
    }

    private static List<String> decodeVarIntPrefix(byte[] raw, int max) {
        if (raw == null || raw.length == 0) return List.of();
        List<String> values = new ArrayList<>();
        int index = 0;
        while (index < raw.length && values.size() < Math.max(1, max) && index < 20) {
            VarIntRead read = readVarInt(raw, index);
            if (read == null || read.nextIndex() <= index) break;
            values.add(String.valueOf(read.value()));
            index = read.nextIndex();
        }
        return values;
    }

    private static void appendCompressionHints(byte[] raw, InspectionBuilder builder) {
        CompressionKind kind = compressionKind(raw);
        if (kind == CompressionKind.GZIP) {
            builder.line("Compression Hint: gzip stream header", AutismColors.packetYellow());
        } else if (kind == CompressionKind.ZLIB) {
            builder.line("Compression Hint: zlib/deflate stream header", AutismColors.packetYellow());
        }
    }

    private static void appendBinaryStats(byte[] raw, InspectionBuilder builder) {
        if (raw == null || raw.length == 0) return;
        int printable = 0;
        int zero = 0;
        int high = 0;
        int[] histogram = new int[256];
        for (byte value : raw) {
            int unsigned = value & 0xFF;
            histogram[unsigned]++;
            if (unsigned == 0) zero++;
            if (unsigned >= 0x80) high++;
            if (unsigned >= 0x20 && unsigned <= 0x7E) printable++;
        }
        builder.line("Binary Stats: printable=" + percent(printable, raw.length)
            + ", zero=" + percent(zero, raw.length)
            + ", high-bit=" + percent(high, raw.length)
            + ", entropy=" + trimDouble(entropy(histogram, raw.length)) + " bits/B",
            AutismColors.textMuted());
    }

    private static double entropy(int[] histogram, int total) {
        if (histogram == null || total <= 0) return 0.0D;
        double entropy = 0.0D;
        for (int count : histogram) {
            if (count <= 0) continue;
            double p = count / (double) total;
            entropy -= p * (Math.log(p) / Math.log(2.0D));
        }
        return entropy;
    }

    private static String percent(int count, int total) {
        if (total <= 0) return "0%";
        return Math.round((count * 1000.0D) / total) / 10.0D + "%";
    }

    private static String trimFloat(float value) {
        if (!Float.isFinite(value)) return String.valueOf(value);
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("\\.?0+$", "");
    }

    private static String trimDouble(double value) {
        if (!Double.isFinite(value)) return String.valueOf(value);
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("\\.?0+$", "");
    }

    private static boolean isPlausibleFloat(float value) {
        if (!Float.isFinite(value)) return false;
        float abs = Math.abs(value);
        return abs == 0.0F || (abs >= 0.000001F && abs <= 1_000_000.0F);
    }

    private static boolean isPlausibleDouble(double value) {
        if (!Double.isFinite(value)) return false;
        double abs = Math.abs(value);
        return abs == 0.0D || (abs >= 0.000001D && abs <= 1_000_000.0D);
    }

    private static boolean isReadablePayloadString(String value) {
        if (value == null || value.isBlank()) return false;
        int printable = 0;
        int control = 0;
        for (int i = 0; i < value.length(); i++) {
            char chr = value.charAt(i);
            if (Character.isISOControl(chr) && chr != '\n' && chr != '\r' && chr != '\t') {
                control++;
            } else {
                printable++;
            }
        }
        return printable > 0 && control * 8 <= Math.max(1, value.length());
    }

    private static String pluralSuffix(int count) {
        return count == 1 ? "" : "s";
    }

    private static void appendJsonPayloadAnalysis(JsonElement json, InspectionBuilder builder) {
        if (json == null) return;
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            builder.line("Decoded As: JSON object (" + object.size() + " keys)", AutismColors.successText());
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                builder.line("  " + entry.getKey() + ": " + jsonValueSummary(entry.getValue()), AutismColors.textPrimary());
            }
        } else if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            builder.line("Decoded As: JSON array (" + array.size() + " items)", AutismColors.successText());
            int index = 0;
            for (JsonElement element : array) {
                if (index >= 16) {
                    builder.line("  ... +" + (array.size() - index) + " more", AutismColors.textMuted());
                    break;
                }
                builder.line("  [" + index + "]: " + jsonValueSummary(element), AutismColors.textPrimary());
                index++;
            }
        } else {
            builder.line("Decoded As: JSON value", AutismColors.successText());
            builder.line("Value: " + jsonValueSummary(json), AutismColors.textPrimary());
        }
    }

    private static String jsonValueSummary(JsonElement element) {
        if (element == null || element.isJsonNull()) return "null";
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) return quote(shorten(primitive.getAsString()));
            return shorten(primitive.toString());
        }
        if (element.isJsonArray()) return "array[" + element.getAsJsonArray().size() + "] " + shorten(element.toString());
        if (element.isJsonObject()) return "object{" + element.getAsJsonObject().size() + "} " + shorten(element.toString());
        return shorten(element.toString());
    }

    private static JsonElement parsePayloadJson(byte[] raw) {
        String text = AutismPayloadSupport.decodeLikelyUtf8Text(raw);
        if (text == null || text.isBlank()) return null;
        String stripped = text.strip();
        if (!(stripped.startsWith("{") || stripped.startsWith("["))) return null;
        try {
            return JsonParser.parseString(stripped);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> extractIdentifierTokens(byte[] raw, int max) {
        String text = new String(raw == null ? new byte[0] : raw, StandardCharsets.UTF_8);
        Matcher matcher = IDENTIFIER_TOKEN_PATTERN.matcher(text);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        while (matcher.find() && tokens.size() < Math.max(1, max)) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(tokens);
    }

    private static int identifierTokenCount(byte[] raw) {
        String text = new String(raw == null ? new byte[0] : raw, StandardCharsets.UTF_8);
        Matcher matcher = IDENTIFIER_TOKEN_PATTERN.matcher(text);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return tokens.size();
    }

    private static List<String> extractReadableRuns(byte[] raw, int max) {
        String text = new String(raw == null ? new byte[0] : raw, StandardCharsets.ISO_8859_1);
        Matcher matcher = READABLE_RUN_PATTERN.matcher(text);
        List<String> runs = new ArrayList<>();
        while (matcher.find() && runs.size() < Math.max(1, max)) {
            String run = matcher.group().strip();
            if (run.length() < 4) continue;
            runs.add(cleanControlChars(run));
        }
        return runs;
    }

    private static String cleanControlChars(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        boolean spaced = false;
        for (int i = 0; i < text.length(); i++) {
            char chr = text.charAt(i);
            if (Character.isISOControl(chr)) {
                if (!spaced) {
                    sb.append(' ');
                    spaced = true;
                }
            } else {
                sb.append(chr);
                spaced = Character.isWhitespace(chr);
            }
        }
        return sb.toString().strip();
    }

    private static boolean isRegisterChannel(String channel) {
        String normalized = normalizeChannel(channel);
        return normalized.equals("minecraft:register") || normalized.equals("register")
            || normalized.equals("minecraft:unregister") || normalized.equals("unregister");
    }

    private static boolean isUnregisterChannel(String channel) {
        String normalized = normalizeChannel(channel);
        return normalized.endsWith("unregister");
    }

    private static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.strip().toLowerCase(Locale.ROOT);
    }

    private static String knownChannelInterpretation(String channel, boolean complete) {
        String normalized = normalizeChannel(channel);
        if (normalized.equals("minecraft:register") || normalized.equals("register")) {
            return "Legacy plugin channel registration list";
        }
        if (normalized.equals("minecraft:unregister") || normalized.equals("unregister")) {
            return "Legacy plugin channel unregister list";
        }
        if (normalized.equals("minecraft:brand")) return "Minecraft brand payload";
        if (normalized.equals("bungeecord") || normalized.equals("bungeecord:main")) {
            return complete ? "BungeeCord/DataOutput plugin message" : "BungeeCord/DataOutput-like plugin message";
        }
        if (normalized.startsWith("worldedit:")) return "WorldEdit/CUI text payload";
        return complete ? "Known channel payload" : "Channel-specific payload";
    }

    private static String describePayloadChannel(String channel) {
        if (channel == null || channel.isBlank()) return "";
        String normalized = channel.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("minecraft:register") || normalized.equals("register")) {
            return "legacy plugin-channel registration list";
        }
        if (normalized.equals("minecraft:unregister") || normalized.equals("unregister")) {
            return "legacy plugin-channel unregister list";
        }
        if (normalized.equals("minecraft:brand")) return "client/server brand payload";
        if (normalized.equals("bungeecord:main") || normalized.equals("bungeecord")) return "BungeeCord/proxy plugin messaging";
        if (normalized.startsWith("vv:") || normalized.startsWith("viaversion:") || normalized.startsWith("viabackwards:")) {
            return "ViaVersion/ViaFabricPlus compatibility payload";
        }
        if (normalized.startsWith("fabric:")) return "Fabric networking payload";
        if (normalized.startsWith("worldedit:")) return "WorldEdit/CUI payload";
        if (normalized.startsWith("oraxen:")) return "Oraxen resource/item plugin payload";
        if (normalized.startsWith("itemsadder:")) return "ItemsAdder resource/item plugin payload";
        if (normalized.startsWith("nexo:")) return "Nexo resource/item plugin payload";
        if (normalized.startsWith("velocity:")) return "Velocity/proxy plugin messaging";
        if (normalized.startsWith("geyser:") || normalized.startsWith("floodgate:")) return "Bedrock bridge plugin payload";
        if (normalized.startsWith("voicechat:") || normalized.startsWith("plasmovoice:")) return "voice chat plugin payload";
        int colon = normalized.indexOf(':');
        if (colon > 0) return "custom payload namespace: " + normalized.substring(0, colon);
        return "custom payload channel";
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static SummaryResult appendBroadKnownPacketSummary(Packet<?> packet, InspectionBuilder builder) {
        if (packet == null) return SummaryResult.NONE;
        String simpleName = packet.getClass().getSimpleName();
        String className = packet.getClass().getName().replace('$', '.');
        boolean wrote = false;
        boolean complete = true;

        switch (simpleName) {
            case "ClientboundBlockUpdatePacket" -> {
                wrote |= appendMeaning(builder, "Server changed one block");
                wrote |= appendValueLine(builder, packet, "Block Pos", "getPos", "pos");
                wrote |= appendValueLine(builder, packet, "Block", "getBlockState", "blockState", "state");
            }
            case "ClientboundBlockEntityDataPacket" -> {
                wrote |= appendMeaning(builder, "Server updated block entity data");
                wrote |= appendValueLine(builder, packet, "Block Pos", "getPos", "pos");
                wrote |= appendValueLine(builder, packet, "Block Entity Type", "getType", "type");
                wrote |= appendValueLine(builder, packet, "Nbt", "getTag", "tag");
            }
            case "ClientboundBlockEventPacket" -> {
                wrote |= appendMeaning(builder, "Server fired a block event");
                wrote |= appendValueLine(builder, packet, "Block Pos", "getPos", "pos");
                wrote |= appendValueLine(builder, packet, "Block", "getBlock", "block");
                wrote |= appendValueLine(builder, packet, "Event Type", "getB0", "b0", "eventType");
                wrote |= appendValueLine(builder, packet, "Event Data", "getB1", "b1", "eventData");
            }
            case "ClientboundSectionBlocksUpdatePacket" -> {
                wrote |= appendMeaning(builder, "Server changed multiple blocks in a chunk section");
                wrote |= appendValueLine(builder, packet, "Section", "sectionPos", "getSectionPos");
                Object positions = firstNonNull(invokeFirstNoArg(packet, "positions", "getPositions"), findInspectablePropertyValue(packet, "Positions"));
                Object states = firstNonNull(invokeFirstNoArg(packet, "states", "getStates"), findInspectablePropertyValue(packet, "States"));
                if (positions != null) wrote |= appendRawSummaryLine(builder, "Positions", positions);
                if (states != null) wrote |= appendRawSummaryLine(builder, "States", states);
                complete = false;
            }
            case "ClientboundLevelEventPacket" -> {
                wrote |= appendMeaning(builder, "World event such as particles, sound, or block effect");
                wrote |= appendValueLine(builder, packet, "Event", "getType", "type");
                wrote |= appendValueLine(builder, packet, "Block Pos", "getPos", "pos");
                wrote |= appendValueLine(builder, packet, "Data", "getData", "data");
                wrote |= appendValueLine(builder, packet, "Global", "isGlobalEvent", "globalEvent");
            }
            case "ClientboundLevelParticlesPacket" -> {
                wrote |= appendMeaning(builder, "Particle spawn");
                wrote |= appendValueLine(builder, packet, "Particle", "getParticle", "getParticleType", "particle");
                wrote |= appendVec3Components(builder, packet, "Pos", "getX", "x", "getY", "y", "getZ", "z");
                wrote |= appendVec3Components(builder, packet, "Offset", "getXDist", "xDist", "getYDist", "yDist", "getZDist", "zDist");
                wrote |= appendValueLine(builder, packet, "Speed", "getMaxSpeed", "maxSpeed");
                wrote |= appendValueLine(builder, packet, "Count", "getCount", "count");
                wrote |= appendValueLine(builder, packet, "Override Limiter", "isOverrideLimiter", "overrideLimiter");
            }
            case "ClientboundForgetLevelChunkPacket" -> {
                wrote |= appendMeaning(builder, "Client should unload a chunk");
                wrote |= appendValueLine(builder, packet, "Chunk", "pos", "getPos");
                wrote |= appendValueLine(builder, packet, "Chunk X", "getX", "x");
                wrote |= appendValueLine(builder, packet, "Chunk Z", "getZ", "z");
            }
            case "ClientboundSetChunkCacheCenterPacket" -> {
                wrote |= appendMeaning(builder, "Server changed chunk cache center");
                wrote |= appendValueLine(builder, packet, "Chunk X", "getX", "x");
                wrote |= appendValueLine(builder, packet, "Chunk Z", "getZ", "z");
            }
            case "ClientboundSetChunkCacheRadiusPacket", "ClientboundSetSimulationDistancePacket" -> {
                wrote |= appendMeaning(builder, simpleName.contains("Simulation") ? "Server changed simulation distance" : "Server changed chunk render distance");
                wrote |= appendValueLine(builder, packet, "Radius", "getRadius", "radius");
                wrote |= appendValueLine(builder, packet, "Distance", "simulationDistance", "distance");
            }
            case "ClientboundMoveEntityPacket.Pos", "ClientboundMoveEntityPacket.Rot", "ClientboundMoveEntityPacket.PosRot",
                 "ClientboundTeleportEntityPacket", "ClientboundRotateHeadPacket", "ClientboundHurtAnimationPacket" -> {
                wrote |= appendMeaning(builder, "Server moved or rotated an entity");
                wrote |= appendEntityLine(builder, packet, "Entity Id", "getEntityId", "entityId", "id");
                wrote |= appendValueLine(builder, packet, "Delta X", "getXa", "xa");
                wrote |= appendValueLine(builder, packet, "Delta Y", "getYa", "ya");
                wrote |= appendValueLine(builder, packet, "Delta Z", "getZa", "za");
                wrote |= appendValueLine(builder, packet, "Yaw", "getYRot", "yRot", "yaw");
                wrote |= appendValueLine(builder, packet, "Pitch", "getXRot", "xRot", "pitch");
                wrote |= appendValueLine(builder, packet, "Head Yaw", "getYHeadRot", "yHeadRot");
                wrote |= appendValueLine(builder, packet, "Change", "change");
                wrote |= appendValueLine(builder, packet, "Relatives", "relatives");
                wrote |= appendValueLine(builder, packet, "On Ground", "isOnGround", "onGround");
            }
            case "ClientboundMoveVehiclePacket", "ServerboundMoveVehiclePacket" -> {
                wrote |= appendMeaning(builder, simpleName.startsWith("Serverbound") ? "Client moved the ridden vehicle" : "Server moved the ridden vehicle");
                wrote |= appendVec3Components(builder, packet, "Pos", "getX", "x", "getY", "y", "getZ", "z");
                wrote |= appendValueLine(builder, packet, "Yaw", "getYRot", "yRot", "yaw");
                wrote |= appendValueLine(builder, packet, "Pitch", "getXRot", "xRot", "pitch");
                wrote |= appendValueLine(builder, packet, "On Ground", "isOnGround", "onGround");
            }
            case "ClientboundRemoveEntitiesPacket" -> {
                wrote |= appendMeaning(builder, "Server removed entities");
                Object ids = firstNonNull(invokeFirstNoArg(packet, "getEntityIds", "entityIds"), getRecordComponentValue(packet, 0));
                if (ids != null) {
                    builder.line("Entities: " + formatEntityIdList(ids), AutismColors.packetCyan());
                    wrote = true;
                }
            }
            case "ClientboundTakeItemEntityPacket" -> {
                wrote |= appendMeaning(builder, "Item/entity pickup animation");
                wrote |= appendEntityLine(builder, packet, "Item Entity", "getItemId", "itemId");
                wrote |= appendEntityLine(builder, packet, "Collector", "getPlayerId", "playerId");
                wrote |= appendValueLine(builder, packet, "Amount", "getAmount", "amount");
            }
            case "ClientboundSetPassengersPacket" -> {
                wrote |= appendMeaning(builder, "Server changed vehicle passengers");
                wrote |= appendEntityLine(builder, packet, "Vehicle", "getVehicle", "vehicle", "vehicleId");
                Object passengers = firstNonNull(invokeFirstNoArg(packet, "getPassengers", "passengers"), findInspectablePropertyValue(packet, "Passengers"));
                if (passengers != null) {
                    builder.line("Passengers: " + formatEntityIdList(passengers), AutismColors.packetCyan());
                    wrote = true;
                }
            }
            case "ClientboundSetEntityLinkPacket" -> {
                wrote |= appendMeaning(builder, "Server linked one entity to another");
                wrote |= appendEntityLine(builder, packet, "Source", "getSourceId", "sourceId", "source");
                wrote |= appendEntityLine(builder, packet, "Destination", "getDestId", "destId", "destination");
            }
            case "ClientboundSetHealthPacket" -> {
                wrote |= appendMeaning(builder, "Player health update");
                wrote |= appendValueLine(builder, packet, "Health", "getHealth", "health");
                wrote |= appendValueLine(builder, packet, "Food", "getFood", "food");
                wrote |= appendValueLine(builder, packet, "Saturation", "getSaturation", "saturation");
            }
            case "ClientboundSetExperiencePacket" -> {
                wrote |= appendMeaning(builder, "Player experience update");
                wrote |= appendValueLine(builder, packet, "Progress", "getExperienceProgress", "experienceProgress", "progress");
                wrote |= appendValueLine(builder, packet, "Level", "getExperienceLevel", "experienceLevel", "level");
                wrote |= appendValueLine(builder, packet, "Total", "getTotalExperience", "totalExperience", "total");
            }
            case "ClientboundSetHeldSlotPacket", "ServerboundSetCarriedItemPacket" -> {
                wrote |= appendMeaning(builder, simpleName.startsWith("Serverbound") ? "Client selected hotbar slot" : "Server selected held hotbar slot");
                Object slot = firstNonNull(invokeFirstNoArg(packet, "getSlot", "slot", "getSelectedSlot", "selectedSlot"), getRecordComponentValue(packet, 0));
                if (slot instanceof Number number) {
                    builder.line("Slot: " + formatHotbarSlot(number.intValue()), AutismColors.textPrimary());
                    wrote = true;
                } else if (slot != null) {
                    builder.line("Slot: " + safeLeafString(slot), AutismColors.textPrimary());
                    wrote = true;
                }
            }
            case "ClientboundSetPlayerInventoryPacket", "ClientboundSetCursorItemPacket" -> {
                wrote |= appendMeaning(builder, simpleName.contains("Cursor") ? "Server updated cursor item" : "Server updated player inventory slot");
                wrote |= appendValueLine(builder, packet, "Slot", "slot", "getSlot");
                wrote |= appendValueLine(builder, packet, "Stack", "contents", "getContents", "item", "getItem");
            }
            case "ClientboundContainerClosePacket" -> {
                wrote |= appendMeaning(builder, "Server closed a handled screen");
                wrote |= appendValueLine(builder, packet, "Sync Id", "getContainerId", "containerId");
            }
            case "ClientboundCooldownPacket" -> {
                wrote |= appendMeaning(builder, "Item cooldown update");
                wrote |= appendValueLine(builder, packet, "Item", "getItem", "item");
                wrote |= appendValueLine(builder, packet, "Ticks", "getDuration", "duration", "ticks");
            }
            case "ClientboundOpenBookPacket" -> {
                wrote |= appendMeaning(builder, "Open book animation/screen");
                wrote |= appendValueLine(builder, packet, "Hand", "getHand", "hand");
            }
            case "ClientboundOpenSignEditorPacket" -> {
                wrote |= appendMeaning(builder, "Open sign editor");
                wrote |= appendValueLine(builder, packet, "Block Pos", "getPos", "pos");
                wrote |= appendValueLine(builder, packet, "Front", "isFrontText", "frontText", "front");
            }
            case "ClientboundSetActionBarTextPacket", "ClientboundSetTitleTextPacket", "ClientboundSetSubtitleTextPacket" -> {
                wrote |= appendMeaning(builder, "Server displayed text on the HUD");
                wrote |= appendTextComponentLine(builder, packet, "Text", "getText", "text");
            }
            case "ClientboundSetTitlesAnimationPacket" -> {
                wrote |= appendMeaning(builder, "Title timing update");
                wrote |= appendValueLine(builder, packet, "Fade In", "getFadeIn", "fadeIn");
                wrote |= appendValueLine(builder, packet, "Stay", "getStay", "stay");
                wrote |= appendValueLine(builder, packet, "Fade Out", "getFadeOut", "fadeOut");
            }
            case "ClientboundClearTitlesPacket" -> {
                wrote |= appendMeaning(builder, "Clear title/subtitle text");
                wrote |= appendValueLine(builder, packet, "Reset Times", "shouldResetTimes", "resetTimes");
            }
            case "ClientboundTabListPacket" -> {
                wrote |= appendMeaning(builder, "Player list header/footer update");
                wrote |= appendTextComponentLine(builder, packet, "Header", "getHeader", "header");
                wrote |= appendTextComponentLine(builder, packet, "Footer", "getFooter", "footer");
            }
            case "ClientboundSetTimePacket" -> {
                wrote |= appendMeaning(builder, "World time update");
                wrote |= appendValueLine(builder, packet, "Game Time", "getGameTime", "gameTime");
                wrote |= appendValueLine(builder, packet, "Day Time", "getDayTime", "dayTime");
                wrote |= appendValueLine(builder, packet, "Tick Day Time", "isTickDayTime", "tickDayTime");
            }
            case "ClientboundPlayerAbilitiesPacket", "ServerboundPlayerAbilitiesPacket" -> {
                wrote |= appendMeaning(builder, "Player ability flags");
                wrote |= appendValueLine(builder, packet, "Invulnerable", "isInvulnerable", "invulnerable");
                wrote |= appendValueLine(builder, packet, "Flying", "isFlying", "flying");
                wrote |= appendValueLine(builder, packet, "Can Fly", "canFly", "mayfly", "mayFly");
                wrote |= appendValueLine(builder, packet, "Instabuild", "canInstabuild", "instabuild");
                wrote |= appendValueLine(builder, packet, "Fly Speed", "getFlyingSpeed", "flyingSpeed");
                wrote |= appendValueLine(builder, packet, "Walk Speed", "getWalkingSpeed", "walkingSpeed");
            }
            case "ClientboundChangeDifficultyPacket", "ServerboundChangeDifficultyPacket", "ServerboundLockDifficultyPacket" -> {
                wrote |= appendMeaning(builder, "Difficulty setting update");
                wrote |= appendValueLine(builder, packet, "Difficulty", "getDifficulty", "difficulty");
                wrote |= appendValueLine(builder, packet, "Locked", "isLocked", "locked");
            }
            case "ClientboundSoundEntityPacket" -> {
                wrote |= appendMeaning(builder, "Sound played from an entity");
                wrote |= appendValueLine(builder, packet, "Sound", "getSound", "sound");
                wrote |= appendValueLine(builder, packet, "Category", "getSource", "source", "category");
                wrote |= appendEntityLine(builder, packet, "Entity", "getId", "id", "entityId");
                wrote |= appendValueLine(builder, packet, "Volume", "getVolume", "volume");
                wrote |= appendValueLine(builder, packet, "Pitch", "getPitch", "pitch");
                wrote |= appendValueLine(builder, packet, "Seed", "getSeed", "seed");
            }
            case "ClientboundStopSoundPacket" -> {
                wrote |= appendMeaning(builder, "Stop one or more sounds");
                wrote |= appendValueLine(builder, packet, "Source", "getSource", "source");
                wrote |= appendValueLine(builder, packet, "Name", "getName", "name");
            }
            case "ClientboundBossEventPacket" -> {
                wrote |= appendMeaning(builder, "Boss bar update");
                wrote |= appendValueLine(builder, packet, "Boss Bar Id", "getId", "id");
                wrote |= appendValueLine(builder, packet, "Operation", "getOperation", "operation");
                complete = false;
            }
            case "ClientboundPlayerInfoUpdatePacket" -> {
                wrote |= appendMeaning(builder, "Player list entries updated");
                wrote |= appendValueLine(builder, packet, "Actions", "actions");
                wrote |= appendCollectionLine(builder, packet, "Entries", "entries");
                complete = false;
            }
            case "ClientboundPlayerInfoRemovePacket" -> {
                wrote |= appendMeaning(builder, "Player list entries removed");
                wrote |= appendCollectionLine(builder, packet, "Profiles", "profileIds", "getProfileIds");
            }
            case "ClientboundPlayerCombatEndPacket", "ClientboundPlayerCombatEnterPacket", "ClientboundPlayerCombatKillPacket" -> {
                wrote |= appendMeaning(builder, "Player combat state update");
                wrote |= appendEntityLine(builder, packet, "Player", "playerId", "getPlayerId");
                wrote |= appendEntityLine(builder, packet, "Killer", "killerId", "getKillerId");
                wrote |= appendValueLine(builder, packet, "Duration", "duration", "getDuration");
                wrote |= appendTextComponentLine(builder, packet, "Message", "message", "getMessage");
            }
            case "ClientboundSetScorePacket", "ClientboundResetScorePacket" -> {
                wrote |= appendMeaning(builder, "Scoreboard score update");
                wrote |= appendValueLine(builder, packet, "Owner", "owner", "getOwner");
                wrote |= appendValueLine(builder, packet, "Objective", "objectiveName", "getObjectiveName");
                wrote |= appendValueLine(builder, packet, "Score", "score", "getScore");
                wrote |= appendValueLine(builder, packet, "Display", "display", "numberFormat", "getDisplay");
            }
            case "ClientboundSetObjectivePacket", "ClientboundSetDisplayObjectivePacket" -> {
                wrote |= appendMeaning(builder, "Scoreboard objective update");
                wrote |= appendValueLine(builder, packet, "Objective", "getObjectiveName", "objectiveName");
                wrote |= appendTextComponentLine(builder, packet, "Display Name", "getDisplayName", "displayName");
                wrote |= appendValueLine(builder, packet, "Render Type", "getRenderType", "renderType");
                wrote |= appendValueLine(builder, packet, "Slot", "getSlot", "slot");
                wrote |= appendValueLine(builder, packet, "Method", "getMethod", "method");
            }
            case "ClientboundSetPlayerTeamPacket" -> {
                wrote |= appendMeaning(builder, "Scoreboard team update");
                wrote |= appendValueLine(builder, packet, "Team", "getName", "name");
                wrote |= appendValueLine(builder, packet, "Method", "getMethod", "method");
                wrote |= appendCollectionLine(builder, packet, "Players", "getPlayers", "players");
                complete = false;
            }
            case "ClientboundSetBorderCenterPacket", "ClientboundSetBorderSizePacket", "ClientboundSetBorderLerpSizePacket",
                 "ClientboundSetBorderWarningDelayPacket", "ClientboundSetBorderWarningDistancePacket",
                 "ClientboundInitializeBorderPacket" -> {
                wrote |= appendMeaning(builder, "World border update");
                wrote |= appendValueLine(builder, packet, "Center X", "getNewCenterX", "newCenterX", "centerX");
                wrote |= appendValueLine(builder, packet, "Center Z", "getNewCenterZ", "newCenterZ", "centerZ");
                wrote |= appendValueLine(builder, packet, "Size", "getSize", "size");
                wrote |= appendValueLine(builder, packet, "Old Size", "getOldSize", "oldSize");
                wrote |= appendValueLine(builder, packet, "New Size", "getNewSize", "newSize");
                wrote |= appendValueLine(builder, packet, "Lerp Time", "getLerpTime", "lerpTime");
                wrote |= appendValueLine(builder, packet, "Warning Blocks", "getWarningBlocks", "warningBlocks");
                wrote |= appendValueLine(builder, packet, "Warning Time", "getWarningTime", "warningTime");
            }
            case "ClientboundResourcePackPushPacket", "ClientboundResourcePackPopPacket" -> {
                wrote |= appendMeaning(builder, "Resource pack request/update");
                wrote |= appendValueLine(builder, packet, "Id", "id", "getId");
                wrote |= appendValueLine(builder, packet, "Url", "url", "getUrl");
                wrote |= appendValueLine(builder, packet, "Hash", "hash", "getHash");
                wrote |= appendValueLine(builder, packet, "Required", "required", "isRequired");
                wrote |= appendTextComponentLine(builder, packet, "Prompt", "prompt", "getPrompt");
            }
            case "ClientboundUpdateRecipesPacket", "ClientboundRecipeBookAddPacket", "ClientboundRecipeBookRemovePacket",
                 "ClientboundRecipeBookSettingsPacket" -> {
                wrote |= appendMeaning(builder, "Recipe book/recipe registry update");
                wrote |= appendCollectionLine(builder, packet, "Recipes", "recipes", "getRecipes", "entries");
                wrote |= appendValueLine(builder, packet, "Settings", "settings", "getSettings");
                complete = false;
            }
            case "ClientboundUpdateTagsPacket" -> {
                wrote |= appendMeaning(builder, "Registry tag update");
                wrote |= appendValueLine(builder, packet, "Tags", "tags", "getTags");
                complete = false;
            }
            case "ServerboundPlayerCommandPacket" -> {
                wrote |= appendMeaning(builder, "Client sent player/entity command");
                wrote |= appendEntityLine(builder, packet, "Entity", "getId", "id", "entityId");
                wrote |= appendValueLine(builder, packet, "Action", "getAction", "action");
                wrote |= appendValueLine(builder, "Data", firstNonNull(invokeFirstNoArg(packet, "getData", "data"), getRecordComponentValue(packet, 2)));
            }
            case "ServerboundPlayerInputPacket" -> {
                wrote |= appendMeaning(builder, "Client movement input state");
                wrote |= appendValueLine(builder, packet, "Input", "input");
                wrote |= appendValueLine(builder, packet, "Forward", "xxa", "forward");
                wrote |= appendValueLine(builder, packet, "Sideways", "zza", "sideways");
                wrote |= appendValueLine(builder, packet, "Jumping", "isJumping", "jumping");
                wrote |= appendValueLine(builder, packet, "Sneaking", "isShiftKeyDown", "shiftKeyDown", "sneaking");
            }
            case "ServerboundPaddleBoatPacket" -> {
                wrote |= appendMeaning(builder, "Client boat paddle state");
                wrote |= appendValueLine(builder, packet, "Left", "getLeft", "left");
                wrote |= appendValueLine(builder, packet, "Right", "getRight", "right");
            }
            case "ServerboundCommandSuggestionPacket" -> {
                wrote |= appendMeaning(builder, "Client requested command completions");
                wrote |= appendValueLine(builder, packet, "Transaction Id", "getId", "id");
                wrote |= appendValueLine(builder, packet, "Command", "getCommand", "command");
            }
            case "ServerboundChatCommandPacket", "ServerboundChatCommandSignedPacket" -> {
                wrote |= appendMeaning(builder, "Outgoing command");
                wrote |= appendValueLine(builder, packet, "Command", "command", "getCommand");
            }
            case "ServerboundPickItemFromBlockPacket" -> {
                wrote |= appendMeaning(builder, "Client pick-blocked a block");
                wrote |= appendValueLine(builder, packet, "Block Pos", "pos", "getPos");
                wrote |= appendValueLine(builder, packet, "Include Data", "includeData", "isIncludeData");
            }
            case "ServerboundPickItemFromEntityPacket" -> {
                wrote |= appendMeaning(builder, "Client pick-blocked an entity");
                wrote |= appendEntityLine(builder, packet, "Entity", "id", "getId", "entityId");
                wrote |= appendValueLine(builder, packet, "Include Data", "includeData", "isIncludeData");
            }
            default -> {
                if (className.contains("ServerboundMovePlayerPacket")) {
                    wrote |= appendMeaning(builder, "Client player movement");
                    wrote |= appendVec3Components(builder, packet, "Pos", "getX", "x", "getY", "y", "getZ", "z");
                    wrote |= appendValueLine(builder, packet, "Yaw", "getYRot", "yRot", "yaw");
                    wrote |= appendValueLine(builder, packet, "Pitch", "getXRot", "xRot", "pitch");
                    wrote |= appendValueLine(builder, packet, "On Ground", "isOnGround", "onGround");
                    wrote |= appendValueLine(builder, packet, "Horizontal Collision", "horizontalCollision");
                } else if (className.contains("ClientboundMoveEntityPacket")) {
                    wrote |= appendMeaning(builder, "Server moved or rotated an entity");
                    wrote |= appendEntityLine(builder, packet, "Entity Id", "getEntityId", "entityId", "id");
                    wrote |= appendValueLine(builder, packet, "Delta X", "getXa", "xa");
                    wrote |= appendValueLine(builder, packet, "Delta Y", "getYa", "ya");
                    wrote |= appendValueLine(builder, packet, "Delta Z", "getZa", "za");
                    wrote |= appendValueLine(builder, packet, "Yaw", "getYRot", "yRot", "yaw");
                    wrote |= appendValueLine(builder, packet, "Pitch", "getXRot", "xRot", "pitch");
                    wrote |= appendValueLine(builder, packet, "On Ground", "isOnGround", "onGround");
                }
            }
        }

        return new SummaryResult(wrote, complete);
    }

    private static boolean appendMeaning(InspectionBuilder builder, String meaning) {
        if (meaning == null || meaning.isBlank()) return false;
        builder.line("Meaning: " + meaning, AutismColors.textPrimary());
        return true;
    }

    private static boolean appendValueLine(InspectionBuilder builder, Packet<?> packet, String label, String... accessors) {
        Object value = firstNonNull(invokeFirstNoArg(packet, accessors), findInspectablePropertyValue(packet, label));
        return appendValueLine(builder, label, value);
    }

    private static boolean appendValueLine(InspectionBuilder builder, String label, Object value) {
        if (value == null || value == INACCESSIBLE) return false;
        builder.line(label + ": " + summarizeValue(label, value), colorForSummary(label, value));
        return true;
    }

    private static boolean appendRawSummaryLine(InspectionBuilder builder, String label, Object value) {
        if (value == null || value == INACCESSIBLE) return false;
        builder.line(label + ": " + summarizeValue(value), colorForSummary(label, value));
        return true;
    }

    private static boolean appendEntityLine(InspectionBuilder builder, Packet<?> packet, String label, String... accessors) {
        Object value = firstNonNull(invokeFirstNoArg(packet, accessors), findInspectablePropertyValue(packet, label));
        if (value instanceof Number number) {
            appendEntityIdLine(builder, label, number.intValue());
            return true;
        }
        if (value instanceof Entity entity) {
            builder.line(label + ": " + formatEntity(entity), AutismColors.packetCyan());
            return true;
        }
        return appendValueLine(builder, label, value);
    }

    private static boolean appendTextComponentLine(InspectionBuilder builder, Packet<?> packet, String label, String... accessors) {
        Object value = firstNonNull(invokeFirstNoArg(packet, accessors), findInspectablePropertyValue(packet, label));
        if (value == null || value == INACCESSIBLE) return false;
        String text = extractTextValue(value);
        builder.line(label + ": " + (text == null || text.isBlank() ? summarizeValue(label, value) : quote(shorten(text))),
            semanticFieldColor(label, value));
        return true;
    }

    private static boolean appendCollectionLine(InspectionBuilder builder, Packet<?> packet, String label, String... accessors) {
        Object value = firstNonNull(invokeFirstNoArg(packet, accessors), findInspectablePropertyValue(packet, label));
        if (value == null || value == INACCESSIBLE) return false;
        if (value instanceof Collection<?> collection) {
            builder.line(label + ": " + formatCollectionPreview(collection), AutismColors.textSecondary());
        } else {
            builder.line(label + ": " + summarizeValue(label, value), colorForSummary(label, value));
        }
        return true;
    }

    private static boolean appendVec3Components(InspectionBuilder builder, Packet<?> packet, String label,
                                                String xGetter, String xField, String yGetter, String yField, String zGetter, String zField) {
        Object x = firstNonNull(invokeFirstNoArg(packet, xGetter, xField), findInspectablePropertyValue(packet, xField));
        Object y = firstNonNull(invokeFirstNoArg(packet, yGetter, yField), findInspectablePropertyValue(packet, yField));
        Object z = firstNonNull(invokeFirstNoArg(packet, zGetter, zField), findInspectablePropertyValue(packet, zField));
        if (!(x instanceof Number xn) || !(y instanceof Number yn) || !(z instanceof Number zn)) return false;
        builder.line(label + ": " + formatVec3(new Vec3(xn.doubleValue(), yn.doubleValue(), zn.doubleValue())),
            AutismColors.packetYellow());
        return true;
    }

    private static SummaryResult appendGenericSummary(Packet<?> packet, InspectionBuilder builder) {
        List<PropertyValue> properties = getInspectableProperties(packet);
        if (properties.isEmpty()) {
            String rawFallback = rawFallbackString(packet);
            if (rawFallback != null) {
                builder.line("Raw: " + rawFallback, AutismColors.textMuted());
                return new SummaryResult(true, false);
            }
            return SummaryResult.NONE;
        }

        int lines = 0;
        for (PropertyValue property : properties) {
            if (lines >= MAX_GENERIC_SUMMARY_LINES) break;
            Object value = property.value();
            if (value == null || value == INACCESSIBLE) continue;
            String summary = summarizeValue(property.label(), value);
            if (summary == null || summary.isBlank()) continue;
            builder.line(property.label() + ": " + summary, colorForSummary(property.label(), value));
            lines++;
        }
        return new SummaryResult(lines > 0, false);
    }

    private static SummaryResult appendReflectivePacketSummary(Packet<?> packet, String packetNameHint,
                                                               AutismPacketLoggerOverlay.LogEntry entry,
                                                               InspectionBuilder builder) {
        if (packet instanceof net.minecraft.network.protocol.game.ServerboundSwingPacket
            || packetMatchesHint(packet, packetNameHint, "HandSwingC2S", "HandSwingC2SPacket")) {
            Object hand = firstNonNull(
                invokeFirstNoArg(packet, "getHand", "hand"),
                getRecordComponentValue(packet, 0),
                findInspectablePropertyValue(packet, "Hand")
            );
            if (hand != null) {
                builder.line("Interaction: Swing InteractionHand (attack / use animation)", AutismColors.textPrimary());
                builder.line("Hand: " + safeLeafString(hand), AutismColors.textPrimary());
                if (MC.player != null && hand instanceof InteractionHand playerHand) {
                    builder.line("Held Item: " + formatItemStack(MC.player.getItemInHand(playerHand)), AutismColors.successText());
                }
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket updateSelectedSlotPacket) {
            Object slot = invokeFirstNoArg(updateSelectedSlotPacket, "getSelectedSlot", "getSlot", "selectedSlot", "slot");
            if (slot != null) {
                if (slot instanceof Number number) {
                    builder.line("Selected Slot: " + formatHotbarSlot(number.intValue()), AutismColors.textPrimary());
                } else {
                    builder.line("Selected Slot: " + safeLeafString(slot), AutismColors.textPrimary());
                }
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundChatPacket chatMessagePacket
            || packetMatchesHint(packet, packetNameHint, "ChatMessageC2S", "ChatMessageC2SPacket")) {
            Object message = firstNonNull(
                invokeFirstNoArg(packet, "chatMessage", "getChatMessage"),
                getRecordComponentValue(packet, 0)
            );
            Object timestamp = firstNonNull(
                invokeFirstNoArg(packet, "timestamp", "getTimestamp"),
                getRecordComponentValue(packet, 1)
            );
            Object salt = firstNonNull(
                invokeFirstNoArg(packet, "salt", "getSalt"),
                getRecordComponentValue(packet, 2)
            );
            Object signature = firstNonNull(
                invokeFirstNoArg(packet, "signature", "getSignature"),
                getRecordComponentValue(packet, 3)
            );
            Object acknowledgment = firstNonNull(
                invokeFirstNoArg(packet, "acknowledgment", "getAcknowledgment"),
                getRecordComponentValue(packet, 4)
            );
            if (message != null || timestamp != null || salt != null) {
                builder.line("Kind: Outgoing Signed Chat Message", AutismColors.textPrimary());
                if (MC.player != null) {
                    String senderName = MC.player.getName() == null ? null : MC.player.getName().getString();
                    if (senderName != null && !senderName.isBlank()) {
                        builder.line("Sender: " + senderName, AutismColors.textPrimary());
                    }
                }
                String text = extractTextValue(message);
                if (text != null && !text.isBlank()) {
                    builder.line("Message: " + quote(shorten(text)), AutismColors.successText());
                }
                if (timestamp != null) builder.line("Timestamp: " + safeLeafString(timestamp), AutismColors.textSecondary());
                if (salt != null) builder.line("Salt: " + safeLeafString(salt), AutismColors.textSecondary());
                if (signature != null) builder.line("Signature: Present", AutismColors.textSecondary());
                if (acknowledgment != null) builder.line("Acknowledgment: " + summarizeValue(acknowledgment), colorForSummary(acknowledgment));
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket
            || packetMatchesHint(packet, packetNameHint, "PlayerActionResponseS2C", "PlayerActionResponseS2CPacket")) {
            Object sequence = firstNonNull(
                invokeFirstNoArg(packet, "sequence", "getSequence"),
                getRecordComponentValue(packet, 0),
                getField(packet, "comp_633"),
                findInspectablePropertyValue(packet, "Sequence")
            );
            if (sequence != null) {
                builder.line("Meaning: Server acknowledged a sequence-based player action", AutismColors.textPrimary());
                builder.line("Sequence: " + safeLeafString(sequence), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClosePacket
            || packetMatchesHint(packet, packetNameHint, "CloseAbstractContainerScreenC2S", "ServerboundContainerClosePacket")) {
            Object syncId = firstNonNull(
                invokeFirstNoArg(packet, "getSyncId", "syncId"),
                getRecordComponentValue(packet, 0),
                getField(packet, "field_12827"),
                findInspectablePropertyValue(packet, "Sync Id", "SyncId")
            );
            if (syncId != null) {
                builder.line("Interaction: Close Handled Screen", AutismColors.textPrimary());
                builder.line("Sync Id: " + safeLeafString(syncId), AutismColors.textPrimary());
                if (entry != null && entry.capturedScreen != null && !entry.capturedScreen.isBlank()) {
                    builder.line("Closed Screen: " + entry.capturedScreen, AutismColors.successText());
                }
                if (syncId instanceof Number number && number.intValue() == 0) {
                    builder.line("Likely Screen: Player Inventory / Survival Crafting", AutismColors.successText());
                }
                builder.line("Note: This packet only sends the sync id, not the closed screen title/type", AutismColors.textSecondary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
            || packetMatchesHint(packet, packetNameHint, "EntityEquipmentUpdateS2C", "EntityEquipmentUpdateS2CPacket")) {
            Object entityIdValue = firstNonNull(
                invokeFirstNoArg(packet, "getEntityId", "entityId", "id"),
                getRecordComponentValue(packet, 0),
                getField(packet, "field_12565"),
                findInspectablePropertyValue(packet, "Entity Id", "EntityId")
            );
            Object equipmentListValue = firstNonNull(
                invokeFirstNoArg(packet, "getEquipmentList", "equipmentList"),
                getRecordComponentValue(packet, 1),
                getField(packet, "field_25721"),
                findInspectablePropertyValue(packet, "Equipment List", "EquipmentList")
            );
            if (entityIdValue != null || equipmentListValue != null) {
                builder.line("Meaning: Server updated an entity's visible equipment", AutismColors.textPrimary());
                if (entityIdValue instanceof Number entityIdNumber) {
                    appendEntityIdLine(builder, "Entity Id", entityIdNumber.intValue());
                } else if (entityIdValue != null) {
                    builder.line("Entity Id: " + safeLeafString(entityIdValue), AutismColors.textPrimary());
                }
                if (equipmentListValue instanceof Collection<?> equipmentEntries) {
                    builder.line("Changed Slots: " + equipmentEntries.size(), AutismColors.textPrimary());
                    String equipmentSummary = summarizeEquipmentEntries(equipmentEntries);
                    if (equipmentSummary != null && !equipmentSummary.isBlank()) {
                        builder.line("Equipment: " + equipmentSummary, AutismColors.successText());
                    }
                } else if (equipmentListValue != null) {
                    builder.line("Equipment: " + summarizeValue(equipmentListValue), colorForSummary(equipmentListValue));
                }
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket buttonClickPacket) {
            Object syncId = invokeFirstNoArg(buttonClickPacket, "getSyncId", "syncId");
            Object buttonId = invokeFirstNoArg(buttonClickPacket, "getButtonId", "buttonId");
            if (syncId != null || buttonId != null) {
                builder.line("Interaction: Click handled-screen button", AutismColors.textPrimary());
                if (syncId != null) builder.line("Sync Id: " + safeLeafString(syncId), AutismColors.textPrimary());
                if (buttonId != null) builder.line("Button Id: " + safeLeafString(buttonId), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket creativeInventoryActionPacket) {
            Object slot = invokeFirstNoArg(creativeInventoryActionPacket, "getSlot", "slot");
            Object stack = invokeFirstNoArg(creativeInventoryActionPacket, "getStack", "stack");
            if (slot != null || stack != null) {
                if (slot != null) builder.line("Slot: " + safeLeafString(slot), AutismColors.textPrimary());
                if (stack != null) builder.line("Stack: " + summarizeValue(stack), colorForSummary(stack));
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket clickSlotPacket) {
            Object syncId = invokeFirstNoArg(clickSlotPacket, "getSyncId", "syncId");
            Object revision = invokeFirstNoArg(clickSlotPacket, "getRevision", "revision");
            Object slot = invokeFirstNoArg(clickSlotPacket, "getSlot", "slot");
            Object button = invokeFirstNoArg(clickSlotPacket, "getButton", "button");
            Object actionType = invokeFirstNoArg(clickSlotPacket, "getActionType", "actionType");
            Object modifiedStacks = invokeFirstNoArg(clickSlotPacket, "getModifiedStacks", "modifiedStacks");
            Object cursorStack = invokeFirstNoArg(clickSlotPacket, "cursor", "getStack", "getCursorStack", "cursorStack");
            if (syncId != null || slot != null || actionType != null) {
                builder.line("Interaction: " + describeContainerInput(actionType), AutismColors.textPrimary());
                if (syncId != null) builder.line("Sync Id: " + safeLeafString(syncId), AutismColors.textPrimary());
                if (revision != null) builder.line("Revision: " + safeLeafString(revision), AutismColors.textSecondary());
                if (slot != null) builder.line("Slot: " + safeLeafString(slot), AutismColors.textPrimary());
                if (button != null) builder.line("Button: " + safeLeafString(button), AutismColors.textPrimary());
                if (actionType != null) builder.line("Action Type: " + safeLeafString(actionType), AutismColors.textPrimary());
                if (cursorStack != null) builder.line("Cursor: " + summarizeValue(cursorStack), colorForSummary(cursorStack));
                if (modifiedStacks instanceof Map<?, ?> map) {
                    builder.line("Changed Slots: " + map.size(), AutismColors.textSecondary());
                } else if (modifiedStacks != null) {
                    builder.line("Changed Slots: " + summarizeValue(modifiedStacks), colorForSummary(modifiedStacks));
                }
                return new SummaryResult(true, false);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundEditBookPacket bookUpdatePacket) {
            Object slot = invokeFirstNoArg(bookUpdatePacket, "slot", "getSlot");
            Object pages = invokeFirstNoArg(bookUpdatePacket, "pages", "getPages");
            Object title = invokeFirstNoArg(bookUpdatePacket, "title", "getTitle");
            if (slot != null || pages != null || title != null) {
                boolean signing = title instanceof Optional<?> optional && optional.isPresent();
                builder.line("Interaction: " + (signing ? "Sign Book" : "Edit Book"), AutismColors.textPrimary());
                if (slot instanceof Number number) {
                    builder.line("Slot: " + formatHotbarSlot(number.intValue()), AutismColors.textPrimary());
                } else if (slot != null) {
                    builder.line("Slot: " + safeLeafString(slot), AutismColors.textPrimary());
                }
                if (title != null) {
                    String titleComponent = extractTextValue(title);
                    if (titleComponent != null && !titleComponent.isBlank()) {
                            builder.line("Title: " + quote(shorten(titleComponent)), AutismColors.successText());
                    }
                }
                if (pages instanceof Collection<?> collection) {
                    builder.line("Pages: " + collection.size(), AutismColors.textPrimary());
                    String preview = summarizeBookPages(collection);
                    if (preview != null) builder.line("Preview: " + preview, AutismColors.textSecondary());
                }
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundSignUpdatePacket updateSignPacket) {
            Object pos = invokeFirstNoArg(updateSignPacket, "getPos", "pos");
            Object lines = invokeFirstNoArg(updateSignPacket, "getText", "text");
            Object front = invokeFirstNoArg(updateSignPacket, "isFront", "front");
            if (pos != null || lines != null || front != null) {
                builder.line("Interaction: Update Sign Component", AutismColors.textPrimary());
                if (pos != null) builder.line("Block Pos: " + summarizeValue(pos), colorForSummary(pos));
                if (front != null) builder.line("Side: " + (Boolean.TRUE.equals(front) ? "Front" : "Back"), AutismColors.textPrimary());
                if (lines instanceof String[] stringLines) {
                    builder.line("Component: " + summarizeSignLines(stringLines), AutismColors.successText());
                } else if (lines != null) {
                    builder.line("Component: " + summarizeValue(lines), colorForSummary(lines));
                }
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundRenameItemPacket renameItemPacket) {
            Object name = invokeFirstNoArg(renameItemPacket, "getName", "name");
            if (name != null) {
                builder.line("Interaction: Rename Item in Anvil", AutismColors.textPrimary());
                builder.line("Name: " + quote(shorten(String.valueOf(name))), AutismColors.successText());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket craftRequestPacket) {
            Object syncId = invokeFirstNoArg(craftRequestPacket, "syncId", "getSyncId");
            Object recipeId = invokeFirstNoArg(craftRequestPacket, "recipeId", "getRecipeId");
            Object craftAll = invokeFirstNoArg(craftRequestPacket, "craftAll", "isCraftAll", "getCraftAll");
            if (syncId != null || recipeId != null || craftAll != null) {
                builder.line("Interaction: Craft Recipe", AutismColors.textPrimary());
                if (syncId != null) builder.line("Sync Id: " + safeLeafString(syncId), AutismColors.textPrimary());
                if (recipeId != null) builder.line("Recipe: " + safeLeafString(recipeId), AutismColors.successText());
                if (craftAll != null) builder.line("Craft All: " + safeLeafString(craftAll), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundSelectTradePacket selectMerchantTradePacket) {
            Object tradeId = invokeFirstNoArg(selectMerchantTradePacket, "getTradeId", "tradeId");
            if (tradeId != null) {
                builder.line("Interaction: Select Merchant Trade", AutismColors.textPrimary());
                builder.line("Trade Index: " + safeLeafString(tradeId), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket screenHandlerPropertyUpdatePacket) {
            Object syncId = invokeFirstNoArg(screenHandlerPropertyUpdatePacket, "getSyncId", "syncId");
            Object property = invokeFirstNoArg(screenHandlerPropertyUpdatePacket, "getPropertyId", "propertyId", "getProperty", "property");
            Object value = invokeFirstNoArg(screenHandlerPropertyUpdatePacket, "getValue", "value");
            if (syncId != null || property != null || value != null) {
                if (syncId != null) builder.line("Sync Id: " + safeLeafString(syncId), AutismColors.textPrimary());
                if (property != null) builder.line("Property: " + safeLeafString(property), AutismColors.textPrimary());
                if (value != null) builder.line("Value: " + safeLeafString(value), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket blockBreakingProgressPacket) {
            Object entityId = invokeFirstNoArg(blockBreakingProgressPacket, "getEntityId", "entityId");
            Object pos = invokeFirstNoArg(blockBreakingProgressPacket, "getPos", "pos");
            Object progress = invokeFirstNoArg(blockBreakingProgressPacket, "getProgress", "progress");
            if (entityId != null || pos != null || progress != null) {
                if (entityId instanceof Number number) {
                    appendEntityIdLine(builder, "Entity Id", number.intValue());
                } else if (entityId != null) {
                    builder.line("Entity Id: " + safeLeafString(entityId), AutismColors.textPrimary());
                }
                if (pos != null) builder.line("Block Pos: " + summarizeValue(pos), colorForSummary(pos));
                if (progress != null) builder.line("Progress: " + safeLeafString(progress), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundEntityEventPacket entityStatusPacket) {
            Object entityId = invokeFirstNoArg(entityStatusPacket, "getEntityId", "entityId");
            if (entityId == null && MC.level != null) {
                Entity entity = entityStatusPacket.getEntity(MC.level);
                if (entity != null) entityId = entity.getId();
            }
            Object status = invokeFirstNoArg(entityStatusPacket, "getStatus", "status");
            if (status == null) status = invokeFirstNoArg(entityStatusPacket, "getEventId", "eventId");
            if (entityId != null || status != null) {
                if (entityId instanceof Number number) {
                    appendEntityIdLine(builder, "Entity Id", number.intValue());
                } else if (entityId != null) {
                    builder.line("Entity Id: " + safeLeafString(entityId), AutismColors.textPrimary());
                }
                if (status != null) builder.line("Status: " + safeLeafString(status), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket entityVelocityUpdatePacket) {
            Object entityId = invokeFirstNoArg(entityVelocityUpdatePacket, "getEntityId", "entityId", "id");
            Object velocity = invokeFirstNoArg(entityVelocityUpdatePacket, "getVelocity", "velocity");
            if (velocity == null) velocity = invokeFirstNoArg(entityVelocityUpdatePacket, "movement");
            if (entityId != null || velocity != null) {
                if (entityId instanceof Number number) {
                    appendEntityIdLine(builder, "Entity Id", number.intValue());
                } else if (entityId != null) {
                    builder.line("Entity Id: " + safeLeafString(entityId), AutismColors.textPrimary());
                }
                if (velocity != null) builder.line("Velocity: " + summarizeValue(velocity), colorForSummary(velocity));
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket gameMessagePacket) {
            Object content = invokeFirstNoArg(gameMessagePacket, "content", "getContent");
            Object overlay = invokeFirstNoArg(gameMessagePacket, "overlay", "isOverlay", "getOverlay");
            if (content != null || overlay != null) {
                String message = extractTextValue(content);
                builder.line("Kind: " + (Boolean.TRUE.equals(overlay) ? "Overlay / HUD message" : "Game / system message"),
                    AutismColors.textPrimary());
                ParsedChatLine parsed = parseChatLine(message);
                if (parsed != null) {
                    builder.line("Possible Sender: " + parsed.sender(), AutismColors.textPrimary());
                    builder.line("Message: " + quote(shorten(parsed.message())), AutismColors.successText());
                } else if (message != null && !message.isBlank()) {
                    builder.line("Message: " + quote(shorten(message)), AutismColors.successText());
                } else if (content != null) {
                    builder.line("Message: " + summarizeValue(content), colorForSummary(content));
                }
                if (overlay != null) builder.line("Overlay: " + safeLeafString(overlay), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket profilelessChatMessagePacket) {
            Object message = invokeFirstNoArg(profilelessChatMessagePacket, "message", "getMessage");
            Object chatType = invokeFirstNoArg(profilelessChatMessagePacket, "chatType", "getChatType");
            ChatSummaryState chatSummary = appendChatSummary(message, chatType, null, null, null, "Incoming Chat Message", builder);
            if (chatSummary.wroteAny()) {
                return new SummaryResult(true, chatSummary.complete());
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket chatMessageS2CPacket) {
            Object body = firstNonNull(
                invokeFirstNoArg(chatMessageS2CPacket, "body", "getBody"),
                getRecordComponentValue(chatMessageS2CPacket, 4),
                findRecordComponentByMethodNames(chatMessageS2CPacket, 2, "content", "timestamp", "salt")
            );
            Object serializedParameters = firstNonNull(
                invokeFirstNoArg(chatMessageS2CPacket, "serializedParameters", "getSerializedParameters"),
                getRecordComponentValue(chatMessageS2CPacket, 7),
                findRecordComponentByMethodNames(chatMessageS2CPacket, 2, "name", "targetName", "type")
            );
            Object sender = firstNonNull(
                invokeFirstNoArg(chatMessageS2CPacket, "sender", "getSender"),
                getRecordComponentValue(chatMessageS2CPacket, 1),
                findRecordComponentByValueType(chatMessageS2CPacket, UUID.class)
            );
            Object filterMask = firstNonNull(
                invokeFirstNoArg(chatMessageS2CPacket, "filterMask", "getFilterMask"),
                getRecordComponentValue(chatMessageS2CPacket, 6),
                findRecordComponentByMethodNames(chatMessageS2CPacket, 1, "isFullyFiltered", "isPassThrough")
            );
            Object unsignedContent = firstNonNull(
                invokeFirstNoArg(chatMessageS2CPacket, "unsignedContent", "getUnsignedContent"),
                getRecordComponentValue(chatMessageS2CPacket, 5),
                findRecordTextComponent(chatMessageS2CPacket, body, serializedParameters, sender, filterMask)
            );
            Object message = unsignedContent;
            Object timestamp = null;
            if (body != null) {
                Object bodyContent = firstNonNull(
                    invokeFirstNoArg(body, "content", "getContent"),
                    getRecordComponentValue(body, 0),
                    findRecordTextComponent(body)
                );
                if (bodyContent != null && (message == null || extractTextValue(message) == null || extractTextValue(message).isBlank())) {
                    message = bodyContent;
                }
                timestamp = firstNonNull(
                    invokeFirstNoArg(body, "timestamp", "getTimestamp"),
                    getRecordComponentValue(body, 1),
                    findRecordComponentByValueType(body, Instant.class)
                );
            }
            ChatSummaryState chatSummary = appendChatSummary(message, serializedParameters, sender instanceof UUID uuid ? uuid : null, timestamp, filterMask,
                "Incoming Signed Chat Message", builder);
            if (chatSummary.wroteAny()) {
                return new SummaryResult(true, chatSummary.complete());
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket overlayMessagePacket) {
            Object text = invokeFirstNoArg(overlayMessagePacket, "text", "getText");
            String message = extractTextValue(text);
            if (message != null || text != null) {
                builder.line("Kind: Overlay / HUD message", AutismColors.textPrimary());
                builder.line("Message: " + (message == null ? summarizeValue(text) : quote(shorten(message))),
                    message == null ? colorForSummary(text) : AutismColors.successText());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundOpenBookPacket openWrittenBookPacket) {
            Object hand = invokeFirstNoArg(openWrittenBookPacket, "getHand", "hand");
            if (hand != null) {
                builder.line("Interaction: Open Written Book", AutismColors.textPrimary());
                builder.line("Hand: " + safeLeafString(hand), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.common.ClientboundDisconnectPacket disconnectPacket) {
            Object reason = invokeFirstNoArg(disconnectPacket, "reason", "getReason");
            if (reason != null) {
                builder.line("Reason: " + summarizeValue(reason), colorForSummary(reason));
                return new SummaryResult(true, true);
            }
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket playerMovePacket) {
            Object x = invokeFirstNoArg(playerMovePacket, "getX", "x");
            Object y = invokeFirstNoArg(playerMovePacket, "getY", "y");
            Object z = invokeFirstNoArg(playerMovePacket, "getZ", "z");
            Object yaw = invokeFirstNoArg(playerMovePacket, "getYaw", "yaw");
            Object pitch = invokeFirstNoArg(playerMovePacket, "getPitch", "pitch");
            Object onGround = invokeFirstNoArg(playerMovePacket, "isOnGround", "onGround");
            if (x != null || y != null || z != null || yaw != null || pitch != null || onGround != null) {
                if (x != null || y != null || z != null) {
                    builder.line("Pos: x=" + safeLeafString(x) + ", y=" + safeLeafString(y) + ", z=" + safeLeafString(z),
                        AutismColors.textPrimary());
                }
                if (yaw != null || pitch != null) {
                    builder.line("Rot: yaw=" + safeLeafString(yaw) + ", pitch=" + safeLeafString(pitch), AutismColors.textPrimary());
                }
                if (onGround != null) builder.line("On Ground: " + safeLeafString(onGround), AutismColors.textPrimary());
                return new SummaryResult(true, true);
            }
        }

        return SummaryResult.NONE;
    }

    private static void dumpRootFields(Object root, InspectionBuilder builder, IdentityHashMap<Object, Boolean> visited) {
        visited.put(root, Boolean.TRUE);
        List<PropertyValue> properties = getInspectableProperties(root);
        List<Field> fields = getInspectableFields(root.getClass());
        Set<String> consumedFieldNames = new LinkedHashSet<>();
        for (PropertyValue property : properties) {
            dumpValue(property.label(), property.value(), 0, builder, visited);
            consumedFieldNames.add(decapitalize(normalizeName(property.label())));
        }
        boolean wroteAny = !properties.isEmpty();
        for (Field field : fields) {
            String fieldLabel = resolveFieldLabel(field);
            if (fieldLabel == null || fieldLabel.isBlank()) continue;
            if (consumedFieldNames.contains(decapitalize(normalizeName(fieldLabel)))) continue;
            dumpValue(fieldLabel, readField(field, root), 0, builder, visited);
            wroteAny = true;
        }
        if (!wroteAny) {
            String rawFallback = rawFallbackString(root);
            if (rawFallback != null) {
                builder.line("Raw: " + rawFallback, AutismColors.textMuted());
            } else {
                builder.line("<No readable fields>", AutismColors.textMuted());
            }
        }
    }

    private static void dumpValue(String label, Object value, int depth, InspectionBuilder builder, IdentityHashMap<Object, Boolean> visited) {
        String indent = "  ".repeat(Math.max(0, depth));
        try {
            dumpValueUnsafe(label, value, depth, indent, builder, visited);
        } catch (Throwable t) {
            builder.line(indent + label + ": <error reading value: " + t.getClass().getSimpleName() + ">", AutismColors.dangerText());
        }
    }

    private static void dumpValueUnsafe(String label, Object value, int depth, String indent, InspectionBuilder builder,
                                        IdentityHashMap<Object, Boolean> visited) {
        if (builder.isFull()) return;

        if (value == INACCESSIBLE) {
            builder.line(indent + label + ": <inaccessible>", AutismColors.textMuted());
            return;
        }
        if (value == null) {
            builder.line(indent + label + ": null", AutismColors.textMuted());
            return;
        }
        if (depth >= MAX_DEPTH) {
            builder.line(indent + label + ": <max depth reached>", AutismColors.textMuted());
            return;
        }

        Class<?> valueClass = value.getClass();
        if (isSimpleValue(value)) {
            builder.line(indent + label + ": " + formatSimpleValue(label, value), semanticFieldColor(label, value));
            return;
        }

        if (value instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                builder.line(indent + label + ": Optional.empty", AutismColors.textMuted());
                return;
            }
            builder.line(indent + label + ": Optional", AutismColors.textSecondary());
            dumpValue("value", optional.get(), depth + 1, builder, visited);
            return;
        }

        if (value instanceof BlockHitResult hit) {
            builder.line(indent + label + ": BlockHitResult", AutismColors.textSecondary());
            dumpValue("blockPos", hit.getBlockPos(), depth + 1, builder, visited);
            dumpValue("hitPos", hit.getLocation(), depth + 1, builder, visited);
            dumpValue("side", hit.getDirection(), depth + 1, builder, visited);
            dumpValue("type", hit.getType(), depth + 1, builder, visited);
            dumpValue("insideBlock", hit.isInside(), depth + 1, builder, visited);
            return;
        }

        if (value instanceof Map<?, ?> map) {
            builder.line(indent + label + ": Map[" + map.size() + "]", AutismColors.textSecondary());
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index >= MAX_COLLECTION_ITEMS) {
                    builder.line(indent + "  ... " + (map.size() - index) + " more entries", AutismColors.textMuted());
                    return;
                }
                if (isSimpleValue(entry.getKey()) && isSimpleValue(entry.getValue())) {
                    builder.line(indent + "  [" + index + "] " + formatSimpleValue(entry.getKey()) + " = " + formatSimpleValue(entry.getValue()),
                        AutismColors.textPrimary());
                } else {
                    builder.line(indent + "  [" + index + "]", AutismColors.textSecondary());
                    dumpValue("key", entry.getKey(), depth + 2, builder, visited);
                    dumpValue("value", entry.getValue(), depth + 2, builder, visited);
                }
                index++;
            }
            return;
        }

        if (value instanceof Collection<?> collection) {
            builder.line(indent + label + ": List[" + collection.size() + "]", AutismColors.textSecondary());
            int index = 0;
            for (Object item : collection) {
                if (index >= MAX_COLLECTION_ITEMS) {
                    builder.line(indent + "  ... " + (collection.size() - index) + " more entries", AutismColors.textMuted());
                    return;
                }
                dumpValue("[" + index + "]", item, depth + 1, builder, visited);
                index++;
            }
            return;
        }

        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            if (valueClass.getComponentType().isPrimitive()) {
                builder.line(indent + label + ": " + formatPrimitiveArray(value), AutismColors.textPrimary());
                return;
            }
            builder.line(indent + label + ": " + prettifyClassName(valueClass) + "[" + length + "]", AutismColors.textSecondary());
            for (int i = 0; i < Math.min(length, MAX_ARRAY_ITEMS); i++) {
                dumpValue("[" + i + "]", Array.get(value, i), depth + 1, builder, visited);
            }
            if (length > MAX_ARRAY_ITEMS) {
                builder.line(indent + "  ... " + (length - MAX_ARRAY_ITEMS) + " more entries", AutismColors.textMuted());
            }
            return;
        }

        if (visited.containsKey(value)) {
            builder.line(indent + label + ": <already shown " + prettifyClassName(valueClass) + ">", AutismColors.textMuted());
            return;
        }

        if (shouldTreatAsLeaf(valueClass)) {
            builder.line(indent + label + ": " + safeLeafString(value), semanticFieldColor(label, value));
            return;
        }

        visited.put(value, Boolean.TRUE);
        List<PropertyValue> properties = getInspectableProperties(value);
        List<Field> fields = getInspectableFields(valueClass);
        if (properties.isEmpty() && fields.isEmpty()) {
            builder.line(indent + label + ": " + safeLeafString(value), semanticFieldColor(label, value));
            return;
        }

        builder.line(indent + label + ": " + prettifyClassName(valueClass), AutismColors.textSecondary());
        Set<String> consumedFieldNames = new LinkedHashSet<>();
        for (PropertyValue property : properties) {
            dumpValue(property.label(), property.value(), depth + 1, builder, visited);
            consumedFieldNames.add(decapitalize(normalizeName(property.label())));
        }
        for (Field field : fields) {
            String fieldLabel = resolveFieldLabel(field);
            if (fieldLabel == null || fieldLabel.isBlank()) continue;
            if (consumedFieldNames.contains(decapitalize(normalizeName(fieldLabel)))) continue;
            dumpValue(fieldLabel, readField(field, value), depth + 1, builder, visited);
        }
    }

    private static List<PropertyValue> getInspectableProperties(Object owner) {
        if (owner == null) return List.of();

        Map<String, PropertyValue> properties = new LinkedHashMap<>();
        Class<?> type = owner.getClass();

        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                Object value = invokeAccessor(component.getAccessor(), owner);
                putProperty(properties, resolveRecordComponentLabel(type, component), value, PropertySource.RECORD);
            }
        }

        Arrays.stream(type.getMethods())
            .filter(method -> isInspectableMethod(method))
            .sorted(Comparator.comparing(Method::getName, String.CASE_INSENSITIVE_ORDER))
            .forEach(method -> putProperty(properties, propertyLabelFor(method), invokeAccessor(method, owner), PropertySource.METHOD));

        if (properties.isEmpty()) {
            addFallbackRecordProperties(properties, owner, type);
        }

        return new ArrayList<>(properties.values());
    }

    private static void addFallbackRecordProperties(Map<String, PropertyValue> properties, Object owner, Class<?> type) {
        if (!type.isRecord()) return;
        Map<String, Integer> labelCounts = new LinkedHashMap<>();
        for (RecordComponent component : type.getRecordComponents()) {
            Object value = invokeAccessor(component.getAccessor(), owner);
            String alias = firstNonBlank(resolveRecordComponentLabel(type, component),
                fallbackLabelFor(component.getAccessor().getReturnType(), labelCounts));
            putProperty(properties, alias, value, PropertySource.RECORD);
        }
    }

    private static String fallbackLabelFor(Class<?> type, Map<String, Integer> labelCounts) {
        String base = fallbackBaseLabel(type);
        int count = labelCounts.getOrDefault(base, 0) + 1;
        labelCounts.put(base, count);
        return count == 1 ? base : base + " " + count;
    }

    private static String fallbackBaseLabel(Class<?> type) {
        if (type == null) return "Value";
        if (type == boolean.class || type == Boolean.class) return "Flag";
        if (type == String.class || Component.class.isAssignableFrom(type)) return "Component";
        if (type == UUID.class) return "Uuid";
        if (type == Identifier.class) return "Identifier";
        if (type == BlockPos.class) return "Block Pos";
        if (type == Vec3.class) return "Position";
        if (type == ItemStack.class) return "Item";
        if (type == Tag.class) return "Nbt";
        if (type == Instant.class) return "Timestamp";
        if (type.isEnum()) return "Type";
        if (type.isPrimitive()) {
            if (type == int.class || type == long.class || type == short.class || type == byte.class) return "Number";
            if (type == float.class || type == double.class) return "Decimal";
            if (type == char.class) return "Character";
        }
        if (Number.class.isAssignableFrom(type)) return "Number";
        if (Collection.class.isAssignableFrom(type)) return "Entries";
        if (Map.class.isAssignableFrom(type)) return "Map";
        if (Optional.class.isAssignableFrom(type)) return "Optional";
        if (type.isArray()) return fallbackBaseLabel(type.getComponentType()) + " List";
        return prettifyClassName(type);
    }

    private static void putProperty(Map<String, PropertyValue> properties, String label, Object value, PropertySource source) {
        if (label == null || label.isBlank()) return;
        if (isObfuscatedComponentLabel(label)) return;
        if (isIgnoredPropertyLabel(label)) return;
        String key = normalizeName(label);
        if (properties.containsKey(key)) return;
        properties.put(key, new PropertyValue(prettifyLabel(label), value, source));
    }

    private static boolean isInspectableMethod(Method method) {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)) return false;
        if (Modifier.isStatic(modifiers)) return false;
        if (method.isSynthetic()) return false;
        if (method.getParameterCount() != 0) return false;
        if (method.getReturnType() == Void.TYPE) return false;
        if (isIgnoredPropertyLabel(method.getName())) return false;
        String mappedLabel = normalizeAccessorLabel(AutismYarnMappings.lookupMethodLabel(method), method.getReturnType());
        if (isIgnoredPropertyLabel(mappedLabel)) return false;
        return isGetterLikeMethod(method)
            || EXTRA_ACCESSOR_NAMES.contains(method.getName())
            || (mappedLabel != null && EXTRA_ACCESSOR_NAMES.contains(mappedLabel))
            || (mappedLabel != null && !isObfuscatedComponentLabel(mappedLabel));
    }

    private static boolean isGetterLikeMethod(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) return true;
        return name.startsWith("is") && name.length() > 2
            && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class);
    }

    private static String propertyLabelFor(Method method) {
        String mappedLabel = normalizeAccessorLabel(AutismYarnMappings.lookupMethodLabel(method), method.getReturnType());
        if (mappedLabel != null && !mappedLabel.isBlank()) {
            return mappedLabel;
        }
        return normalizeAccessorLabel(method.getName(), method.getReturnType());
    }

    private static Object invokeAccessor(Method method, Object owner) {
        try {
            if (!method.canAccess(owner)) method.setAccessible(true);
            return method.invoke(owner);
        } catch (Throwable ignored) {
            return INACCESSIBLE;
        }
    }

    private static List<Field> getInspectableFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            fields.addAll(Arrays.stream(cursor.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !Modifier.isTransient(field.getModifiers()))
                .filter(field -> !field.isSynthetic())
                .filter(field -> {
                    String fieldLabel = resolveFieldLabel(field);
                    return fieldLabel != null && !fieldLabel.isBlank();
                })
                .sorted((a, b) -> resolveFieldLabel(a).compareToIgnoreCase(resolveFieldLabel(b)))
                .collect(Collectors.toList()));
            cursor = cursor.getSuperclass();
        }
        return fields;
    }

    private static Object readField(Field field, Object owner) {
        try {
            if (!field.canAccess(owner)) field.setAccessible(true);
            return field.get(owner);
        } catch (Throwable ignored) {
            return INACCESSIBLE;
        }
    }

    private static Object getField(Object owner, String fieldName) {
        if (owner == null || fieldName == null) return null;
        Class<?> cursor = owner.getClass();
        while (cursor != null && cursor != Object.class) {
            try {
                Field field = cursor.getDeclaredField(fieldName);
                if (!field.canAccess(owner)) field.setAccessible(true);
                return field.get(owner);
            } catch (Throwable ignored) {  }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private static Object firstNonNullField(Object owner, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = getField(owner, fieldName);
            if (value != null) return value;
        }
        return null;
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null && value != INACCESSIBLE) return value;
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String resolveRecordComponentLabel(Class<?> ownerType, RecordComponent component) {
        if (component == null) return null;
        return firstNonBlank(AutismYarnMappings.lookupMethodLabel(component.getAccessor()), component.getName());
    }

    private static String resolveFieldLabel(Field field) {
        if (field == null) return null;
        return firstNonBlank(AutismYarnMappings.lookupFieldLabel(field),
            isObfuscatedComponentLabel(field.getName()) ? null : field.getName());
    }

    private static Object getRecordComponentValue(Object owner, int index) {
        if (owner == null || index < 0) return null;
        try {
            Class<?> type = owner.getClass();
            if (!type.isRecord()) return null;
            RecordComponent[] components = type.getRecordComponents();
            if (components == null || index >= components.length) return null;
            return invokeAccessor(components[index].getAccessor(), owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findRecordComponentByValueType(Object owner, Class<?> expectedType) {
        if (owner == null || expectedType == null) return null;
        try {
            Class<?> type = owner.getClass();
            if (!type.isRecord()) return null;
            RecordComponent[] components = type.getRecordComponents();
            if (components == null) return null;
            for (RecordComponent component : components) {
                Object value = invokeAccessor(component.getAccessor(), owner);
                if (value != null && value != INACCESSIBLE && expectedType.isInstance(value)) {
                    return value;
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Object findRecordComponentByMethodNames(Object owner, int requiredMatches, String... methodNames) {
        if (owner == null || requiredMatches <= 0 || methodNames == null || methodNames.length == 0) return null;
        try {
            Class<?> type = owner.getClass();
            if (!type.isRecord()) return null;
            RecordComponent[] components = type.getRecordComponents();
            if (components == null) return null;
            for (RecordComponent component : components) {
                Object value = invokeAccessor(component.getAccessor(), owner);
                if (value == null || value == INACCESSIBLE) continue;
                int matches = 0;
                for (String methodName : methodNames) {
                    if (hasNoArgMethod(value.getClass(), methodName)) {
                        matches++;
                    }
                }
                if (matches >= requiredMatches) {
                    return value;
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Object findRecordTextComponent(Object owner, Object... excludedValues) {
        if (owner == null) return null;
        try {
            Class<?> type = owner.getClass();
            if (!type.isRecord()) return null;
            RecordComponent[] components = type.getRecordComponents();
            if (components == null) return null;
            for (RecordComponent component : components) {
                Object value = invokeAccessor(component.getAccessor(), owner);
                if (value == null || value == INACCESSIBLE || isExcludedReference(value, excludedValues)) continue;
                String text = extractTextValue(value);
                if (text != null && !text.isBlank()) {
                    return value;
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Integer getIntField(Object owner, String fieldName) {
        Object value = getField(owner, fieldName);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Boolean getBooleanField(Object owner, String fieldName) {
        Object value = getField(owner, fieldName);
        return value instanceof Boolean bool ? bool : null;
    }

    private static Object invokeNoArg(Object owner, String methodName) {
        if (owner == null || methodName == null) return null;
        try {
            Method method = owner.getClass().getMethod(methodName);
            if (!method.canAccess(owner)) method.setAccessible(true);
            return method.invoke(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeFirstNoArg(Object owner, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = invokeNoArg(owner, methodName);
            if (value != null) return value;
        }
        return null;
    }

    private static Object findInspectablePropertyValue(Object owner, String... labels) {
        if (owner == null || labels == null || labels.length == 0) return null;
        List<PropertyValue> properties = getInspectableProperties(owner);
        for (String label : labels) {
            String normalizedTarget = normalizeName(label);
            for (PropertyValue property : properties) {
                if (normalizedTarget.equals(normalizeName(property.label()))) {
                    Object value = property.value();
                    if (value != null && value != INACCESSIBLE) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasNoArgMethod(Class<?> type, String methodName) {
        if (type == null || methodName == null || methodName.isBlank()) return false;
        try {
            type.getMethod(methodName);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isExcludedReference(Object value, Object... excludedValues) {
        if (value == null || excludedValues == null) return false;
        for (Object excluded : excludedValues) {
            if (value == excluded) return true;
        }
        return false;
    }

    private static boolean packetMatchesHint(Packet<?> packet, String packetNameHint, String... names) {
        if (packetNameHint != null) {
            for (String name : names) {
                if (name.equalsIgnoreCase(packetNameHint)) return true;
            }
        }
        String simpleName = packet == null || packet.getClass() == null ? null : packet.getClass().getSimpleName();
        if (simpleName != null) {
            for (String name : names) {
                if (name.equalsIgnoreCase(simpleName)) return true;
            }
        }
        return false;
    }

    private static boolean isSimpleValue(Object value) {
        if (value == null) return true;
        return value instanceof String
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof Character
            || value instanceof Enum<?>
            || value instanceof UUID
            || value instanceof Instant
            || value instanceof Identifier
            || value instanceof Component
            || value instanceof BlockPos
            || value instanceof Vec3
            || value instanceof Entity
            || value instanceof ItemStack
            || value instanceof Tag
            || value instanceof BlockState
            || value instanceof Holder<?>
            || value instanceof BitSet
            || value instanceof Class<?>;
    }

    private static boolean shouldTreatAsLeaf(Class<?> type) {
        if (type == null) return true;
        String name = type.getName();
        return Entity.class.isAssignableFrom(type)
            || ItemStack.class.isAssignableFrom(type)
            || Tag.class.isAssignableFrom(type)
            || name.startsWith("net.minecraft.world.")
            || name.startsWith("net.minecraft.client.")
            || name.startsWith("io.netty.")
            || name.contains("StreamCodec")
            || name.contains("PacketType")
            || name.contains("ByteBuf");
    }

    private static int colorForValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? AutismColors.packetGreen() : AutismColors.packetPink();
        }
        if (value instanceof Entity || value instanceof UUID) {
            return AutismColors.packetCyan();
        }
        if (value instanceof BlockState || value instanceof BlockPos || value instanceof Vec3) {
            return AutismColors.packetYellow();
        }
        if (value instanceof ItemStack || value instanceof Component || value instanceof String) {
            return AutismColors.packetGreen();
        }
        if (value instanceof Enum<?>) {
            return AutismColors.packetOrange();
        }
        if (value instanceof Identifier) {
            return AutismColors.packetBlue();
        }
        if (value instanceof Number || value instanceof Instant || value instanceof BitSet) {
            return AutismColors.packetGray();
        }
        return AutismColors.packetWhite();
    }

    private static int directionColor(String direction) {
        return "C2S".equalsIgnoreCase(direction) ? AutismColors.packetCyan() : AutismColors.packetOrange();
    }

    private static String formatSimpleValue(Object value) {
        return formatSimpleValue("", value);
    }

    private static String formatSimpleValue(String label, Object value) {
        if (value == null) return "null";
        if (value instanceof Number number && isEntityIdLabel(label)) return formatEntityId(number.intValue());
        if (value instanceof String string) return quote(shorten(string));
        if (value instanceof Character c) return quote(String.valueOf(c));
        if (value instanceof Component text) return quote(shorten(text.getString()));
        if (value instanceof Identifier id) return id.toString();
        if (value instanceof UUID uuid) return uuid.toString();
        if (value instanceof Instant instant) return instant.toString();
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        if (value instanceof BlockPos pos) return formatBlockPos(pos);
        if (value instanceof Vec3 vec) return formatVec3(vec);
        if (value instanceof Entity entity) return formatEntity(entity);
        if (value instanceof ItemStack stack) return formatItemStack(stack);
        if (value instanceof Tag nbt) return shorten(nbt.asString().orElse(String.valueOf(nbt)));
        if (value instanceof BlockState state) return formatBlockState(state);
        if (value instanceof Holder<?> entry) return formatHolder(entry);
        if (value instanceof BitSet bitSet) return formatBitSet(bitSet);
        if (value instanceof Class<?> clazz) return prettifyClassName(clazz);
        return rewriteKnownClassAliases(shorten(String.valueOf(value)));
    }

    private static String safeLeafString(Object value) {
        if (value == null) return "null";
        if (isSimpleValue(value)) return formatSimpleValue(value);
        try {
            return rewriteKnownClassAliases(shorten(String.valueOf(value)));
        } catch (Throwable ignored) {
            return "<unprintable " + prettifyClassName(value.getClass()) + ">";
        }
    }

    private static String rawFallbackString(Object value) {
        if (value == null) return null;
        try {
            String raw = rewriteKnownClassAliases(shorten(String.valueOf(value)));
            if (raw == null || raw.isBlank()) return null;
            if (looksLikeDefaultObjectString(raw)) return null;
            return raw;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean looksLikeDefaultObjectString(String raw) {
        return raw != null && raw.matches("[\\w.$]+@[0-9a-fA-F]+");
    }

    private static String formatHotbarSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            return (slot + 1) + " (hotbar index " + slot + ")";
        }
        return String.valueOf(slot);
    }

    private static String describePlayerAction(ServerboundPlayerActionPacket.Action action) {
        return switch (action) {
            case START_DESTROY_BLOCK -> "Start breaking targeted block";
            case ABORT_DESTROY_BLOCK -> "Abort breaking targeted block";
            case STOP_DESTROY_BLOCK -> "Finish breaking targeted block";
            case DROP_ALL_ITEMS -> "Drop full held stack";
            case DROP_ITEM -> "Drop one held item";
            case RELEASE_USE_ITEM -> "Release item use";
            case SWAP_ITEM_WITH_OFFHAND -> "Swap main hand and offhand";
            default -> prettifyClassName(action.name());
        };
    }

    private static String describeContainerInput(Object actionType) {
        if (!(actionType instanceof Enum<?> enumValue)) {
            return actionType == null ? "Click Slot" : "Click Slot (" + safeLeafString(actionType) + ")";
        }
        return switch (enumValue.name()) {
            case "PICKUP" -> "Pick up / place stack";
            case "QUICK_MOVE" -> "Quick move / shift-click";
            case "SWAP" -> "Swap with hotbar slot";
            case "CLONE" -> "Clone stack";
            case "THROW" -> "Throw item";
            case "QUICK_CRAFT" -> "Drag split across slots";
            case "PICKUP_ALL" -> "Pick up all matching items";
            default -> "Click Slot (" + enumValue.name() + ")";
        };
    }

    private static String extractTextValue(Object value) {
        if (value == null || value == INACCESSIBLE) return null;
        if (value instanceof Component text) return text.getString();
        if (value instanceof String string) return string;
        if (value instanceof Optional<?> optional) return optional.map(AutismPacketInspector::extractTextValue).orElse(null);
        if (value instanceof String[] array) return String.join(" | ", array);
        Object stringLike = invokeFirstNoArg(value, "getString", "string", "content", "raw", "name");
        if (stringLike instanceof Component text) return text.getString();
        if (stringLike instanceof String string) return string;
        return null;
    }

    private static String summarizeBookPages(Collection<?> pages) {
        List<String> previews = new ArrayList<>();
        int index = 0;
        for (Object page : pages) {
            String text = extractTextValue(page);
            if ((text == null || text.isBlank()) && page != null) {
                text = safeLeafString(page);
            }
            if (text != null && !text.isBlank()) {
                previews.add("p" + (index + 1) + "=" + quote(shorten(text)));
            }
            index++;
            if (previews.size() >= 2) break;
        }
        if (previews.isEmpty()) return null;
        return String.join(" | ", previews);
    }

    private static String summarizeSignLines(String[] lines) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) continue;
            parts.add("L" + (i + 1) + "=" + quote(shorten(line)));
        }
        return parts.isEmpty() ? "<empty sign>" : String.join(" | ", parts);
    }

    private static ChatSummaryState appendChatSummary(Object messageValue, Object parameters, UUID senderUuid, Object timestampValue,
                                                      Object filterMask, String kind, InspectionBuilder builder) {
        boolean wrote = false;
        boolean informative = false;
        if (kind != null && !kind.isBlank()) {
            builder.line("Kind: " + kind, semanticFieldColor("Kind", kind));
            wrote = true;
        }

        String chatType = extractMessageType(parameters);
        if (chatType != null && !chatType.isBlank()) {
            builder.line("Chat Type: " + chatType, semanticFieldColor("Chat Type", chatType));
            wrote = true;
            informative = true;
        }

        String sender = extractMessageSender(parameters, senderUuid);
        if (sender != null && !sender.isBlank()) {
            builder.line("Sender: " + sender, semanticFieldColor("Sender", sender));
            wrote = true;
            informative = true;
        }

        String target = extractMessageTarget(parameters);
        if (target != null && !target.isBlank()) {
            builder.line("Target: " + target, semanticFieldColor("Target", target));
            wrote = true;
            informative = true;
        }

        String message = extractTextValue(messageValue);
        if (message != null && !message.isBlank()) {
            builder.line("Message: " + quote(shorten(message)), semanticFieldColor("Message", message));
            wrote = true;
            informative = true;
        } else if (messageValue != null) {
            builder.line("Message: " + summarizeValue(messageValue), colorForSummary("Message", messageValue));
            wrote = true;
            informative = true;
        }

        if (timestampValue != null) {
            builder.line("Timestamp: " + safeLeafString(timestampValue), semanticFieldColor("Timestamp", timestampValue));
            wrote = true;
            informative = true;
        }

        String filter = describeFilterMask(filterMask);
        if (filter != null) {
            builder.line("Filter: " + filter, semanticFieldColor("Filter", filter));
            wrote = true;
            informative = true;
        }
        return new ChatSummaryState(wrote, informative);
    }

    private static String extractMessageSender(Object parameters, UUID senderUuid) {
        String senderName = extractTextValue(firstNonNull(
            invokeNoArg(parameters, "name"),
            getRecordComponentValue(parameters, 1),
            findRecordTextComponent(parameters, getRecordComponentValue(parameters, 0), getRecordComponentValue(parameters, 2))
        ));
        if (senderName == null || senderName.isBlank()) {
            senderName = resolvePlayerName(senderUuid);
        }
        if (senderName != null && !senderName.isBlank()) {
            return senderUuid == null ? shorten(senderName) : shorten(senderName) + " (" + senderUuid + ")";
        }
        return senderUuid == null ? null : senderUuid.toString();
    }

    private static String extractMessageTarget(Object parameters) {
        String targetName = extractTextValue(firstNonNull(
            invokeNoArg(parameters, "targetName"),
            getRecordComponentValue(parameters, 2)
        ));
        return (targetName == null || targetName.isBlank()) ? null : shorten(targetName);
    }

    private static String extractMessageType(Object parameters) {
        Object type = firstNonNull(
            invokeNoArg(parameters, "type"),
            getRecordComponentValue(parameters, 0)
        );
        return type == null ? null : safeLeafString(type);
    }

    private static String describeFilterMask(Object filterMask) {
        if (filterMask == null) return null;
        Object fullyFiltered = invokeNoArg(filterMask, "isFullyFiltered");
        if (Boolean.TRUE.equals(fullyFiltered)) return "Fully filtered";
        Object passThrough = invokeNoArg(filterMask, "isPassThrough");
        if (Boolean.TRUE.equals(passThrough)) return "Pass-through";
        return "Partially filtered";
    }

    private static String resolvePlayerName(UUID uuid) {
        if (uuid == null) return null;
        try {
            if (MC.getConnection() != null) {
                var entry = MC.getConnection().getPlayerInfo(uuid);
                if (entry != null && entry.getProfile() != null) {
                    String profileName = extractTextValue(entry.getProfile());
                    if (profileName != null && !profileName.isBlank()) {
                        return profileName;
                    }
                }
            }
        } catch (Throwable ignored) {  }
        try {
            if (MC.level != null) {
            for (var player : MC.level.players()) {
                    if (uuid.equals(player.getUUID()) && player.getGameProfile() != null) {
                        String profileName = extractTextValue(player.getGameProfile());
                        if (profileName != null && !profileName.isBlank()) {
                            return profileName;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static ParsedChatLine parseChatLine(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty()) return null;

        if (text.startsWith("<")) {
            int end = text.indexOf("> ");
            if (end > 1 && end + 2 < text.length()) {
                return new ParsedChatLine(text.substring(1, end).trim(), text.substring(end + 2).trim());
            }
        }

        int colon = text.indexOf(": ");
        if (colon > 0 && colon <= 48 && colon + 2 < text.length()) {
            String sender = text.substring(0, colon).trim();
            String message = text.substring(colon + 2).trim();
            if (!sender.isEmpty() && !message.isEmpty()) {
                return new ParsedChatLine(sender, message);
            }
        }
        return null;
    }

    private static String formatEntity(Entity entity) {
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        String name = entity.getDisplayName() == null ? "" : entity.getDisplayName().getString();
        return typeId + " #" + entity.getId() + " [" + shorten(name) + "] @" + formatVec3(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
    }

    private static void appendEntityIdLine(InspectionBuilder builder, String label, int entityId) {
        builder.line(label + ": " + formatEntityId(entityId), AutismColors.packetCyan());
    }

    private static String formatEntityId(int entityId) {
        Entity entity = resolveEntity(entityId);
        if (entity != null) {
            return entityId + " -> " + formatEntity(entity);
        }
        return entityId + " (entity not loaded in client world)";
    }

    private static String formatEntityIdList(Object ids) {
        if (ids == null || ids == INACCESSIBLE) return "null";
        List<String> parts = new ArrayList<>();
        int total = 0;
        if (ids instanceof int[] array) {
            total = array.length;
            for (int i = 0; i < Math.min(array.length, 8); i++) parts.add(formatEntityId(array[i]));
        } else if (ids instanceof Collection<?> collection) {
            total = collection.size();
            int shown = 0;
            for (Object id : collection) {
                if (shown >= 8) break;
                if (id instanceof Number number) {
                    parts.add(formatEntityId(number.intValue()));
                } else {
                    parts.add(summarizeValue(id));
                }
                shown++;
            }
        } else if (ids.getClass().isArray()) {
            int length = Array.getLength(ids);
            total = length;
            for (int i = 0; i < Math.min(length, 8); i++) {
                Object id = Array.get(ids, i);
                parts.add(id instanceof Number number ? formatEntityId(number.intValue()) : summarizeValue(id));
            }
        } else {
            return summarizeValue(ids);
        }
        if (total > parts.size()) parts.add("... +" + (total - parts.size()) + " more");
        return "[" + total + "] " + String.join(", ", parts);
    }

    private static String formatCollectionPreview(Collection<?> collection) {
        if (collection == null) return "null";
        List<String> parts = new ArrayList<>();
        int shown = 0;
        for (Object item : collection) {
            if (shown >= 6) break;
            parts.add(summarizeValue(item));
            shown++;
        }
        if (collection.size() > shown) parts.add("... +" + (collection.size() - shown) + " more");
        return "[" + collection.size() + "] " + String.join(", ", parts);
    }

    private static Entity resolveEntity(int entityId) {
        try {
            return MC.level == null ? null : MC.level.getEntity(entityId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isEntityIdLabel(String label) {
        String normalized = normalizeName(label);
        return normalized.equals("entityid")
            || normalized.equals("entity")
            || normalized.equals("entityindex")
            || normalized.equals("targetentityid")
            || normalized.equals("sourceentityid")
            || normalized.endsWith("entityid");
    }

    private static String formatEntityType(EntityType<?> type) {
        if (type == null) return "Unknown";
        try {
            return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        } catch (Throwable ignored) {
            return safeLeafString(type);
        }
    }

    private static String formatItemStack(ItemStack stack) {
        if (stack.isEmpty()) return "ItemStack.EMPTY";
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String name = stack.getHoverName() == null ? "" : stack.getHoverName().getString();
        String display = itemId;
        if (!name.isEmpty() && !Objects.equals(name, stack.getItem().getName(stack).getString())) {
            display += " [" + shorten(name) + "]";
        }
        return stack.getCount() + "x " + display;
    }

    private static String formatBlockState(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " " + shorten(state.toString());
    }

    private static String formatBlockPos(BlockPos pos) {
        return "x=" + pos.getX() + ", y=" + pos.getY() + ", z=" + pos.getZ();
    }

    private static String formatVec3(Vec3 vec) {
        return String.format(Locale.ROOT, "x=%.3f, y=%.3f, z=%.3f", vec.x, vec.y, vec.z);
    }

    private static String formatBitSet(BitSet bitSet) {
        int shown = 0;
        StringBuilder builder = new StringBuilder("BitSet{");
        for (int bit = bitSet.nextSetBit(0); bit >= 0; bit = bitSet.nextSetBit(bit + 1)) {
            if (shown >= 16) {
                builder.append("...");
                break;
            }
            if (shown > 0) builder.append(", ");
            builder.append(bit);
            shown++;
        }
        builder.append("}");
        return builder.toString();
    }

    private static String formatHolder(Holder<?> entry) {
        try {
            Optional<?> key = entry.unwrapKey();
            if (key.isPresent()) {
                return shorten(String.valueOf(key.get()));
            }
        } catch (Throwable ignored) {  }
        try {
            Object value = invokeNoArg(entry, "value");
            if (value != null) {
                return shorten(String.valueOf(value));
            }
        } catch (Throwable ignored) {  }
        return shorten(String.valueOf(entry));
    }

    private static String summarizeValue(Object value) {
        return summarizeValue("", value);
    }

    private static String summarizeValue(String label, Object value) {
        if (value == null || value == INACCESSIBLE) return null;
        if (isSimpleValue(value)) return formatSimpleValue(label, value);
        if (value instanceof Optional<?> optional) {
            return optional.map(item -> summarizeValue(label, item)).orElse("Optional.empty");
        }
        if (value instanceof Collection<?> collection) {
            return prettifyClassName(value.getClass()) + "[" + collection.size() + "]";
        }
        if (value instanceof Map<?, ?> map) {
            return "Map[" + map.size() + "]";
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            return type.getComponentType().getSimpleName() + "[" + Array.getLength(value) + "]";
        }
        return safeLeafString(value);
    }

    private static int colorForSummary(String label, Object value) {
        if (value == null || value == INACCESSIBLE) return AutismColors.textMuted();
        if (value instanceof Collection<?> || value instanceof Map<?, ?> || value.getClass().isArray()) {
            return AutismColors.textSecondary();
        }
        return semanticFieldColor(label, value);
    }

    private static int colorForSummary(Object value) {
        return colorForSummary("", value);
    }

    private static int semanticFieldColor(String label, Object value) {
        String normalized = normalizeName(label);
        if (value instanceof Boolean bool) {
            return bool ? AutismColors.packetGreen() : AutismColors.packetPink();
        }
        if (normalized.contains("sender") || normalized.contains("author") || normalized.contains("player")
            || normalized.equals("entity") || normalized.contains("entityid") || normalized.contains("owner")) {
            return AutismColors.packetCyan();
        }
        if (normalized.contains("target") || normalized.contains("recipient") || normalized.contains("team")) {
            return AutismColors.packetPink();
        }
        if (normalized.contains("message") || normalized.contains("content") || normalized.equals("text")
            || normalized.contains("title") || normalized.contains("book") || normalized.contains("page")
            || normalized.contains("sign") || normalized.contains("recipe") || normalized.contains("item")
            || normalized.contains("stack")) {
            return AutismColors.packetGreen();
        }
        if (normalized.equals("kind") || normalized.contains("action") || normalized.contains("interaction")
            || normalized.equals("type") || normalized.contains("mode") || normalized.contains("reason")
            || normalized.contains("status") || normalized.contains("hand")) {
            return AutismColors.packetOrange();
        }
        if (normalized.contains("timestamp") || normalized.contains("time") || normalized.contains("sequence")
            || normalized.contains("salt")) {
            return AutismColors.packetLightYellow();
        }
        if (normalized.contains("slot") || normalized.contains("syncid") || normalized.contains("index")
            || normalized.contains("revision") || normalized.contains("checksum") || normalized.contains("offset")) {
            return AutismColors.packetBlue();
        }
        if (normalized.contains("sound") || normalized.contains("channel") || normalized.contains("identifier")
            || normalized.contains("packettype")) {
            return AutismColors.packetBlue();
        }
        if (normalized.contains("block") || normalized.contains("pos") || normalized.equals("x")
            || normalized.equals("y") || normalized.equals("z") || normalized.contains("yaw")
            || normalized.contains("pitch") || normalized.contains("velocity")) {
            return AutismColors.packetYellow();
        }
        if (normalized.contains("filter")) {
            String text = value == null ? "" : String.valueOf(value).toLowerCase(Locale.ROOT);
            if (text.contains("fully filtered")) return AutismColors.packetPink();
            if (text.contains("pass-through")) return AutismColors.packetGreen();
            return AutismColors.packetYellow();
        }
        return colorForValue(value);
    }

    private static String prettifyLabel(String label) {
        return prettifyClassName(label)
            .replace('.', ' ')
            .trim();
    }

    private static String decapitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String normalizeName(String value) {
        if (value == null) return "";
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isObfuscatedComponentLabel(String label) {
        if (label == null || label.isBlank()) return false;
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("(comp|field|method|class)_?\\d+");
    }

    private static boolean isIgnoredPropertyLabel(String label) {
        if (label == null || label.isBlank()) return false;
        return IGNORED_PROPERTY_METHODS.contains(label) || IGNORED_PROPERTY_NORMALIZED.contains(normalizeName(label));
    }

    private static int countNonEmpty(List<ItemStack> stacks) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) count++;
        }
        return count;
    }

    private static InteractionCapture captureInteraction(ServerboundInteractPacket packet) {
        InteractionCapture capture = new InteractionCapture();
        try {
            capture.kind = "Interact At";
            capture.hand = packet.hand();
            capture.hitPos = packet.location();
        } catch (Throwable ignored) {
            Object interactionType = getField(packet, "type");
            if (interactionType != null && interactionType != INACCESSIBLE) {
                capture.kind = prettifyClassName(interactionType.getClass());
                Object hand = firstNonNullField(interactionType, "hand", "interactionHand");
                if (hand instanceof InteractionHand actualHand) {
                    capture.hand = actualHand;
                }
                Object hitPos = firstNonNullField(interactionType, "pos", "hitPos", "location");
                if (hitPos instanceof Vec3 actualHitPos) {
                    capture.hitPos = actualHitPos;
                }
            }
        }
        return capture;
    }

    private static String formatPrimitiveArray(Object array) {
        int length = Array.getLength(array);
        StringBuilder builder = new StringBuilder();
        builder.append(array.getClass().getComponentType().getSimpleName()).append('[').append(length).append("] ");
        builder.append('[');
        int shown = Math.min(length, MAX_ARRAY_ITEMS);
        for (int i = 0; i < shown; i++) {
            if (i > 0) builder.append(", ");
            builder.append(Array.get(array, i));
        }
        if (length > shown) builder.append(", ...");
        builder.append(']');
        return builder.toString();
    }

    private static String prettifyClassName(Class<?> type) {
        if (type == null) return "Unknown";
        String alias = AutismYarnMappings.lookupClassAlias(type);
        if (alias != null && !alias.isBlank()) {
            return prettifyClassName(alias);
        }
        return prettifyClassName(type.getSimpleName().isEmpty() ? type.getName() : type.getSimpleName());
    }

    private static String prettifyClassName(String value) {
        if (value == null || value.isEmpty()) return "";
        String alias = firstNonBlank(
            AutismYarnMappings.lookupClassAlias(value.replace('/', '.')),
            AutismYarnMappings.lookupSimpleClassAlias(value.substring(value.lastIndexOf('.') + 1))
        );
        String source = (alias == null ? value : alias).replace('$', '.');
        if (source.endsWith("Packet")) {
            source = source.substring(0, source.length() - 6);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (i > 0 && Character.isUpperCase(ch) && Character.isLowerCase(source.charAt(i - 1))) {
                builder.append(' ');
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    private static String normalizeAccessorLabel(String label, Class<?> returnType) {
        if (label == null || label.isBlank()) return label;
        if (label.startsWith("get") && label.length() > 3 && Character.isUpperCase(label.charAt(3))) {
            return label.substring(3);
        }
        if (label.startsWith("is") && label.length() > 2
            && Character.isUpperCase(label.charAt(2))
            && (returnType == boolean.class || returnType == Boolean.class)) {
            return label.substring(2);
        }
        return label;
    }

    private static String summarizeEquipmentEntries(Collection<?> equipmentEntries) {
        if (equipmentEntries == null || equipmentEntries.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        int shown = 0;
        for (Object entry : equipmentEntries) {
            if (shown >= 6) {
                parts.add("... +" + (equipmentEntries.size() - shown) + " more");
                break;
            }
            String summary = summarizeEquipmentEntry(entry);
            if (summary != null && !summary.isBlank()) {
                parts.add(summary);
                shown++;
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String summarizeEquipmentEntry(Object entry) {
        if (entry == null) return null;
        Object slot = firstNonNull(
            invokeFirstNoArg(entry, "getFirst", "first", "slot", "getSlot"),
            getRecordComponentValue(entry, 0),
            getField(entry, "first")
        );
        Object stack = firstNonNull(
            invokeFirstNoArg(entry, "getSecond", "second", "stack", "getStack"),
            getRecordComponentValue(entry, 1),
            getField(entry, "second")
        );
        if (slot == null && stack == null) {
            return summarizeValue(entry);
        }
        String slotComponent = slot == null ? "Unknown Slot" : safeLeafString(slot);
        String stackComponent = stack instanceof ItemStack itemStack ? formatItemStack(itemStack) : summarizeValue(stack);
        return slotComponent + "=" + stackComponent;
    }

    private static String rewriteKnownClassAliases(String raw) {
        if (raw == null || raw.isBlank()) return raw;

        String rewritten = replaceAliases(raw, QUALIFIED_CLASS_ALIAS_PATTERN, true);
        rewritten = replaceAliases(rewritten, SIMPLE_CLASS_ALIAS_PATTERN, false);
        return rewritten;
    }

    private static String replaceAliases(String raw, Pattern pattern, boolean qualified) {
        Matcher matcher = pattern.matcher(raw);
        StringBuffer buffer = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String original = matcher.group(1);
            String alias = qualified
                ? AutismYarnMappings.lookupClassAlias(original)
                : AutismYarnMappings.lookupSimpleClassAlias(original);
            if (alias == null || alias.isBlank()) continue;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(alias));
            changed = true;
        }
        if (!changed) return raw;
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private static String shorten(String value) {
        if (value == null) return "";
        String sanitized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (sanitized.length() <= MAX_STRING_LENGTH) return sanitized;
        return sanitized.substring(0, MAX_STRING_LENGTH - 3) + "...";
    }

    private static BlockState getWorldBlockState(BlockPos pos) {
        try {
            return MC.level != null ? MC.level.getBlockState(pos) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final class PacketInspection {
        private final String title;
        private final List<InspectionLine> lines;
        private final String copyText;

        private PacketInspection(String title, List<InspectionLine> lines, String copyText) {
            this.title = title;
            this.lines = Collections.unmodifiableList(lines);
            this.copyText = copyText;
        }

        public String getTitle() {
            return title;
        }

        public List<InspectionLine> getLines() {
            return lines;
        }

        public String getCopyText() {
            return copyText;
        }
    }

    public static final class InspectionLine {
        private final String text;
        private final int color;

        public InspectionLine(String text, int color) {
            this.text = text;
            this.color = color;
        }

        public String getText() {
            return text;
        }

        public int getColor() {
            return color;
        }
    }

    public record PayloadAnalysisView(boolean done, boolean failed, double progress, String status, List<InspectionLine> lines) {
        public PayloadAnalysisView {
            progress = Math.max(0.0D, Math.min(1.0D, progress));
            status = status == null ? "" : status;
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    private record PropertyValue(String label, Object value, PropertySource source) {
    }

    private record ParsedChatLine(String sender, String message) {
    }

    private record SummaryResult(boolean wroteAny, boolean complete) {
        private static final SummaryResult NONE = new SummaryResult(false, false);
    }

    private record ChatSummaryState(boolean wroteAny, boolean complete) {
    }

    private record VarIntRead(int value, int nextIndex) {
    }

    private record ByteBufStringField(int offset, int length, String value) {
    }

    private record DecodeOutcome(boolean anything, boolean strong, boolean textual, boolean complete) {
        private static final DecodeOutcome NONE = new DecodeOutcome(false, false, false, false);
        private static final DecodeOutcome COMPLETE_TEXT = new DecodeOutcome(true, true, true, true);
    }

    private enum CompressionKind {
        NONE("none"),
        GZIP("gzip"),
        ZLIB("zlib/deflate");

        private final String label;

        CompressionKind(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private static final class PayloadByteReader {
        private final byte[] bytes;
        private int index;

        private PayloadByteReader(byte[] bytes) {
            this.bytes = bytes == null ? new byte[0] : bytes;
        }

        int position() {
            return index;
        }

        void seek(int position) {
            index = Math.max(0, Math.min(bytes.length, position));
        }

        int remaining() {
            return Math.max(0, bytes.length - index);
        }

        byte[] remainingBytes() {
            if (remaining() <= 0) return new byte[0];
            return Arrays.copyOfRange(bytes, index, bytes.length);
        }

        String readJavaUtf() throws IOException {
            if (remaining() < 2) throw new IOException("expected Java UTF length");
            int length = ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
            if (length < 0 || index + 2 + length > bytes.length) {
                throw new IOException("Java UTF length " + length + " exceeds remaining " + remaining());
            }
            int start = index;
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, start, length + 2))) {
                String value = in.readUTF();
                index = start + 2 + length;
                return value;
            }
        }

        byte[] readShortBytes() throws IOException {
            if (remaining() < 2) throw new IOException("expected unsigned short byte length");
            int length = ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
            index += 2;
            if (length < 0 || index + length > bytes.length) {
                throw new IOException("byte array length " + length + " exceeds remaining " + remaining());
            }
            byte[] out = Arrays.copyOfRange(bytes, index, index + length);
            index += length;
            return out;
        }
    }

    private enum PropertySource {
        RECORD,
        METHOD
    }

    private static final class InteractionCapture {
        private String kind;
        private InteractionHand hand;
        private Vec3 hitPos;
    }

    private static final class InspectionBuilder {
        private final String title;
        private final List<InspectionLine> lines = new ArrayList<>();
        private final StringBuilder plain = new StringBuilder();
        private boolean truncated;

        private InspectionBuilder(String title) {
            this.title = title;
        }

        void section(String title, int color) {
            line("[" + title + "]", color);
        }

        void line(String text, int color) {
            if (truncated) return;
            if (lines.size() >= MAX_LINES) {
                truncated = true;
                lines.add(new InspectionLine("... output truncated ...", AutismColors.dangerText()));
                plain.append("... output truncated ...\n");
                return;
            }
            lines.add(new InspectionLine(text, color));
            plain.append(text).append('\n');
        }

        void blank() {
            line("", AutismColors.textPrimary());
        }

        void appendFrom(InspectionBuilder other) {
            if (other == null) return;
            for (InspectionLine line : other.lines) {
                this.line(line.getText(), line.getColor());
            }
        }

        boolean isFull() {
            return truncated;
        }

        PacketInspection build() {
            return new PacketInspection(title, lines, plain.toString());
        }
    }
}
