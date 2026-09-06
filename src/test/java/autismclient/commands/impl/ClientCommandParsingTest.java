package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCommandParsingTest {
    @Test
    void clickSlotAcceptsCountsAboveTheOldOneThousandLimit() {
        CommandDispatcher<AutismCommandSource> dispatcher = dispatcher("click-slot", new ClickSlotCommand());

        assertParses(dispatcher, "click-slot gui1 left 1001");
        assertParses(dispatcher, "click-slot gui1 shift-left " + ItemClickCommandSupport.MAX_REPEAT_COUNT);
        assertDoesNotParse(dispatcher, "click-slot gui1 left " + (ItemClickCommandSupport.MAX_REPEAT_COUNT + 1));
    }

    @Test
    void syncCommandAcceptsServerCommandsMessagesAndQuotedMacroNames() {
        CommandDispatcher<AutismCommandSource> dispatcher = dispatcher("sync", new SyncCommand());

        assertParses(dispatcher, "sync send /ver");
        assertParses(dispatcher, "sync send hello from every client");
        assertParses(dispatcher, "sync macro spark");
        assertParses(dispatcher, "sync macro \"Spark Run\"");
    }

    private static CommandDispatcher<AutismCommandSource> dispatcher(
        String rootName,
        autismclient.commands.Command command
    ) {
        CommandDispatcher<AutismCommandSource> dispatcher = new CommandDispatcher<>();
        LiteralArgumentBuilder<AutismCommandSource> root = LiteralArgumentBuilder.literal(rootName);
        command.build(root);
        dispatcher.register(root);
        return dispatcher;
    }

    private static void assertParses(CommandDispatcher<AutismCommandSource> dispatcher, String input) {
        ParseResults<AutismCommandSource> result = dispatcher.parse(input, AutismCommandSource.INSTANCE);
        assertFalse(result.getReader().canRead(), () -> "Unread command input: "
            + result.getReader().getRemaining());
        assertTrue(result.getExceptions().isEmpty(), () -> "Parse exceptions: " + result.getExceptions());
    }

    private static void assertDoesNotParse(CommandDispatcher<AutismCommandSource> dispatcher, String input) {
        ParseResults<AutismCommandSource> result = dispatcher.parse(input, AutismCommandSource.INSTANCE);
        assertTrue(result.getReader().canRead() || !result.getExceptions().isEmpty(),
            "Out-of-range count unexpectedly parsed");
    }
}
