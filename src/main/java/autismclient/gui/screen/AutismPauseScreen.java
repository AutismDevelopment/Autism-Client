package autismclient.gui.screen;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonLinks;

import java.util.function.Supplier;

public class AutismPauseScreen extends Screen {
    private static final Component RETURN_TO_GAME = Component.translatable("menu.returnToGame");
    private static final Component ADVANCEMENTS = Component.translatable("gui.advancements");
    private static final Component STATS = Component.translatable("gui.stats");
    private static final Component SEND_FEEDBACK = Component.translatable("menu.sendFeedback");
    private static final Component REPORT_BUGS = Component.translatable("menu.reportBugs");
    private static final Component OPTIONS = Component.translatable("menu.options");
    private static final Component MULTIPLAYER_OPTIONS = Component.translatable("menu.multiplayerOptions.button");
    private static final Component PLAYER_REPORTING = Component.translatable("menu.playerReporting");
    private static final Component GAME = Component.translatable("menu.game");

    private FriendsButton friends;

    public AutismPauseScreen() {
        super(GAME);
    }

    @Override
    protected void init() {
        createPauseMenu();
        int textWidth = this.font.width(this.title);
        this.addRenderableWidget(new StringWidget(this.width / 2 - textWidth / 2, 40, textWidth, 9, this.title, this.font));
    }

    private void createPauseMenu() {
        GridLayout gridLayout = new GridLayout();
        gridLayout.defaultCellSetting().padding(4, 4, 4, 0);
        GridLayout.RowHelper helper = gridLayout.createRowHelper(2);
        helper.addChild(Button.builder(RETURN_TO_GAME, b -> {
            this.minecraft.gui.setScreen(null);
            this.minecraft.mouseHandler.grabMouse();
        }).width(204).build(), 2, gridLayout.newCellSettings().paddingTop(50));
        helper.addChild(openScreenButton(ADVANCEMENTS, () -> new AdvancementsScreen(this.minecraft.player.connection.getAdvancements(), this)));
        helper.addChild(openScreenButton(STATS, () -> new StatsScreen(this, this.minecraft.player.getStats())));

        LinearLayout iconButtonRow = LinearLayout.horizontal().spacing(4);
        SpriteIconButton reportBugsButton = SpriteIconButton.builder(REPORT_BUGS, ConfirmLinkScreen.confirmLink(this, CommonLinks.SNAPSHOT_BUGS_FEEDBACK), true)
            .width(20)
            .sprite(Identifier.withDefaultNamespace("pause_menu/bug"), 15, 15)
            .withTootip()
            .build();
        reportBugsButton.active = !SharedConstants.getCurrentVersion().dataVersion().isSideSeries();
        iconButtonRow.addChild(reportBugsButton);
        SpriteIconButton feedbackButton = SpriteIconButton.builder(
                SEND_FEEDBACK,
                ConfirmLinkScreen.confirmLink(this, SharedConstants.getCurrentVersion().stable() ? CommonLinks.RELEASE_FEEDBACK : CommonLinks.SNAPSHOT_FEEDBACK),
                true
            )
            .width(20)
            .sprite(Identifier.withDefaultNamespace("pause_menu/social_interactions"), 15, 15)
            .withTootip()
            .build();
        iconButtonRow.addChild(feedbackButton);
        this.friends = CommonButtons.friends(
            20,
            b -> OnlineOptionsScreen.confirmFriendsListEnabled(this.minecraft, () -> this.minecraft.gui.setScreen(new FriendsOverlayScreen(this)), this),
            !this.minecraft.isDemo()
        );
        iconButtonRow.addChild(this.friends);
        SpriteIconButton playerReportingButton = SpriteIconButton.builder(PLAYER_REPORTING, b -> this.minecraft.gui.setScreen(new SocialInteractionsScreen(this)), true)
            .width(20)
            .sprite(Identifier.withDefaultNamespace("pause_menu/player_reporting"), 15, 15)
            .withTootip()
            .build();
        iconButtonRow.addChild(playerReportingButton);
        helper.addChild(iconButtonRow, 2, gridLayout.newCellSettings().alignHorizontallyCenter());

        if (this.minecraft.hasSingleplayerServer()) {
            helper.addChild(openScreenButton(OPTIONS, () -> new OptionsScreen(this, this.minecraft.options, true)));
            helper.addChild(openScreenButton(MULTIPLAYER_OPTIONS, () -> new MultiplayerOptionsScreen(this)));
        } else {
            helper.addChild(Button.builder(OPTIONS, b -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, true))).width(204).build(), 2);
        }

        helper.addChild(Button.builder(CommonComponents.disconnectButtonLabel(this.minecraft.isLocalServer()), b -> {
            b.active = false;
            this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }).width(204).build(), 2);

        gridLayout.arrangeElements();
        FrameLayout.alignInRectangle(gridLayout, 0, 0, this.width, this.height, 0.5F, 0.25F);
        gridLayout.visitWidgets(this::addRenderableWidget);
    }

    private Button openScreenButton(Component message, Supplier<Screen> newScreen) {
        return Button.builder(message, b -> this.minecraft.gui.setScreen(newScreen.get())).width(98).build();
    }

    @Override
    public void tick() {
        if (this.friends != null) this.friends.refreshIncomingRequestCount();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
        this.minecraft.mouseHandler.grabMouse();
    }
}
