package autismclient.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import autismclient.mixin.accessor.AutismClientConnectionAccessor;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.ScaffoldModule;
import autismclient.security.AutismProtectorPackStrip;
import autismclient.security.AutismResourcePackTruthGuard;
import autismclient.security.AutismSpoofPayloadFilter;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.macro.PacketGateManager;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismContainerHold;
import autismclient.util.AutismContainerTarget;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismServerRotationView;
import autismclient.AutismClientAddon;
import autismclient.util.multi.MultiConnectionContext;
import autismclient.util.multi.MultiConnectionMarker;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.client.Minecraft;

@Mixin(Connection.class)
public abstract class AutismClientConnectionMixin implements MultiConnectionMarker {
    @Unique
    private static final boolean AUTISM_PACKET_TRACE = Boolean.getBoolean("autism.packet.trace");

    @Unique
    private volatile boolean autism$spoofPipelineInstalled;

    @Unique
    private volatile boolean autism$multiManaged;

    @Unique
    private volatile MultiConnectionContext.ProxySpec autism$multiProxy;

    @Unique
    private volatile PacketListener autism$protocolHintListener;

    @Unique
    private volatile String autism$protocolHintCache = "";

    @Unique
    private static final String AUTISM_SPOOF_FILTER = "autism_spoof_filter";

    @Unique private static volatile autismclient.modules.Module autism$noFallCached;
    @Unique private static volatile int autism$noFallRevision = -1;

    @Unique
    private static autismclient.modules.Module autism$noFallModule() {
        int revision = autismclient.modules.ModuleRegistry.revision();
        if (revision != autism$noFallRevision) {
            autism$noFallCached = autismclient.modules.ModuleRegistry.get("no-fall");
            autism$noFallRevision = revision;
        }
        return autism$noFallCached;
    }

    @Unique
    private boolean autism$isMultiConnectionOrChannel() {

        return autism$multiManaged;
    }

    @Override
    public boolean autism$isMultiManaged() {
        return autism$multiManaged;
    }

    @Override
    public MultiConnectionContext.ProxySpec autism$multiProxy() {
        return autism$multiProxy;
    }

    @Override
    public void autism$setMultiManaged(MultiConnectionContext.ProxySpec proxy) {
        autism$multiProxy = proxy;
        autism$multiManaged = true;
    }

    @Override
    public void autism$clearMultiManaged() {
        autism$multiManaged = false;
        autism$multiProxy = null;
    }

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void autism$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
        autism$spoofPipelineInstalled = false;
        MultiConnectionContext.bindChannel((Connection) (Object) this, context.channel());
        if (autism$isMultiConnectionOrChannel()) return;

        autism$ensureSpoofPipelineFilter();
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void autism$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
        autism$spoofPipelineInstalled = false;
        autismclient.util.AutismNetworkCaptureState.clearCodecSuppression();
        if (autism$isMultiConnectionOrChannel()) {
            MultiConnectionContext.unbindChannel(context.channel());

            return;
        }

