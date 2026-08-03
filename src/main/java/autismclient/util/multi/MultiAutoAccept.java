package autismclient.util.multi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MultiAutoAccept {
    public static final int MAX_RESPONDERS = 6;

    public String tpaToMeCommand = "/tpahere {bot}";
    public String tpaToBotCommand = "/tpa {bot}";
    public String tradeCommand = "/trade {bot}";

    public boolean tpaEnabled = true;
    public String tpaAcceptCommand = "/tpaccept {name}";
    public boolean tpaUseMacro = false;
    public String tpaMacroName = "";
    public int tpaArmWindowMs = 15000;
    public int tpaAcceptDelayMs = 300;
    public boolean tradeEnabled = true;
    public String tradeAcceptCommand = "/trade accept";
    public boolean tradeUseMacro = false;
    public String tradeMacroName = "";
    public int tradeArmWindowMs = 15000;
    public int tradeAcceptDelayMs = 300;

    public final List<Responder> responders = new ArrayList<>();

    public record Responder(String trigger, String response, boolean useMacro, String macroName, int delayMs) {
        public Responder {
            trigger = trigger == null ? "" : trigger.trim();
            response = response == null ? "" : response.trim();
            macroName = macroName == null ? "" : macroName.trim();
            delayMs = Math.max(0, Math.min(60_000, delayMs));
        }

        public boolean valid() {
            return !trigger.isBlank() && (useMacro ? !macroName.isBlank() : !response.isBlank());
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("trigger", trigger);
            tag.putString("response", response);
            tag.putBoolean("useMacro", useMacro);
            tag.putString("macroName", macroName);
            tag.putInt("delayMs", delayMs);
            return tag;
        }

        static Responder fromTag(CompoundTag tag) {
            return new Responder(tag.getStringOr("trigger", ""), tag.getStringOr("response", ""),
                tag.getBooleanOr("useMacro", false), tag.getStringOr("macroName", ""), tag.getIntOr("delayMs", 0));
        }
    }

    public MultiAutoAccept() {
    }

    public MultiAutoAccept(MultiAutoAccept source) {
        if (source == null) return;
        tpaToMeCommand = source.tpaToMeCommand;
        tpaToBotCommand = source.tpaToBotCommand;
        tradeCommand = source.tradeCommand;
        tpaEnabled = source.tpaEnabled;
        tpaAcceptCommand = source.tpaAcceptCommand;
        tpaUseMacro = source.tpaUseMacro;
        tpaMacroName = source.tpaMacroName;
        tpaArmWindowMs = source.tpaArmWindowMs;
        tpaAcceptDelayMs = source.tpaAcceptDelayMs;
        tradeEnabled = source.tradeEnabled;
        tradeAcceptCommand = source.tradeAcceptCommand;
        tradeUseMacro = source.tradeUseMacro;
        tradeMacroName = source.tradeMacroName;
        tradeArmWindowMs = source.tradeArmWindowMs;
        tradeAcceptDelayMs = source.tradeAcceptDelayMs;
        responders.addAll(source.responders);
    }

    public void normalize() {
        tpaToMeCommand = clean(tpaToMeCommand, "/tpahere {bot}");
        tpaToBotCommand = clean(tpaToBotCommand, "/tpa {bot}");
        tradeCommand = clean(tradeCommand, "/trade {bot}");
        tpaAcceptCommand = clean(tpaAcceptCommand, "/tpaccept {name}");
        tradeAcceptCommand = clean(tradeAcceptCommand, "/trade accept");
        tpaMacroName = tpaMacroName == null ? "" : tpaMacroName.trim();
        tradeMacroName = tradeMacroName == null ? "" : tradeMacroName.trim();
        tpaArmWindowMs = clampArm(tpaArmWindowMs);
        tpaAcceptDelayMs = clampDelay(tpaAcceptDelayMs);
        tradeArmWindowMs = clampArm(tradeArmWindowMs);
        tradeAcceptDelayMs = clampDelay(tradeAcceptDelayMs);
        responders.removeIf(r -> r == null || !r.valid());
        while (responders.size() > MAX_RESPONDERS) responders.remove(responders.size() - 1);
    }

    public static String expand(String template, String botName, String userName) {
        String out = template == null ? "" : template;
        out = replaceCi(out, "{bot}", botName == null ? "" : botName);
        out = replaceCi(out, "{name}", userName == null ? "" : userName);
        out = replaceCi(out, "{me}", userName == null ? "" : userName);
        return out.trim();
    }

    private static String replaceCi(String in, String token, String value) {

        StringBuilder sb = new StringBuilder();
        String lower = in.toLowerCase(Locale.ROOT);
        String tok = token.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < in.length()) {
            int at = lower.indexOf(tok, i);
            if (at < 0) {
                sb.append(in, i, in.length());
                break;
            }
            sb.append(in, i, at).append(value);
            i = at + tok.length();
        }
        return sb.toString();
    }

    private static String clean(String value, String fallback) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() ? fallback : v;
    }

    private static int clampArm(int value) {
        return Math.max(2000, Math.min(60_000, value));
    }

    private static int clampDelay(int value) {
        return Math.max(0, Math.min(10_000, value));
    }

    public CompoundTag toTag() {
        normalize();
        CompoundTag tag = new CompoundTag();
        tag.putString("tpaToMe", tpaToMeCommand);
        tag.putString("tpaToBot", tpaToBotCommand);
        tag.putString("tradeCmd", tradeCommand);
        tag.putBoolean("tpaEnabled", tpaEnabled);
        tag.putString("tpaAccept", tpaAcceptCommand);
        tag.putBoolean("tpaUseMacro", tpaUseMacro);
        tag.putString("tpaMacro", tpaMacroName);
        tag.putInt("tpaArmMs", tpaArmWindowMs);
        tag.putInt("tpaDelayMs", tpaAcceptDelayMs);
        tag.putBoolean("tradeEnabled", tradeEnabled);
        tag.putString("tradeAccept", tradeAcceptCommand);
        tag.putBoolean("tradeUseMacro", tradeUseMacro);
        tag.putString("tradeMacro", tradeMacroName);
        tag.putInt("tradeArmMs", tradeArmWindowMs);
        tag.putInt("tradeDelayMs", tradeAcceptDelayMs);
        ListTag list = new ListTag();
        for (Responder responder : responders) list.add(responder.toTag());
        tag.put("responders", list);
        return tag;
    }

    public static MultiAutoAccept fromTag(CompoundTag tag) {
        MultiAutoAccept auto = new MultiAutoAccept();
        auto.tpaToMeCommand = tag.getStringOr("tpaToMe", auto.tpaToMeCommand);
        auto.tpaToBotCommand = tag.getStringOr("tpaToBot", auto.tpaToBotCommand);
        auto.tradeCommand = tag.getStringOr("tradeCmd", auto.tradeCommand);
        auto.tpaEnabled = tag.getBooleanOr("tpaEnabled", true);
        auto.tpaAcceptCommand = tag.getStringOr("tpaAccept", auto.tpaAcceptCommand);
        auto.tpaUseMacro = tag.getBooleanOr("tpaUseMacro", false);
        auto.tpaMacroName = tag.getStringOr("tpaMacro", "");
        auto.tradeEnabled = tag.getBooleanOr("tradeEnabled", true);
        auto.tradeAcceptCommand = tag.getStringOr("tradeAccept", auto.tradeAcceptCommand);
        auto.tradeUseMacro = tag.getBooleanOr("tradeUseMacro", false);
        auto.tradeMacroName = tag.getStringOr("tradeMacro", "");

        int legacyArm = tag.getIntOr("armWindowMs", 15000);
        int legacyDelay = tag.getIntOr("acceptDelayMs", 300);
        auto.tpaArmWindowMs = tag.getIntOr("tpaArmMs", legacyArm);
        auto.tpaAcceptDelayMs = tag.getIntOr("tpaDelayMs", legacyDelay);
        auto.tradeArmWindowMs = tag.getIntOr("tradeArmMs", legacyArm);
        auto.tradeAcceptDelayMs = tag.getIntOr("tradeDelayMs", legacyDelay);
        auto.responders.clear();
        for (Tag value : tag.getListOrEmpty("responders")) {
            if (value instanceof CompoundTag compound) auto.responders.add(Responder.fromTag(compound));
        }
        auto.normalize();
        return auto;
    }
}
