package autismclient.gui.screen;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismColors;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismThemeTextures;
import autismclient.util.AutismLinks;
import autismclient.util.AutismPerf;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.modules.PackHideState;
import autismclient.util.AutismUiScale;
import autismclient.util.AutismWindowBranding;
import com.mojang.authlib.minecraft.BanDetails;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class AutismTitleScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/autism_client_logo.png");
    private static final Identifier BUTTON_CLICK_SOUND_ID = Identifier.fromNamespaceAndPath("autismclient", "gui.main_menu_click");
    private static final SoundEvent BUTTON_CLICK_SOUND = SoundEvent.createVariableRangeEvent(BUTTON_CLICK_SOUND_ID);
    private static final int LOGO_TEXTURE_WIDTH = 516;
    private static final int LOGO_TEXTURE_HEIGHT = 144;
    private static final int LOGO_MAX_WIDTH = 320;
    private static final int LOGO_MAX_HEIGHT = 72;
    private static final Identifier TEXT_SINGLEPLAYER = buttonText("singleplayer");
    private static final Identifier TEXT_MULTIPLAYER = buttonText("multiplayer");
    private static final Identifier TEXT_REALMS = buttonText("minecraft_realms");
    private static final Identifier TEXT_OPTIONS = buttonText("options");
    private static final Identifier TEXT_QUIT = buttonText("quit_game");

    private static final Identifier ESSENTIAL_ICON = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/icons/essential.png");
    private static final Identifier MODMENU_ICON = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/icons/modmenu.png");
    private static final Identifier REPLAYMOD_ICON = Identifier.fromNamespaceAndPath("replaymod", "logo_button.png");
    private static final Identifier FLASHBACK_ICON = Identifier.fromNamespaceAndPath("flashback", "icon.png");
    private static final Identifier DISCORD_SUPPORT_ICON = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/icons/discord.png");
    private static final Identifier DONATE_SUPPORT_ICON = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/icons/donate.png");
    private static final int SUPPORT_ICON_WIDTH = 32;
    private static final int SUPPORT_ICON_HEIGHT = 32;
    private static final int SUPPORT_ICON_DRAW_SIZE = 32;

    private static final Identifier LANGUAGE_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "icon/language");
    private static final Identifier ACCESSIBILITY_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "icon/accessibility");
    private static final int VANILLA_SPRITE_SIZE = 15;
    private static final int ICON_TEXTURE_SIZE = 32;
    private static final int REPLAYMOD_ICON_TEXTURE_SIZE = 164;
    private static final int FLASHBACK_ICON_TEXTURE_SIZE = 128;
    private static final Component TITLE = Component.translatable("narrator.screen.title");

    private final CompactTheme theme = new CompactTheme();
    private final List<MenuButton> buttons = new ArrayList<>();
    private final String modCountText = createModCountText();
    private final boolean modMenuLoaded = FabricLoader.getInstance().isModLoaded("modmenu");
    private final boolean essentialLoaded = FabricLoader.getInstance().isModLoaded("essential");
    private final boolean replayModLoaded = FabricLoader.getInstance().isModLoaded("replaymod");
    private final boolean flashbackLoaded = FabricLoader.getInstance().isModLoaded("flashback");
    private List<MeteorCreditLine> meteorCredits = List.of();
    private boolean meteorCreditsLoadFailed;
    private int cachedServerCount = -1;
    private long serverCountCheckedAt;
    private boolean layoutDirty = true;
    private int layoutScreenW = -1;
    private int layoutScreenH = -1;
    private PanelMetrics panelMetrics = new PanelMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private EssentialPanelMetrics essentialPanelMetrics = EssentialPanelMetrics.hidden();
    private Model.Simple widePlayerModel;
    private Model.Simple slimPlayerModel;
    private boolean essentialPreviewDragging;
    private float essentialPreviewRotationX = -5.0F;
    private float essentialPreviewRotationY = 30.0F;
    private double essentialPreviewAutoRotationStartTime = Blaze3D.getTime();
    private String essentialSkinLookupKey = "";
    private Supplier<PlayerSkinRenderCache.RenderInfo> essentialSkinLookup;
    private CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>> essentialSkinFuture = CompletableFuture.completedFuture(Optional.empty());
    private PlayerSkin essentialFallbackSkin;

    private static final Component COPYRIGHT_TEXT = Component.translatable("title.credits");
    private static final java.util.Random SPLASH_RANDOM = new java.util.Random();

    private static List<String> vanillaSplashPool;
    private final LogoRenderer vanillaLogo = new LogoRenderer(false);
    private SplashRenderer vanillaSplash;
    private boolean vanillaSplashChosen;

    public AutismTitleScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {

        if (!autismclient.util.AutismMenuPrefs.customMainMenuEnabled() && this.minecraft != null) {
            this.minecraft.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            return;
        }

        this.ensurePlayerModels();
        this.layoutDirty = true;

        this.cachedServerCount = -1;
    }

    private void ensurePlayerModels() {
        if (this.widePlayerModel != null && this.slimPlayerModel != null) return;
        if (this.minecraft == null) return;
        try {
            net.minecraft.client.model.geom.EntityModelSet models = this.minecraft.getEntityModels();
            this.widePlayerModel = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), AutismRenderTypes::skinPreview);
            this.slimPlayerModel = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER_SLIM), AutismRenderTypes::skinPreview);
        } catch (Exception ignored) {

            this.widePlayerModel = null;
            this.slimPlayerModel = null;
        }
    }

    private void initVanillaSkin() {
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

        int rowY = realmsY + 36;
        SpriteIconButton language = this.addRenderableWidget(CommonButtons.language(20,
            b -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())), true));
        language.setPosition(this.width / 2 - 124, rowY);
        this.addRenderableWidget(Button.builder(Component.translatable("menu.options"),
            b -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false)))
            .bounds(this.width / 2 - 100, rowY, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("menu.quit"), b -> this.minecraft.stop())
            .bounds(this.width / 2 + 2, rowY, 98, 20).build());
        SpriteIconButton accessibility = this.addRenderableWidget(CommonButtons.accessibility(20,
            b -> this.minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen(this, this.minecraft.options)), true));
        accessibility.setPosition(this.width / 2 + 104, rowY);

        int copyrightWidth = this.font.width(COPYRIGHT_TEXT);
        this.addRenderableWidget(new PlainTextButton(this.width - copyrightWidth - 2, this.height - 10, copyrightWidth, 10,
            COPYRIGHT_TEXT, b -> this.minecraft.gui.setScreen(new CreditsAndAttributionScreen(this)), this.font));
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
        long perf = AutismPerf.begin();
        this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);

        float uiMouseX = (float) AutismUiScale.toVirtual(mouseX);
        float uiMouseY = (float) AutismUiScale.toVirtual(mouseY);
        layout();

        AutismUiScale.pushOverlayScale(graphics);
        try {
            renderAccountProxyInfo(graphics);
            renderDonationHeader(graphics);
            renderMeteorCredits(graphics);
            renderCommandPanel(graphics, uiMouseX, uiMouseY, delta);
            renderEssentialPanel(graphics, uiMouseX, uiMouseY, delta);
            Component hoveredTooltip = null;
            for (MenuButton button : buttons) {
                button.render(graphics, uiMouseX, uiMouseY, delta);
                if (button.contains(uiMouseX, uiMouseY)) {
                    graphics.requestCursor(button.enabled ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
                    if (button.tooltip != null) {
                        hoveredTooltip = button.tooltip;
                    }
                }
            }
            if (hoveredTooltip != null) {
                renderCustomTooltip(graphics, hoveredTooltip, uiMouseX, uiMouseY);
            }
            renderModCount(graphics);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
            AutismPerf.end("title.render", perf);
        }
    }

    private void renderVanillaSkin(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
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
        graphics.text(this.font, version, 2, this.height - 10, AutismColors.accent());
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;

        float uiMouseX = (float) AutismUiScale.toVirtual(event.x());
        float uiMouseY = (float) AutismUiScale.toVirtual(event.y());
        layout();

        if (essentialPanelMetrics.previewContains(uiMouseX, uiMouseY)) {
            essentialPreviewRotationY = currentEssentialPreviewRotationY();
            essentialPreviewAutoRotationStartTime = Blaze3D.getTime();
            essentialPreviewDragging = true;
            return true;
        }

        for (MenuButton button : buttons) {
            if (button.click(uiMouseX, uiMouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (essentialPreviewDragging) {
            essentialPreviewAutoRotationStartTime = Blaze3D.getTime();
            essentialPreviewDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (essentialPreviewDragging) {
            essentialPreviewRotationX = Math.max(-50.0F, Math.min(50.0F, essentialPreviewRotationX - (float) AutismUiScale.toVirtual(dy) * 2.5F));
            essentialPreviewRotationY += (float) AutismUiScale.toVirtual(dx) * 2.5F;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public void removed() {
    }

    private void layout() {
        int screenW = AutismUiScale.getVirtualScreenWidth();
        int screenH = AutismUiScale.getVirtualScreenHeight();
        if (!layoutDirty && screenW == layoutScreenW && screenH == layoutScreenH) {
            return;
        }
        layoutDirty = false;
        layoutScreenW = screenW;
        layoutScreenH = screenH;
        int panelW = Math.max(1, Math.min(356, Math.max(1, screenW - 24)));
        int pad = screenH < 360 ? 8 : 12;
        int rowH = screenH < 360 ? 18 : 21;
        int gap = screenH < 360 ? 4 : 5;
        int heroH = screenH < 360 ? 58 : 76;
        int rowsH = rowH * 5 + gap * 4;
        int panelH = Math.max(1, Math.min(Math.max(1, screenH - 18), pad * 2 + heroH + 14 + rowsH));
        int panelX = (screenW - panelW) / 2;
        int panelY = Math.max(6, (screenH - panelH) / 2);
        int heroY = panelY + pad;
        int rowTop = heroY + heroH + 14;
        int rowW = Math.min(panelW - pad * 2, screenH < 360 ? 214 : 236);
        int rowX = panelX + (panelW - rowW) / 2;
        this.panelMetrics = new PanelMetrics(panelX, panelY, panelW, panelH, pad, 0, heroY, heroH, rowTop, rowH, 0, 0);

        Component disabledReason = multiplayerDisabledReason();
        boolean multiplayerAllowed = disabledReason == null;

        buttons.clear();
        buttons.add(new MenuButton(rowX, rowTop, rowW, rowH, Component.translatable("menu.singleplayer"), true,
            () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))).asMainRow(1));
        buttons.get(buttons.size() - 1).withLabelTexture(TEXT_SINGLEPLAYER, 248, 24, 124, 12);
        buttons.add(new MenuButton(rowX, rowTop + rowH + gap, rowW, rowH, Component.translatable("menu.multiplayer"), multiplayerAllowed,
            () -> {
                Screen next = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
                this.minecraft.gui.setScreen(next);
            }).withTooltip(disabledReason).withRightBadge(serverCountBadge()).asMainRow(2));
        buttons.get(buttons.size() - 1).withLabelTexture(TEXT_MULTIPLAYER, 222, 24, 111, 12);
        buttons.add(new MenuButton(rowX, rowTop + (rowH + gap) * 2, rowW, rowH, Component.translatable("menu.online"), multiplayerAllowed,
            () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this))).withTooltip(disabledReason).asMainRow(3));
        buttons.get(buttons.size() - 1).withLabelTexture(TEXT_REALMS, 136, 24, 68, 12);
        buttons.add(new MenuButton(rowX, rowTop + (rowH + gap) * 3, rowW, rowH, Component.translatable("menu.options"), true,
            () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false))).asMainRow(4));
        buttons.get(buttons.size() - 1).withLabelTexture(TEXT_OPTIONS, 138, 24, 69, 12);
        int quitY = rowTop + (rowH + gap) * 4;
        buttons.add(new MenuButton(rowX, quitY, rowW, rowH, Component.translatable("menu.quit"), true,
            () -> this.minecraft.stop()).asMainRow(5));
        buttons.get(buttons.size() - 1).withLabelTexture(TEXT_QUIT, 74, 24, 37, 12);

        layoutUtilityButtons(rowX, rowW, quitY, rowH);

        layoutSupportButtons();

        if (essentialLoaded) {
            layoutEssentialPanel(panelX, panelY, panelH);
            layoutEssentialButtons();
        } else {
            essentialPanelMetrics = EssentialPanelMetrics.hidden();
        }
    }

    private void layoutEssentialPanel(int centerPanelX, int centerPanelY, int centerPanelH) {
        int screenW = AutismUiScale.getVirtualScreenWidth();
        int screenH = AutismUiScale.getVirtualScreenHeight();
        int margin = 6;
        int availableW = centerPanelX - margin * 2;
        if (availableW < 92 || screenW < 460 || screenH < 156) {
            int compactW = availableW >= 76 ? Math.min(108, availableW) : 0;
            int compactH = 18;
            int compactX = margin;
            int compactY = Math.max(6, centerPanelY + (centerPanelH - compactH) / 2);
            essentialPanelMetrics = new EssentialPanelMetrics(compactX, compactY, compactW, compactH, 0, 0, 0, 0, true, compactW > 0);
            if (compactW > 0) {
                addEssentialButton(compactX, compactY, compactW, compactH, "Wardrobe", autismclient.util.AutismEssentialMenu::openWardrobe);
            }
            return;
        }

        int panelW = Math.min(176, Math.max(126, availableW));
        int panelH = Math.min(174, Math.max(1, screenH - 36));
        int panelX = margin;
        int panelY = Math.max(18, Math.min(screenH - panelH - 18, centerPanelY + (centerPanelH - panelH) / 2));
        int buttonH = 18;
        int buttonY = panelY + panelH - buttonH - 8;
        int buttonX = panelX + 10;
        int buttonW = panelW - 20;
        int previewX = panelX + 10;
        int previewY = panelY + 34;
        int previewW = panelW - 20;
        int previewH = Math.max(48, buttonY - previewY - 8);
        essentialPanelMetrics = new EssentialPanelMetrics(panelX, panelY, panelW, panelH, previewX, previewY, previewW, previewH, false, true);
        addEssentialButton(buttonX, buttonY, buttonW, buttonH, "Wardrobe", autismclient.util.AutismEssentialMenu::openWardrobe);
    }

    private void layoutEssentialButtons() {
        int w = 92;
        int h = 34;
        int gap = 4;
        int x = AutismUiScale.getVirtualScreenWidth() - w - 6;

        int count = 6;
        int totalH = count * h + (count - 1) * gap;
        int y = Math.max(6, (AutismUiScale.getVirtualScreenHeight() - totalH) / 2);

        addEssentialButton(x, y, w, h, "Wardrobe", autismclient.util.AutismEssentialMenu::openWardrobe);
        addEssentialButton(x, y + (h + gap), w, h, "Social", autismclient.util.AutismEssentialMenu::openSocial);
        addEssentialButton(x, y + (h + gap) * 2, w, h, "Pictures", autismclient.util.AutismEssentialMenu::openPictures);
        addEssentialButton(x, y + (h + gap) * 3, w, h, "Host", autismclient.util.AutismEssentialMenu::openHost);
        addEssentialButton(x, y + (h + gap) * 4, w, h, "Settings", autismclient.util.AutismEssentialMenu::openSettings);
        addEssentialButton(x, y + (h + gap) * 5, w, h, "Account", autismclient.util.AutismEssentialMenu::openAccount);
    }

    private void addEssentialButton(int x, int y, int w, int h, String label, Runnable action) {
        buttons.add(new MenuButton(x, y, w, h, Component.literal(label), true, action)
            .withTooltip(Component.literal("Essential: " + label)));
    }

    private void renderEssentialPanel(GuiGraphicsExtractor graphics, float mouseX, float mouseY, float delta) {
        EssentialPanelMetrics panel = essentialPanelMetrics;
        if (!essentialLoaded || !panel.visible()) return;
        if (panel.compact()) return;

        boolean previewHovered = panel.previewContains(mouseX, mouseY);
        if (previewHovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
        renderEssentialSkinPreview(graphics, panel);
    }

    private void renderEssentialSkinPreview(GuiGraphicsExtractor graphics, EssentialPanelMetrics panel) {
        this.ensurePlayerModels();
        if (widePlayerModel == null || slimPlayerModel == null || this.minecraft == null) return;
        PlayerSkin skin = currentEssentialSkin();
        Model.Simple model = skin.model().name().equalsIgnoreCase("slim") ? slimPlayerModel : widePlayerModel;
        float drawScale = AutismUiScale.getOverlayDrawScale();
        int inset = Math.max(2, panel.previewW() / 22);
        int scaledX0 = Math.round((panel.previewX() + inset) * drawScale);
        int scaledY0 = Math.round((panel.previewY() + 2) * drawScale);
        int scaledX1 = Math.round((panel.previewX() + panel.previewW() - inset) * drawScale);
        int scaledY1 = Math.round((panel.previewY() + panel.previewH() - 2) * drawScale);
        float scale = 0.94F * Math.max(1, scaledY1 - scaledY0) / 2.125F;
        graphics.skin(model, skin.body().texturePath(), scale, essentialPreviewRotationX, currentEssentialPreviewRotationY(), -1.0625F, scaledX0, scaledY0, scaledX1, scaledY1);
    }

    private float currentEssentialPreviewRotationY() {
        if (essentialPreviewDragging) return essentialPreviewRotationY;
        return essentialPreviewRotationY + (float) ((Blaze3D.getTime() - essentialPreviewAutoRotationStartTime) * 18.0D);
    }

    private PlayerSkin currentEssentialSkin() {
        UUID id = this.minecraft.getUser().getProfileId();
        String name = this.minecraft.getUser().getName();
        String key = (id == null ? "" : id.toString()) + ":" + name;
        if (!key.equals(essentialSkinLookupKey) || essentialFallbackSkin == null) {
            essentialSkinLookupKey = key;
            UUID fallbackId = id == null ? UUIDUtil.createOfflinePlayerUUID(name) : id;
            essentialFallbackSkin = DefaultPlayerSkin.get(fallbackId);
            essentialSkinLookup = null;
            essentialSkinFuture = CompletableFuture.completedFuture(Optional.empty());
            try {
                ResolvableProfile profile = id == null ? ResolvableProfile.createUnresolved(name) : ResolvableProfile.createUnresolved(id);
                PlayerSkinRenderCache.RenderInfo defaultInfo = this.minecraft.playerSkinRenderCache().getOrDefault(profile);
                essentialFallbackSkin = defaultInfo.playerSkin();
                essentialSkinLookup = this.minecraft.playerSkinRenderCache().createLookup(profile);
                essentialSkinFuture = this.minecraft.playerSkinRenderCache().lookup(profile);
            } catch (Exception ignored) {  }
        }
        try {
            if (essentialSkinLookup != null) {
                PlayerSkinRenderCache.RenderInfo info = essentialSkinLookup.get();
                if (info != null && info.playerSkin() != null) return info.playerSkin();
            }
            if (essentialSkinFuture != null) {
                Optional<PlayerSkinRenderCache.RenderInfo> info = essentialSkinFuture.getNow(Optional.empty());
                if (info.isPresent() && info.get().playerSkin() != null) return info.get().playerSkin();
            }
        } catch (Exception ignored) {  }
        return essentialFallbackSkin;
    }

    private void layoutSupportButtons() {
        int x = 4;
        int y = donationHeaderY() + UiText.fontHeight(UiAssets.FONT_LABEL) + 5;
        int h = 18;

        Component cardDonateLabel = Component.literal("Card/PayPal");
        buttons.add(new MenuButton(x, y, supportButtonWidth(cardDonateLabel), h, cardDonateLabel, true,
            () -> AutismLinks.open(AutismLinks.KOFI))
            .withLeftIcon(DONATE_SUPPORT_ICON, SUPPORT_ICON_WIDTH, SUPPORT_ICON_HEIGHT, SUPPORT_ICON_DRAW_SIZE));

        Component cryptoDonateLabel = Component.literal("Crypto");
        buttons.add(new MenuButton(x, y + h + 3, supportButtonWidth(cryptoDonateLabel), h, cryptoDonateLabel, true,
            () -> AutismLinks.open(AutismLinks.CRYPTO_DONATE))
            .withLeftIcon(DONATE_SUPPORT_ICON, SUPPORT_ICON_WIDTH, SUPPORT_ICON_HEIGHT, SUPPORT_ICON_DRAW_SIZE));

        Component incDiscordLabel = Component.literal("AUTISM INC");
        buttons.add(new MenuButton(x, y + (h + 3) * 2, supportButtonWidth(incDiscordLabel), h, incDiscordLabel, true,
            () -> AutismLinks.open(AutismLinks.AUTISM_INC_DISCORD))
            .withLeftIcon(DISCORD_SUPPORT_ICON, SUPPORT_ICON_WIDTH, SUPPORT_ICON_HEIGHT, SUPPORT_ICON_DRAW_SIZE));

        Component clientDiscordLabel = Component.literal("AUTISM Client");
        buttons.add(new MenuButton(x, y + (h + 3) * 3, supportButtonWidth(clientDiscordLabel), h, clientDiscordLabel, true,
            () -> AutismLinks.open(AutismLinks.DISCORD))
            .withLeftIcon(DISCORD_SUPPORT_ICON, SUPPORT_ICON_WIDTH, SUPPORT_ICON_HEIGHT, SUPPORT_ICON_DRAW_SIZE));
    }

    private int supportButtonWidth(Component label) {
        return UiText.width(this.font, label.getString(), UiAssets.FONT_LABEL, 0xFFFFFFFF) + compactSupportIconSize() + 11;
    }

    private int donationHeaderY() {
        int infoLineH = UiText.fontHeight(UiAssets.FONT_BODY) + 3;
        int infoLines = 1 + (AutismProxyManager.get().getEnabled() != null ? 1 : 0);
        return 4 + infoLineH * infoLines + 6;
    }

    private int compactSupportIconSize() {
        return Math.min(SUPPORT_ICON_DRAW_SIZE, 16);
    }

    private void layoutUtilityButtons(int quitX, int quitWidth, int y, int height) {

        List<UtilityButtonSpec> all = new ArrayList<>();
        if (modMenuLoaded) {
            all.add(new UtilityButtonSpec("MODS", "", MODMENU_ICON, null,
                ICON_TEXTURE_SIZE, () -> openModMenu(), Component.literal("Mod Menu")));
        }
        all.add(new UtilityButtonSpec("LANGUAGE", "", null, LANGUAGE_SPRITE,
            ICON_TEXTURE_SIZE,
            () -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())),
            Component.literal("Language")));
        all.add(new UtilityButtonSpec("MODULES", "", UiAssets.ICON_MAIN_MENU_CATEGORY, null,
            ICON_TEXTURE_SIZE,
            () -> {
                if (!PackHideState.isHardLocked()) {
                    this.minecraft.gui.setScreen(new AutismModuleScreen(this, AutismModuleScreen.Mode.TITLE_SETUP));
                }
            },
            Component.literal("Modules & Macros")));
        all.add(new UtilityButtonSpec("MATCHMAKING", "", UiAssets.ICON_MATCHMAKING, null,
            ICON_TEXTURE_SIZE,
            () -> {
                if (!PackHideState.isHardLocked()) {
                    autismclient.modules.AutismModule mod = autismclient.modules.AutismModule.get();
                    autismclient.util.AutismMatchmakingOverlay overlay = mod == null ? null : mod.getMatchmakingOverlay();
                    if (overlay != null) {
                        autismclient.util.AutismOverlayManager.get().register(overlay);
                        overlay.setMainMenuMode(true);
                        overlay.setVisible(true);
                        this.minecraft.gui.setScreen(new AutismOverlayHostScreen(overlay, this, true));
                    }
                }
            },
            Component.literal("Matchmaking")));
        all.add(new UtilityButtonSpec("PROFILES", "", UiAssets.ICON_PROFILES, null,
            ICON_TEXTURE_SIZE,
            () -> {
                if (!PackHideState.isHardLocked()) {
                    autismclient.modules.AutismModule mod = autismclient.modules.AutismModule.get();
                    autismclient.util.AutismProfilesOverlay overlay = mod == null ? null : mod.getProfilesOverlay();
                    if (overlay != null) {
                        autismclient.util.AutismOverlayManager.get().register(overlay);
                        overlay.setMainMenuMode(true);
                        overlay.setVisible(true);
                        this.minecraft.gui.setScreen(new AutismOverlayHostScreen(overlay, this, true));
                    }
                }
            },
            Component.literal("Profiles")));
        all.add(new UtilityButtonSpec("ACCESSIBILITY", "", null, ACCESSIBILITY_SPRITE,
            ICON_TEXTURE_SIZE,
            () -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)),
            Component.literal("Accessibility")));
        if (replayModLoaded) {
            all.add(new UtilityButtonSpec("REPLAYS", "", REPLAYMOD_ICON, null,
                REPLAYMOD_ICON_TEXTURE_SIZE, () -> openReplayViewer(), Component.literal("Replay Viewer")));
        }
        if (flashbackLoaded) {
            all.add(new UtilityButtonSpec("FLASHBACK", "", FLASHBACK_ICON, null,
                FLASHBACK_ICON_TEXTURE_SIZE, () -> openFlashback(), Component.literal("Flashback Replays")));
        }
        if (essentialLoaded) {
            all.add(new UtilityButtonSpec("ESSENTIAL", "", ESSENTIAL_ICON, null,
                ICON_TEXTURE_SIZE, () -> openEssential(), Component.literal("Essential")));
        }

        int leftCount = all.size() / 2;
        List<UtilityButtonSpec> left = all.subList(0, leftCount);
        List<UtilityButtonSpec> right = all.subList(leftCount, all.size());

        int buttonSize = height;
        int buttonGap = 5;

        int leadGap = 6;

        for (int i = 0; i < left.size(); i++) {
            int slotFromRight = left.size() - 1 - i;
            int cellX = quitX - leadGap - buttonSize - slotFromRight * (buttonSize + buttonGap);
            addUtilityButton(left.get(i), cellX, y, buttonSize);
        }

        int rightStartX = quitX + quitWidth + leadGap;
        for (int i = 0; i < right.size(); i++) {
            int cellX = rightStartX + i * (buttonSize + buttonGap);
            addUtilityButton(right.get(i), cellX, y, buttonSize);
        }
    }

    private void addUtilityButton(UtilityButtonSpec spec, int x, int y, int size) {
        MenuButton button = new MenuButton(x, y, size, size,
            Component.literal(spec.title()), spec.onPress() != null, spec.onPress())
            .asUtility(spec.subtitle())
            .withTooltip(spec.tooltip());
        if (spec.sprite() != null) {
            button.withSprite(spec.sprite());
        } else if (spec.icon() != null) {
            button.withIcon(spec.icon(), spec.iconTextureSize());
        }
        buttons.add(button);
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

    private void renderCommandPanel(GuiGraphicsExtractor graphics, float mouseX, float mouseY, float delta) {
        PanelMetrics p = panelMetrics;
        if (p.width() <= 0 || p.height() <= 0) return;

        renderHero(graphics, p);
    }

    private void renderHero(GuiGraphicsExtractor graphics, PanelMetrics p) {
        int x = p.x() + p.pad();
        int y = p.heroY();
        int w = p.width() - p.pad() * 2;
        int h = p.heroHeight();
        int logoMaxW = Math.min(LOGO_MAX_WIDTH, w - 12);
        int logoMaxH = Math.min(LOGO_MAX_HEIGHT, h);
        float scale = Math.min(logoMaxW / (float) LOGO_TEXTURE_WIDTH, logoMaxH / (float) LOGO_TEXTURE_HEIGHT);
        int drawW = Math.max(120, Math.round(LOGO_TEXTURE_WIDTH * scale));
        int drawH = Math.max(34, Math.round(LOGO_TEXTURE_HEIGHT * scale));
        int logoX = UiSizing.centerInside(x, w, drawW);
        int logoY = UiSizing.centerInside(y, h, drawH);
        graphics.blit(RenderPipelines.GUI_TEXTURED, AutismThemeTextures.recolored(LOGO, Channel.ACCENT), logoX, logoY, 0.0F, 0.0F, drawW, drawH,
            LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, ARGB.white(1.0F));
    }

    private void drawHudText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, Identifier fontId) {
        UiText.draw(graphics, this.font, text == null ? "" : text, fontId, color, x, y, false);
    }

    private void renderModCount(GuiGraphicsExtractor graphics) {
        Identifier fontId = UiAssets.FONT_BODY;
        int y = AutismUiScale.getVirtualScreenHeight() - UiText.fontHeight(fontId) - 3;
        UiText.draw(graphics, this.font, modCountText, fontId, theme.color(UiTone.MUTED), 4, y, false);
    }

    private String fitText(String text, int maxWidth, Identifier fontId, int color) {
        if (text == null) return "";
        if (UiText.width(this.font, text, fontId, color) <= maxWidth) return text;
        String trimmed = text;
        while (trimmed.length() > 1 && UiText.width(this.font, trimmed + ".", fontId, color) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ".";
    }

    private void renderAccountProxyInfo(GuiGraphicsExtractor graphics) {
        Identifier fontId = UiAssets.FONT_BODY;
        int x = 4;
        int y = 4;
        int white = theme.color(UiTone.BODY);
        int muted = theme.color(UiTone.MUTED);
        String prefix = "Logged in as ";
        String username = this.minecraft.getUser().getName();

        UiText.draw(graphics, this.font, prefix, fontId, white, x, y, false);
        int prefixWidth = UiText.width(this.font, prefix, fontId, white);
        UiText.draw(graphics, this.font, username, fontId, muted, x + prefixWidth, y, false);

        AutismProxy proxy = AutismProxyManager.get().getEnabled();
        if (proxy == null) return;

        y += UiText.fontHeight(fontId) + 3;
        String left = "Using proxy ";
        String right = (proxy.name != null && !proxy.name.isEmpty() ? "(" + proxy.name + ") " : "") + proxy.address + ":" + proxy.port;
        UiText.draw(graphics, this.font, left, fontId, white, x, y, false);
        int leftWidth = UiText.width(this.font, left, fontId, white);
        UiText.draw(graphics, this.font, right, fontId, muted, x + leftWidth, y, false);
    }

    private void renderDonationHeader(GuiGraphicsExtractor graphics) {
        UiText.draw(graphics, this.font, "DONATE:", UiAssets.FONT_LABEL, AutismTheme.recolor(0xFFFFDADA, Channel.ACCENT), 4, donationHeaderY(), false);
    }

    private void renderMeteorCredits(GuiGraphicsExtractor graphics) {
        List<MeteorCreditLine> credits = getMeteorCredits();
        if (credits.isEmpty()) return;

        int screenW = AutismUiScale.getVirtualScreenWidth();
        int y = 3;
        int lineGap = UiText.fontHeight(UiAssets.FONT_BODY) + 2;
        for (MeteorCreditLine credit : credits) {
            int x = screenW - 3 - credit.width(this.font);
            for (MeteorCreditSegment segment : credit.segments()) {
                if (!segment.text().isEmpty()) {
                    UiText.draw(graphics, this.font, segment.text(), UiAssets.FONT_BODY, segment.color(), x, y, false);
                    x += UiText.width(this.font, segment.text(), UiAssets.FONT_BODY, segment.color());
                }
            }
            y += lineGap;
        }
    }

    private List<MeteorCreditLine> getMeteorCredits() {
        if (!meteorCredits.isEmpty() || meteorCreditsLoadFailed) return meteorCredits;
        if (!FabricLoader.getInstance().isModLoaded("meteor-client")) return meteorCredits;

        try {
            Class<?> addonManagerClass = Class.forName("meteordevelopment.meteorclient.addons.AddonManager");
            Field addonsField = addonManagerClass.getField("ADDONS");
            Object value = addonsField.get(null);
            if (!(value instanceof Iterable<?> addons)) return meteorCredits;

            List<MeteorCreditLine> loaded = new ArrayList<>();
            for (Object addon : addons) {
                MeteorCreditLine line = meteorCreditLine(addon);
                if (line != null) loaded.add(line);
            }
            meteorCredits = List.copyOf(loaded);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            meteorCreditsLoadFailed = true;
        }
        return meteorCredits;
    }

    private static MeteorCreditLine meteorCreditLine(Object addon) throws ReflectiveOperationException {
        if (addon == null) return null;
        Class<?> addonClass = addon.getClass();
        String name = stringField(addonClass, addon, "name");
        String[] authors = authorsField(addonClass, addon);
        if (name == null || name.isBlank() || authors.length == 0) return null;

        int addonColor = addonColor(addonClass, addon);
        List<MeteorCreditSegment> segments = new ArrayList<>();
        segments.add(new MeteorCreditSegment(name, addonColor));
        segments.add(new MeteorCreditSegment(" by ", 0xFFAAAAAA));
        for (int i = 0; i < authors.length; i++) {
            if (i > 0) segments.add(new MeteorCreditSegment(i == authors.length - 1 ? " & " : ", ", 0xFFAAAAAA));
            segments.add(new MeteorCreditSegment(authors[i], 0xFFFFFFFF));
        }
        return new MeteorCreditLine(List.copyOf(segments));
    }

    private static String stringField(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
        Object value = type.getField(name).get(instance);
        return value instanceof String text ? text : null;
    }

    private static String[] authorsField(Class<?> type, Object instance) throws ReflectiveOperationException {
        Object value = type.getField("authors").get(instance);
        if (!(value instanceof String[] authors)) return new String[0];
        return authors;
    }

    private static int addonColor(Class<?> type, Object instance) throws ReflectiveOperationException {
        Object color = type.getField("color").get(instance);
        if (color == null) return 0xFFFFFFFF;
        Method getPacked = color.getClass().getMethod("getPacked");
        Object packed = getPacked.invoke(color);
        return packed instanceof Integer intColor ? intColor : 0xFFFFFFFF;
    }

    private void openModMenu() {
        try {
            Class<?> apiClass = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
            Method createMethod = apiClass.getMethod("createModsScreen", Screen.class);
            Screen modsScreen = (Screen) createMethod.invoke(null, this);
            this.minecraft.gui.setScreen(modsScreen);
        } catch (Exception e) {  }
    }

    private static Identifier buttonText(String name) {
        return Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/button_text/" + name + ".png");
    }

    private static String createModCountText() {
        int modCount = FabricLoader.getInstance().getAllMods().size();
        return modCount + (modCount == 1 ? " Mod" : " Mods");
    }

    private void openEssential() {
        try {
            Class<?> clazz = Class.forName("gg.essential.gui.modals.QuickAccessModal");
            Object companion = clazz.getDeclaredField("Companion").get(null);
            Method open = companion.getClass().getDeclaredMethod("open");
            open.setAccessible(true);
            open.invoke(companion);
        } catch (Exception e) {  }
    }

    private void openReplayViewer() {
        try {
            Class<?> replayModuleClass = Class.forName("com.replaymod.replay.ReplayModReplay");
            Field instanceField = replayModuleClass.getField("instance");
            Object replayModule = instanceField.get(null);
            if (replayModule == null) return;

            Class<?> viewerClass = Class.forName("com.replaymod.replay.gui.screen.GuiReplayViewer");
            Constructor<?> constructor = viewerClass.getConstructor(replayModuleClass);
            Object viewer = constructor.newInstance(replayModule);
            Method display = findNoArgMethod(viewerClass, "display");
            if (display == null) return;
            display.setAccessible(true);
            display.invoke(viewer);
        } catch (Exception e) {  }
    }

    private void openFlashback() {
        try {

            Class<?> screenClass = Class.forName("com.moulberry.flashback.screen.select_replay.SelectReplayScreen");
            Constructor<?> constructor = screenClass.getConstructor(Screen.class);
            Object screen = constructor.newInstance(this);
            if (screen instanceof Screen s) this.minecraft.gui.setScreen(s);
        } catch (Exception e) {  }
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void renderCustomTooltip(GuiGraphicsExtractor graphics, Component tooltip, float uiMouseX, float uiMouseY) {
        autismclient.gui.vanillaui.components.Tooltip.render(
            UiContexts.overlay(graphics, this.font, Math.round(uiMouseX), Math.round(uiMouseY)),
            tooltip.getString(), Math.round(uiMouseX), Math.round(uiMouseY), 220);
    }

    private String serverCountBadge() {
        int count = savedServerCount();
        if (count < 0) return null;
        return Integer.toString(count);
    }

    private int savedServerCount() {
        long now = System.currentTimeMillis();
        if (cachedServerCount >= 0 && now - serverCountCheckedAt < 2000L) return cachedServerCount;
        serverCountCheckedAt = now;
        try {
            ServerList serverList = new ServerList(this.minecraft);
            serverList.load();
            cachedServerCount = serverList.size();
        } catch (RuntimeException ignored) {
            cachedServerCount = -1;
        }
        return cachedServerCount;
    }

    private record PanelMetrics(int x, int y, int width, int height, int pad, int topHeight, int heroY, int heroHeight,
                                int rowTop, int rowHeight, int dataY, int utilityY) {
    }

    private record EssentialPanelMetrics(int x, int y, int width, int height,
                                         int previewX, int previewY, int previewW, int previewH,
                                         boolean compact, boolean visible) {
        private static EssentialPanelMetrics hidden() {
            return new EssentialPanelMetrics(0, 0, 0, 0, 0, 0, 0, 0, false, false);
        }

        private boolean previewContains(float mouseX, float mouseY) {
            return visible && !compact && previewW > 0 && previewH > 0
                && mouseX >= previewX && mouseY >= previewY
                && mouseX < previewX + previewW && mouseY < previewY + previewH;
        }
    }

    private record UtilityButtonSpec(String title, String subtitle, Identifier icon, Identifier sprite,
                                     int iconTextureSize, Runnable onPress, Component tooltip) {
    }

    private record MeteorCreditLine(List<MeteorCreditSegment> segments) {
        private int width(net.minecraft.client.gui.Font font) {
            int width = 0;
            for (MeteorCreditSegment segment : segments) {
                width += UiText.width(font, segment.text(), UiAssets.FONT_BODY, segment.color());
            }
            return width;
        }
    }

    private record MeteorCreditSegment(String text, int color) {
    }

    private final class MenuButton {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final Component label;
        private final boolean enabled;
        private final Runnable onPress;
        private Identifier icon;
        private int iconTextureSize = ICON_TEXTURE_SIZE;
        private Identifier iconSprite;
        private Identifier leftIcon;
        private int leftIconTextureWidth = ICON_TEXTURE_SIZE;
        private int leftIconTextureHeight = ICON_TEXTURE_SIZE;
        private int leftIconDrawSize = 14;
        private Identifier labelTexture;
        private int labelTextureWidth;
        private int labelTextureHeight;
        private int labelDrawWidth;
        private int labelDrawHeight;
        private Component tooltip;
        private String rightBadge;
        private int rowIndex = -1;
        private boolean utilityCell;
        private String utilitySubtitle = "";
        private MenuButton(int x, int y, int width, int height, Component label, boolean enabled, Runnable onPress) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label;
            this.enabled = enabled;
            this.onPress = onPress;
        }

        private MenuButton withTooltip(Component tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        private MenuButton withRightBadge(String rightBadge) {
            this.rightBadge = rightBadge;
            return this;
        }

        private MenuButton withIcon(Identifier icon) {
            return withIcon(icon, ICON_TEXTURE_SIZE);
        }

        private MenuButton withIcon(Identifier icon, int iconTextureSize) {
            this.icon = icon;
            this.iconTextureSize = Math.max(1, iconTextureSize);
            return this;
        }

        private MenuButton withSprite(Identifier sprite) {
            this.iconSprite = sprite;
            return this;
        }

        private MenuButton withLeftIcon(Identifier icon, int textureWidth, int textureHeight, int drawSize) {
            this.leftIcon = icon;
            this.leftIconTextureWidth = Math.max(1, textureWidth);
            this.leftIconTextureHeight = Math.max(1, textureHeight);
            this.leftIconDrawSize = Math.max(1, drawSize);
            return this;
        }

        private MenuButton withLabelTexture(Identifier labelTexture, int labelTextureWidth, int labelTextureHeight, int labelDrawWidth, int labelDrawHeight) {
            this.labelTexture = labelTexture;
            this.labelTextureWidth = labelTextureWidth;
            this.labelTextureHeight = labelTextureHeight;
            this.labelDrawWidth = labelDrawWidth;
            this.labelDrawHeight = labelDrawHeight;
            return this;
        }

        private MenuButton asMainRow(int rowIndex) {
            this.rowIndex = rowIndex;
            return this;
        }

        private MenuButton asUtility(String subtitle) {
            this.utilityCell = true;
            this.utilitySubtitle = subtitle == null ? "" : subtitle;
            return this;
        }

        private boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }

        private boolean click(float mouseX, float mouseY) {
            if (!contains(mouseX, mouseY)) return false;
            if (!enabled) return true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(BUTTON_CLICK_SOUND, 1.0F, 0.7F));
            if (onPress != null) onPress.run();
            return true;
        }

        private void render(GuiGraphicsExtractor graphics, float mouseX, float mouseY, float delta) {
            if (utilityCell) {
                renderUtility(graphics, mouseX, mouseY);
                return;
            }
            renderMainRow(graphics, mouseX, mouseY);
        }

        private void renderMainRow(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
            boolean hovered = enabled && contains(mouseX, mouseY);

            int border = AutismTheme.recolor(enabled ? 0x99662C2C : 0x55452A2A, Channel.OUTLINE);
            UiRenderer.frame(graphics, UiBounds.of(x, y, width, height), AutismTheme.recolor(enabled ? 0xB80A0606 : 0x90501010, Channel.BUTTON), border);
            float hoverT = autismclient.gui.vanillaui.HoverFades.get(
                autismclient.gui.vanillaui.HoverFades.key(UiBounds.of(x, y, width, height)), hovered);
            if (hoverT > 0.001f) {
                UiRenderer.rect(graphics, UiBounds.of(x + 1, y + 1, width - 2, height - 2),
                    (((int) Math.round(0x14 * hoverT)) << 24) | 0x00FFFFFF);
            }

            String marker = rightBadge != null && !rightBadge.isBlank() ? rightBadge : null;
            if (labelTexture != null && labelTextureWidth > 0 && labelTextureHeight > 0 && labelDrawWidth > 0 && labelDrawHeight > 0) {
                int maxW = Math.max(1, width - 8);
                int maxH = Math.max(1, height <= 18 ? height - 4 : height - 8);
                float scale = Math.min(1.0f, Math.min(maxW / (float) labelDrawWidth, maxH / (float) labelDrawHeight));
                int drawW = Math.max(1, Math.round(labelDrawWidth * scale));
                int drawH = Math.max(1, Math.round(labelDrawHeight * scale));
                int textX = x + (width - drawW) / 2;
                int textY = y + (height - drawH) / 2;
                int textColor = AutismTheme.recolor(enabled ? 0xFFF7EEEE : 0xFF806565, Channel.TEXT);
                graphics.blit(RenderPipelines.GUI_TEXTURED, labelTexture, textX, textY, 0.0F, 0.0F, drawW, drawH,
                    labelTextureWidth, labelTextureHeight, labelTextureWidth, labelTextureHeight, textColor);
            } else if (label != null) {
                String text = label.getString();
                int textColor = AutismTheme.recolor(enabled ? 0xFFF7EEEE : 0xFF806565, Channel.TEXT);
                Identifier fontId = UiAssets.FONT_LABEL;
                int textY = UiSizing.alignTextY(y, height, UiText.fontHeight(fontId), 1);
                int textX;
                String drawText = text;
                if (leftIcon != null) {
                    int iconSize = Math.min(leftIconDrawSize, Math.max(1, Math.min(height - 2, width - 12)));
                    int iconX = x + 3;
                    int iconY = y + (height - iconSize) / 2;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, AutismThemeTextures.whitened(leftIcon), iconX, iconY, 0.0F, 0.0F, iconSize, iconSize,
                        leftIconTextureWidth, leftIconTextureHeight, leftIconTextureWidth, leftIconTextureHeight,
                        AutismTheme.recolor(enabled ? 0xFFF7EEEE : 0xFF806565, Channel.TEXT));
                    textX = iconX + iconSize + 4;
                    int maxTextW = Math.max(1, x + width - 4 - textX);
                    drawText = UiText.trimToWidth(AutismTitleScreen.this.font, text, maxTextW, fontId, textColor);
                } else {
                    int textW = UiText.width(AutismTitleScreen.this.font, text, fontId, textColor);
                    textX = x + (width - textW) / 2;
                }
                UiText.draw(graphics, AutismTitleScreen.this.font, drawText, fontId, textColor, textX, textY, false);
            }

            if (marker != null && !marker.isBlank()) {
                Identifier fontId = UiAssets.FONT_LABEL;
                int markerColor = enabled ? AutismTheme.recolor(0xFFFFD4D4, Channel.ACCENT) : 0xFF806565;
                int markerW = UiText.width(AutismTitleScreen.this.font, marker, fontId, markerColor);
                int markerX = x + width - markerW - 12;
                int markerY = UiSizing.alignTextY(y, height, UiText.fontHeight(fontId), 1);
                drawHudText(graphics, marker, markerX, markerY, markerColor, fontId);
            }
        }

        private void renderUtility(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
            boolean hovered = enabled && contains(mouseX, mouseY);
            int border = AutismTheme.recolor(enabled ? 0x99662C2C : 0x55452A2A, Channel.OUTLINE);
            UiRenderer.frame(graphics, UiBounds.of(x, y, width, height), AutismTheme.recolor(enabled ? 0x99080505 : 0x66501010, Channel.BUTTON), border);
            float hoverT = autismclient.gui.vanillaui.HoverFades.get(
                autismclient.gui.vanillaui.HoverFades.key(UiBounds.of(x, y, width, height)), hovered);
            if (hoverT > 0.001f) {
                UiRenderer.rect(graphics, UiBounds.of(x + 1, y + 1, width - 2, height - 2),
                    (((int) Math.round(0x14 * hoverT)) << 24) | 0x00FFFFFF);
            }

            if (iconSprite != null) {

                int iconSize = Math.min(VANILLA_SPRITE_SIZE, Math.max(12, height - 6));
                int iconX = x + (width - iconSize) / 2;
                int iconY = y + (height - iconSize) / 2;
                float alpha = enabled ? 1.0f : 0.45f;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, iconSprite, iconX, iconY, iconSize, iconSize, alpha);
                return;
            }
            if (icon == null) return;
            int iconSize = Math.min(16, Math.max(12, height - 7));
            int iconX = x + (width - iconSize) / 2;
            int iconY = y + (height - iconSize) / 2;

            int iconTint = AutismTheme.recolor(enabled ? 0xFFF7EEEE : 0xFF806565, Channel.TEXT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, AutismThemeTextures.whitened(icon), iconX, iconY, 0.0F, 0.0F, iconSize, iconSize,
                iconTextureSize, iconTextureSize, iconTextureSize, iconTextureSize, iconTint);
        }

    }

}
