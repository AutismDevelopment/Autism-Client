package autismclient.util.macro;

import autismclient.api.custommenu.CustomMenuAdapterRegistry;
import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.api.custommenu.CustomMenuSubmitResult;
import autismclient.util.AutismNotifications;
import autismclient.util.custommenu.CustomMenuTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.Optional;

public final class CustomMenuAction implements MacroAction {

    public final ArrayList<String> fieldValues = new ArrayList<>();

    public String clickButton = "";
    public int timeoutMs = 30_000;
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        long deadline = System.currentTimeMillis() + boundedTimeout();
        while (System.currentTimeMillis() <= deadline && !Thread.currentThread().isInterrupted()) {
            CustomMenuSnapshot snapshot = CustomMenuTracker.current();
            if (snapshot != null) {
                CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(this, snapshot, value -> {
                    String withSecrets = autismclient.util.AutismJoinMacroController.resolveStoredFormTemplate(value);
                    if (withSecrets == null) throw new IllegalStateException("Missing form value");
                    MacroTemplate.Resolution resolved = MacroVariables.resolve(withSecrets, mc);
                    if (!resolved.success()) throw new IllegalStateException("Missing macro value");
                    return resolved.value();
                });
                if (!prepared.success()) {

                    if (prepared.error() != null && prepared.error().contains("unavailable")) {
                        java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
                        continue;
                    }
                    fail(prepared.error());
                    return;
                }
                CustomMenuSubmitResult result = CustomMenuAdapterRegistry.submit(snapshot, prepared.submission());
                if (!result.success()) { fail(result.error()); return; }
                for (Packet<?> packet : result.packets()) {

                    if (!autismclient.util.AutismJoinMacroController.sendCommonPacket(packet)) {
                        fail("Connection closed before custom-menu submission");
                        return;
                    }
                }
                CustomMenuTracker.consume(snapshot, result.replacement());
                if (result.clientAction() != null && mc != null) {
                    mc.execute(() -> {
                        if (mc.gui.screen() instanceof DialogScreen<?> dialog) {
                            dialog.runAction(Optional.of(result.clientAction()));
                        }
                    });
                }
                return;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
        }
        fail("Custom screen never appeared (timed out)");
    }

    private void fail(String reason) {
        AutismNotifications.error(reason == null || reason.isBlank() ? "Custom screen action failed" : reason);
        MacroExecutor.stopCurrentActionRun();
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
