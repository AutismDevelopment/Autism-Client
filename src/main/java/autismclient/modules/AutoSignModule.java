package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.StringSetting;

public final class AutoSignModule extends Module {

    AutoSignModule() {
        super("auto-sign", "AutoSign", ModuleCategory.PLAYER, "Fills signs with configured text.");

        add(new StringSetting("line-1", "Line 1", "")
            .description("Text for sign line one.").build());
        add(new StringSetting("line-2", "Line 2", "")
            .description("Text for sign line two.").build());
        add(new StringSetting("line-3", "Line 3", "")
            .description("Text for sign line three.").build());
        add(new StringSetting("line-4", "Line 4", "")
            .description("Text for sign line four.").build());
        add(new BoolSetting("edit-existing", "Also When Editing", false)
            .description("Replace text when editing signs.").build());
        add(new BoolSetting("auto-done", "Auto Done", true)
            .description("Close editor after filling text.").build());
    }

    public String[] signLines() {
        return new String[]{text("line-1"), text("line-2"), text("line-3"), text("line-4")};
    }

    public boolean editExisting() {
        return bool("edit-existing");
    }

    public boolean autoDone() {
        return bool("auto-done");
    }
}
