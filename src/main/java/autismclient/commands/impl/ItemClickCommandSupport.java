package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.CommandSuggest;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismDropHelper;
import autismclient.util.AutismInventoryClickHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

final class ItemClickCommandSupport {
    static final int MAX_REPEAT_COUNT = 100_000;

    private ItemClickCommandSupport() {
    }

    record ClickSpec(String label, ContainerInput input, int button, DropMode dropMode) {
        static ClickSpec click(String label, ContainerInput input, int button) {
            return new ClickSpec(label, input, button, DropMode.NONE);
        }

        static ClickSpec drop(String label, DropMode dropMode) {
            return new ClickSpec(label, ContainerInput.THROW, dropMode == DropMode.STACK ? 1 : 0, dropMode);
        }
    }

    enum DropMode {
        NONE,
        ITEM,
        STACK
    }

    @FunctionalInterface
    interface ClickExecutor {
        int execute(CommandContext<AutismCommandSource> context, ClickSpec spec, int times);
    }

    static void attachModes(ArgumentBuilder<AutismCommandSource, ?> target, ClickExecutor executor) {
        target.then(repeated("left", ClickSpec.click("Left", ContainerInput.PICKUP, 0), executor));
        target.then(repeated("right", ClickSpec.click("Right", ContainerInput.PICKUP, 1), executor));
        target.then(repeated("middle", ClickSpec.click("Middle", ContainerInput.PICKUP, 2), executor));
        target.then(repeated("shift-left", ClickSpec.click("Shift Left", ContainerInput.QUICK_MOVE, 0), executor));
        target.then(repeated("shift-right", ClickSpec.click("Shift Right", ContainerInput.QUICK_MOVE, 1), executor));
        target.then(repeated("clone", ClickSpec.click("Clone", ContainerInput.CLONE, 2), executor));
        target.then(repeated("pickup-all", ClickSpec.click("Pick Up All", ContainerInput.PICKUP_ALL, 0), executor));
        target.then(repeated("drop-item", ClickSpec.drop("Drop Item", DropMode.ITEM), executor));
        target.then(single("drop-stack", ClickSpec.drop("Drop Stack", DropMode.STACK), executor));

        LiteralArgumentBuilder<AutismCommandSource> swap = LiteralArgumentBuilder.literal("swap");
        RequiredArgumentBuilder<AutismCommandSource, Integer> hotbar = RequiredArgumentBuilder
            .<AutismCommandSource, Integer>argument("swap-hotbar", IntegerArgumentType.integer(1, 9))
            .suggests((context, builder) -> CommandSuggest.literals(
                builder, "1", "2", "3", "4", "5", "6", "7", "8", "9"))
            .executes(context -> executor.execute(
                context,
                ClickSpec.click("Swap " + IntegerArgumentType.getInteger(context, "swap-hotbar"),
                    ContainerInput.SWAP,
                    IntegerArgumentType.getInteger(context, "swap-hotbar") - 1),
                1));
        hotbar.then(timesArgument((context, times) -> executor.execute(
            context,
            ClickSpec.click("Swap " + IntegerArgumentType.getInteger(context, "swap-hotbar"),
                ContainerInput.SWAP,
                IntegerArgumentType.getInteger(context, "swap-hotbar") - 1),
            times)));
        swap.then(hotbar);
        target.then(swap);
    }

    static int clickHandlerSlot(int handlerSlot, ClickSpec spec, int times, String targetLabel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null || mc.player.containerMenu == null) {
            AutismClientMessaging.sendPrefixed("§cNo active inventory or GUI.");
            return 1;
        }
        if (handlerSlot < 0 || handlerSlot >= mc.player.containerMenu.slots.size()) {
            AutismClientMessaging.sendPrefixed("§cThat slot does not exist in the current GUI.");
            return 1;
        }

        int requested = Math.max(1, times);
        int completed = 0;
        if (spec.dropMode() == DropMode.ITEM) {
            ItemStack stack = mc.player.containerMenu.slots.get(handlerSlot).getItem();
            if (!stack.isEmpty()) {
                int actual = Math.min(requested, stack.getCount());
                if (AutismDropHelper.dropFromHandlerSlot(mc, handlerSlot, actual) > 0) completed = actual;
            }
        } else if (spec.dropMode() == DropMode.STACK) {
            completed = AutismDropHelper.dropFromHandlerSlot(mc, handlerSlot, 0) > 0 ? 1 : 0;
        } else {
            for (int i = 0; i < requested; i++) {
                if (!AutismInventoryClickHelper.click(mc, handlerSlot, spec.button(), spec.input())) {
                    break;
                }
                completed++;
            }
        }

        if (completed == 0) {
            AutismClientMessaging.sendPrefixed("§cCould not click §f" + targetLabel + "§c.");
        } else {
            String repeat = completed > 1 && spec.dropMode() != DropMode.STACK ? " x" + completed : "";
            String partial = completed < requested && spec.dropMode() != DropMode.STACK
                ? " §e(" + completed + "/" + requested + ")"
                : "";
            AutismClientMessaging.sendPrefixed(
                "§a" + spec.label() + " clicked §f" + targetLabel + "§a" + repeat + "." + partial);
        }
        return 1;
    }

    private static LiteralArgumentBuilder<AutismCommandSource> repeated(
        String literal,
        ClickSpec spec,
        ClickExecutor executor
    ) {
        LiteralArgumentBuilder<AutismCommandSource> node = single(literal, spec, executor);
        node.then(timesArgument((context, times) -> executor.execute(context, spec, times)));
        return node;
    }

    private static LiteralArgumentBuilder<AutismCommandSource> single(
        String literal,
        ClickSpec spec,
        ClickExecutor executor
    ) {
        return LiteralArgumentBuilder.<AutismCommandSource>literal(literal)
            .executes(context -> executor.execute(context, spec, 1));
    }

    private static RequiredArgumentBuilder<AutismCommandSource, Integer> timesArgument(TimesExecutor executor) {
        return RequiredArgumentBuilder.<AutismCommandSource, Integer>argument(
                "times", IntegerArgumentType.integer(1, MAX_REPEAT_COUNT))
            .suggests(CommandSuggest::counts)
            .executes(context -> executor.execute(context, IntegerArgumentType.getInteger(context, "times")));
    }

    @FunctionalInterface
    private interface TimesExecutor {
        int execute(CommandContext<AutismCommandSource> context, int times);
    }
}
