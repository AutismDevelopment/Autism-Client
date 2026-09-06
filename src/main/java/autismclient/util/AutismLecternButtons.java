package autismclient.util;

import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.ScreenButton;
import autismclient.modules.AutismModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class AutismLecternButtons {
    private AutismLecternButtons() {
    }

    public static List<ScreenButton> build(Minecraft client, AutismQueueEditorOverlay queueEditorOverlay) {
        List<ScreenButton> buttons = new ArrayList<>();
        AutismSharedState shared = AutismSharedState.get();
        int baseX = 5;
        int baseY = 5;
        int defaultWidth = 140;
        int defaultHeight = 20;

        buttons.add(new ScreenButton(
            baseX, baseY, defaultWidth, defaultHeight,
            Component.literal("Close without packet"),
            Button.Tone.NORMAL,
            () -> AutismGuiActions.closeCurrentScreen(client, false)
        ));

        buttons.add(new ScreenButton(
            baseX, baseY + 24, defaultWidth, defaultHeight,
            Component.literal("De-sync"),
            Button.Tone.DANGER,
            () -> {
                if (!AutismGuiActions.desyncCurrentScreen(client)) {
                    AutismClientMessaging.sendPrefixed("Failed to desync: no open networked GUI.");
                }
            }
        ));

        buttons.add(new ScreenButton(
            baseX, baseY + 48, defaultWidth, defaultHeight,
            Component.literal("Send packets: " + onOff(shared.shouldSendGuiPackets())),
            shared.shouldSendGuiPackets() ? Button.Tone.SUCCESS : Button.Tone.DANGER,
            () -> {
                boolean newValue = !shared.shouldSendGuiPackets();
                AutismModule.get().applySendGuiPacketsUiBehavior(newValue);
                AutismNotifications.show("Send Packets " + (newValue ? "on" : "off"), newValue ? 0xFF35D873 : 0xFFFF3B3B);
            }
        ));

        buttons.add(new ScreenButton(
            baseX, baseY + 72, defaultWidth, defaultHeight,
            Component.literal("Delay packets: " + onOff(shared.shouldDelayGuiPackets())),
            shared.shouldDelayGuiPackets() ? Button.Tone.SUCCESS : Button.Tone.DANGER,
            () -> {
                boolean newValue = !shared.shouldDelayGuiPackets();
                AutismModule module = AutismModule.get();
                int sent = module.applyDelayGuiPacketsUiBehavior(newValue);
                module.notifyDelayPacketsUiResult(newValue, sent);
            }
        ));

        int thirdWidth = (defaultWidth - 10) / 3;
        buttons.add(new ScreenButton(
            baseX, baseY + 96, thirdWidth, defaultHeight,
            Component.literal("Clear Q"),
            Button.Tone.DANGER,
            () -> {
                AutismModule module = AutismModule.get();
                int count = module.clearQueuedPacketsUiBehavior();
                module.notifyClearQueuedPacketsUiResult(count);
            }
        ));

        buttons.add(new ScreenButton(
            baseX + thirdWidth + 5, baseY + 96, thirdWidth, defaultHeight,
            Component.literal("Q Editor"),
            Button.Tone.NORMAL,
            () -> {
                if (queueEditorOverlay != null) {
                    queueEditorOverlay.setVisible(!queueEditorOverlay.isVisible());
                }
            }
        ));

        buttons.add(new ScreenButton(
            baseX + thirdWidth * 2 + 10, baseY + 96, thirdWidth, defaultHeight,
            Component.literal("Pkt Log"),
            Button.Tone.NORMAL,
            () -> AutismModule.get().togglePacketLoggerUiBehavior()
        ));

        int halfWidth = (defaultWidth - 5) / 2;
        buttons.add(new ScreenButton(
            baseX, baseY + 120, halfWidth, defaultHeight,
            Component.literal("Save GUI"),
            Button.Tone.NORMAL,
            () -> AutismGuiActions.saveCurrentGui(client)
        ));

        buttons.add(new ScreenButton(
            baseX + halfWidth + 5, baseY + 120, halfWidth, defaultHeight,
            Component.literal("Load GUI"),
            Button.Tone.NORMAL,
            () -> {
                if (shared.getStoredScreen() == null || shared.getStoredAbstractContainerMenu() == null) {
                    AutismNotifications.error("No stored GUI.");
                    return;
                }
                client.execute(() -> {
                    client.gui.setScreen(shared.getStoredScreen());
                    if (client.player != null) client.player.containerMenu = shared.getStoredAbstractContainerMenu();
                });
                AutismNotifications.show("GUI restored.", 0xFF35D873);
            }
        ));

        buttons.add(new ScreenButton(
            baseX, baseY + 144, defaultWidth + 45, defaultHeight,
            Component.literal("Disconnect and send packets"),
            Button.Tone.DANGER,
            () -> {
                AutismModule.get().setDelayGuiPackets(false);
                if (client.getConnection() != null) {
                    int sent = shared.flushDelayedPackets(client.getConnection());
                    client.getConnection().getConnection().disconnect(Component.literal("Disconnecting (Autism)"));
                    AutismNotifications.show(
                        sent > 0 ? "Sent " + sent + " packet" + (sent == 1 ? "" : "s") + " and disconnected" : "Disconnected - queue empty",
                        sent > 0 ? 0xFF35D873 : 0xFFFFC857
                    );
                } else AutismClientMessaging.sendPrefixed("§cFailed to disconnect: No network.");
            }
        ));

        return buttons;
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

}
