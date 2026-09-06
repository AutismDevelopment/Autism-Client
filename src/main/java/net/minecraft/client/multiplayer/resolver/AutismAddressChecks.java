package net.minecraft.client.multiplayer.resolver;

import com.google.common.collect.ImmutableList;

import java.lang.reflect.Constructor;

public final class AutismAddressChecks {
    private static final AddressCheck INLINE_ALLOW_ALL = new AddressCheck() {
        @Override
        public boolean isAllowed(ResolvedServerAddress address) {
            return true;
        }

        @Override
        public boolean isAllowed(ServerAddress address) {
            return true;
        }
    };

    private AutismAddressChecks() {
    }

    public static AddressCheck allowAll() {
        AddressCheck vanilla = emptyVanillaCheck();
        return vanilla != null ? vanilla : INLINE_ALLOW_ALL;
    }

    private static AddressCheck emptyVanillaCheck() {
        try {
            for (Class<?> nested : AddressCheck.class.getDeclaredClasses()) {
                if (nested == AddressCheck.class || !AddressCheck.class.isAssignableFrom(nested)) continue;
                Constructor<?> constructor = nested.getDeclaredConstructor(ImmutableList.class);
                constructor.setAccessible(true);
                return (AddressCheck) constructor.newInstance(ImmutableList.of());
            }
        } catch (Throwable ignored) {  }
        return null;
    }
}
