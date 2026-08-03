package autismclient.api.macro;

import autismclient.addons.AddonManager;
import autismclient.gui.macro.editor.ActionFieldSchema;
import autismclient.util.macro.MacroAction;

import java.util.function.Supplier;

public final class MacroActionEntry {

    public enum Kind { ACTION, CONDITION }

    private final String typeId;
    private final Supplier<MacroAction> factory;
    private final ActionFieldSchema schema;
    private String pickerCategory;
    private final String pickerLabel;
    private final String pickerTip;
    private final Kind kind;

    private MacroActionEntry(Builder b) {
        this.typeId = b.typeId;
        this.factory = b.factory;
        this.schema = b.schema;
        this.pickerCategory = b.pickerCategory;
        this.pickerLabel = b.pickerLabel;
        this.pickerTip = b.pickerTip;
        this.kind = b.kind;
    }

    public String typeId() { return typeId; }
    public Supplier<MacroAction> factory() { return factory; }
    public ActionFieldSchema schema() { return schema; }

    public boolean hasPicker() { return pickerCategory != null && pickerLabel != null; }

    boolean wantsPicker() { return pickerLabel != null; }
    public String pickerCategory() { return pickerCategory; }
    public String pickerLabel() { return pickerLabel; }
    public String pickerTip() { return pickerTip == null ? "" : pickerTip; }
    public Kind kind() { return kind; }
    public boolean isCondition() { return kind == Kind.CONDITION; }

    void setPickerCategory(String categoryId) {
        this.pickerCategory = categoryId;
    }

    public static Builder builder(String typeId, Supplier<MacroAction> factory) {
        return new Builder(typeId, factory);
    }

    public static Builder local(String localId, Supplier<MacroAction> factory) {
        return new Builder(AddonManager.scopedId(localId), factory);
    }

    public static final class Builder {
        private final String typeId;
        private final Supplier<MacroAction> factory;
        private ActionFieldSchema schema;
        private String pickerCategory;
        private String pickerLabel;
        private String pickerTip;
        private Kind kind = Kind.ACTION;

        private Builder(String typeId, Supplier<MacroAction> factory) {
            this.typeId = typeId;
            this.factory = factory;
        }

        public Builder schema(ActionSchema schema) {
            this.schema = schema == null ? null : schema.internal();
            return this;
        }

        public Builder picker(String label, String tip) {
            return picker(null, label, tip);
        }

        public Builder picker(String categoryId, String label, String tip) {
            this.pickerCategory = categoryId;
            this.pickerLabel = label;
            this.pickerTip = tip;
            this.kind = Kind.ACTION;
            return this;
        }

        public Builder condition(String label, String tip) {
            return condition(null, label, tip);
        }

        public Builder condition(String categoryId, String label, String tip) {
            this.pickerCategory = categoryId;
            this.pickerLabel = label;
            this.pickerTip = tip;
            this.kind = Kind.CONDITION;
            return this;
        }

        public MacroActionEntry build() {
            return new MacroActionEntry(this);
        }
    }
}
