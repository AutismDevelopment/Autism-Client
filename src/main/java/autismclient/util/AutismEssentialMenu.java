package autismclient.util;

import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class AutismEssentialMenu {
    private static final String GUI_UTIL = "gg.essential.util.GuiUtil";
    private static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.Screen";

    private AutismEssentialMenu() {
    }

    public static boolean isAvailable() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("essential");
    }

    public static void openSocial() {
        openScreen(constructDefaulted("gg.essential.gui.friends.SocialMenu"));
    }

    public static void openWardrobe() {
        openScreen(constructDefaulted("gg.essential.gui.wardrobe.Wardrobe"));
    }

    public static void openPictures() {
        openScreen(constructDefaulted("gg.essential.gui.screenshot.components.ScreenshotBrowser"));
    }

    public static void openHost() {
        if (!pushModal(modalManager -> constructWithArgs("gg.essential.gui.sps.WorldSelectionModal", modalManager))) {
            openQuickAccess();
        }
    }

    public static void openSettings() {
        try {
            Class<?> cfg = Class.forName("gg.essential.config.McEssentialConfig");
            Object instance = cfg.getField("INSTANCE").get(null);
            Method gui = cfg.getMethod("gui", String.class);
            gui.setAccessible(true);
            Object screen = gui.invoke(instance, (Object) null);
            openScreen(screen);
        } catch (Throwable ignored) {  }
    }

    public static void openAccount() {
        if (!pushModal(modalManager -> {
            Object accountManager = constructNoArg("gg.essential.gui.menu.AccountManager");
            if (accountManager == null) return null;
            Object accounts = invokeNoArg(accountManager, "getAllAccounts");
            if (accounts == null) return null;
            return constructWithArgs("gg.essential.gui.menu.AccountManagerModal", modalManager, accountManager, accounts);
        })) {
            openQuickAccess();
        }
    }

    public static void openQuickAccess() {
        try {
            Class<?> clazz = Class.forName("gg.essential.gui.modals.QuickAccessModal");
            Object companion = clazz.getDeclaredField("Companion").get(null);
            Method open = companion.getClass().getDeclaredMethod("open");
            open.setAccessible(true);
            open.invoke(companion);
        } catch (Throwable ignored) {  }
    }

    private static void openScreen(Object screen) {
        if (screen == null) return;
        try {
            Class<?> guiUtil = Class.forName(GUI_UTIL);
            Object instance = guiUtil.getField("INSTANCE").get(null);
            Class<?> screenType = Class.forName(SCREEN_CLASS);
            Method open = guiUtil.getMethod("openScreen", screenType);
            open.setAccessible(true);
            open.invoke(instance, screen);
        } catch (Throwable t) {
            if (screen instanceof Screen s) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null) mc.execute(() -> mc.gui.setScreen(s));
            }
        }
    }

    private static boolean pushModal(ModalFactory factory) {
        if (factory == null) return false;
        try {
            Class<?> guiUtil = Class.forName(GUI_UTIL);
            Object instance = guiUtil.getField("INSTANCE").get(null);
            Class<?> functionType = Class.forName("kotlin.jvm.functions.Function1");
            Object function = Proxy.newProxyInstance(
                functionType.getClassLoader(),
                new Class<?>[] { functionType },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("toString".equals(name)) return "AutismEssentialModalFactory";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                    if ("invoke".equals(name) && args != null && args.length == 1) {
                        return factory.create(args[0]);
                    }
                    return null;
                }
            );
            Method push = guiUtil.getMethod("pushModal", functionType);
            push.setAccessible(true);
            push.invoke(instance, function);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object constructNoArg(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object constructWithArgs(String className, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length != (args == null ? 0 : args.length)) continue;
                boolean match = true;
                for (int i = 0; i < params.length; i++) {
                    Object arg = args[i];
                    if (arg == null) {
                        if (params[i].isPrimitive()) {
                            match = false;
                            break;
                        }
                    } else if (!wrap(params[i]).isInstance(arg)) {
                        match = false;
                        break;
                    }
                }
                if (!match) continue;
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Object constructDefaulted(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            try {
                Constructor<?> noArg = clazz.getDeclaredConstructor();
                noArg.setAccessible(true);
                return noArg.newInstance();
            } catch (NoSuchMethodException ignored) {  }
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length < 2) continue;
                if (params[params.length - 1].getName().equals("kotlin.jvm.internal.DefaultConstructorMarker")
                    && params[params.length - 2] == int.class) {
                    Object[] args = new Object[params.length];
                    for (int i = 0; i < params.length - 2; i++) {
                        args[i] = defaultValue(params[i]);
                    }
                    int realParams = params.length - 2;
                    args[params.length - 2] = realParams >= 32 ? -1 : ((1 << realParams) - 1);
                    args[params.length - 1] = null;
                    ctor.setAccessible(true);
                    return ctor.newInstance(args);
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Object invokeNoArg(Object instance, String methodName) {
        if (instance == null || methodName == null) return null;
        try {
            Method method = instance.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return Void.class;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        return 0;
    }

    @FunctionalInterface
    private interface ModalFactory {
        Object create(Object modalManager);
    }
}
