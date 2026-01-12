package cat.nyaa.yasui.hook;

import java.util.UUID;

/**
 * NMS-side gate for spreading Mob AI ticks across tick groups.
 *
 * Loaded by the agent classloader and referenced from transformed NMS bytecode.
 * Uses only JDK types to avoid classloader issues - NMS access via reflection.
 */
public final class TickGroupGate {
    private static volatile boolean enabled = false;
    private static volatile int tickGroups = 0;
    private static volatile boolean hookActive = false;

    private TickGroupGate() {}

    public static void configure(boolean enabled, int tickGroups) {
        TickGroupGate.enabled = enabled;
        TickGroupGate.tickGroups = Math.max(0, tickGroups);
    }

    public static boolean isHookActive() {
        return hookActive;
    }

    public static void markHookActive() {
        hookActive = true;
    }

    public static boolean shouldSkipAi(Object mob) {
        hookActive = true;
        if (!enabled || mob == null || tickGroups <= 0) {
            return false;
        }
        if (!NmsReflect.init(mob)) {
            return false;
        }
        if (!NmsReflect.isMobAware(mob)) {
            return false;
        }
        Object level = NmsReflect.getEntityLevel(mob);
        if (level == null) {
            return false;
        }
        Object pos = NmsReflect.getEntityBlockPos(mob);
        if (pos == null) {
            return false;
        }
        long posKey = NmsReflect.blockPosAsLong(pos);
        float heat = HotChunkMap.getHeat(level, HotChunkUtil.chunkKeyFromBlockPos(posKey));
        if (heat <= 0f) {
            return false;
        }

        int groups = tickGroups + 1;
        int group = computeGroup(mob, groups);
        int tick = NmsReflect.getCurrentTick();
        if (Math.floorMod(tick, groups) == group) {
            return false;
        }
        NmsReflect.incrementNoActionTime(mob);
        return true;
    }

    private static int computeGroup(Object mob, int groups) {
        UUID uuid = NmsReflect.getEntityUuid(mob);
        int hash = uuid != null ? uuid.hashCode() : NmsReflect.getEntityId(mob);
        return Math.floorMod(hash, groups);
    }
}
