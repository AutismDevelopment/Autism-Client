package autismclient.api.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import autismclient.util.macro.MacroTemplate;
import autismclient.util.macro.MacroValue;

public interface MacroExecutionContext {

    Minecraft mc();

    void runOnClientThread(Runnable r);

    <T> T callOnClientThread(Supplier<T> s);

    void waitTicks(int ticks);

    void awaitCondition(CompletableFuture<Void> future);

    void awaitCondition(AddonCondition condition);

    boolean isActive();

    void setStatus(String status);

    void sendPacket(Packet<?> packet);

    Optional<MacroValue> variable(String name);

    void setVariable(String name, MacroValue value);

    default void setVariable(String name, Object value) {
        setVariable(name, MacroValue.text(value));
    }

    void removeVariable(String name);

    Map<String, MacroValue> variables();

    MacroTemplate.Resolution resolveTemplate(String template);

    default void captureResult(String name, MacroValue value) {
        setVariable(name, value);
    }

    default void captureResults(Map<String, MacroValue> values) {
        if (values != null) values.forEach(this::setVariable);
    }

    default Optional<Integer> resolveInt(String template) {
        MacroTemplate.Resolution resolved = resolveTemplate(template);
        if (!resolved.success()) return Optional.empty();
        try { return Optional.of(Integer.parseInt(resolved.value().trim())); }
        catch (NumberFormatException ignored) { return Optional.empty(); }
    }

    default Optional<Double> resolveDouble(String template) {
        MacroTemplate.Resolution resolved = resolveTemplate(template);
        if (!resolved.success()) return Optional.empty();
        try { return Optional.of(Double.parseDouble(resolved.value().trim())); }
        catch (NumberFormatException ignored) { return Optional.empty(); }
    }

    default <E extends Enum<E>> Optional<E> resolveEnum(String template, Class<E> type) {
        if (type == null) return Optional.empty();
        MacroTemplate.Resolution resolved = resolveTemplate(template);
        if (!resolved.success()) return Optional.empty();
        for (E value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(resolved.value().trim())) return Optional.of(value);
        }
        return Optional.empty();
    }
}
