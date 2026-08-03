package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.commands.AutismCommands;
import autismclient.modules.PackHideState;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.direct.DirectViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

public class AutismChatField {
    private static final CompactTheme THEME = new CompactTheme();

    private final Minecraft mc;
    private final Font textRenderer;
    private final CompactTextInput field = new CompactTextInput()
        .setHorizontalPadding(3)
        .setTextYOffset(1);

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean submitOnEnter;
    private boolean multiline;
    private boolean enterInsertsNewline;
    private boolean spaceKeyInsertsSpace;
    private Predicate<String> submitHandler;
    private String placeholderText = "";

    public AutismChatField(Minecraft mc, Font textRenderer, int x, int y, int width, int height, boolean submitOnEnter) {
        this.mc = mc;
        this.textRenderer = textRenderer;
        this.x = x;
        this.y = y;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.submitOnEnter = submitOnEnter;
        field.setHistoryProvider(() -> this.mc.gui != null ? this.mc.gui.hud.getChat().getRecentChat() : java.util.List.of());
        syncBounds();
    }

    public void setSubmitOnEnter(boolean submitOnEnter) {
        this.submitOnEnter = submitOnEnter;
    }

    public void setSubmitHandler(Predicate<String> submitHandler) {
        this.submitHandler = submitHandler;
    }

    public void setChangedListener(Consumer<String> changedListener) {
        field.setOnChange(changedListener);
    }

    public void setDisplayTextProvider(Function<String, Component> displayTextProvider) {
        field.setDisplayTextProvider(displayTextProvider);
    }

    public void setDisplayTextRevisionProvider(LongSupplier revisionProvider) {
        field.setDisplayTextRevisionProvider(revisionProvider);
    }

    public void setMultiline(boolean multiline) {
        this.multiline = multiline;
        field.setMultiline(multiline);
    }

    public void setEnterInsertsNewline(boolean enterInsertsNewline) {
        this.enterInsertsNewline = enterInsertsNewline;
    }

    public void setHoverEffectsEnabled(boolean hoverEffectsEnabled) {
        field.setHoverEffectsEnabled(hoverEffectsEnabled);
    }

    public void setFocusEffectsEnabled(boolean focusEffectsEnabled) {
        field.setFocusEffectsEnabled(focusEffectsEnabled);
    }

    public void setBackgroundColorOverride(int backgroundColorOverride) {
        field.setBackgroundColorOverride(backgroundColorOverride);
    }

    public void setSpaceKeyInsertsSpace(boolean spaceKeyInsertsSpace) {
        this.spaceKeyInsertsSpace = spaceKeyInsertsSpace;
    }

    public void setHistoryNavigationEnabled(boolean historyNavigationEnabled) {
        field.setHistoryNavigationEnabled(historyNavigationEnabled);
    }

    public void setPlaceholder(Component placeholder) {
        placeholderText = placeholder == null ? "" : AutismText.sanitizeUiLabel(placeholder.getString());
        field.setPlaceholder(placeholderText);
    }

    public String getPlaceholderText() {
        return placeholderText;
    }

    public void setDrawsBackground(boolean drawsBackground) {
        field.setDrawBackground(drawsBackground);
    }

    public void setEditable(boolean editable) {
        field.setEditable(editable);
    }

    public void setFilter(Predicate<String> filter) {
        field.setFilter(filter);
    }

    public void setNumericOnly(boolean allowNegative) {
        field.setFilter(next -> {
            if (next == null || next.isEmpty()) return true;
            for (int i = 0; i < next.length(); i++) {
                char chr = next.charAt(i);
                if (allowNegative && chr == '-' && i == 0) continue;
                if (!Character.isDigit(chr)) return false;
            }
            return true;
        });
    }

    public boolean isFocused() {
        return field.isFocused();
    }

    public void setFocused(boolean focused) {
        field.setFocused(focused);
    }

    public void setMaxLength(int maxLength) {
        field.setMaxLength(maxLength);
    }

    public void setText(String text) {
        field.setText(text);
    }

    public String getText() {
        return field.text();
    }

