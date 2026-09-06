package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.modules.PackHideState;
import autismclient.modules.ModuleRegistry;
import autismclient.util.macro.DelayAction;
import autismclient.util.macro.SendChatAction;
import autismclient.util.macro.ToggleModuleAction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AutismCompatManager {
    public static final List<String> COMMAND_PREFIX_CHOICES = List.of(".", "%", "-", "_", "*", "#", "@", "&", "=");
    private static final String METEOR_MACROS_CLASS = "meteordevelopment.meteorclient.systems.macros.Macros";
    private static final String METEOR_MACRO_CLASS = "meteordevelopment.meteorclient.systems.macros.Macro";
    private static final String METEOR_MODULES_CLASS = "meteordevelopment.meteorclient.systems.modules.Modules";
    private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";
    private static final String BARITONE_GOAL_CLASS = "baritone.api.pathing.goals.Goal";
    private static final String BARITONE_GOAL_BLOCK_CLASS = "baritone.api.pathing.goals.GoalBlock";
    private static final boolean METEOR_AVAILABLE = FabricLoader.getInstance().isModLoaded("meteor-client")
        && classExists(METEOR_MACROS_CLASS);
    private static final boolean BARITONE_AVAILABLE = FabricLoader.getInstance().isModLoaded("baritone")
        || classExists(BARITONE_API_CLASS);
    private static volatile String cachedEffectiveStored;
    private static volatile String cachedEffectivePrefix;
    private static volatile String cachedMeteorCommandPrefix = ".";
    private static volatile long meteorPrefixRefreshAtNanos;
    private static volatile int cachedAutismModuleRevision = Integer.MIN_VALUE;
    private static volatile List<String> cachedAutismModuleNames = List.of();
    private static volatile List<String> cachedMeteorOnlyModuleNames;

    private AutismCompatManager() {
    }

    public static boolean isMeteorAvailable() {
        return METEOR_AVAILABLE;
    }

    public static String effectiveCommandPrefix() {
        autismclient.modules.AutismModule module = autismclient.modules.AutismModule.get();
        String stored = module == null ? AutismConfig.getGlobal().commandPrefix : module.getCommandPrefix();
        String safeStored = stored == null ? "" : stored;
        String cachedStored = cachedEffectiveStored;
        String cachedPrefix = cachedEffectivePrefix;
        if (safeStored.equals(cachedStored) && cachedPrefix != null) return cachedPrefix;
        String normalized = normalizeStoredCommandPrefix(safeStored);
        cachedEffectiveStored = safeStored;
        cachedEffectivePrefix = normalized;
        return normalized;
    }

    public static String environmentDefaultCommandPrefix() {
        return isMeteorAvailable() ? "%" : ".";
    }

    public static String normalizeStoredCommandPrefix(String prefix) {
        String selected = prefix == null ? "" : prefix.trim();
        if (!COMMAND_PREFIX_CHOICES.contains(selected)) return environmentDefaultCommandPrefix();
        if (".".equals(selected) && isMeteorAvailable()) return "%";
        return selected;
    }

    public static boolean isSelectableCommandPrefix(String prefix) {
        String selected = prefix == null ? "" : prefix.trim();
        return COMMAND_PREFIX_CHOICES.contains(selected) && !(".".equals(selected) && isMeteorAvailable());
    }

    public static boolean isBaritoneAvailable() {
        return BARITONE_AVAILABLE;
    }

    public static String meteorCommandPrefix() {
        if (!METEOR_AVAILABLE) return ".";
        long now = System.nanoTime();
        if (now < meteorPrefixRefreshAtNanos) return cachedMeteorCommandPrefix;
        synchronized (AutismCompatManager.class) {
            now = System.nanoTime();
            if (now < meteorPrefixRefreshAtNanos) return cachedMeteorCommandPrefix;
            cachedMeteorCommandPrefix = readMeteorCommandPrefix();
            meteorPrefixRefreshAtNanos = now + 1_000_000_000L;
            return cachedMeteorCommandPrefix;
        }
    }

    public static List<AutismMacro> getMeteorMacros() {
        if (!isMeteorAvailable()) return Collections.emptyList();

        List<AutismMacro> macros = new ArrayList<>();
        try {
            Object meteorMacros = getMeteorMacrosSystem();
            Method getAllMethod = meteorMacros.getClass().getMethod("getAll");
            Object allMacros = getAllMethod.invoke(meteorMacros);

            if (!(allMacros instanceof Iterable<?> iterable)) return macros;

            for (Object meteorMacro : iterable) {
                AutismMacro converted = convertMeteorMacro(meteorMacro);
                if (converted != null) macros.add(converted);
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("[Autism] Failed to read Meteor macros reflectively.", e);
        }
        return macros;
    }

    public static List<String> getMeteorMacroNames() {
        List<String> names = new ArrayList<>();
        for (AutismMacro macro : getMeteorMacros()) {
            if (macro != null && macro.name != null && !macro.name.isBlank()) names.add(macro.name);
        }
        return names;
    }

    public static AutismMacro getMeteorMacro(String macroName) {
        if (macroName == null || macroName.isBlank()) return null;
        for (AutismMacro macro : getMeteorMacros()) {
            if (macroName.equalsIgnoreCase(macro.name)) return macro;
        }
        return null;
    }

    public static boolean importToMeteor(AutismMacro packUtilMacro) {
        return false;
    }

    public static List<String> getAutismModuleNames() {
        int revision = ModuleRegistry.revision();
        List<String> names = cachedAutismModuleNames;
        if (cachedAutismModuleRevision != revision) {
            List<String> rebuilt = new ArrayList<>(ModuleRegistry.names());
            rebuilt.removeIf(name -> name == null || name.isBlank());
            rebuilt.sort(String::compareToIgnoreCase);
            names = List.copyOf(rebuilt);
            cachedAutismModuleNames = names;
            cachedAutismModuleRevision = revision;
        }
        return new ArrayList<>(names);
    }

    public static List<String> getMeteorOnlyModuleNames() {
        if (!isMeteorAvailable()) return Collections.emptyList();

        List<String> cached = cachedMeteorOnlyModuleNames;
        if (cached != null) return new ArrayList<>(cached);

        List<String> nativeNames = getAutismModuleNames();
        List<String> names = new ArrayList<>();
        try {
            Object modulesSystem = getMeteorModulesSystem();
            Method getAllMethod = modulesSystem.getClass().getMethod("getAll");
            Object allModules = getAllMethod.invoke(modulesSystem);
            if (allModules instanceof Iterable<?> iterable) {
                for (Object module : iterable) {
                    Object name = getFieldValue(module, "name");
                    if (name instanceof String s && !s.isBlank()
                            && !containsIgnoreCase(nativeNames, s)
                            && !containsIgnoreCase(names, s)) {
                        names.add(s);
                    }
                }
            }
            names.sort(String::compareToIgnoreCase);
            cachedMeteorOnlyModuleNames = List.copyOf(names);
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("[Autism] Failed to read Meteor modules reflectively.", e);
        }
        return names;
    }

    private static String readMeteorCommandPrefix() {
        try {
            Class<?> configClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config");
            Object config = configClass.getMethod("get").invoke(null);
            Object prefixSetting = configClass.getField("prefix").get(config);
            Object prefix = prefixSetting.getClass().getMethod("get").invoke(prefixSetting);
            if (prefix instanceof String stringPrefix && !stringPrefix.isBlank()) return stringPrefix.trim();
        } catch (Throwable ignored) {  }
        return ".";
    }

    public static List<String> getMeteorModuleNames() {
        List<String> names = getAutismModuleNames();
        names.addAll(getMeteorOnlyModuleNames());
        return names;
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) return false;
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    public static boolean toggleMeteorModule(String moduleName, ToggleModuleAction.ToggleMode toggleMode) {
        if (PackHideState.isActive() && !PackHideState.isHideModuleName(moduleName)) return false;
        if (ModuleRegistry.toggle(moduleName, toggleMode)) return true;
        if (!isMeteorAvailable() || moduleName == null || moduleName.isBlank()) return false;

        try {
            Object modulesSystem = getMeteorModulesSystem();
            Method getAllMethod = modulesSystem.getClass().getMethod("getAll");
            Object allModules = getAllMethod.invoke(modulesSystem);
            Object targetModule = null;
            if (allModules instanceof Iterable<?> iterable) {
                for (Object module : iterable) {
                    Object name = getFieldValue(module, "name");
                    if (name instanceof String s && moduleName.equalsIgnoreCase(s)) {
                        targetModule = module;
                        break;
                    }
                }
            }
            if (targetModule == null) return false;

            Method isActiveMethod = targetModule.getClass().getMethod("isActive");
            Method toggleMethod = targetModule.getClass().getMethod("toggle");
            boolean isActive = (boolean) isActiveMethod.invoke(targetModule);

            switch (toggleMode) {
                case ENABLE -> {
                    if (!isActive) toggleMethod.invoke(targetModule);
                }
                case DISABLE -> {
                    if (isActive) toggleMethod.invoke(targetModule);
                }
                default -> toggleMethod.invoke(targetModule);
            }
            return true;
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("[Autism] Failed to toggle Meteor module '{}' reflectively.", moduleName, e);
            return false;
        }
    }

    public static boolean sendBaritoneCommand(Minecraft mc, String command) {
        if (PackHideState.isHardLocked()) return false;
        if (!isBaritoneAvailable() || mc == null || mc.getConnection() == null || command == null || command.isBlank()) {
            return false;
        }

        mc.getConnection().sendChat(command);
        return true;
    }

    public static boolean startBaritoneGoTo(Minecraft mc, int x, int y, int z) {
        if (!isBaritoneAvailable()) return false;

        try {
            Object baritone = getPrimaryBaritone();
            Object process = invokeMethod(baritone, "getCustomGoalProcess");
            Class<?> goalType = Class.forName(BARITONE_GOAL_CLASS);
            Class<?> goalBlockClass = Class.forName(BARITONE_GOAL_BLOCK_CLASS);
            Object goal = goalBlockClass.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
            invokeMethod(process, "setGoalAndPath", new Class<?>[]{goalType}, new Object[]{goal});
            return true;
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Autism] Failed to start Baritone goto reflectively, falling back to chat command.", t);
            return sendBaritoneCommand(mc, String.format("#goto %d %d %d", x, y, z));
        }
    }

    public static boolean startBaritoneMine(Minecraft mc, List<String> blockNames) {
        if (!isBaritoneAvailable() || blockNames == null || blockNames.isEmpty()) return false;

        List<String> sanitized = new ArrayList<>();
        for (String blockName : blockNames) {
            if (blockName == null) continue;
            String trimmed = blockName.trim();
            if (trimmed.isEmpty()) continue;
            sanitized.add(trimmed.startsWith("minecraft:") ? trimmed.substring(10) : trimmed);
        }
        if (sanitized.isEmpty()) return false;

        try {
            Object baritone = getPrimaryBaritone();
            Object process = invokeMethod(baritone, "getMineProcess");
            invokeMethod(process, "mineByName", new Class<?>[]{String[].class}, new Object[]{sanitized.toArray(String[]::new)});
            return true;
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Autism] Failed to start Baritone mine reflectively, falling back to chat command.", t);
            return sendBaritoneCommand(mc, "#mine " + String.join(" ", sanitized));
        }
    }

    public static boolean isBaritoneGoalActive() {
        return queryBaritoneProcessActive("getCustomGoalProcess");
    }

    public static boolean isBaritoneMineActive() {
        return queryBaritoneProcessActive("getMineProcess");
    }

    public static boolean isBaritonePathing() {
        try {
            Object pathing = invokeMethod(getPrimaryBaritone(), "getPathingBehavior");
            return invokeBoolean(pathing, "isPathing");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isBaritoneBusy() {
        if (!isBaritoneAvailable()) return false;

        try {
            Object baritone = getPrimaryBaritone();
            Object pathing = invokeMethod(baritone, "getPathingBehavior");
            boolean pathingNow = invokeBoolean(pathing, "isPathing");
            boolean hasPath = invokeBoolean(pathing, "hasPath");
            Object inProgress = invokeMethod(pathing, "getInProgress");
            boolean calculating = inProgress instanceof java.util.Optional<?> optional && optional.isPresent();

            return pathingNow
                || hasPath
                || calculating
                || queryProcessActive(baritone, "getCustomGoalProcess")
                || queryProcessActive(baritone, "getMineProcess");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void stopBaritone(Minecraft mc) {

        if (!isBaritoneAvailable()) return;
        boolean stoppedReflectively = false;

        try {
            Object baritone = getPrimaryBaritone();
            Object pathing = invokeMethod(baritone, "getPathingBehavior");
            invokeMethod(pathing, "cancelEverything");
            stoppedReflectively = true;
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Autism] Failed to stop Baritone reflectively, falling back to chat command.", t);
        }

        if (!stoppedReflectively) {
            sendBaritoneCommand(mc, "#stop");
        }
    }

    private static AutismMacro convertMeteorMacro(Object meteorMacro) {
        if (meteorMacro == null) return null;
        try {
            AutismMacro macro = new AutismMacro();

            String name = null;
            List<String> messages = null;
            Object keybindObj = null;

            try {
                Object nameVal = getSettingValue(meteorMacro, "name");
                if (nameVal instanceof String s) name = s;
            } catch (Exception ignored) {  }

            try {
                Object msgVal = getSettingValue(meteorMacro, "messages");
                if (msgVal instanceof List<?> list) {
                    messages = new ArrayList<>();
                    for (Object o : list) {
                        if (o instanceof String s) messages.add(s);
                    }
                } else if (msgVal instanceof Iterable<?> iter) {
                    messages = new ArrayList<>();
                    for (Object o : iter) {
                        if (o instanceof String s) messages.add(s);
                    }
                }
            } catch (Exception ignored) {  }

            try {
                keybindObj = getSettingValue(meteorMacro, "keybind");
            } catch (Exception ignored) {  }

            if (name == null || messages == null) {
                try {
                    Object settings = getFieldValue(meteorMacro, "settings");
                    if (settings instanceof Iterable<?> groups) {
                        for (Object group : groups) {
                            if (group instanceof Iterable<?> settingIter) {
                                for (Object setting : settingIter) {
                                    try {
                                        String sName = (String) setting.getClass().getField("name").get(setting);
                                        Method getM = setting.getClass().getMethod("get");
                                        Object val = getM.invoke(setting);

                                        if ("name".equals(sName) && name == null && val instanceof String s) {
                                            name = s;
                                        } else if ("messages".equals(sName) && messages == null) {
                                            if (val instanceof List<?> list) {
                                                messages = new ArrayList<>();
                                                for (Object o : list) {
                                                    if (o instanceof String s) messages.add(s);
                                                }
                                            }
                                        } else if ("keybind".equals(sName) && keybindObj == null) {
                                            keybindObj = val;
                                        }
                                    } catch (Exception ignored) {  }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    AutismClientAddon.LOG.warn("[Autism] Settings iteration fallback failed.", e);
                }
            }

            if (name == null || name.isBlank()) return null;
            macro.name = name;

            if (messages != null) {
                for (int mi = 0; mi < messages.size(); mi++) {
                    String msg = messages.get(mi);
                    if (msg == null) continue;
                    String trimmed = msg.trim();
                    if (trimmed.isEmpty()) continue;

                    if (trimmed.startsWith(".toggle ")) {
                        String modName = trimmed.substring(".toggle ".length()).trim();
                        if (!modName.isEmpty()) {
                            macro.actions.add(new ToggleModuleAction(modName));
                        }
                    } else {

                        if (trimmed.startsWith(".") && !trimmed.startsWith("./") && trimmed.length() > 1) {
                            macro.description += (macro.description.isEmpty() ? "" : "; ")
                                + "Warning: '" + trimmed + "' is a Meteor command, may not work as chat";
                        }
                        macro.actions.add(new SendChatAction(msg));
                    }

                    if (mi < messages.size() - 1) {
                        macro.actions.add(new DelayAction(50));
                    }
                }
            }

            if (keybindObj != null) {
                try {
                    Method getValue = keybindObj.getClass().getMethod("getValue");
                    Object val = getValue.invoke(keybindObj);
                    if (val instanceof Integer k && k != -1 && k != 0) {
                        macro.keyCode = k;
                    }
                } catch (Exception ignored) {  }
            }

            return macro;
        } catch (Exception e) {
            AutismClientAddon.LOG.warn("[Autism] Error converting Meteor macro.", e);
            return null;
        }
    }

    private static Object getMeteorMacrosSystem() throws Exception {
        Class<?> macrosClass = Class.forName(METEOR_MACROS_CLASS);
        Method getMethod = macrosClass.getMethod("get");
        return getMethod.invoke(null);
    }

    private static Object getMeteorModulesSystem() throws Exception {
        Class<?> modulesClass = Class.forName(METEOR_MODULES_CLASS);
        Method getMethod = modulesClass.getMethod("get");
        return getMethod.invoke(null);
    }

    private static Object getPrimaryBaritone() throws Exception {
        Class<?> apiClass = Class.forName(BARITONE_API_CLASS);
        Object provider = apiClass.getMethod("getProvider").invoke(null);
        return provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
    }

    private static Object getSettingValue(Object owner, String fieldName) throws Exception {
        Object setting = getFieldValue(owner, fieldName);
        if (setting == null) return null;
        Method getMethod = setting.getClass().getMethod("get");
        return getMethod.invoke(setting);
    }

    private static void setSettingValue(Object owner, String fieldName, Object value) throws Exception {
        Object setting = getFieldValue(owner, fieldName);
        if (setting == null) return;
        Method setMethod = setting.getClass().getMethod("set", Object.class);
        setMethod.invoke(setting, value);
    }

    private static Object getFieldValue(Object owner, String fieldName) throws Exception {
        Field field = owner.getClass().getField(fieldName);
        return field.get(owner);
    }

    private static boolean queryBaritoneProcessActive(String getterName) {
        if (!isBaritoneAvailable()) return false;

        try {
            return queryProcessActive(getPrimaryBaritone(), getterName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean queryProcessActive(Object baritone, String getterName) throws Exception {
        Object process = invokeMethod(baritone, getterName);
        return invokeBoolean(process, "isActive");
    }

    private static boolean invokeBoolean(Object target, String methodName) throws Exception {
        Object value = invokeMethod(target, methodName);
        return value instanceof Boolean b && b;
    }

    private static Object invokeMethod(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private static Object invokeMethod(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        return target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
