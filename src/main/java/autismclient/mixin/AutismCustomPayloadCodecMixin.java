package autismclient.mixin;

import autismclient.modules.PackHideState;
import autismclient.util.AutismNetworkCaptureState;
import autismclient.util.AutismPayloadSupport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public abstract class AutismCustomPayloadCodecMixin {
    @Unique
    private static final ThreadLocal<CaptureCursor> AUTISM_CAPTURE_CURSOR =
        ThreadLocal.withInitial(CaptureCursor::new);

    @Inject(method = "encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void autism$encodeRawCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) {
            return;
        }
        long captureState = AutismNetworkCaptureState.codecState();
        boolean capturePayload = AutismNetworkCaptureState.capturesPayloads(captureState);
        if (capturePayload && buf != null) {
            CaptureCursor cursor = AUTISM_CAPTURE_CURSOR.get();
            cursor.encodeStartIndex = buf.writerIndex();
            cursor.encodeState = captureState;
        }
        boolean isRaw = payload instanceof AutismPayloadSupport.RawCustomPacketPayload;
        if (!isRaw && !capturePayload) return;

        byte[] rememberedBytes = AutismPayloadSupport.getRememberedUnknownPayloadBytes(payload);
        if (rememberedBytes == null && !isRaw) return;

        CustomPacketPayload.Type<?> type = payload.type();
        if (type == null || type.id() == null) return;

        byte[] bytes = rememberedBytes != null
            ? rememberedBytes
            : ((AutismPayloadSupport.RawCustomPacketPayload) payload).bytes();

        buf.writeIdentifier(type.id());
        if (bytes.length > 0) {
            buf.writeBytes(bytes);
        }
        ci.cancel();
    }

    @Inject(method = "encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("TAIL"))
    private void autism$captureEncodedCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
        long captureState = AutismNetworkCaptureState.codecState();
        if (!AutismNetworkCaptureState.capturesPayloads(captureState)) return;
        CaptureCursor cursor = AUTISM_CAPTURE_CURSOR.get();
        int startIndex = cursor.encodeState == captureState ? cursor.encodeStartIndex : -1;
        cursor.encodeStartIndex = -1;
        cursor.encodeState = 0L;
        if (payload == null || buf == null || startIndex < 0) return;
        int end = buf.writerIndex();
        if (end <= startIndex) return;
        byte[] encodedBytes = new byte[end - startIndex];
        buf.getBytes(startIndex, encodedBytes);
        AutismPayloadSupport.rememberDecodedPayloadBytes(payload, autism$payloadChannel(payload), encodedBytes);
    }

    @Inject(method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;",
            at = @At("HEAD"))
    private void autism$captureDecodeStart(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
        long captureState = AutismNetworkCaptureState.codecState();
        if (!AutismNetworkCaptureState.capturesPayloads(captureState) || buf == null) return;
        CaptureCursor cursor = AUTISM_CAPTURE_CURSOR.get();
        cursor.decodeStartIndex = buf.readerIndex();
        cursor.decodeState = captureState;
    }

    @Inject(method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;",
            at = @At("RETURN"))
    private void autism$captureDecodedCustomPacketPayload(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
        long captureState = AutismNetworkCaptureState.codecState();
        if (!AutismNetworkCaptureState.capturesPayloads(captureState)) return;
        CaptureCursor cursor = AUTISM_CAPTURE_CURSOR.get();
        int startIndex = cursor.decodeState == captureState ? cursor.decodeStartIndex : -1;
        cursor.decodeStartIndex = -1;
        cursor.decodeState = 0L;
        CustomPacketPayload payload = cir.getReturnValue();
        if (payload == null || buf == null || startIndex < 0) return;
        int end = buf.readerIndex();
        if (end <= startIndex) return;
        byte[] encodedBytes = new byte[end - startIndex];
        buf.getBytes(startIndex, encodedBytes);
        AutismPayloadSupport.rememberDecodedPayloadBytes(payload, autism$payloadChannel(payload), encodedBytes);
    }

    @Unique
    private static String autism$payloadChannel(CustomPacketPayload payload) {
        try {
            CustomPacketPayload.Type<?> type = payload == null ? null : payload.type();
            if (type != null && type.id() != null) return type.id().toString();
        } catch (Throwable ignored) {  }
        return "";
    }

    @Unique
    private static final class CaptureCursor {
        private int encodeStartIndex = -1;
        private int decodeStartIndex = -1;
        private long encodeState;
        private long decodeState;
    }
}