    public void write(String content) {
        if (content == null || content.isEmpty() || !field.isFocused() || !field.isEditable()) return;
        field.insertText(content);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        syncBounds();
        if (!contains(mouseX, mouseY)) {
            field.setFocused(false);
            return false;
        }
        return field.mouseClicked(inputContext(), (float) mouseX, (float) mouseY, button);
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean ignored) {
        return mouseClicked(click.x(), click.y(), 0);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncBounds();
        return field.mouseReleased(inputContext(), (float) mouseX, (float) mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        syncBounds();
        return field.mouseDragged(inputContext(), (float) mouseX, (float) mouseY, button, (float) deltaX, (float) deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        syncBounds();
        if (!contains(mouseX, mouseY)) return false;
        return field.mouseScrolled(inputContext(), (float) mouseX, (float) mouseY, (float) amount);
    }

    public boolean keyPressed(KeyEvent keyInput) {
        if (!field.isFocused()) return false;

        int key = keyInput.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (submitOnEnter) {
                return handleSubmit();
            }
            if (multiline && enterInsertsNewline) {
                return field.keyPressed(inputContext(), key, keyInput.scancode(), keyInput.modifiers());
            }
            field.setFocused(false);
            return true;
        }
        if (spaceKeyInsertsSpace && key == GLFW.GLFW_KEY_SPACE) {
            int mods = keyInput.modifiers();
            if ((mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0) {
                return field.charTyped(inputContext(), ' ', mods);
            }
        }

        return field.keyPressed(inputContext(), key, keyInput.scancode(), keyInput.modifiers());
    }

    public boolean charTyped(CharacterEvent charInput) {
        return field.isFocused() && field.charTyped(inputContext(), (char) charInput.codepoint(), 0);
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        syncBounds();
        field.render(renderContext(context, mouseX, mouseY, delta));
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
        syncBounds();
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
        syncBounds();
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = Math.max(1, width);
        syncBounds();
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = Math.max(1, height);
        syncBounds();
    }

    public void setSelectionEnd(int selectionEnd) {
        field.setSelectionEnd(selectionEnd);
    }

    public String getSelectedText() {
        return field.selectedText();
    }

    public void insertText(String text) {
        field.insertText(text);
    }

    private void syncBounds() {
        field.setBounds(x, y, width, height);
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean handleSubmit() {
        String value = field.text().trim();
        if (value.isEmpty()) return true;
        if (PackHideState.isHardLocked()) {
            field.setText("");
            field.setFocused(false);
            return true;
        }

        boolean submitted = submitHandler != null ? submitHandler.test(value) : sendChat(value);
        if (!submitted) return true;
        if (AutismCommands.isBlockedPanicCommandMessage(value)) {
            field.setText("");
            field.setFocused(false);
            return true;
        }

        AutismClientMessaging.rememberRecentChat(value);
        field.addHistoryEntry(value);
        field.setText("");
        field.setFocused(false);
        return true;
    }

    private boolean sendChat(String message) {
        if (PackHideState.isHardLocked()) return true;
        if (AutismCommands.isAutismCommandMessage(message)) {
            if (AutismCommands.isBlockedPanicCommandMessage(message)) return true;
            String body = AutismCommands.commandBody(message);
            if (!body.isBlank()) AutismCommands.dispatch(body);
            return true;
        }
        if (mc.getConnection() == null) {
            AutismClientAddon.LOG.warn("Failed to send Autism chat message because network handler was null.");
            return false;
        }

        if (message.startsWith("/") && message.length() > 1) {
            mc.getConnection().sendCommand(message.substring(1));
        } else {
            mc.getConnection().sendChat(message);
        }
        return true;
    }

    private DirectRenderContext inputContext() {
        return new DirectRenderContext(null, textRenderer, DirectViewport.current(CompactTheme.DEFAULT_DENSITY), THEME, 0, 0, 0);
    }

    private DirectRenderContext renderContext(GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float delta) {
        return new DirectRenderContext(
            drawContext,
            textRenderer,
            DirectViewport.current(CompactTheme.DEFAULT_DENSITY),
            THEME,
            mouseX,
            mouseY,
            delta
        );
    }
}
