package autismclient.util;

import java.util.ArrayList;
import java.util.List;

public final class AutismMeteorBridge {
    private static final String MODULES_CLASS = "meteordevelopment.meteorclient.systems.modules.Modules";
    private static final String HUD_CLASS = "meteordevelopment.meteorclient.systems.hud.Hud";

    private AutismMeteorBridge() {
    }

    public static void disableAndSave(AutismConfig config) {
        if (config == null) return;
        try {
            Object modules = modulesInstance();
            if (modules == null) return;

            List<?> active = (List<?>) modules.getClass().getMethod("getActive").invoke(modules);
            List<Object> snapshot = new ArrayList<>(active);
            List<String> names = new ArrayList<>();
            for (Object module : snapshot) {
                String name = (String) module.getClass().getField("name").get(module);
                names.add(name);
                module.getClass().getMethod("disable").invoke(module);
            }
            config.hideRestoreMeteorModules = names;

            Object hud = hudInstance();
            if (hud != null) {
                config.hideMeteorHudActive = hud.getClass().getField("active").getBoolean(hud);
                hud.getClass().getField("active").setBoolean(hud, false);
            }
        } catch (Throwable ignored) {  }
    }

    public static void restore(AutismConfig config) {
        if (config == null) return;
        try {
            Object modules = modulesInstance();
            if (modules != null && config.hideRestoreMeteorModules != null) {
                for (String name : config.hideRestoreMeteorModules) {
                    Object module = modules.getClass().getMethod("get", String.class).invoke(modules, name);
                    if (module == null) continue;
                    boolean activeNow = (Boolean) module.getClass().getMethod("isActive").invoke(module);
                    if (!activeNow) module.getClass().getMethod("enable").invoke(module);
                }
            }
            config.hideRestoreMeteorModules = new ArrayList<>();

            Object hud = hudInstance();
            if (hud != null) {
                hud.getClass().getField("active").setBoolean(hud, config.hideMeteorHudActive);
            }
        } catch (Throwable ignored) {  }
    }

    public static void enforceHidden() {
        try {
            Object modules = modulesInstance();
            if (modules != null) {
                List<?> active = (List<?>) modules.getClass().getMethod("getActive").invoke(modules);
                for (Object module : new ArrayList<>(active)) {
                    module.getClass().getMethod("disable").invoke(module);
                }
            }
            Object hud = hudInstance();
            if (hud != null) hud.getClass().getField("active").setBoolean(hud, false);
        } catch (Throwable ignored) {  }
    }

    private static Object modulesInstance() throws ReflectiveOperationException {
        try {
            return Class.forName(MODULES_CLASS).getMethod("get").invoke(null);
        } catch (ClassNotFoundException notInstalled) {
            return null;
        }
    }

    private static Object hudInstance() throws ReflectiveOperationException {
        try {
            return Class.forName(HUD_CLASS).getMethod("get").invoke(null);
        } catch (ClassNotFoundException notInstalled) {
            return null;
        }
    }
}
