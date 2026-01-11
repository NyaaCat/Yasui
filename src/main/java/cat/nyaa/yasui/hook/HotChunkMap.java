package cat.nyaa.yasui.hook;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Holds per-world hot chunk heat values for NMS hooks.
 */
public final class HotChunkMap {
    private static final Map<Object, HotChunks> HOT_CHUNKS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private HotChunkMap() {}

    public static void update(Object owner, long[] chunkKeys, float[] heat) {
        if (owner == null) {
            return;
        }
        if (chunkKeys == null || heat == null || chunkKeys.length == 0 || chunkKeys.length != heat.length) {
            HOT_CHUNKS.remove(owner);
            return;
        }
        long[] keysCopy = Arrays.copyOf(chunkKeys, chunkKeys.length);
        float[] heatCopy = Arrays.copyOf(heat, heat.length);
        HOT_CHUNKS.put(owner, new HotChunks(keysCopy, heatCopy));
    }

    public static float getHeat(Object owner, long chunkKey) {
        if (owner == null) {
            return 0f;
        }
        HotChunks chunks = HOT_CHUNKS.get(owner);
        if (chunks == null) {
            return 0f;
        }
        return chunks.getHeat(chunkKey);
    }

    private record HotChunks(long[] keys, float[] heat) {
        private float getHeat(long key) {
            int index = Arrays.binarySearch(keys, key);
            if (index < 0) {
                return 0f;
            }
            return heat[index];
        }
    }
}
