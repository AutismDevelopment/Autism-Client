package autismclient.mixin;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.mixin.accessor.AutismCommandSuggestionsListAccessor;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.PackHideState;
import autismclient.util.AutismCompatManager;
import autismclient.util.AutismMacroManager;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class AutismChatSuggestMixin {
    @Shadow @Final private EditBox input;
    @Shadow @Nullable private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow @Nullable private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow @Nullable private CommandSuggestions.SuggestionsList suggestions;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private boolean currentParseIsCommand;
    @Shadow private boolean currentParseIsMessage;
    @Shadow private boolean keepSuggestions;

    @Unique private boolean autism$owningSuggestions;
    @Unique private String autism$cachedInput;
    @Unique private int autism$cachedCursor = -1;
    @Unique private int autism$cachedCommandRevision = Integer.MIN_VALUE;
    @Unique private int autism$cachedModuleRevision = Integer.MIN_VALUE;
    @Unique private long autism$cachedMacroRevision = Long.MIN_VALUE;
    @Unique private AbstractContainerMenu autism$cachedMenu;
    @Unique private int autism$cachedMenuRevision = Integer.MIN_VALUE;

    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);
    @Shadow private void updateUsageInfo(ParseResults<ClientSuggestionProvider> parseResults, Suggestions suggestions) {}

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void autism$updateAutismCommandInfo(CallbackInfo ci) {
        String value = input.getValue();
        if (autism$shouldSuppressMeteorSuggestions(value)) {
            autism$clearActiveSuggestions();
            ci.cancel();
            return;
        }

        if (!autism$isAutismCommandInput(value)) {
            if (autism$owningSuggestions) autism$clearActiveSuggestions();
            return;
        }

        int prefixLen = AutismCommands.effectivePrefix().length();
        StringReader reader = new StringReader(value);
        reader.setCursor(prefixLen);
        int cursor = Math.max(prefixLen, Math.min(input.getCursorPosition(), value.length()));
        int commandRevision = AutismCommands.revision();
        int moduleRevision = ModuleRegistry.revision();
        long macroRevision = AutismMacroManager.get().getRevision();
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerMenu menu = minecraft.player == null ? null : minecraft.player.containerMenu;
        int menuRevision = menu == null ? -1 : menu.getStateId();

        if (value.equals(autism$cachedInput)
                && cursor == autism$cachedCursor
                && commandRevision == autism$cachedCommandRevision
                && moduleRevision == autism$cachedModuleRevision
                && macroRevision == autism$cachedMacroRevision
                && menu == autism$cachedMenu
                && menuRevision == autism$cachedMenuRevision
                && (autism$owningSuggestions || pendingSuggestions != null)) {
            ci.cancel();
            return;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        ParseResults<AutismCommandSource> parse = AutismCommands.dispatcher().parse(reader, AutismCommandSource.INSTANCE);

        @SuppressWarnings("unchecked")
        ParseResults<ClientSuggestionProvider> widgetParse =
                (ParseResults<ClientSuggestionProvider>) (Object) parse;

        currentParse = widgetParse;
        currentParseIsCommand = true;
        currentParseIsMessage = false;
        commandUsage.clear();

        if (suggestions != null && keepSuggestions && autism$canKeepSuggestionList(value)) {
            autism$rememberSuggestionKey(value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision);
            ci.cancel();
            return;
        }

        autism$clearActiveSuggestions();
        autism$rememberSuggestionKey(value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision);

        String inputSnapshot = value;
        CompletableFuture<Suggestions> future = AutismCommands.dispatcher()
                .getCompletionSuggestions(parse, cursor);
        pendingSuggestions = future;
        future.thenAccept(result -> {
            if (pendingSuggestions != future) return;
            if (!inputSnapshot.equals(input.getValue())) return;
            if (!autism$isAutismCommandInput(inputSnapshot)) return;
            autism$owningSuggestions = true;
            updateUsageInfo(widgetParse, result);
            if (pendingSuggestions == future && inputSnapshot.equals(input.getValue())) {
                showSuggestions(false);
            }
        });
        ci.cancel();
    }

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void autism$hideLateMeteorSuggestionsInPanic(CallbackInfo ci) {
        if (autism$shouldSuppressMeteorSuggestions(input.getValue())) {
            autism$clearActiveSuggestions();
        }
    }

    @Inject(method = "showSuggestions", at = @At("HEAD"), cancellable = true)
    private void autism$hideShownMeteorSuggestionsInPanic(boolean narrateFirstSuggestion, CallbackInfo ci) {
        if (autism$shouldSuppressMeteorSuggestions(input.getValue())) {
            autism$clearActiveSuggestions();
            ci.cancel();
        }
    }

    @Unique
    private static boolean autism$isAutismCommandInput(String value) {
        return !AutismCommands.commandsBlockedByPanic() && AutismCommands.isAutismCommandMessage(value);
    }

    @Unique
    private static boolean autism$shouldSuppressMeteorSuggestions(String value) {
        if (!PackHideState.isActive()) return false;
        if (!AutismCompatManager.isMeteorAvailable()) return false;
        if (value == null) return false;

        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("/")) return false;

        String prefix = AutismCompatManager.meteorCommandPrefix();
        return !prefix.isEmpty() && trimmed.startsWith(prefix);
    }

    @Unique
    private boolean autism$canKeepSuggestionList(String currentInput) {
        if (suggestions == null || currentInput == null) return false;
        try {
            AutismCommandSuggestionsListAccessor accessor =
                    (AutismCommandSuggestionsListAccessor) suggestions;
            String original = accessor.autism$getOriginalContents();
            List<Suggestion> list = accessor.autism$getSuggestionList();
            if (original == null || list == null || list.isEmpty()) return false;
            for (Suggestion suggestion : list) {
                if (suggestion == null) continue;
                try {
                    if (currentInput.equals(suggestion.apply(original))) return true;
                } catch (Throwable ignored) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    @Unique
    private void autism$rememberSuggestionKey(String value, int cursor, int commandRevision, int moduleRevision,
                                               long macroRevision, AbstractContainerMenu menu, int menuRevision) {
        autism$cachedInput = value;
        autism$cachedCursor = cursor;
        autism$cachedCommandRevision = commandRevision;
        autism$cachedModuleRevision = moduleRevision;
        autism$cachedMacroRevision = macroRevision;
        autism$cachedMenu = menu;
        autism$cachedMenuRevision = menuRevision;
    }

    @Unique
    private void autism$clearActiveSuggestions() {
        input.setSuggestion(null);
        suggestions = null;
        pendingSuggestions = null;
        keepSuggestions = false;
        autism$owningSuggestions = false;
    }
}
