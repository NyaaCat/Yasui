package cat.nyaa.yasui.hook;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks per-chunk change epochs for NMS hook caches.
 *
 * Owners are typically ServerLevel or PoiManager instances.
 */
public final class ChunkEpochMap {
    private static final Map<Object, Epochs> EPOCHS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private ChunkEpochMap() {}

    public static int getEpoch(Object owner, long chunkKey) {
        if (owner == null) {
            return 0;
        }
        Epochs epochs = EPOCHS.get(owner);
        if (epochs == null) {
            return 0;
        }
        return epochs.get(chunkKey);
    }

    public static void bumpEpoch(Object owner, long chunkKey) {
        if (owner == null) {
            return;
        }
        Epochs epochs = EPOCHS.computeIfAbsent(owner, key -> new Epochs());
        epochs.bump(chunkKey);
    }

    public static void bumpEpochs(Object owner, long[] chunkKeys) {
        if (owner == null || chunkKeys == null || chunkKeys.length == 0) {
            return;
        }
        Epochs epochs = EPOCHS.computeIfAbsent(owner, key -> new Epochs());
        for (long chunkKey : chunkKeys) {
            epochs.bump(chunkKey);
        }
    }

    public static void clearEpoch(Object owner, long chunkKey) {
        if (owner == null) {
            return;
        }
        Epochs epochs = EPOCHS.get(owner);
        if (epochs == null) {
            return;
        }
        epochs.remove(chunkKey);
    }

    private static final class Epochs {
        private final Map<Long, Integer> map = new HashMap<>();

        synchronized int get(long key) {
            return map.getOrDefault(key, 0);
        }

        synchronized void bump(long key) {
            int next = map.getOrDefault(key, 0) + 1;
            map.put(key, next);
        }

        synchronized void remove(long key) {
            map.remove(key);
        }
    }
}
