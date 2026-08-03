package autismclient.util;

import java.util.List;

public class MeteorMacroAdapter {
    public static List<AutismMacro> getMeteorMacros() {
        return AutismCompatManager.getMeteorMacros();
    }

    public static void importToMeteor(AutismMacro packUtilMacro) {
        if (AutismCompatManager.importToMeteor(packUtilMacro)) {
            AutismClientMessaging.sendPrefixed("§aImported to Meteor: " + packUtilMacro.name);
        } else {
            AutismClientMessaging.sendPrefixed("§cFailed to import to Meteor");
        }
    }

    public static boolean importToAutism(String macroName) {
        AutismMacro packUtilMacro = AutismCompatManager.getMeteorMacro(macroName);
        if (packUtilMacro == null) {
            AutismClientMessaging.sendPrefixed("§cMeteor macro not found: " + macroName);
            return false;
        }

        AutismMacro imported = AutismMacroManager.get().addImportedCopy(packUtilMacro, packUtilMacro.name);
        if (imported == null) return false;

        if (!imported.name.equals(packUtilMacro.name)) {
            AutismClientMessaging.sendPrefixed("§eImported Meteor macro as: " + imported.name);
        }
        return true;
    }
}
