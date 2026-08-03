package autismclient.util.multi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public final class MultiQuickAction {
    public static final int MAX_STEPS = 6;

    public record Step(String packetClass, String arguments) {
        public Step {
            packetClass = packetClass == null ? "" : packetClass.trim();
            arguments = arguments == null ? "" : MultiManager.singleLine(arguments, 2048);
        }

        public boolean blank() {
            return packetClass.isBlank();
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("packet", packetClass);
            tag.putString("args", arguments);
            return tag;
        }

        static Step fromTag(CompoundTag tag) {
            return new Step(tag.getStringOr("packet", ""), tag.getStringOr("args", ""));
        }
    }

    public String name = "";
    public final List<Step> steps = new ArrayList<>();

    public MultiQuickAction() {
    }

    public MultiQuickAction(MultiQuickAction source) {
        if (source == null) return;
        name = source.name;
        steps.addAll(source.steps);
        normalize();
    }

    public MultiQuickAction(String name, String packetClass, String arguments) {
        this.name = name;
        if (packetClass != null && !packetClass.isBlank()) steps.add(new Step(packetClass, arguments));
        normalize();
    }

    public boolean empty() {
        for (Step step : steps) {
            if (!step.blank()) return false;
        }
        return true;
    }

    public String firstPacketClass() {
        for (Step step : steps) {
            if (!step.blank()) return step.packetClass();
        }
        return "";
    }

    public int packetCount() {
        int count = 0;
        for (Step step : steps) {
            if (!step.blank()) count++;
        }
        return count;
    }

    public String label(int index) {
        normalize();
        if (empty()) return "Empty";
        if (name != null && !name.isBlank()) return name;
        String simple = shortLabel(firstPacketClass());
        if (simple.isBlank()) return "Slot " + (index + 1);
        int extra = packetCount() - 1;
        return extra > 0 ? simple + " +" + extra : simple;
    }

    public void normalize() {
        name = name == null ? "" : MultiManager.singleLine(name, 32);
        steps.removeIf(step -> step == null || step.blank());
        while (steps.size() > MAX_STEPS) steps.remove(steps.size() - 1);
    }

    public static String shortLabel(String packetClass) {
        if (packetClass == null || packetClass.isBlank()) return "";
        int cut = Math.max(packetClass.lastIndexOf('.'), packetClass.lastIndexOf('$'));
        String simple = cut < 0 ? packetClass : packetClass.substring(cut + 1);
        simple = stripPrefix(stripPrefix(simple, "Serverbound"), "Clientbound");
        if (simple.endsWith("Packet")) simple = simple.substring(0, simple.length() - "Packet".length());
        return simple.isBlank() ? packetClass : simple;
    }

    private static String stripPrefix(String value, String prefix) {
        return value.startsWith(prefix) && value.length() > prefix.length() ? value.substring(prefix.length()) : value;
    }

    CompoundTag toTag() {
        normalize();
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        ListTag list = new ListTag();
        for (Step step : steps) list.add(step.toTag());
        tag.put("steps", list);
        return tag;
    }

    static MultiQuickAction fromTag(CompoundTag tag) {
        MultiQuickAction action = new MultiQuickAction();
        if (tag != null) {
            action.name = tag.getStringOr("name", "");
            ListTag list = tag.getListOrEmpty("steps");
            if (list.isEmpty()) {

                String packet = tag.getStringOr("packet", "");
                if (!packet.isBlank()) action.steps.add(new Step(packet, tag.getStringOr("args", "")));
            } else {
                for (Tag value : list) {
                    if (value instanceof CompoundTag compound) action.steps.add(Step.fromTag(compound));
                }
            }
        }
        action.normalize();
        return action;
    }
}
