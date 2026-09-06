package autismclient.commands;

import autismclient.AutismClientAddon;
import autismclient.api.AddonRegistrationResult;
import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismCompatManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class AutismCommands {
    private static final CommandDispatcher<AutismCommandSource> DISPATCHER = new CommandDispatcher<>();
    private static final List<Command> ALL = new ArrayList<>();
    private static final Map<String, Command> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, String> ADDON_COMMAND_OWNERS = new LinkedHashMap<>();
    private static final Map<Command, String> ADDON_COMMAND_OBJECT_OWNERS = new IdentityHashMap<>();
    private static final Set<String> DISABLED_ADDON_COMMAND_NAMES = new HashSet<>();
    private static final List<String> PANIC_PREFIX_FALLBACKS = List.of(".", ",", ";", ":", "'", "\"", "\\", "|", "-", "_", "+", "=", "*", "#", "@", "!", "$", "%", "&", "~");
    private static boolean initialized = false;
    private static int revision;

    private AutismCommands() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        register(new autismclient.commands.impl.MacroCommand());
        register(new autismclient.commands.impl.SyncCommand());
        register(new autismclient.commands.impl.DelayCommand());
        register(new autismclient.commands.impl.SendCommand());
        register(new autismclient.commands.impl.VClipCommand());
        register(new autismclient.commands.impl.HClipCommand());
        register(new autismclient.commands.impl.TpCommand());
        register(new autismclient.commands.impl.CopyPosCommand());
        register(new autismclient.commands.impl.NbtCommand());
        register(new autismclient.commands.impl.ServerCommand());
        register(new autismclient.commands.impl.PluginsCommand());
        register(new autismclient.commands.impl.PrefixCommand());
        register(new autismclient.commands.impl.CommandsCommand());
        register(new autismclient.commands.impl.HelpCommand());
        register(new autismclient.commands.impl.BindsCommand());
        register(new autismclient.commands.impl.BindCommand());
        register(new autismclient.commands.impl.ClearCommand());
        register(new autismclient.commands.impl.DismountCommand());
        register(new autismclient.commands.impl.DisconnectCommand());
        register(new autismclient.commands.impl.SayCommand());
        register(new autismclient.commands.impl.DropCommand());
        register(new autismclient.commands.impl.ChangeSlotCommand());
        register(new autismclient.commands.impl.ClickSlotCommand());
        register(new autismclient.commands.impl.ClickItemCommand());
        register(new autismclient.commands.impl.GiveCommand());
        register(new autismclient.commands.impl.DamageCommand());

        if (!autismclient.util.AutismLiteVariant.enabled()) {
            register(new autismclient.commands.impl.IrcCommand());
            register(new autismclient.commands.impl.MatchmakingCommand());
            register(new autismclient.commands.impl.MultiCommand());
            register(new autismclient.commands.impl.ToggleCommand());
            register(new autismclient.commands.impl.ModulesCommand());
            register(new autismclient.commands.impl.OreSimCommand());
            register(new autismclient.commands.impl.GhostBlockCommand());
            register(new autismclient.commands.impl.FriendCommand());
            register(new autismclient.commands.impl.WaypointsCommand());
            register(new autismclient.commands.impl.GamemodeCommand());
            register(new autismclient.commands.impl.FakeGmCommand());
            register(new autismclient.commands.impl.XCarryCommand());
            register(new autismclient.commands.impl.RemoteViewCommand());
            register(new autismclient.commands.impl.NameScrapeCommand());
        }
    }

    private static void register(Command command) {
        registerWithName(command, command.name(), true);
    }

    private static List<String> registerWithName(Command command, String primaryName, boolean registerAliases) {
        List<String> registeredNames = new ArrayList<>();
        revision++;
        ALL.add(command);
        LiteralArgumentBuilder<AutismCommandSource> primary = LiteralArgumentBuilder.literal(primaryName);
        command.build(primary);
        DISPATCHER.register(primary);
        BY_NAME.put(primaryName.toLowerCase(Locale.ROOT), command);
        DISABLED_ADDON_COMMAND_NAMES.remove(primaryName.toLowerCase(Locale.ROOT));
        registeredNames.add(primaryName.toLowerCase(Locale.ROOT));

        if (!registerAliases) return registeredNames;
        for (String alias : command.aliases()) {
            if (alias == null || alias.isBlank()) continue;
            if (BY_NAME.containsKey(alias.toLowerCase(Locale.ROOT))) continue;
            LiteralArgumentBuilder<AutismCommandSource> aliasBuilder = LiteralArgumentBuilder.literal(alias);
            command.build(aliasBuilder);
            DISPATCHER.register(aliasBuilder);
            BY_NAME.put(alias.toLowerCase(Locale.ROOT), command);
            DISABLED_ADDON_COMMAND_NAMES.remove(alias.toLowerCase(Locale.ROOT));
            registeredNames.add(alias.toLowerCase(Locale.ROOT));
        }
        return registeredNames;
    }

    public static synchronized void registerAddonCommand(Command command, String addonId) {
        registerAddonCommandDetailed(command, addonId);
    }

    public static synchronized AddonRegistrationResult registerAddonCommandDetailed(Command command, String addonId) {
        if (command == null) return AddonRegistrationResult.rejected("command", "", "command was null");
        if (addonId == null || addonId.isBlank()) {
            return rejectAddonCommand(addonId, "", "registration outside an addon lifecycle - register from onInitialize() or onRegisterCategories()");
        }
        String name = command.name();
        if (name == null || name.isBlank()) {
            return rejectAddonCommand(addonId, "", "blank command name");
        }
        List<String> registered;
        boolean collision = BY_NAME.containsKey(name.toLowerCase(Locale.ROOT));
        if (collision) {
            String namespaced = (addonId == null || addonId.isBlank() ? "addon" : addonId) + ":" + name;
            AutismClientAddon.LOG.warn("[Commands] Command name '{}' from addon '{}' collides with an existing "
                    + "command; registering it as '{}' instead", name, addonId, namespaced);
            registered = registerWithName(command, namespaced, false);
        } else {
            registered = registerWithName(command, name, true);
        }
        ADDON_COMMAND_OBJECT_OWNERS.put(command, addonId);
        for (String registeredName : registered) {
            ADDON_COMMAND_OWNERS.put(registeredName, addonId);
        }
        String id = registered.isEmpty() ? name : registered.get(0);
        autismclient.addons.AddonManager.recordAcceptedRegistration("command", id);
        return AddonRegistrationResult.accepted("command", id);
    }

    private static AddonRegistrationResult rejectAddonCommand(String addonId, String id, String reason) {
        AutismClientAddon.LOG.warn("[Commands] Rejecting addon command '{}': {}", id, reason);
        autismclient.addons.AddonManager.recordRejectedRegistration(addonId, "command", id, reason);
        return AddonRegistrationResult.rejected("command", id, reason);
    }

    public static synchronized void unregisterAddonCommands(String addonId) {
        if (addonId == null || addonId.isBlank()) return;
        List<String> removeNames = new ArrayList<>();
        for (Map.Entry<String, String> owner : ADDON_COMMAND_OWNERS.entrySet()) {
            if (addonId.equals(owner.getValue())) removeNames.add(owner.getKey());
        }
        for (String name : removeNames) {
            BY_NAME.remove(name);
            ADDON_COMMAND_OWNERS.remove(name);
            DISABLED_ADDON_COMMAND_NAMES.add(name);
        }
        ALL.removeIf(command -> addonId.equals(ADDON_COMMAND_OBJECT_OWNERS.get(command)));
        ADDON_COMMAND_OBJECT_OWNERS.entrySet().removeIf(entry -> addonId.equals(entry.getValue()));
        if (!removeNames.isEmpty()) revision++;
    }

    public static CommandDispatcher<AutismCommandSource> dispatcher() { return DISPATCHER; }

    public static int revision() { return revision; }

    public static List<Command> all() { return Collections.unmodifiableList(ALL); }

    public static Command find(String nameOrAlias) {
        if (nameOrAlias == null) return null;
        return BY_NAME.get(nameOrAlias.trim().toLowerCase(Locale.ROOT));
    }

    public static String effectivePrefix() { return AutismCompatManager.effectiveCommandPrefix(); }

    public static boolean isAutismCommandMessage(String message) {
        if (message == null || message.isBlank()) return false;
        String trimmed = message.trim();
        String prefix = effectivePrefix();
        return !prefix.isEmpty() && trimmed.startsWith(prefix);
    }

    public static boolean commandsBlockedByPanic() {
        return PackHideState.isHardLocked();
    }

    public static boolean isBlockedPanicCommandMessage(String message) {
        return commandsBlockedByPanic() && isPanicBlockedCommandMessage(message);
    }

    private static boolean isPanicBlockedCommandMessage(String message) {
        if (message == null || message.isBlank()) return false;
        String trimmed = message.trim();
        if ("^toggleautism".equalsIgnoreCase(trimmed)) return true;

        String prefix = effectivePrefix();
        if (!prefix.isEmpty() && trimmed.startsWith(prefix)) return true;

        for (String fallback : PANIC_PREFIX_FALLBACKS) {
            if (fallback == null || fallback.isEmpty() || fallback.equals(prefix)) continue;
            if (!trimmed.startsWith(fallback)) continue;
            String body = trimmed.substring(fallback.length()).trim();
            if (body.isEmpty()) continue;
            String first = firstToken(body).toLowerCase(Locale.ROOT);
            if (BY_NAME.containsKey(first) || DISABLED_ADDON_COMMAND_NAMES.contains(first)) return true;
        }
        return false;
    }

    public static String commandBody(String message) {
        if (!isAutismCommandMessage(message)) return "";
        String trimmed = message.trim();
        int prefixLength = effectivePrefix().length();
        return trimmed.length() <= prefixLength ? "" : trimmed.substring(prefixLength).trim();
    }

    private static final ThreadLocal<Boolean> PLAIN_CHAT_BYPASS = ThreadLocal.withInitial(() -> false);

    public static boolean plainChatBypass() {
        return PLAIN_CHAT_BYPASS.get();
    }

    public static void sendPlainChat(net.minecraft.client.multiplayer.ClientPacketListener conn, String message) {
        PLAIN_CHAT_BYPASS.set(true);
        try {
            conn.sendChat(message);
        } finally {
            PLAIN_CHAT_BYPASS.remove();
        }
    }

    public static boolean dispatch(String body) {
        if (commandsBlockedByPanic()) return true;
        if (body == null) return false;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return false;
        if (DISABLED_ADDON_COMMAND_NAMES.contains(firstToken(trimmed).toLowerCase(Locale.ROOT))) {
            sendSyntaxError(trimmed, CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create());
            return true;
        }
        try {

            if (!autismclient.util.AutismLiteVariant.enabled()
                && autismclient.util.multi.MultiPovCommandRouter.route(trimmed)) return true;
            DISPATCHER.execute(trimmed, AutismCommandSource.INSTANCE);
            return true;
        } catch (CommandSyntaxException e) {
            sendSyntaxError(trimmed, e);
            return true;
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Commands] dispatch failed for '{}'", trimmed, t);
            AutismClientMessaging.sendPrefixed("§cCommand error: "
                    + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            return true;
        }
    }

    private static void sendSyntaxError(String body, CommandSyntaxException e) {
        String first = firstToken(body);
        Command command = find(first);
        String prefix = effectivePrefix();
        if (command == null) {
            AutismClientMessaging.sendPrefixed("§cUnknown AUTISM command: §f" + first);
            AutismClientMessaging.sendPrefixed("§7Use §f" + prefix + "commands §7or §f" + prefix + "help§7.");
            return;
        }

        String message = e.getMessage();
        if (message == null || message.isBlank()) message = "Incomplete or invalid command.";
        AutismClientMessaging.sendPrefixed("§c" + message);
        AutismClientMessaging.sendPrefixed("§7Use §f" + prefix + "help " + command.name() + "§7.");
    }

    private static String firstToken(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return "";
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
