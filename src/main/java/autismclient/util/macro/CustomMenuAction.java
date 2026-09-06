package autismclient.util.macro;

import autismclient.api.custommenu.CustomMenuAdapterRegistry;
import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.api.custommenu.CustomMenuSubmitResult;
import autismclient.util.AutismNotifications;
import autismclient.util.custommenu.CustomMenuScreens;
import autismclient.util.custommenu.CustomMenuTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;

public final class CustomMenuAction implements MacroAction {

    private static final long FAILURE_REPEAT_MS = 15_000L;

    private static String lastFailure = "";
    private static long lastFailureAtMs;

    public final ArrayList<String> fieldValues = new ArrayList<>();

    public String clickButton = "";
    public int timeoutMs = 30_000;
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        long deadline = System.currentTimeMillis() + boundedTimeout();

        boolean gateOnRun = MacroExecutor.isCurrentActionRunActive();
        String lastError = "";
        while (System.currentTimeMillis() <= deadline && !Thread.currentThread().isInterrupted()) {
            if (gateOnRun && !MacroExecutor.isCurrentActionRunActive()) return;

            long seenGeneration = CustomMenuTracker.generation();
            CustomMenuSnapshot snapshot = CustomMenuTracker.current();
            if (snapshot != null) seenGeneration = snapshot.generation();
            else snapshot = CustomMenuScreens.openScreenSnapshot(mc);
            if (snapshot == null) {
                java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
                continue;
            }

            CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(this, snapshot, value -> {
                String withSecrets = autismclient.util.AutismJoinMacroController.resolveStoredFormTemplate(value);
                if (withSecrets == null) throw new IllegalStateException("Missing form value");
                MacroTemplate.Resolution resolved = MacroVariables.resolve(withSecrets, mc);
                if (!resolved.success()) throw new IllegalStateException("Missing macro value");
                return resolved.value();
            });
            if (!prepared.success()) {
                lastError = prepared.error() == null ? "" : prepared.error();

                if (prepared.error() != null && prepared.error().contains("unavailable")) {
                    java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
                    continue;
                }
                fail(prepared.error());
                return;
            }
            Screen answered = CustomMenuScreens.openScreen(mc);
            CustomMenuSubmitResult result = CustomMenuAdapterRegistry.submit(snapshot, prepared.submission());
            if (!result.success()) { fail(result.error()); return; }
            for (Packet<?> packet : result.packets()) {

                if (!autismclient.util.AutismJoinMacroController.sendCommonPacket(packet)) {
                    fail("Connection closed before custom-menu submission");
                    return;
                }
            }
            CustomMenuTracker.consumeAt(seenGeneration, result.replacement(), snapshot.phase());

            CustomMenuScreens.advanceAfterSubmit(mc, result.clientAction(), answered);
            return;
        }

        fail(lastError.isBlank() ? "Custom screen never appeared (timed out)" : lastError + " (timed out)");
    }

    private void fail(String reason) {
        warnOnce(reason == null || reason.isBlank() ? "Custom screen action failed" : reason);
    }

    private static void warnOnce(String message) {
        long now = System.currentTimeMillis();
        synchronized (CustomMenuAction.class) {
            if (message.equals(lastFailure) && now - lastFailureAtMs < FAILURE_REPEAT_MS) return;
            lastFailure = message;
            lastFailureAtMs = now;
        }
        AutismNotifications.warning(message);
    }

    public int boundedTimeout() { return Math.max(100, Math.min(120_000, timeoutMs)); }

    @Override public MacroActionType getType() { return MacroActionType.CUSTOM_MENU; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", MacroActionType.CUSTOM_MENU.name());
        tag.put("fieldValues", MacroStringList.toTag(fieldValues));
        tag.putString("clickButton", clickButton);
        tag.putInt("timeoutMs", boundedTimeout());
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        fieldValues.clear();
        clickButton = tag.getStringOr("clickButton", "");
        if (tag.getList("fieldValues").isPresent()) {
            fieldValues.addAll(MacroStringList.fromTag(tag.getList("fieldValues").orElse(new ListTag())));
        } else {

            String primary = tag.getStringOr("primaryValue", "");
            if (!primary.isBlank()) fieldValues.add(primary);
            fieldValues.addAll(MacroStringList.fromTag(tag.getList("inputValues").orElse(new ListTag())));
            if (clickButton.isBlank()) {
                String buttonMode = tag.getStringOr("buttonMode", "SAFE_AUTO");
                String selector = tag.getStringOr("buttonSelector", "");
                int index = Math.max(1, tag.getIntOr("buttonIndex", 1));
                clickButton = switch (buttonMode.toUpperCase(java.util.Locale.ROOT)) {
                    case "ACTION_ID", "LABEL" -> selector;
                    case "INDEX" -> "#" + index;
                    default -> "";
                };
            }
        }
        timeoutMs = Math.max(100, Math.min(120_000, tag.getIntOr("timeoutMs", 30_000)));
        enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        String button = clickButton.isBlank() ? "auto" : clickButton;
        return "Custom Screen (press " + button + ")";
    }

    @Override public String getIcon() { return "CS"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
