package cat.nyaa.yasui.hook;

final class HotChunkUtil {
    private HotChunkUtil() {}

    static long chunkKeyFromBlockPos(long blockPosKey) {
        int chunkX = unpackX(blockPosKey) >> 4;
        int chunkZ = unpackZ(blockPosKey) >> 4;
        return packChunkKey(chunkX, chunkZ);
    }

    static long packChunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }
}
