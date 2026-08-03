package autismclient.gui.screen;

import com.mojang.authlib.minecraft.BanDetails;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import com.mojang.realmsclient.RealmsMainScreen;

import java.util.ArrayList;
import java.util.List;

public final class AutismPanicTitleScreen extends Screen {
    private static final Component TITLE = Component.translatable("narrator.screen.title");
    private static final Component COPYRIGHT_TEXT = Component.translatable("title.credits");
    private static final java.util.Random SPLASH_RANDOM = new java.util.Random();

    private static List<String> vanillaSplashPool;

    private final LogoRenderer vanillaLogo = new LogoRenderer(false);
    private SplashRenderer vanillaSplash;
    private boolean vanillaSplashChosen;
    private FriendsButton friends;

    public AutismPanicTitleScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        int spacing = 24;
        int topPos = this.height / 4 + 48;

        this.addRenderableWidget(Button.builder(Component.translatable("menu.singleplayer"),
            b -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)))
            .bounds(this.width / 2 - 100, topPos, 200, 20).build());

        Component disabledReason = multiplayerDisabledReason();
        boolean multiplayerAllowed = disabledReason == null;
        Tooltip tooltip = disabledReason != null ? Tooltip.create(disabledReason) : null;

        int multiplayerY = topPos + spacing;
        this.addRenderableWidget(Button.builder(Component.translatable("menu.multiplayer"), b -> {
            Screen next = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
            this.minecraft.gui.setScreen(next);
        }).bounds(this.width / 2 - 100, multiplayerY, 200, 20).tooltip(tooltip).build()).active = multiplayerAllowed;

        int realmsY = multiplayerY + spacing;
        this.addRenderableWidget(Button.builder(Component.translatable("menu.online"),
            b -> this.minecraft.gui.setScreen(new RealmsMainScreen(this)))
            .bounds(this.width / 2 - 100, realmsY, 200, 20).tooltip(tooltip).build()).active = multiplayerAllowed;

        int iconRowY = realmsY + spacing;
        boolean friendsAvailable = !this.minecraft.isDemo();
        this.friends = this.addRenderableWidget(CommonButtons.friends(20,
            b -> OnlineOptionsScreen.confirmFriendsListEnabled(this.minecraft,
                () -> this.minecraft.gui.setScreen(new FriendsOverlayScreen(this)), this), friendsAvailable));
        this.friends.setPosition(horizontalPosition(1, 3, 20), iconRowY);
        SpriteIconButton language = this.addRenderableWidget(CommonButtons.language(20,
            b -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())), true));
        language.setPosition(horizontalPosition(2, 3, 20), iconRowY);
        SpriteIconButton accessibility = this.addRenderableWidget(CommonButtons.accessibility(20,
            b -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)), true));
        accessibility.setPosition(horizontalPosition(3, 3, 20), iconRowY);

        int optionsRowY = iconRowY + spacing;
        this.addRenderableWidget(Button.builder(Component.translatable("menu.options"),
            b -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false)))
            .bounds(this.width / 2 - 100, optionsRowY, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("menu.quit"), b -> this.minecraft.stop())
            .bounds(this.width / 2 + 2, optionsRowY, 98, 20).build());

        int copyrightWidth = this.font.width(COPYRIGHT_TEXT);
        this.addRenderableWidget(new PlainTextButton(this.width - copyrightWidth - 2, this.height - 10, copyrightWidth, 10,
            COPYRIGHT_TEXT, b -> this.minecraft.gui.setScreen(new CreditsAndAttributionScreen(this)), this.font));
    }

    private int horizontalPosition(int currentButton, int numberOfButtons, int buttonWidth) {
        int totalWidth = numberOfButtons * buttonWidth + (numberOfButtons - 1) * 4;
        return this.width / 2 - totalWidth / 2 + (currentButton - 1) * (buttonWidth + 4);
    }

    @Override
    public void tick() {
        if (this.friends != null) this.friends.refreshIncomingRequestCount();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
        this.vanillaLogo.extractRenderState(graphics, this.width, 1.0F);
        if (!this.vanillaSplashChosen) {
            this.vanillaSplash = pickVanillaSplash();
            this.vanillaSplashChosen = true;
        }
        if (this.vanillaSplash != null && !this.minecraft.options.hideSplashTexts().get()) {
            this.vanillaSplash.extractRenderState(graphics, this.width, this.font, 1.0F);
        }
        String version = "Minecraft " + SharedConstants.getCurrentVersion().name();
        graphics.text(this.font, version, 2, this.height - 10, ARGB.white(1.0F));
    }

    private SplashRenderer pickVanillaSplash() {
        List<String> pool = vanillaSplashPool();
        if (pool.isEmpty()) return null;
        String text = pool.get(SPLASH_RANDOM.nextInt(pool.size()));
        return new SplashRenderer(Component.literal(text).setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(-256)));
    }

    private List<String> vanillaSplashPool() {
        if (vanillaSplashPool != null) return vanillaSplashPool;
        List<String> pool = new ArrayList<>();
        try {
            net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream> supplier =
                this.minecraft.getVanillaPackResources().getResource(
                    net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                    Identifier.withDefaultNamespace("texts/splashes.txt"));
            if (supplier != null) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(supplier.get(), java.nio.charset.StandardCharsets.UTF_8))) {
                    reader.lines().map(String::trim)
                        .filter(line -> !line.isEmpty() && line.hashCode() != 125780783)
                        .forEach(pool::add);
                }
            }
        } catch (Exception ignored) {  }
        vanillaSplashPool = List.copyOf(pool);
        return vanillaSplashPool;
    }

    private Component multiplayerDisabledReason() {
        if (this.minecraft.allowsMultiplayer()) return null;
        if (this.minecraft.isNameBanned()) return Component.translatable("title.multiplayer.disabled.banned.name");

        BanDetails multiplayerBan = this.minecraft.multiplayerBan();
        if (multiplayerBan != null) {
            return multiplayerBan.expires() != null
                ? Component.translatable("title.multiplayer.disabled.banned.temporary")
                : Component.translatable("title.multiplayer.disabled.banned.permanent");
        }
        return Component.translatable("title.multiplayer.disabled");
    }
}
