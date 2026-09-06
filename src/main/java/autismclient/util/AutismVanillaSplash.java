package autismclient.util;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class AutismVanillaSplash {
    private static final Random RANDOM = new Random();
    private static final int YELLOW = -256;
    private static List<String> pool;
    private static Component panicSplash;

    private AutismVanillaSplash() {
    }

    public static SplashRenderer pick(Minecraft client) {
        List<String> lines = pool(client);
        if (lines.isEmpty()) return null;
        String text = lines.get(RANDOM.nextInt(lines.size()));
        return new SplashRenderer(Component.literal(text).setStyle(Style.EMPTY.withColor(YELLOW)));
    }

    public static List<Component> components(Minecraft client) {
        return pool(client).stream()
            .<Component>map(text -> Component.literal(text).setStyle(Style.EMPTY.withColor(YELLOW)))
            .toList();
    }

    public static void renderPanicSplash(Minecraft client, GuiGraphicsExtractor graphics, int screenWidth, Font font, float alpha) {
        Component splash = panicSplash(client);
        if (splash == null) return;

        int textWidth = font.width(splash);
        ActiveTextCollector textRenderer = graphics.textRenderer();
        float textPhase = 1.8F - Mth.abs(Mth.sin((float)(Util.getMillis() % 1000L) / 1000.0F * (float) (Math.PI * 2)) * 0.1F);
        float textScale = textPhase * 100.0F / (textWidth + 32);
        Matrix3x2f transform = new Matrix3x2f(textRenderer.defaultParameters().pose())
            .translate(screenWidth / 2.0F + 123.0F, 69.0F)
            .rotate((float) (-Math.PI / 9))
            .scale(textScale);
        ActiveTextCollector.Parameters renderParameters = textRenderer.defaultParameters().withOpacity(alpha).withPose(transform);
        textRenderer.accept(TextAlignment.LEFT, -textWidth / 2, -8, renderParameters, splash);
    }

    private static Component panicSplash(Minecraft client) {
        if (panicSplash != null) return panicSplash;
        List<String> lines = pool(client);
        if (lines.isEmpty()) return null;
        String text = lines.get(RANDOM.nextInt(lines.size()));
        panicSplash = Component.literal(text).setStyle(Style.EMPTY.withColor(YELLOW));
        return panicSplash;
    }

    private static List<String> pool(Minecraft client) {
        if (pool != null) return pool;
        List<String> lines = new ArrayList<>();
        try {
            IoSupplier<InputStream> supplier = client.getVanillaPackResources().getResource(
                PackType.CLIENT_RESOURCES, Identifier.withDefaultNamespace("texts/splashes.txt"));
            if (supplier != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(supplier.get(), StandardCharsets.UTF_8))) {
                    reader.lines().map(String::trim)
                        .filter(line -> !line.isEmpty() && line.hashCode() != 125780783)
                        .forEach(lines::add);
                }
            }
        } catch (Exception ignored) {  }
        pool = List.copyOf(lines);
        return pool;
    }
}
