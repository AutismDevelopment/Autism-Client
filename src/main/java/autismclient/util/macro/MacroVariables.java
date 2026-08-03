package autismclient.util.macro;

import net.minecraft.client.Minecraft;

public final class MacroVariables {
    private static final ThreadLocal<MacroVariableContext> FALLBACK = ThreadLocal.withInitial(MacroVariableContext::new);

    private MacroVariables() {}

    public static void clear() {
        context().clear();
    }

    public static void set(String name, Object value) {
        setValue(name, value instanceof MacroValue typed ? typed : MacroValue.text(value));
    }

    public static void setValue(String name, MacroValue value) {
        context().set(name, value);
    }

    public static void setAll(java.util.Map<String, MacroValue> values) {
        context().setAll(values);
    }

    public static void remove(String name) {
        context().remove(name);
    }

    public static String get(String name, Minecraft mc) {
        MacroTemplate.Resolution resolution = MacroTemplate.resolve("{" + name + "}", context(), mc);
        return resolution.success() ? resolution.value() : "";
    }

    public static java.util.Optional<MacroValue> value(String name) {
        return context().get(name);
    }

    public static String expand(String text, Minecraft mc) {
        MacroTemplate.Resolution resolution = resolve(text, mc);
        return resolution.success() ? resolution.value() : "";
    }

    public static MacroTemplate.Resolution resolve(String text, Minecraft mc) {
        return MacroTemplate.resolve(text, context(), mc);
    }

    public static MacroVariableContext currentContext() {
        return context();
    }

    private static MacroVariableContext context() {
        MacroVariableContext active = MacroExecutor.currentVariableContext();
        return active == null ? FALLBACK.get() : active;
    }
}
