package autismclient.util.multi;

import autismclient.AutismClientAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class MultiPovChat {
    private static final int VANILLA_HISTORY_LIMIT = 100;
    record HistoryLine(long receivedAt, Component component) {
    }

    private static volatile String activeAccountId;
    private static volatile long viewEpoch;
    private static ChatComponent activeChat;
    private static ChatComponent.State renderedClientState;
    private static final ThreadLocal<Boolean> ADDING_BOT_MESSAGE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> ROUTING_RENDERED_CLIENT = ThreadLocal.withInitial(() -> false);

    private MultiPovChat() {
    }

    static void enter(String accountId) {
        Minecraft mc = Minecraft.getInstance();
        if (accountId == null || mc == null || mc.gui == null || mc.gui.hud == null) return;
        ChatComponent chat = mc.gui.hud.getChat();
        if (chat == null) return;

        exit();
        activeChat = chat;
        renderedClientState = chat.storeState();
        activeAccountId = accountId;
        viewEpoch++;

        MultiManager manager = MultiManager.getIfInitialized();
        List<HistoryLine> history = manager == null ? List.of() : manager.povChatHistory(accountId);
        chat.restoreState(historyState(mc, history));
        chat.resetChatScroll();
    }

    static void exit() {
        ChatComponent chat = activeChat;
        ChatComponent.State restore = renderedClientState;
        activeAccountId = null;
        viewEpoch++;
        activeChat = null;
        renderedClientState = null;
        if (chat == null || restore == null) return;
        try {
            chat.restoreState(restore);
            chat.resetChatScroll();
        } catch (RuntimeException | Error error) {
            AutismClientAddon.LOG.warn("Could not restore rendered-client chat after POV", error);
        }
    }

    static void onBotChat(MultiSession session, Component message) {
        String accountId = activeAccountId;
        if (session == null || message == null || accountId == null || !accountId.equals(session.accountId())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        long expectedEpoch = viewEpoch;
        mc.execute(() -> {
            if (expectedEpoch != viewEpoch || !session.accountId().equals(activeAccountId)) return;
            ChatComponent chat = activeChat;
            if (chat == null || mc.gui == null || mc.gui.hud == null || mc.gui.hud.getChat() != chat) return;
            ADDING_BOT_MESSAGE.set(true);
            try {

                chat.addServerSystemMessage(message);
            } finally {
                ADDING_BOT_MESSAGE.remove();
            }
        });
    }

    public static ChatComponent.State beginRenderedClientMutation(ChatComponent chat) {
        if (chat == null || chat != activeChat || activeAccountId == null
            || ADDING_BOT_MESSAGE.get() || ROUTING_RENDERED_CLIENT.get()) return null;
        ChatComponent.State botState = chat.storeState();
        ChatComponent.State mainState = renderedClientState;
        if (mainState == null) return null;
        ROUTING_RENDERED_CLIENT.set(true);
        try {
            chat.restoreState(mainState);
        } catch (Throwable error) {
            ROUTING_RENDERED_CLIENT.remove();
            throw error;
        }
        return botState;
    }

    public static void endRenderedClientMutation(ChatComponent chat, ChatComponent.State botState) {
        if (chat == null || botState == null) return;
        try {
            renderedClientState = chat.storeState();
            chat.restoreState(botState);
        } finally {
            ROUTING_RENDERED_CLIENT.remove();
        }
    }

    private static ChatComponent.State historyState(Minecraft mc, List<HistoryLine> history) {
        int size = Math.min(VANILLA_HISTORY_LIMIT, history == null ? 0 : history.size());
        List<GuiMessage> messages = new ArrayList<>(size);
        int currentTick = mc.gui.hud.getGuiTicks();
        long now = System.currentTimeMillis();
        int first = history == null ? 0 : history.size() - size;
        for (int i = first; history != null && i < history.size(); i++) {
            HistoryLine line = history.get(i);
            long ageTicks = Math.max(0L, (now - line.receivedAt()) / 50L);
            GuiMessage message = new GuiMessage(
                (int) Math.max(0L, currentTick - Math.min(Integer.MAX_VALUE, ageTicks)),
                line.component(),
                null,
                GuiMessageSource.SYSTEM_SERVER,
                GuiMessageTag.systemSinglePlayer()
            );

            messages.add(0, message);
        }
        return new ChatComponent.State(messages, List.of(), List.of());
    }
}
