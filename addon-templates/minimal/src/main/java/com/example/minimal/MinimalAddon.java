package com.example.minimal;

import autismclient.api.ApiVersion;
import autismclient.api.SimpleAddon;
import autismclient.api.module.SimpleModule;
import autismclient.util.AutismClientMessaging;

public final class MinimalAddon extends SimpleAddon {
    public static final String ID = "autism-minimal-addon-template";

    public MinimalAddon() {
        super(ApiVersion.CURRENT, "com.example.minimal");
    }

    @Override
    protected void initialize() {
        SimpleModule module = new SimpleModule("hello-module", "Hello Module", "Tiny example addon module.")
            .addBool("greet", "Greet On Enable", true)
            .addText("message", "Message", "Hello from my addon")
            .onEnabled(self -> {
                if (self.getBool("greet")) {
                    AutismClientMessaging.sendPrefixed("\u00a7a" + self.getText("message"));
                }
            });
        registerModule(module);

        registerSimpleAction("say-hello", "Say Hello", "Send a short addon message.", "S", mc ->
            AutismClientMessaging.sendPrefixed("\u00a7aHello from the minimal addon!"));

        registerSimpleCondition("wait-player", "Wait Player", "Wait until the local player exists.",
            "Waiting for player", "P", mc -> mc.player != null);
    }
}
