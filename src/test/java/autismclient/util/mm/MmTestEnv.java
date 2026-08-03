package autismclient.util.mm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

final class MmTestEnv {
    private static boolean ready;

    private MmTestEnv() {}

    static synchronized void ensureGameDir(Path gameDir) {
        if (ready) return;
        try {
            Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            Class<?> impl = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
            Field gd = impl.getDeclaredField("gameDir");
            gd.setAccessible(true);
            if (gd.get(loader) == null) {
                Method m = impl.getDeclaredMethod("setGameDir", Path.class);
                m.setAccessible(true);
                m.invoke(loader, gameDir);
            }
            ready = true;
        } catch (Throwable t) {
            throw new IllegalStateException("could not initialize a test game dir", t);
        }
    }
}
