package autismclient.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public final class SodiumTerrainPassGuard {

    private static final int TRANSITION_PASS_BUDGET = 600;
    private static final AtomicInteger ARM_GENERATION = new AtomicInteger(1);
    private static int seenArmGeneration;
    private static int remainingTransitionPasses;
    private static volatile boolean xrayActive;

    private static volatile boolean accessUnavailable;

    private record PassAccess(MethodHandle isTranslucent) {}
    private record ListsAccess(MethodHandle iterator) {}
    private record RenderListAccess(MethodHandle getRegion, MethodHandle sectionsWithGeometryIterator) {}
    private record RegionAccess(MethodHandle getStorage, MethodHandle getCachedBatch, MethodHandle getResources) {}
    private record BatchAccess(MethodHandle isEmpty) {}

    private static final ClassValue<PassAccess> PASS_ACCESS = new ClassValue<>() {
        @Override protected PassAccess computeValue(Class<?> type) {
            return new PassAccess(findHandle(type, "isTranslucent", 0));
        }
    };
    private static final ClassValue<ListsAccess> LISTS_ACCESS = new ClassValue<>() {
        @Override protected ListsAccess computeValue(Class<?> type) {
            return new ListsAccess(findHandle(type, "iterator", 1));
        }
    };
    private static final ClassValue<RenderListAccess> RENDER_LIST_ACCESS = new ClassValue<>() {
        @Override protected RenderListAccess computeValue(Class<?> type) {
            return new RenderListAccess(findHandle(type, "getRegion", 0),
                findHandle(type, "sectionsWithGeometryIterator", 1));
        }
    };
    private static final ClassValue<RegionAccess> REGION_ACCESS = new ClassValue<>() {
        @Override protected RegionAccess computeValue(Class<?> type) {
            return new RegionAccess(findHandle(type, "getStorage", 1), findHandle(type, "getCachedBatch", 1),
                findHandle(type, "getResources", 0));
        }
    };
    private static final ClassValue<BatchAccess> BATCH_ACCESS = new ClassValue<>() {
        @Override protected BatchAccess computeValue(Class<?> type) {
            return new BatchAccess(findHandle(type, "isEmpty", 0));
        }
    };

    private SodiumTerrainPassGuard() {}

    public static void armForTransition() {
        ARM_GENERATION.incrementAndGet();
    }

    public static void armForPositionCorrection() {
        if (!hasInspectionWork()) armForTransition();
    }

    public static void setXrayActive(boolean active) {
        if (xrayActive != active) armForTransition();
        xrayActive = active;
    }

    static boolean hasInspectionWork() {
        return !accessUnavailable
            && (xrayActive || ARM_GENERATION.get() != seenArmGeneration || remainingTransitionPasses > 0);
    }

    static void resetForTests() {
        xrayActive = false;
        accessUnavailable = false;
        seenArmGeneration = ARM_GENERATION.get();
        remainingTransitionPasses = 0;
    }

    public static boolean shouldSkip(Object lists, Object pass) {
        if (accessUnavailable) return false;
        int generation = ARM_GENERATION.get();
        if (generation != seenArmGeneration) {
            seenArmGeneration = generation;
            remainingTransitionPasses = TRANSITION_PASS_BUDGET;
        } else if (!xrayActive && remainingTransitionPasses <= 0) {
            return false;
        }
        if (!xrayActive) remainingTransitionPasses--;
        if (lists == null || pass == null) return false;

        try {
            PassAccess passAccess = PASS_ACCESS.get(pass.getClass());
            ListsAccess listsAccess = LISTS_ACCESS.get(lists.getClass());
            if (passAccess.isTranslucent() == null || listsAccess.iterator() == null) return disableCompatibilityGuard();
            boolean translucent = (boolean) passAccess.isTranslucent().invoke(pass);
            Object result = listsAccess.iterator().invoke(lists, translucent);
            if (!(result instanceof Iterator<?> iterator)) return false;

            while (iterator.hasNext()) {
                Object renderList = iterator.next();
                if (renderList == null) continue;
                RenderListAccess renderAccess = RENDER_LIST_ACCESS.get(renderList.getClass());
                if (renderAccess.getRegion() == null || renderAccess.sectionsWithGeometryIterator() == null) {
                    return disableCompatibilityGuard();
                }
                Object region = renderAccess.getRegion().invoke(renderList);
                if (region == null) continue;
                RegionAccess regionAccess = REGION_ACCESS.get(region.getClass());
                if (regionAccess.getStorage() == null || regionAccess.getCachedBatch() == null
                    || regionAccess.getResources() == null) {
                    return disableCompatibilityGuard();
                }
                if (regionAccess.getStorage().invoke(region, pass) == null) continue;

                Object batch = regionAccess.getCachedBatch().invoke(region, pass);
                BatchAccess batchAccess = batch == null ? null : BATCH_ACCESS.get(batch.getClass());
                if (batchAccess != null && batchAccess.isEmpty() == null) return disableCompatibilityGuard();
                boolean cachedDraws = batchAccess != null && !(boolean) batchAccess.isEmpty().invoke(batch);
                boolean listedGeometry = renderAccess.sectionsWithGeometryIterator().invoke(renderList, translucent) != null;
                if ((cachedDraws || listedGeometry) && regionAccess.getResources().invoke(region) == null) {
                    remainingTransitionPasses = Math.max(remainingTransitionPasses, TRANSITION_PASS_BUDGET);
                    return true;
                }
            }
        } catch (Throwable ignored) {

        }
        return false;
    }

    private static boolean disableCompatibilityGuard() {
        accessUnavailable = true;
        remainingTransitionPasses = 0;
        return false;
    }

    private static MethodHandle findHandle(Class<?> owner, String name, int parameters) {
        for (Method candidate : owner.getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != parameters) continue;
            try {
                return MethodHandles.publicLookup().unreflect(candidate);
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
        return null;
    }
}
