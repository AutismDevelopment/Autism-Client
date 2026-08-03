package autismclient.util;

final class AutismPluginProbeIdAllocator {
    static final int BLOCK_SIZE = 10_000;
    private static final int FIRST_ID = 1_000_000;

    private int nextId = FIRST_ID;

    int allocateBlock() {

        if (nextId > AutismCommandSuggestionIds.MACRO_FIRST_ID - (BLOCK_SIZE * 2)) nextId = FIRST_ID;
        int base = nextId;
        nextId += BLOCK_SIZE;
        return base;
    }
}
