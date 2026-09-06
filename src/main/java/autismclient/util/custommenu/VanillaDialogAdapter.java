package autismclient.util.custommenu;

import autismclient.api.custommenu.CustomMenuAdapter;
import autismclient.api.custommenu.CustomMenuButton;
import autismclient.api.custommenu.CustomMenuEvent;
import autismclient.api.custommenu.CustomMenuInput;
import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.api.custommenu.CustomMenuSubmission;
import autismclient.api.custommenu.CustomMenuSubmitResult;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.ButtonListDialog;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogListDialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.SimpleDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VanillaDialogAdapter implements CustomMenuAdapter {
    public static final String ID = "minecraft:dialog";

    private record State(Dialog dialog, List<ActionButton> actions) {}

    @Override public String id() { return ID; }

    @Override
    public boolean acceptsInbound(Packet<?> packet) {
        return packet instanceof ClientboundShowDialogPacket || packet instanceof ClientboundClearDialogPacket;
    }

    @Override
    public CustomMenuEvent inspectInbound(Packet<?> packet, String phase) {
        if (packet instanceof ClientboundClearDialogPacket) return CustomMenuEvent.CLEAR;
        if (!(packet instanceof ClientboundShowDialogPacket show) || show.dialog() == null) return CustomMenuEvent.NONE;
        return CustomMenuEvent.open(snapshot(show.dialog().value(), phase));
    }

    public static CustomMenuSnapshot snapshotOf(Dialog dialog, String phase) {
        return dialog == null ? null : snapshot(dialog, phase);
    }

    private static CustomMenuSnapshot snapshot(Dialog dialog, String phase) {
        List<CustomMenuInput> inputs = new ArrayList<>();
        int inputIndex = 1;
        for (Input input : dialog.common().inputs()) inputs.add(input(inputIndex++, input));
        List<ActionButton> actions = actions(dialog);
        List<CustomMenuButton> buttons = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) buttons.add(button(i + 1, actions.get(i)));
        return new CustomMenuSnapshot(ID, phase, 0L, dialog.common().title().getString(), inputs, buttons,
            new State(dialog, actions));
    }

    private static CustomMenuInput input(int index, Input input) {
        String key = input.key();
        if (input.control() instanceof TextInput text) {
            return new CustomMenuInput(index, key, text.label().getString(), CustomMenuInput.Kind.TEXT,
                text.initial(), text.maxLength(), 0, 0, 0, List.of());
        }
        if (input.control() instanceof BooleanInput bool) {
            return new CustomMenuInput(index, key, bool.label().getString(), CustomMenuInput.Kind.BOOLEAN,
                Boolean.toString(bool.initial()), 0, 0, 0, 0, List.of(bool.onFalse(), bool.onTrue()));
        }
        if (input.control() instanceof NumberRangeInput number) {
            NumberRangeInput.RangeInfo range = number.rangeInfo();
            float initial = range.initial().orElse((range.start() + range.end()) / 2.0F);
            return new CustomMenuInput(index, key, number.label().getString(), CustomMenuInput.Kind.NUMBER,
                numberString(initial), 0, Math.min(range.start(), range.end()), Math.max(range.start(), range.end()),
                range.step().orElse(0.0F), List.of());
        }
        if (input.control() instanceof SingleOptionInput option) {
            String initial = option.initial().orElse(option.entries().getFirst()).id();
            return new CustomMenuInput(index, key, option.label().getString(), CustomMenuInput.Kind.OPTION,
                initial, 0, 0, 0, 0, option.entries().stream().map(SingleOptionInput.Entry::id).toList());
        }
        return new CustomMenuInput(index, key, "", CustomMenuInput.Kind.TEXT, "", 0, 0, 0, 0, List.of());
    }

    private static List<ActionButton> actions(Dialog dialog) {
        List<ActionButton> result = new ArrayList<>();
        if (dialog instanceof SimpleDialog simple) result.addAll(simple.mainActions());
        else if (dialog instanceof MultiActionDialog multi) {
            result.addAll(multi.actions());
            multi.exitAction().ifPresent(result::add);
        } else if (dialog instanceof DialogListDialog list) {
            list.dialogs().stream().forEach(holder -> result.add(new ActionButton(
                new CommonButtonData(holder.value().common().computeExternalTitle(), list.buttonWidth()),
                Optional.of(new StaticAction(new ClickEvent.ShowDialog(holder))))));
            list.exitAction().ifPresent(result::add);
        } else if (dialog instanceof ButtonListDialog list) {
            list.exitAction().ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static CustomMenuButton button(int index, ActionButton button) {
        String label = button.button().label().getString();
        String color = labelColor(button.button().label());
        if (button.action().isEmpty()) return new CustomMenuButton(index, label, "", CustomMenuButton.Kind.EMPTY, color);
        Action action = button.action().get();
        String actionId = action instanceof CustomAll custom ? custom.id().toString() : "";
        CustomMenuButton.Kind kind = CustomMenuButton.Kind.OTHER;
        if (action instanceof CustomAll) kind = CustomMenuButton.Kind.CUSTOM;
        else if (action instanceof net.minecraft.server.dialog.action.CommandTemplate) kind = CustomMenuButton.Kind.COMMAND;
        else if (action instanceof StaticAction stat) {
            ClickEvent click = stat.value();
            if (click instanceof ClickEvent.Custom custom) {
                kind = CustomMenuButton.Kind.CUSTOM;
                actionId = custom.id().toString();
            } else if (click instanceof ClickEvent.RunCommand) kind = CustomMenuButton.Kind.COMMAND;
            else if (click instanceof ClickEvent.ShowDialog) kind = CustomMenuButton.Kind.DIALOG;
            else if (click instanceof ClickEvent.OpenUrl) kind = CustomMenuButton.Kind.URL;
            else if (click instanceof ClickEvent.CopyToClipboard) kind = CustomMenuButton.Kind.CLIPBOARD;
        }
        return new CustomMenuButton(index, label, actionId, kind, color);
    }

    private static String labelColor(net.minecraft.network.chat.Component label) {
        if (label == null) return "";
        net.minecraft.network.chat.Style style = label.getStyle();
        net.minecraft.network.chat.TextColor color = style == null ? null : style.getColor();
        if (color != null) return color.serialize().toLowerCase(java.util.Locale.ROOT);
        for (net.minecraft.network.chat.Component sibling : label.getSiblings()) {
            String nested = labelColor(sibling);
            if (!nested.isEmpty()) return nested;
        }
        return "";
    }

    @Override
    public CustomMenuSubmitResult submit(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
        if (!(snapshot.adapterState() instanceof State state)) return CustomMenuSubmitResult.failure("Invalid dialog state");
        if (submission == null || submission.button() == null) return CustomMenuSubmitResult.failure("No dialog button selected");
        int index = submission.button().index() - 1;
        if (index < 0 || index >= state.actions().size()) return CustomMenuSubmitResult.failure("Dialog button is unavailable");
        ActionButton selected = state.actions().get(index);
        if (selected.action().isEmpty()) return CustomMenuSubmitResult.failure("Dialog button has no action");

        Map<String, Action.ValueGetter> getters = new LinkedHashMap<>();
        for (Input input : state.dialog().common().inputs()) {
            String raw = submission.values().get(input.key());
            getters.put(input.key(), valueGetter(input, raw));
        }
        Optional<ClickEvent> click = selected.action().get().createAction(getters);
        if (click.isEmpty()) return CustomMenuSubmitResult.failure("Dialog action produced no response");
        if (click.get() instanceof ClickEvent.Custom custom) {
            return CustomMenuSubmitResult.packets(List.of(new ServerboundCustomClickActionPacket(custom.id(), custom.payload())));
        }
        if (click.get() instanceof ClickEvent.RunCommand command) {
            if (!"PLAY".equalsIgnoreCase(snapshot.phase())) {
                return CustomMenuSubmitResult.failure("Commands cannot be sent during configuration");
            }
            String value = command.command();
            if (value.startsWith("/")) value = value.substring(1);
            return CustomMenuSubmitResult.packets(List.of(new ServerboundChatCommandPacket(value)));
        }
        if (click.get() instanceof ClickEvent.ShowDialog nested) {
            return CustomMenuSubmitResult.replacement(snapshot(nested.dialog().value(), snapshot.phase()), click.get());
        }
        return CustomMenuSubmitResult.failure("Dialog action is not safe for automation");
    }

    private static Action.ValueGetter valueGetter(Input input, String supplied) {
        if (input.control() instanceof TextInput text) {
            String value = supplied == null ? text.initial() : supplied;
            if (value.length() > text.maxLength()) throw new IllegalArgumentException("Input '" + input.key() + "' exceeds max length");
            return getter(StringTag.escapeWithoutQuotes(value), StringTag.valueOf(value));
        }
        if (input.control() instanceof BooleanInput bool) {
            boolean value = supplied == null ? bool.initial() : parseBoolean(input.key(), supplied);
            return getter(value ? bool.onTrue() : bool.onFalse(), ByteTag.valueOf(value));
        }
        if (input.control() instanceof NumberRangeInput number) {
            NumberRangeInput.RangeInfo range = number.rangeInfo();
            float value = supplied == null
                ? range.initial().orElse((range.start() + range.end()) / 2.0F)
                : parseFloat(input.key(), supplied);
            float min = Math.min(range.start(), range.end());
            float max = Math.max(range.start(), range.end());
            if (value < min || value > max) throw new IllegalArgumentException("Input '" + input.key() + "' is outside its range");
            if (range.step().isPresent()) {
                float origin = range.initial().orElse(range.start());
                float quotient = (value - origin) / range.step().get();
                if (Math.abs(quotient - Math.round(quotient)) > 0.0001F) {
                    throw new IllegalArgumentException("Input '" + input.key() + "' does not match its step");
                }
            }
            return getter(numberString(value), FloatTag.valueOf(value));
        }
        if (input.control() instanceof SingleOptionInput option) {
            String value = supplied == null ? option.initial().orElse(option.entries().getFirst()).id() : supplied;
            if (option.entries().stream().noneMatch(entry -> entry.id().equals(value))) {
                throw new IllegalArgumentException("Input '" + input.key() + "' is not a valid option");
            }
            return getter(value, StringTag.valueOf(value));
        }
        throw new IllegalArgumentException("Unsupported input '" + input.key() + "'");
    }

    private static Action.ValueGetter getter(String text, Tag tag) {
        return new Action.ValueGetter() {
            @Override public String asTemplateSubstitution() { return text; }
            @Override public Tag asTag() { return tag; }
        };
    }

    private static boolean parseBoolean(String key, String raw) {
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new IllegalArgumentException("Input '" + key + "' is not a boolean");
        };
    }

    private static float parseFloat(String key, String raw) {
        try { return Float.parseFloat(raw.trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Input '" + key + "' is not a number"); }
    }

    private static String numberString(float value) {
        int integer = (int) value;
        return integer == value ? Integer.toString(integer) : Float.toString(value);
    }
}
