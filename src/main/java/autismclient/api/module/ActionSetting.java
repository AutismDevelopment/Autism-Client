package autismclient.api.module;

public final class ActionSetting extends Setting<Void, ActionSetting> {
    private String buttonLabel = "Run";

    public ActionSetting(String name, String title, Runnable action) {
        super(Kind.ACTION, name, title, null);
        setAction(action);
    }

    public ActionSetting buttonLabel(String buttonLabel) {
        this.buttonLabel = buttonLabel == null || buttonLabel.isBlank() ? "Run" : buttonLabel;
        return this;
    }

    public String buttonLabel() {
        return buttonLabel;
    }

    @Override
    protected Void decode(String raw) {
        return null;
    }

    @Override
    protected String encode(Void value) {
        return "";
    }
}
