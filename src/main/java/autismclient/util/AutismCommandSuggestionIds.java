package autismclient.util;

import java.util.concurrent.atomic.AtomicInteger;

public final class AutismCommandSuggestionIds {

    public static final int MACRO_FIRST_ID = 2_000_000_000;

    private static final AtomicInteger NEXT_MACRO_ID = new AtomicInteger(MACRO_FIRST_ID);

    public static int nextMacroId() {
        return NEXT_MACRO_ID.getAndUpdate(id -> id >= Integer.MAX_VALUE - 1 ? MACRO_FIRST_ID : id + 1);
    }

    public static boolean isMacroId(int id) {
        return id >= MACRO_FIRST_ID;
    }

    private AutismCommandSuggestionIds() {}
}
