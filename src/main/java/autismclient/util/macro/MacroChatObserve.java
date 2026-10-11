package autismclient.util.macro;

import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;

final class MacroChatObserve {
    private MacroChatObserve() {
    }
    public static void observeIncomingChat(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        MacroExecutor.ChatCapture capture = extractIncomingChat(packet);
        if (capture == null || capture.message() == null || capture.message().isBlank()) return;
        syncRecentChatServerContext();
        recordRecentChat(capture);
    }

    private static void recordRecentChat(MacroExecutor.ChatCapture capture) {
        MacroExecutor.RecentChatMessage message = new MacroExecutor.RecentChatMessage(
            capture.sender() == null ? "" : capture.sender(),
            capture.message(),
            capture.displayText(),
            capture.displayComponent(),
            capture.source(),
            System.currentTimeMillis()
        );
        synchronized (MacroExecutor.recentChatLock) {
            MacroExecutor.recentChatMessages.removeIf(existing -> existing.displayText().equals(message.displayText()));
            MacroExecutor.recentChatMessages.addFirst(message);
            while (MacroExecutor.recentChatMessages.size() > MacroExecutor.MAX_RECENT_CHAT_MESSAGES) {
                MacroExecutor.recentChatMessages.removeLast();
            }
        }
    }


    public static void syncRecentChatServerContext() {
        String currentServerKey = getCurrentMultiplayerServerKey();
        if (currentServerKey.isEmpty()) return;

        if (currentServerKey.equals(MacroExecutor.recentChatServerKeyMirror)) return;
        synchronized (MacroExecutor.recentChatLock) {
            if (MacroExecutor.recentChatServerKey.isEmpty()) {
                MacroExecutor.recentChatServerKey = currentServerKey;
                MacroExecutor.recentChatServerKeyMirror = currentServerKey;
                return;
            }
            if (!MacroExecutor.recentChatServerKey.equals(currentServerKey)) {
                MacroExecutor.recentChatMessages.clear();
                MacroExecutor.recentChatServerKey = currentServerKey;
                MacroExecutor.recentChatServerKeyMirror = currentServerKey;
                synchronized (MacroExecutor.recentGuiLock) {
                    MacroExecutor.recentGuiTitles.clear();
                }
            } else {
                MacroExecutor.recentChatServerKeyMirror = currentServerKey;
            }
        }
    }

    private static String getCurrentMultiplayerServerKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isLocalServer()) return "";

        ServerData entry = mc.getCurrentServer();
        if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return normalizeServerKey(entry.ip);
        }

        if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            SocketAddress address = mc.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                    host = inet.getAddress().getHostAddress();
                }
                if (host != null && !host.isBlank()) {
                    return normalizeServerKey(host + ":" + inet.getPort());
                }
            } else if (address != null) {
                String raw = address.toString();
                if (raw != null && !raw.isBlank()) {
                    return normalizeServerKey(raw);
                }
            }
        }

        return "";
    }

    private static String normalizeServerKey(String address) {
        if (address == null) return "";
        String stripped = address.startsWith("/") ? address.substring(1) : address;
        return stripped.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matchesWaitingChat(MacroExecutor.ChatCapture capture) {
        String patternJson = MacroExecutor.waitingChatPatternJson.get();
        if (!MacroExecutor.waitingChatIsRegex.get() && patternJson != null && !patternJson.isBlank() && capture.displayComponent() != null) {
            String captureJson = MacroChatText.serializeTextComponent(capture.displayComponent());
            if (patternJson.equals(captureJson)) return true;
        }
        String pattern = MacroChatText.normalizeManualText(MacroExecutor.waitingChatPattern.get());
        if (pattern == null || pattern.isBlank()) return true;
        if (MacroExecutor.waitingChatIsRegex.get()) {
            try {
                java.util.regex.Pattern compiled = MacroCapturePattern.compiled(pattern);
                return compiled.matcher(capture.message()).matches() || compiled.matcher(capture.displayText()).matches();
            } catch (Exception ignored) {
                return false;
            }
        }
        int threshold = MacroExecutor.waitingChatFuzzyPercent.get();
        return MacroChatText.fuzzyChatMatch(pattern, capture.message(), threshold)
            || MacroChatText.fuzzyChatMatch(pattern, capture.displayText(), threshold);
    }

    private static boolean matchesChatAction(WaitForChatAction action, MacroExecutor.ChatCapture capture) {
        return captureChatAction(action, capture, MacroLifecycle.currentVariableContext()).isPresent();
    }

    private static java.util.Optional<java.util.Map<String, MacroValue>> captureChatAction(WaitForChatAction action, MacroExecutor.ChatCapture capture) {
        return captureChatAction(action, capture, MacroLifecycle.currentVariableContext());
    }

    public static java.util.Optional<java.util.Map<String, MacroValue>> captureChatAction(WaitForChatAction action, MacroExecutor.ChatCapture capture,
                                                                                           MacroVariableContext context) {
        if (action == null || capture == null) return java.util.Optional.empty();
        MacroCapturePattern.Mode mode = action.effectiveMatchMode();
        if (mode == MacroCapturePattern.Mode.MATCH && action.patternJson != null && !action.patternJson.isBlank() && capture.displayComponent() != null) {
            String captureJson = MacroChatText.serializeTextComponent(capture.displayComponent());
            if (action.patternJson.equals(captureJson)) return java.util.Optional.of(chatValues(action, capture, java.util.Map.of()));
        }
        String pattern = MacroChatText.normalizeManualText(action.pattern);
        if (mode == MacroCapturePattern.Mode.MATCH) {
            MacroTemplate.Resolution patternResolution = MacroTemplate.resolve(pattern, context, Minecraft.getInstance());
            if (!patternResolution.success()) return java.util.Optional.empty();
            pattern = patternResolution.value();
        }
        if (pattern == null || pattern.isBlank()) return java.util.Optional.of(chatValues(action, capture, java.util.Map.of()));
        if (mode != MacroCapturePattern.Mode.MATCH) {
            var messageResult = MacroCapturePattern.match(mode, pattern, capture.message());
            var result = messageResult.isPresent() ? messageResult : MacroCapturePattern.match(mode, pattern, capture.displayText());
            return result.map(value -> chatValues(action, capture, value.values()));
        }
        int threshold = WaitForChatAction.clampFuzzyPercent(action.fuzzyPercent);
        boolean matched = MacroChatText.fuzzyChatMatch(pattern, capture.message(), threshold)
            || MacroChatText.fuzzyChatMatch(pattern, capture.displayText(), threshold);
        return matched ? java.util.Optional.of(chatValues(action, capture, java.util.Map.of())) : java.util.Optional.empty();
    }

    private static java.util.Map<String, MacroValue> chatValues(WaitForChatAction action, MacroExecutor.ChatCapture capture,
                                                                  java.util.Map<String, MacroValue> captures) {
        java.util.Map<String, MacroValue> values = new java.util.LinkedHashMap<>(captures);
        if (action.saveAs != null && !action.saveAs.isBlank()) {
            java.util.Map<String, MacroValue> properties = new java.util.LinkedHashMap<>();
            properties.put("sender", MacroValue.text(capture.sender()));
            properties.put("message", MacroValue.text(capture.message()));
            properties.put("display", MacroValue.text(capture.displayText()));
            properties.put("source", MacroValue.text(capture.source().name()));
            values.put(action.saveAs, MacroValue.structured(MacroValue.Kind.TEXT, capture.displayText(), properties));
        }
        return java.util.Map.copyOf(values);
    }

    public static MacroExecutor.ChatCapture extractIncomingChat(Packet<?> packet) {

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket systemChat) {
            Component content = systemChat.content();
            if (content == null) return null;
            String raw = content.getString();
            if (raw == null || raw.isBlank()) return null;
            return buildChatCapture("", raw, raw, content, MacroExecutor.ChatSource.PLAYER);
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket disguisedChat) {
            Component content = disguisedChat.message();
            String text = content == null ? null : content.getString();
            if (text == null || text.isBlank()) return null;
            Component decorated = disguisedChat.chatType().decorate(content);
            String sender = disguisedChat.chatType().name().getString();
            return buildChatCapture(sender, text, null, decorated, MacroExecutor.ChatSource.PLAYER);
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket playerChat) {

            Component content = playerChat.unsignedContent() != null
                ? playerChat.unsignedContent()
                : Component.literal(playerChat.body().content());
            String message = content.getString();
            if (message == null || message.isBlank()) message = playerChat.body().content();
            if (message == null || message.isBlank()) return null;

            Component decorated = playerChat.chatType().decorate(content);
            String sender = playerChat.chatType().name().getString();
            if (sender == null || sender.isBlank()) {
                String byUuid = MacroChatText.resolvePlayerName(playerChat.sender());
                if (byUuid != null && !byUuid.isBlank()) sender = byUuid;
            }
            return buildChatCapture(sender, message, null, decorated, MacroExecutor.ChatSource.PLAYER);
        }

        return null;
    }

    private static MacroExecutor.ChatCapture buildChatCapture(String sender, String message, String displayText, Component displayComponent, MacroExecutor.ChatSource source) {
        String cleanMessage = MacroChatText.sanitizeChatText(message);
        if (cleanMessage.isBlank()) return null;
        String cleanSender = MacroChatText.sanitizeChatText(sender);
        String cleanDisplay = MacroChatText.sanitizeChatText(displayText);
        Component rendered = MacroChatText.literalizeTextComponent(displayComponent);
        String display = cleanDisplay;
        if (rendered == null || rendered.getString().isBlank()) {
            rendered = cleanSender.isBlank()
                    ? Component.literal(cleanMessage)
                    : Component.literal("<" + cleanSender + "> ").append(Component.literal(cleanMessage));
        }
        if (display == null || display.isBlank()) {
            display = MacroChatText.sanitizeChatText(rendered.getString());
        }
        MacroExecutor.ChatSource resolvedSource = source != null ? source : MacroExecutor.ChatSource.PLAYER;
        return new MacroExecutor.ChatCapture(cleanSender, cleanMessage, MacroChatText.sanitizeChatText(display), rendered, resolvedSource);
    }
}
