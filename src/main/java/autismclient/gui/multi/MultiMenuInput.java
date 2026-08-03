package autismclient.gui.multi;

public final class MultiMenuInput {
    public final MultiRenameField rename = new MultiRenameField();
    public int beaconPrimary = -1;
    public int beaconSecondary = -1;
    public int recipeIndex = -1;
    private String type = "";

    public void sync(String typeId) {
        String t = typeId == null ? "" : typeId;
        if (t.equals(type)) return;
        type = t;
        if (!t.endsWith("anvil")) rename.blur();
        beaconPrimary = -1;
        beaconSecondary = -1;
        recipeIndex = -1;
    }
}
