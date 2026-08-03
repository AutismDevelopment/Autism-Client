package autismclient.api.macro;

import autismclient.gui.macro.editor.ActionFieldSchema;

public final class ActionSchema {
    private final ActionFieldSchema internal;

    private ActionSchema(ActionFieldSchema internal) {
        this.internal = internal;
    }

    public ActionFieldSchema internal() {
        return internal;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final ActionFieldSchema.Builder delegate = ActionFieldSchema.builder();

        private Builder() {}

        public Builder toggle(String key, String label) { delegate.toggle(key, label); return this; }

        public Builder number(String key, String label) { delegate.number(key, label); return this; }

        public Builder decimal(String key, String label) { delegate.decimal(key, label); return this; }

        public Builder text(String key, String label) { delegate.text(key, label); return this; }

        public Builder macroSelect(String key, String label) { delegate.macroSelect(key, label); return this; }

        public Builder enumField(String key, String label, String... options) { delegate.enumField(key, label, options); return this; }

        public Builder stringList(String key, String label) { delegate.stringList(key, label); return this; }

        public Builder slot(String key, String label) { delegate.slot(key, label); return this; }

        public Builder targetSummary(String key, String label) { delegate.targetSummary(key, label); return this; }

        public Builder capturePacketClick(String key, String label) { delegate.capturePacketClick(key, label); return this; }

        public Builder blockPos(String key, String label) { delegate.blockPos(key, label); return this; }

        public Builder range(int min, int max) { delegate.range(min, max); return this; }

        public Builder decRange(double min, double max) { delegate.decRange(min, max); return this; }

        public Builder showWhen(String key) { delegate.showWhen(key); return this; }

        public Builder hideWhen(String key) { delegate.hideWhen(key); return this; }

        public Builder showWhenEnum(String key, String value) { delegate.showWhenEnum(key, value); return this; }

        public Builder hideWhenEnum(String key, String value) { delegate.hideWhenEnum(key, value); return this; }

        public Builder addLabel(String label) { delegate.addLabel(label); return this; }

        public Builder xyzKeys(String... keys) { delegate.xyzKeys(keys); return this; }

        public Builder xyzDouble(boolean value) { delegate.xyzDouble(value); return this; }

        public Builder captureBlock() { delegate.captureBlock(); return this; }

        public Builder captureEntity() { delegate.captureEntity(); return this; }

        public Builder captureCatalog() { delegate.captureCatalog(); return this; }

        public Builder captureItemSlot() { delegate.captureItemSlot(); return this; }

        public Builder capturePacketName() { delegate.capturePacketName(); return this; }

        public Builder exclusiveWith(String... keys) { delegate.exclusiveWith(keys); return this; }

        public Builder dynamic() { delegate.dynamic(); return this; }

        public ActionSchema build() {
            return new ActionSchema(delegate.build());
        }
    }
}
