package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AutismChamsHit {
    private static final long FLASH_MS = 500L;
    private static final int PENDING_TICKS = 6;
    private static final int HURT_TIME = 10;

    private static final Map<Integer, Long> FLASH = new ConcurrentHashMap<>();
    private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(int prevHurtTime, int ticksLeft) {
    }

    private AutismChamsHit() {
    }

    public static void onAttack(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        PENDING.put(living.getId(), new Pending(living.hurtTime, PENDING_TICKS));
    }

    public static void mark(Entity entity) {
        if (entity == null) return;
        FLASH.put(entity.getId(), System.currentTimeMillis());
    }

    public static void tick() {
        if (PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            PENDING.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Pending> e = it.next();
            Entity entity = mc.level.getEntity(e.getKey());
            Pending p = e.getValue();
            boolean landed = entity instanceof LivingEntity living
                && (living.hurtTime > p.prevHurtTime()
                    || p.prevHurtTime() >= HURT_TIME && living.hurtTime >= HURT_TIME);
            if (landed) {
                FLASH.put(e.getKey(), System.currentTimeMillis());
                it.remove();
            } else if (entity == null || p.ticksLeft() <= 1) {
                it.remove();
            } else {
                e.setValue(new Pending(p.prevHurtTime(), p.ticksLeft() - 1));
            }
        }
    }

    public static boolean isFlashing(Entity entity) {
        if (entity == null) return false;
        Long at = FLASH.get(entity.getId());
        if (at == null) return false;
        if (System.currentTimeMillis() - at < FLASH_MS) return true;
        FLASH.remove(entity.getId());
        return false;
    }
}