        if (!(((Connection) (Object) this).getPacketListener() instanceof ClientGamePacketListener)) {
            return;
        }
        AutismServerRotationView.reset();
        ScaffoldModule.onConnectionClosed();
        AutismProtectorPackStrip.clearAll();
        AutismResourcePackTruthGuard.clearAll();
    }

    @Inject(method = "doSendPacket", at = @At("HEAD"))
    private void autism$recordWrittenServerRotation(
        Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci
    ) {
        if (!autism$isMultiConnectionOrChannel()) {
            AutismServerRotationView.onPacketWritten(packet);
            ScaffoldModule.onFinalPacketWritten(packet);
        }
    }

    @Inject(method = "configurePacketHandler", at = @At("TAIL"))
    private void autism$onConfigurePacketHandler(ChannelPipeline pipeline, CallbackInfo ci) {
        if (autism$isMultiConnectionOrChannel()) return;
        autism$ensureSpoofPipelineFilter();
    }

    @ModifyVariable(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0
    )
    private Packet<?> autism$silentUseItemRotation(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return packet;
        if (autism$isMultiConnectionOrChannel()
            || !(packet instanceof ServerboundUseItemPacket usePacket)) return packet;
        float yaw = usePacket.getYRot();
        float pitch = usePacket.getXRot();
        if (autismclient.util.AutismInputClicker.isFastExpUseInProgress()) {
            yaw = autismclient.modules.BuiltinModules.manualFastExpUseYaw(yaw);
            pitch = autismclient.modules.BuiltinModules.manualFastExpUsePitch(pitch);
        } else {
            autismclient.util.AutismRotationUtil.Rotation rotation =
                autismclient.util.AutismSilentAim.activeUseItemRotation(Minecraft.getInstance().player);
            if (rotation != null) {
                yaw = rotation.yaw();
                pitch = rotation.pitch();
            }
        }
        if (Float.compare(yaw, usePacket.getYRot()) == 0
            && Float.compare(pitch, usePacket.getXRot()) == 0) return packet;
        return new ServerboundUseItemPacket(
            usePacket.getHand(), usePacket.getSequence(), yaw, pitch);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void yang$onSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {

        if (autism$isMultiConnectionOrChannel()) return;

        ScaffoldModule.onPacketQueued(packet);
        autism$trackSentMessage(packet);
        autism$ensureSpoofPipelineFilter();
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
            if (AutismResourcePackTruthGuard.shouldCancelOutboundStatus(resourcePackPacket)) {
                ci.cancel();
                return;
            }
            AutismProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
        }
        PacketListener packetListener = ((Connection) (Object) this).getPacketListener();
        AutismModule module = AutismModule.get();
        AutismModule.PacketHookSnapshot hooks = module == null
            ? AutismModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(isPlayConnectionActive());
        boolean normalLoggerPath = hooks.normalPath();
        String protocolHint = hooks.packetLoggerCapturing() || hooks.pluginDiscoveryObservation()
            ? autism$protocolHint(packetListener)
            : "";
        if (packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket) {
            if (AutismSpoofPayloadFilter.shouldBlockForVanillaSpoof(module, packet)) {
                ci.cancel();
                return;
            }
            if (AutismSpoofPayloadFilter.shouldDropForProtector(packet)) {
                ci.cancel();
                return;
            }
        }
        if (PackHideState.isHardLocked()) return;

        if (autismclient.util.multi.PacketTeleportController.isControllerOwnedSend()) return;
        if (autismclient.util.multi.PacketTeleportController.shouldSuppressMainMovement(packet)) {
            ci.cancel();
            return;
        }

        if (autismclient.util.AutismClientWake.isActive()
            && packet instanceof ServerboundPlayerCommandPacket autism$cmd
            && autism$cmd.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SLEEPING) {
            ci.cancel();
            return;
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket) {
            autismclient.modules.Module autism$noFall = autism$noFallModule();
            if (autism$noFall != null && autism$noFall.isEnabled()) autism$noFall.onPacketSend(packet);
        }
        if (autism$isLocalClientPlayConnection()
            && (autismclient.util.macro.PingSpoofController.interceptOutbound(packet)
                || autismclient.modules.AutismBlinkManager.interceptOutbound(packet))) {
            ci.cancel();
            return;
        }
        boolean payloadLoggedEarly = false;
        if (module != null && hooks.packetLoggerCapturing()) {
            payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "C2S", protocolHint);
        }
        if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketSend(packet);
        }

        AutismSharedState shared = AutismSharedState.get();

        if (packet instanceof ServerboundUseItemOnPacket pibp) {
            if (shared.consumeBlockCaptureCallback(pibp.getHitResult().getBlockPos(), pibp.getHitResult().getDirection())) {
                ScaffoldModule.onPacketAbandoned(packet);
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ServerboundInteractPacket && shared.hasEntityCaptureCallback()) {
            shared.consumeEntityCaptureCallback(Minecraft.getInstance().crosshairPickEntity);
            ScaffoldModule.onPacketAbandoned(packet);
            ci.cancel();
            return;
        }

        if (packet instanceof ServerboundContainerClosePacket closeForHold) {
            if (shared.consumeSuppressNextContainerClosePacket()) {
                ci.cancel();
                return;
            }
            if (AutismContainerHold.isHeld(closeForHold.getContainerId())) {
                AutismContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                ci.cancel();
                return;
            }
            if (autism$shouldKeepXCarryOpen(shared, closeForHold)) {
                ci.cancel();
                return;
            }
        }

        if (!normalLoggerPath) return;

        if (packet instanceof ServerboundUseItemOnPacket pibp) {
            shared.setLastInteractedBlockPos(pibp.getHitResult().getBlockPos());
            shared.setLastContainerTarget(AutismContainerTarget.forBlockHit(pibp.getHitResult(), pibp.getHand()));
        }

        if (packet instanceof ServerboundInteractPacket entityPacket) {
            net.minecraft.world.entity.Entity targeted = Minecraft.getInstance().crosshairPickEntity;
            if (targeted != null && targeted != Minecraft.getInstance().player) {
                net.minecraft.world.InteractionHand capturedHand = entityPacket.hand();
                net.minecraft.world.phys.Vec3 capturedHitPos = entityPacket.location();
                shared.setLastContainerTarget(
                    capturedHitPos != null
                        ? AutismContainerTarget.forEntityAt(targeted, capturedHand, capturedHitPos)
                        : AutismContainerTarget.forEntity(targeted, capturedHand)
                );
            }
        }

        if (packet instanceof ServerboundSignUpdatePacket && shared.consumeSuppressNextSignUpdatePacket()) {
            ci.cancel();
            return;
        }

        if (packet instanceof ServerboundEditBookPacket && shared.consumeSuppressNextBookEditPacket()) {
            ci.cancel();
            return;
        }

        boolean forceBookOrSignPacket =
            packet instanceof ServerboundSignUpdatePacket && shared.consumeForceNextSignUpdatePacket()
                || packet instanceof ServerboundEditBookPacket && shared.consumeForceNextBookEditPacket();

        if (packet instanceof ServerboundSignUpdatePacket && !shared.shouldEditSigns()) {
            shared.setAllowSignEditing(true);
            if (!forceBookOrSignPacket) {
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ServerboundEditBookPacket && !shared.shouldUpdateBook()) {
            shared.setAllowBookUpdate(true);
            if (!forceBookOrSignPacket) {
                ci.cancel();
                return;
            }
        }

        if (shared.isGBreakCapturing()) {
            if (packet instanceof ServerboundPlayerActionPacket) {

                shared.onGBreakPacket(packet);
            }

            return;
        }

        if (packet instanceof ServerboundPlayerActionPacket actionPacket
            && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            if (ModuleRegistry.dispatchStartBreakingBlock(actionPacket.getPos(), actionPacket.getDirection())) {
                ci.cancel();
                return;
            }
        }

        if (module.handlePacketSend(packet, payloadLoggedEarly)) {
            ScaffoldModule.onPacketAbandoned(packet);
            ci.cancel();
            return;
        }

        if (forceBookOrSignPacket) return;

        if (!shared.isFlushing()) {
            PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "C2S");
            if (gateResult == PacketGateManager.Result.CANCEL) {
                ScaffoldModule.onPacketAbandoned(packet);
                ci.cancel();
                return;
            }
            if (gateResult == PacketGateManager.Result.DELAY) {
                shared.enqueuePacket(packet);
                ci.cancel();
                return;
            }
        }

        boolean anyFeatureActive = shared.shouldDelayGuiPackets()
            || !shared.shouldSendGuiPackets()
            || shared.shouldUseCustomPackets();
        if (!anyFeatureActive) return;

        if (shared.isFlushing()) return;

        if (autism$isTransactionSync(packet)) return;

        boolean shouldHandle = false;

        if (shared.shouldUseCustomPackets()) {

            shouldHandle = shared.getC2SPackets().contains(packet.getClass());
        } else {

            shouldHandle = isGuiPacket(packet);
        }

        if (!shouldHandle) return;

        if (AUTISM_PACKET_TRACE) {
            AutismClientAddon.LOG.debug("[Autism] Packet detected: {} | Send={} Delay={} | Custom={}",
                packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(),
                shared.shouldDelayGuiPackets(), shared.shouldUseCustomPackets());
        }

        if (!shared.shouldSendGuiPackets()) {
            if (AUTISM_PACKET_TRACE) AutismClientAddon.LOG.debug("[Autism] CANCELLED packet (send disabled)");
            ScaffoldModule.onPacketAbandoned(packet);
            ci.cancel();
            return;
        }

        if (shared.shouldDelayGuiPackets()) {
            if (AUTISM_PACKET_TRACE) AutismClientAddon.LOG.debug("[Autism] QUEUED packet (delay enabled)");
            AutismModule captureModule = AutismModule.get();
            AutismSharedState.ReplayMode captureMode = (captureModule != null && captureModule.isCaptureAsExact())
                ? AutismSharedState.ReplayMode.EXACT
                : AutismSharedState.ReplayMode.REGENERATE;
            shared.enqueuePacket(packet, captureMode);
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("TAIL"))
    private void yang$afterSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (autism$isMultiConnectionOrChannel()) return;
        if (PackHideState.isHardLocked()) return;
        if (!isAutismActive()) return;
        if (!isPlayConnectionActive()) return;
        MacroExecutor.onPacketSent(packet);
    }

    @Unique
    private static boolean autism$isTransactionSync(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.common.ServerboundPongPacket
            || packet instanceof net.minecraft.network.protocol.common.ClientboundPingPacket
            || packet instanceof net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
            || packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
    }

    @Unique
    private void autism$trackSentMessage(Packet<?> packet) {
        if (packet instanceof ServerboundChatPacket chat) {
            AutismSharedState.get().setLastSentMessage(chat.message());
        } else if (packet instanceof ServerboundChatCommandPacket command) {
            AutismSharedState.get().setLastSentMessage("/" + command.command());
        } else if (packet instanceof ServerboundChatCommandSignedPacket signed) {
            AutismSharedState.get().setLastSentMessage("/" + signed.command());
        }
    }

    @Unique
    private boolean isGuiPacket(Packet<?> packet) {
        return packet instanceof ServerboundContainerClickPacket
            || packet instanceof ServerboundContainerButtonClickPacket
            || packet instanceof ServerboundSetCreativeModeSlotPacket
            || packet instanceof ServerboundPlayerActionPacket
            || packet instanceof ServerboundUseItemPacket
            || packet instanceof ServerboundSignUpdatePacket
            || packet instanceof ServerboundEditBookPacket
            || packet instanceof ServerboundChatPacket
            || packet instanceof ServerboundChatCommandPacket
            || packet instanceof ServerboundChatCommandSignedPacket
            || packet instanceof net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
    }

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void yang$onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {

        if (autism$isMultiConnectionOrChannel()) return;
        PacketListener listener = ((Connection) (Object) this).getPacketListener();
        AutismModule module = AutismModule.get();
        AutismModule.PacketHookSnapshot hooks = module == null
            ? AutismModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(isPlayReceiveListener(listener));
        boolean normalLoggerPath = hooks.normalPath();

        boolean vanillaDialogPacket = packet instanceof net.minecraft.network.protocol.common.ClientboundShowDialogPacket
            || packet instanceof net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
        boolean customMenuPacket = !vanillaDialogPacket
            && autismclient.api.custommenu.CustomMenuAdapterRegistry.acceptsInbound(packet);
        String protocolHint = hooks.packetLoggerCapturing() || hooks.pluginDiscoveryObservation() || customMenuPacket
            ? autism$protocolHint(listener)
            : "";
        if (PackHideState.isHardLocked()) {
            autism$clearRuntimeConnectionStateOnDisconnect(packet);
            return;
        }
        if (customMenuPacket) autismclient.util.custommenu.CustomMenuTracker.acceptInterested(packet, protocolHint);
        if (isPlayReceiveListener(listener)
            && (autismclient.util.macro.PingSpoofController.interceptInbound(packet)
                || autismclient.modules.AutismBlinkManager.interceptInbound(packet))) {
            ci.cancel();
            return;
        }
        boolean payloadLoggedEarly = false;
        if (module != null && hooks.packetLoggerCapturing()) {
            payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "S2C", protocolHint);
        }
        if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketReceive(packet);
        }

        if (autism$isIncomingChatPacket(packet)) {
            MacroExecutor.observeIncomingChat(packet);

            autismclient.modules.AutoLoginModule.observeIncomingChat(packet);
        }

        autismclient.modules.ModuleEspChunkCache.onPacketReceived(packet);
        if (!normalLoggerPath) return;

        autismclient.util.macro.ServerTickTracker.onS2CPacket(packet);
        MacroExecutor.onPacketReceived(packet);

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket macroSuggestions
                && autismclient.util.AutismCommandSuggestionIds.isMacroId(macroSuggestions.id())) {
            ci.cancel();
            return;
        }

        if (packet instanceof ClientboundOpenScreenPacket openScreenPacket) {
            AutismContainerHold.onContainerOpened(openScreenPacket.getContainerId());
        }
        if (packet instanceof ClientboundDisconnectPacket) {
            autism$clearRuntimeConnectionStateOnDisconnect(packet);
        }

        if (module.handlePacketReceive(packet, payloadLoggedEarly)) {
            ci.cancel();
            return;
        }

        AutismSharedState shared = AutismSharedState.get();

        PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "S2C");
        if (gateResult == PacketGateManager.Result.CANCEL) {
            ci.cancel();
            return;
        }
        if (gateResult == PacketGateManager.Result.DELAY) {
            shared.enqueuePacket(packet);
            ci.cancel();
            return;
        }

        boolean anyFeatureActive = shared.shouldDelayGuiPackets()
            || !shared.shouldSendGuiPackets()
            || shared.shouldUseCustomPackets();
        if (!anyFeatureActive) return;

        if (autism$isTransactionSync(packet)) return;

        boolean shouldHandle = false;

        if (shared.shouldUseCustomPackets()) {

            shouldHandle = shared.getS2CPackets().contains(packet.getClass());
        }

        if (!shouldHandle) return;

        if (AUTISM_PACKET_TRACE) {
            AutismClientAddon.LOG.debug("[Autism] S2C Packet detected: {} | Send={} Delay={}",
                packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(),
                shared.shouldDelayGuiPackets());
        }

        if (!shared.shouldSendGuiPackets()) {
            ci.cancel();
            return;
        }

        if (shared.shouldDelayGuiPackets()) {
            shared.enqueuePacket(packet);
            ci.cancel();
        }
    }

    @Unique
    private boolean isAutismActive() {
        AutismModule module = AutismModule.get();
        return module != null && module.arePacketHooksActive();
    }

    @Unique
    private void autism$ensureSpoofPipelineFilter() {
        if (autism$spoofPipelineInstalled) return;
        Channel channel = null;
        try {
            channel = ((AutismClientConnectionAccessor) this).getChannel();
        } catch (Throwable ignored) {  }
        if (channel == null) return;

        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline == null || pipeline.get(AUTISM_SPOOF_FILTER) != null) {
                autism$spoofPipelineInstalled = true;
                return;
            }
            if (pipeline.get("encoder") != null) {
                pipeline.addAfter("encoder", AUTISM_SPOOF_FILTER, new AutismSpoofPayloadFilter());
                autism$spoofPipelineInstalled = true;
            }
        } catch (Throwable t) {
            AutismClientAddon.LOG.debug("[Autism] Failed to install client spoof payload filter", t);
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void autism$onSendPacketWithFlush(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (autism$isMultiConnectionOrChannel()) return;
        ScaffoldModule.onPacketQueued(packet);
        autism$trackSentMessage(packet);
        autism$ensureSpoofPipelineFilter();
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket
            && AutismResourcePackTruthGuard.shouldCancelOutboundStatus(resourcePackPacket)) {
            ci.cancel();
            return;
        }
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
            AutismProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
        }

        if (packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket) {
            if (AutismSpoofPayloadFilter.shouldBlockForVanillaSpoof(AutismModule.get(), packet)) {
                ci.cancel();
                return;
            }
            if (AutismSpoofPayloadFilter.shouldDropForProtector(packet)) {
                ci.cancel();
            }
            return;
        }
        if (PackHideState.isHardLocked()) return;
        AutismSharedState shared = AutismSharedState.get();
        if (packet instanceof ServerboundContainerClosePacket closeForHold) {
            if (shared.consumeSuppressNextContainerClosePacket()) {
                ci.cancel();
                return;
            }
            if (AutismContainerHold.isHeld(closeForHold.getContainerId())) {
                AutismContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                ci.cancel();
                return;
            }
            if (autism$shouldKeepXCarryOpen(shared, closeForHold)) {
                ci.cancel();
            }
        }
    }

    @Unique
    private void autism$clearRuntimeConnectionStateOnDisconnect(Packet<?> packet) {
        if (!(packet instanceof ClientboundDisconnectPacket)) return;
        AutismContainerHold.clearAll();
        PacketGateManager.clearAll();
        autismclient.util.macro.PingSpoofController.clearQueue();
        autismclient.modules.AutismBlinkManager.clear();

        AutismSharedState s = AutismSharedState.get();
        s.setXCarryForcedTargets(java.util.Collections.emptySet(), false);
        s.setXCarryForced(false);
        s.setXCarryActive(false);
    }

    @Unique
    private boolean isPlayConnectionActive() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getConnection() != null;
    }

    @Unique
    private static boolean isPlayReceiveListener(PacketListener listener) {
        return listener instanceof ClientGamePacketListener;
    }

    @Unique
    private boolean autism$isLocalClientPlayConnection() {
        return ((Connection) (Object) this).getPacketListener() instanceof ClientGamePacketListener;
    }

    @Unique
    private static boolean autism$isIncomingChatPacket(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket
            || packet instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket
            || packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
    }

    @Unique
    private String autism$protocolHint(PacketListener listener) {
        if (listener == null) return "";
        if (listener == autism$protocolHintListener) return autism$protocolHintCache;
        String hint;
        if (listener instanceof ClientGamePacketListener) {
            hint = "play";
        } else {
            String name = listener.getClass().getName();
            hint = name.toLowerCase(java.util.Locale.ROOT).contains("configuration")
                ? "configuration"
                : "";
        }
        autism$protocolHintCache = hint;
        autism$protocolHintListener = listener;
        return hint;
    }

    @Unique
    private boolean autism$shouldKeepXCarryOpen(AutismSharedState shared, ServerboundContainerClosePacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return false;
        AutismModule module = AutismModule.get();
        boolean allowPassiveXCarry = module != null && module.isXCarryEnabled();

        if (!allowPassiveXCarry && !shared.isXCarryForced()) return false;
        if (packet.getContainerId() != client.player.inventoryMenu.containerId) return false;

        java.util.Set<Integer> mask;
        boolean carryCursor;
        if (shared.isXCarryForced()) {
            mask = shared.getXCarryForcedSlotMask();
            carryCursor = shared.isXCarryForcedCarryCursor();
        } else {
            mask = module == null ? null : module.getXCarryModuleSlotMask();
            carryCursor = module == null || module.isXCarryCarryCursor();
        }
        boolean hasItems = autismclient.util.macro.XCarryAction.hasStoredItems(
                client.player.inventoryMenu, carryCursor, mask);
        shared.setXCarryActive(hasItems);

        return true;
    }
}
