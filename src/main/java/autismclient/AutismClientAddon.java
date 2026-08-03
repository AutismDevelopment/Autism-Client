package autismclient;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class AutismClientAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final String MOD_ID = "autism";
    public static final java.io.File FOLDER = FabricLoader.getInstance().getConfigDir().resolve("autism").toFile();

    static {
        FOLDER.mkdirs();
    }

    private AutismClientAddon() {}
}
