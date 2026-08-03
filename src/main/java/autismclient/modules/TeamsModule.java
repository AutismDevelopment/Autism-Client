package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.KeybindSetting;
import autismclient.api.module.StringListSetting;
import autismclient.util.AutismBindUtil;
import autismclient.util.AutismClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TeamsModule extends Module {

    public static final int DEFAULT_FRIENDS_COLOR = 0xCC55FFFF;

    private static TeamsModule instance;

    private String cachedFriendsSource;
    private Set<String> cachedFriendNames = Set.of();
    private boolean pickWasDown;

    public TeamsModule() {
        super("teams", "Teams", ModuleCategory.MISC, "Friends and team detection.");
        instance = this;
        add(new StringListSetting("friends", "Friends", "").playerNameList().build());
        add(new BoolSetting("auto-teams", "Auto Teams", false)
            .description("Detect teammates automatically.")
            .build());
        add(new BoolSetting("quick-add", "Quick Add", true)
            .description("Add/remove with key.")
            .build());
        add(new KeybindSetting("quick-add-bind", "Quick Add Key",
            AutismBindUtil.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE))
            .visibleWhen(() -> bool("quick-add"))
            .build());
        add(new BoolSetting("esp", "ESP", true).build());
        add(new BoolSetting("tracers", "Tracers", true).build());
        add(new BoolSetting("nametags", "Nametags", true).build());
        add(new ColorSetting("friends-color", "Friends Color", DEFAULT_FRIENDS_COLOR)
            .visibleWhen(() -> bool("esp") || bool("tracers") || bool("nametags"))
            .build());
        add(new BoolSetting("aimassist", "AimAssist", false).build());
        add(new BoolSetting("killaura", "KillAura", false).build());
        add(new BoolSetting("triggerbot", "TriggerBot", false).build());
    }

    @Override
    public void onOptionValueChanged(String settingId) {
        if ("friends".equals(settingId)) cachedFriendsSource = null;
    }

    @Override
    public void tick() {
        if (MC == null || MC.player == null || MC.level == null) return;
        int bind = integer("quick-add-bind");
        boolean down = bool("quick-add")
            && bind != -1
            && MC.gui.screen() == null
            && AutismBindUtil.isBindPressed(MC, bind);
        if (down && !pickWasDown
            && MC.hitResult instanceof EntityHitResult hit
            && hit.getEntity() instanceof Player player
            && player != MC.player) {
            toggleFriend(player.getScoreboardName());
        }
        pickWasDown = down;
    }

    private void toggleFriend(String name) {
        if (name == null || name.isBlank()) return;
        List<String> friends = new ArrayList<>(list("friends"));
        String existing = null;
        for (String friend : friends) {
            if (friend.equalsIgnoreCase(name)) {
                existing = friend;
                break;
            }
        }
        if (existing != null) {
            friends.remove(existing);
            AutismClientMessaging.sendPrefixed("Removed friend " + existing + ".");
        } else {
            friends.add(name);
            AutismClientMessaging.sendPrefixed("Added friend " + name + ".");
        }
        setValue("friends", String.join("|", friends));
    }

    public static boolean addFriend(String name) {
        TeamsModule module = instance;
        if (module == null || name == null || name.isBlank()) {
            AutismClientMessaging.sendPrefixed("§cTeams module unavailable.");
            return false;
        }
        List<String> friends = new ArrayList<>(module.list("friends"));
        for (String friend : friends) {
            if (friend.equalsIgnoreCase(name)) {
                AutismClientMessaging.sendPrefixed("§e" + friend + " §7is already a friend.");
                return false;
            }
        }
        friends.add(name);
        module.setValue("friends", String.join("|", friends));
        AutismClientMessaging.sendPrefixed("§aAdded friend §f" + name + "§a.");
        return true;
    }

    public static boolean removeFriend(String name) {
        TeamsModule module = instance;
        if (module == null || name == null || name.isBlank()) {
            AutismClientMessaging.sendPrefixed("§cTeams module unavailable.");
            return false;
        }
        List<String> friends = new ArrayList<>(module.list("friends"));
        for (String friend : friends) {
            if (friend.equalsIgnoreCase(name)) {
                friends.remove(friend);
                module.setValue("friends", String.join("|", friends));
                AutismClientMessaging.sendPrefixed("§aRemoved friend §f" + friend + "§a.");
                return true;
            }
        }
        AutismClientMessaging.sendPrefixed("§cNo friend named §f" + name + "§c.");
        return false;
    }

    public static boolean clearFriends() {
        TeamsModule module = instance;
        if (module == null) {
            AutismClientMessaging.sendPrefixed("§cTeams module unavailable.");
            return false;
        }
        int count = module.list("friends").size();
        if (count == 0) {
            AutismClientMessaging.sendPrefixed("§7Friend list is already empty.");
            return false;
        }
        module.setValue("friends", "");
        AutismClientMessaging.sendPrefixed("§aCleared §f" + count + " §afriend" + (count == 1 ? "" : "s") + ".");
        return true;
    }

    public static List<String> storedFriendNames() {
        TeamsModule module = instance;
        if (module == null) return List.of();
        return module.list("friends");
    }

    private static TeamsModule active() {
        TeamsModule module = instance;
        return module != null && module.isEnabled() ? module : null;
    }

    public static boolean isFriendOrTeam(Entity entity) {
        TeamsModule module = active();
        if (module == null || !(entity instanceof Player player) || entity == MC.player) return false;
        return module.isListedFriend(player) || module.bool("auto-teams") && module.isAutoTeammate(player);
    }

    public static boolean combatExcluded(Entity entity, String option) {
        TeamsModule module = active();
        if (module == null) return false;
        if (module.bool(option)) return false;
        return isFriendOrTeam(entity);
    }

    public static boolean visualTargetsFriends(String option) {
        TeamsModule module = active();
        return module != null && module.bool(option);
    }

    public static int friendsColor() {
        TeamsModule module = active();
        if (module == null) return DEFAULT_FRIENDS_COLOR;
        return ModuleRenderUtil.color(module, "friends-color", DEFAULT_FRIENDS_COLOR);
    }

    private boolean isListedFriend(Player player) {
        String name = player.getScoreboardName();
        if (name == null || name.isBlank()) return false;
        return friendNames().contains(name.toLowerCase(Locale.ROOT));
    }

    private Set<String> friendNames() {
        List<String> entries = list("friends");
        String source = String.join("|", entries);
        if (source.equals(cachedFriendsSource)) return cachedFriendNames;
        Set<String> names = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String name = entry.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) names.add(name);
        }
        cachedFriendsSource = source;
        cachedFriendNames = Set.copyOf(names);
        return cachedFriendNames;
    }

    private boolean isAutoTeammate(Player suspected) {
        LocalPlayer self = MC.player;
        if (self == null) return false;

        if (self.isAlliedTo(suspected)) return true;

        var ownName = self.getDisplayName();
        var theirName = suspected.getDisplayName();
        var ownColor = ownName == null ? null : ownName.getStyle().getColor();
        var theirColor = theirName == null ? null : theirName.getStyle().getColor();
        if (ownColor != null && ownColor.equals(theirColor)) return true;

        Integer ownHelmet = dyedColor(self);
        Integer theirHelmet = dyedColor(suspected);
        return ownHelmet != null && ownHelmet.equals(theirHelmet);
    }

    private static Integer dyedColor(Player player) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.HEAD);
        if (stack == null || stack.isEmpty()) return null;
        var dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed == null ? null : dyed.rgb() & 0xFFFFFF;
    }
}
