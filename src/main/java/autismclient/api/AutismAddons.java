package autismclient.api;

import autismclient.addons.AddonManager;
import autismclient.api.event.AddonEvents;
import autismclient.api.custommenu.CustomMenuAdapter;
import autismclient.api.custommenu.CustomMenuAdapterRegistry;
import autismclient.api.hud.HudElementProvider;
import autismclient.api.hud.HudElements;
import autismclient.api.macro.MacroActionEntry;
import autismclient.api.macro.MacroActionRegistry;
import autismclient.api.macro.MacroPresetRegistry;
import autismclient.commands.Command;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;
import autismclient.modules.ModuleRegistry;
import autismclient.util.macro.MacroAction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AutismAddons {
    private AutismAddons() {}

    public static Modules modules() { return Modules.INSTANCE; }

    public static Commands commands() { return Commands.INSTANCE; }

    public static MacroActions macroActions() { return MacroActions.INSTANCE; }

    public static Presets presets() { return Presets.INSTANCE; }

    public static Hud hud() { return Hud.INSTANCE; }

    public static Events events() { return Events.INSTANCE; }

    public static CustomMenus customMenus() { return CustomMenus.INSTANCE; }

    public static List<AutismAddon> list() { return AddonManager.loaded(); }

    public static String id(String localId) { return AddonManager.scopedId(localId); }

    public static int apiVersion() { return ApiVersion.CURRENT; }

    public static final class Modules {
        static final Modules INSTANCE = new Modules();
        private Modules() {}

        public boolean register(Module module) {
            return ModuleRegistry.registerAddonModule(module, AddonManager.currentAddonId());
        }

        public AddonRegistrationResult registerDetailed(Module module) {
            return ModuleRegistry.registerAddonModuleDetailed(module, AddonManager.currentAddonId());
        }

        public ModuleCategory autoCategory() {
            return ModuleCategory.registerAddon(AddonManager.currentAddonId(),
                AddonManager.scopedCategoryLabel(null));
        }

        public ModuleCategory registerCategory(String label) {
            return ModuleCategory.registerAddon(AddonManager.currentAddonId(),
                AddonManager.scopedCategoryLabel(label));
        }
    }

    public static final class Commands {
        static final Commands INSTANCE = new Commands();
        private Commands() {}

        public void register(Command command) {
            autismclient.commands.AutismCommands.registerAddonCommand(command, AddonManager.currentAddonId());
        }

        public AddonRegistrationResult registerDetailed(Command command) {
            return autismclient.commands.AutismCommands.registerAddonCommandDetailed(command, AddonManager.currentAddonId());
        }
    }

    public static final class MacroActions {
        static final MacroActions INSTANCE = new MacroActions();
        private MacroActions() {}

        public boolean register(MacroActionEntry entry) {
            return MacroActionRegistry.register(entry);
        }

        public AddonRegistrationResult registerDetailed(MacroActionEntry entry) {
            return MacroActionRegistry.registerDetailed(entry);
        }

        public void registerCategory(String id, String label, int color) {
            MacroActionRegistry.registerScopedCategory(id, label, color);
        }
    }

    public static final class CustomMenus {
        static final CustomMenus INSTANCE = new CustomMenus();
        private CustomMenus() {}

        public boolean register(CustomMenuAdapter adapter) {
            return CustomMenuAdapterRegistry.register(adapter);
        }
    }

    public static final class Presets {
        static final Presets INSTANCE = new Presets();
        private Presets() {}

        public void registerCategory(String id, String label) {
            MacroPresetRegistry.registerScopedCategory(id, label);
        }

        public void register(String label, String tip, Supplier<List<MacroAction>> builder) {
            MacroPresetRegistry.register(MacroPresetRegistry.ensureScopedCategory(null), label, tip, builder);
        }

        public AddonRegistrationResult registerDetailed(String label, String tip, Supplier<List<MacroAction>> builder) {
            return MacroPresetRegistry.registerDetailed(MacroPresetRegistry.ensureScopedCategory(null), label, tip, builder);
        }

        public void register(String categoryId, String label, String tip, Supplier<List<MacroAction>> builder) {
            MacroPresetRegistry.register(MacroPresetRegistry.ensureScopedCategory(categoryId), label, tip, builder);
        }

        public AddonRegistrationResult registerDetailed(String categoryId, String label, String tip, Supplier<List<MacroAction>> builder) {
            return MacroPresetRegistry.registerDetailed(MacroPresetRegistry.ensureScopedCategory(categoryId), label, tip, builder);
        }
    }

    public static final class Hud {
        static final Hud INSTANCE = new Hud();
        private Hud() {}

        public boolean register(HudElementProvider provider) {
            return HudElements.register(provider);
        }

        public AddonRegistrationResult registerDetailed(HudElementProvider provider) {
            return HudElements.registerDetailed(provider);
        }
    }

    public static final class Events {
        static final Events INSTANCE = new Events();
        private Events() {}

        public void onTick(Consumer<Minecraft> listener) { AddonEvents.onTick(listener); }

        public void onPacketSend(Predicate<Packet<?>> listener) { AddonEvents.onPacketSend(listener); }

        public void onPacketReceive(Consumer<Packet<?>> listener) { AddonEvents.onPacketReceive(listener); }

        public void onGameJoin(Runnable listener) { AddonEvents.onGameJoin(listener); }

        public void onGameLeft(Runnable listener) { AddonEvents.onGameLeft(listener); }
    }
}
