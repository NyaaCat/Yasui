package cat.nyaa.yasui.nms;

public final class ChunkEpochNmsHook {
    private ChunkEpochNmsHook() {}

    public static void bumpEpoch(Object owner, long chunkKey) {
        ChunkEpochMapBridge.bumpEpoch(owner, chunkKey);
    }

    public static void bumpEpochs(Object owner, long[] chunkKeys) {
        ChunkEpochMapBridge.bumpEpochs(owner, chunkKeys);
    }

    public static void clearEpoch(Object owner, long chunkKey) {
        ChunkEpochMapBridge.clearEpoch(owner, chunkKey);
    }
}
