package autismclient.gui.macro.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ActionFieldSchema {
    private final List<FieldDef> fields;

    private ActionFieldSchema(List<FieldDef> fields) {
        this.fields = Collections.unmodifiableList(fields);
    }

    public List<FieldDef> fields() { return fields; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<FieldDef> fields = new ArrayList<>();

        private String     pendingKey;
        private String     pendingLabel;
        private FieldType  pendingType;
        private int        min            = Integer.MIN_VALUE;
        private int        max            = Integer.MAX_VALUE;
        private double     decMin         = -Double.MAX_VALUE;
        private double     decMax         = Double.MAX_VALUE;
        private List<String> enumOpts;
        private String     showWhenKey;
        private boolean    showWhenInv;
        private String     showWhenValue;
        private String     addLabel;
        private String[]   xyzKeys;
        private boolean    xyzDouble;
        private CaptureMode captureMode   = CaptureMode.NONE;
        private String[]   exclusiveWith;
        private boolean    dynamic;

        private void commit() {
            if (pendingKey == null) return;
            fields.add(new FieldDef(pendingKey, pendingLabel, pendingType,
                    min, max, decMin, decMax,
                    enumOpts, showWhenKey, showWhenInv, showWhenValue,
                    addLabel, xyzKeys, xyzDouble, captureMode, exclusiveWith, dynamic));
            pendingKey   = null;
            pendingLabel = null;
            pendingType  = null;
            min          = Integer.MIN_VALUE;
            max          = Integer.MAX_VALUE;
            decMin       = -Double.MAX_VALUE;
            decMax       = Double.MAX_VALUE;
            enumOpts     = null;
            showWhenKey  = null;
            showWhenInv  = false;
            showWhenValue = null;
            addLabel     = null;
            xyzKeys      = null;
            xyzDouble    = false;
            captureMode  = CaptureMode.NONE;
            exclusiveWith = null;
            dynamic       = false;
        }

        private Builder start(String key, String label, FieldType type) {
            commit();
            pendingKey   = key;
            pendingLabel = label;
            pendingType  = type;
            return this;
        }

        public Builder toggle(String key, String label)   { return start(key, label, FieldType.TOGGLE); }
        public Builder number(String key, String label)   { return start(key, label, FieldType.NUMBER); }
        public Builder decimal(String key, String label)  { return start(key, label, FieldType.DECIMAL); }
        public Builder text(String key, String label)     { return start(key, label, FieldType.TEXT); }
        public Builder macroSelect(String key, String label) { return start(key, label, FieldType.MACRO_SELECT); }
        public Builder stringList(String key, String label) { return start(key, label, FieldType.STRING_LIST); }
        public Builder blockPos(String key, String label) { return start(key, label, FieldType.BLOCK_POS); }
        public Builder slot(String key, String label)     { return start(key, label, FieldType.SLOT); }
        public Builder targetSummary(String key, String label) { return start(key, label, FieldType.TARGET_SUMMARY); }
        public Builder condition(String key, String label) { return start(key, label, FieldType.CONDITION); }
        public Builder capturePacketClick(String key, String label) {
            start(key, label, FieldType.CAPTURE_BUTTON);
            this.captureMode = CaptureMode.PACKET_CLICK_TARGET;
            return this;
        }

        public Builder enumField(String key, String label, String... options) {
            start(key, label, FieldType.ENUM);
            enumOpts = new ArrayList<>(Arrays.asList(options));
            return this;
        }

        public Builder range(int min, int max)            { this.min = min; this.max = max; return this; }
        public Builder decRange(double min, double max)   { this.decMin = min; this.decMax = max; return this; }
        public Builder showWhen(String key)               { showWhenKey = key; showWhenInv = false; return this; }
        public Builder hideWhen(String key)               { showWhenKey = key; showWhenInv = true;  return this; }
        public Builder showWhenEnum(String key, String value) {
            if (key.equals(showWhenKey) && !showWhenInv && showWhenValue != null && !showWhenValue.isEmpty()) {
                showWhenValue += "|" + value;
            } else {
                showWhenKey = key;
                showWhenInv = false;
                showWhenValue = value;
            }
            return this;
        }
        public Builder hideWhenEnum(String key, String value) {
            if (key.equals(showWhenKey) && showWhenInv && showWhenValue != null && !showWhenValue.isEmpty()) {
                showWhenValue += "|" + value;
            } else {
                showWhenKey = key;
                showWhenInv = true;
                showWhenValue = value;
            }
            return this;
        }
        public Builder addLabel(String label)             { this.addLabel = label; return this; }
        public Builder xyzKeys(String... keys)            { this.xyzKeys = keys; return this; }
        public Builder xyzDouble(boolean v)               { this.xyzDouble = v;  return this; }

        public Builder captureBlock()                     { this.captureMode = CaptureMode.BLOCK_ID;      return this; }

        public Builder captureEntity()                    { this.captureMode = CaptureMode.ENTITY_ID;     return this; }

        public Builder captureCatalog()                   { this.captureMode = CaptureMode.BLOCK_CATALOG; return this; }

        public Builder captureItemSlot()                  { this.captureMode = CaptureMode.ITEM_SLOT;     return this; }

        public Builder capturePacketName()                { this.captureMode = CaptureMode.PACKET_NAME;   return this; }

        public Builder captureMenuInput()                 { this.captureMode = CaptureMode.CUSTOM_MENU_INPUT;  return this; }

        public Builder captureMenuButton()                { this.captureMode = CaptureMode.CUSTOM_MENU_BUTTON; return this; }

        public Builder captureMenuTitle()                 { this.captureMode = CaptureMode.CUSTOM_MENU_TITLE;  return this; }

        public Builder exclusiveWith(String... keys)      { this.exclusiveWith = keys; return this; }

        public Builder dynamic()                          { this.dynamic = true; return this; }

        public ActionFieldSchema build() {
            commit();
            return new ActionFieldSchema(new ArrayList<>(fields));
        }
    }
}
