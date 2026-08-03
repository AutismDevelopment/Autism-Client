package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.modules.AutismModule;
import net.minecraft.client.Minecraft;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class MatchmakingCommand extends Command {
    public MatchmakingCommand() { super("matchmaking", "Toggle the Matchmaking overlay.", "mm"); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();

            mc.execute(() -> {
                AutismModule mod = AutismModule.get();
                if (mod != null) mod.toggleMatchmakingUiBehavior();
            });
            return SUCCESS;
        });
    }
}
