package com.example.addon.events;

import autismclient.api.AutismAddons;
import autismclient.util.AutismClientMessaging;

// Event hooks. Register listeners once in onInitialize().
// Available: onTick, onPacketSend (return true to cancel), onPacketReceive, onGameJoin, onGameLeft.
public final class ExampleEvents {
    private ExampleEvents() {}

    public static void register() {
        AutismAddons.events().onGameJoin(() ->
            AutismClientMessaging.sendPrefixed("\u00a7a[Example] joined a world!"));
    }
}
