package cat.nyaa.yasui.hook;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reflection-based access to NMS classes.
 *
 * This class uses the classloader from NMS objects to resolve types,
 * avoiding compile-time dependencies on NMS that would fail when loaded
 * from the system classloader.
 */
public final class NmsReflect {
    private static volatile boolean initialized = false;
    private static volatile boolean initFailed = false;
    private static volatile ClassLoader nmsClassLoader;

    // MinecraftServer
    private static volatile MethodHandle currentTickGetter;

    // Brain
    private static volatile MethodHandle brainGetMemory;

    // Container
    private static volatile Class<?> containerClass;
    private static volatile MethodHandle getContainerSize;
    private static volatile MethodHandle getItem;

    // WorldlyContainer
    private static volatile Class<?> worldlyContainerClass;
    private static volatile MethodHandle getSlotsForFace;

    // CompoundContainer
    private static volatile Class<?> compoundContainerClass;
    private static volatile MethodHandle getContainer1;
    private static volatile MethodHandle getContainer2;

    // ItemStack
    private static volatile MethodHandle itemIsEmpty;
    private static volatile MethodHandle itemGetCount;
    private static volatile MethodHandle itemGetMaxStackSize;

    // Direction
    private static volatile Class<?> directionClass;
    private static volatile Object[] directionValues;

    // Path
    private static volatile MethodHandle pathCopy;

    // PathNavigation
    private static volatile MethodHandle pathNavGetMob;

    // Entity
    private static volatile MethodHandle entityGetId;
    private static volatile MethodHandle entityGetUuid;
    private static volatile MethodHandle entityBlockPosition;
    private static volatile MethodHandle entityGetLevel;

    // BlockPos
    private static volatile MethodHandle blockPosAsLong;

    // Level / world access
    private static volatile MethodHandle levelGetBlockStateIfLoadedAndInBounds;
    private static volatile MethodHandle levelGetBlockStateIfLoaded;
    private static volatile MethodHandle levelIsLoadedAndInBounds;
    private static volatile MethodHandle levelGetWorldBorder;
    private static volatile MethodHandle worldBorderIsWithinBounds;
    private static volatile MethodHandle blockGetterGetBlockState;
    private static volatile MethodHandle chunkAccessGetPos;
    private static volatile Field chunkPosXField;
    private static volatile Field chunkPosZField;

    // PoiAccess
    private static volatile MethodHandle poiAccessFindNearest;
    private static volatile MethodHandle poiAccessFindAny;
    private static volatile MethodHandle poiAccessFindClosest;
    private static volatile MethodHandle poiAccessFindClosestWithType;
    private static volatile MethodHandle poiManagerGetType;
    private static volatile MethodHandle poiManagerExists;

    private NmsReflect() {}

    /**
     * Initialize reflection using the classloader from the given NMS object.
     */
    public static boolean init(Object nmsObject) {
        if (initialized) {
            return !initFailed;
        }
        synchronized (NmsReflect.class) {
            if (initialized) {
                return !initFailed;
            }
            try {
                nmsClassLoader = nmsObject.getClass().getClassLoader();
                initCore();
                initialized = true;
                return true;
            } catch (Throwable t) {
                initFailed = true;
                initialized = true;
                log("Init failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
                return false;
            }
        }
    }

    private static void initCore() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        // MinecraftServer.currentTick
        Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer", true, nmsClassLoader);
        Field tickField = minecraftServerClass.getField("currentTick");
        currentTickGetter = lookup.unreflectGetter(tickField);

        // Brain
        Class<?> brainClass = Class.forName("net.minecraft.world.entity.ai.Brain", true, nmsClassLoader);
        Class<?> memoryModuleTypeClass = Class.forName("net.minecraft.world.entity.ai.memory.MemoryModuleType", true, nmsClassLoader);
        brainGetMemory = lookup.findVirtual(brainClass, "getMemory",
            MethodType.methodType(Optional.class, memoryModuleTypeClass));

        // Container
        containerClass = Class.forName("net.minecraft.world.Container", true, nmsClassLoader);
        getContainerSize = lookup.findVirtual(containerClass, "getContainerSize", MethodType.methodType(int.class));
        Class<?> itemStackClass = Class.forName("net.minecraft.world.item.ItemStack", true, nmsClassLoader);
        getItem = lookup.findVirtual(containerClass, "getItem", MethodType.methodType(itemStackClass, int.class));

        // ItemStack
        itemIsEmpty = lookup.findVirtual(itemStackClass, "isEmpty", MethodType.methodType(boolean.class));
        itemGetCount = lookup.findVirtual(itemStackClass, "getCount", MethodType.methodType(int.class));
        itemGetMaxStackSize = lookup.findVirtual(itemStackClass, "getMaxStackSize", MethodType.methodType(int.class));

        // Direction
        directionClass = Class.forName("net.minecraft.core.Direction", true, nmsClassLoader);
        directionValues = (Object[]) directionClass.getMethod("values").invoke(null);

        // WorldlyContainer
        worldlyContainerClass = Class.forName("net.minecraft.world.WorldlyContainer", true, nmsClassLoader);
        getSlotsForFace = lookup.findVirtual(worldlyContainerClass, "getSlotsForFace",
            MethodType.methodType(int[].class, directionClass));

        // CompoundContainer
        compoundContainerClass = Class.forName("net.minecraft.world.CompoundContainer", true, nmsClassLoader);
        Field container1Field = compoundContainerClass.getField("container1");
        Field container2Field = compoundContainerClass.getField("container2");
        getContainer1 = lookup.unreflectGetter(container1Field);
        getContainer2 = lookup.unreflectGetter(container2Field);

        // Path
        Class<?> pathClass = Class.forName("net.minecraft.world.level.pathfinder.Path", true, nmsClassLoader);
        pathCopy = lookup.findVirtual(pathClass, "copy", MethodType.methodType(pathClass));

        // PathNavigation
        try {
            Class<?> pathNavigationClass = Class.forName("net.minecraft.world.entity.ai.navigation.PathNavigation", true, nmsClassLoader);
            Field mobField = pathNavigationClass.getDeclaredField("mob");
            mobField.setAccessible(true);
            pathNavGetMob = lookup.unreflectGetter(mobField);
        } catch (Throwable ignored) {
            pathNavGetMob = null;
        }

        // Entity
        Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity", true, nmsClassLoader);
        entityGetId = lookup.findVirtual(entityClass, "getId", MethodType.methodType(int.class));
        entityGetUuid = lookup.findVirtual(entityClass, "getUUID", MethodType.methodType(java.util.UUID.class));
        try {
            Class<?> levelClass = Class.forName("net.minecraft.world.level.Level", true, nmsClassLoader);
            entityGetLevel = lookup.findVirtual(entityClass, "level", MethodType.methodType(levelClass));
        } catch (Throwable ignored) {
            entityGetLevel = null;
        }
        // BlockPos
        Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos", true, nmsClassLoader);
        blockPosAsLong = lookup.findVirtual(blockPosClass, "asLong", MethodType.methodType(long.class));
        try {
            entityBlockPosition = lookup.findVirtual(entityClass, "blockPosition", MethodType.methodType(blockPosClass));
        } catch (Throwable ignored) {
            entityBlockPosition = null;
        }

        // Level / world access
        try {
            Class<?> blockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState", true, nmsClassLoader);
            Class<?> levelClass = Class.forName("net.minecraft.world.level.Level", true, nmsClassLoader);
            levelGetBlockStateIfLoadedAndInBounds = lookup.findVirtual(
                levelClass, "getBlockStateIfLoadedAndInBounds", MethodType.methodType(blockStateClass, blockPosClass));
            levelGetBlockStateIfLoaded = lookup.findVirtual(
                levelClass, "getBlockStateIfLoaded", MethodType.methodType(blockStateClass, blockPosClass));
            levelIsLoadedAndInBounds = lookup.findVirtual(
                levelClass, "isLoadedAndInBounds", MethodType.methodType(boolean.class, blockPosClass));
            Class<?> worldBorderClass = Class.forName("net.minecraft.world.level.border.WorldBorder", true, nmsClassLoader);
            levelGetWorldBorder = lookup.findVirtual(levelClass, "getWorldBorder", MethodType.methodType(worldBorderClass));
            worldBorderIsWithinBounds = lookup.findVirtual(
                worldBorderClass, "isWithinBounds", MethodType.methodType(boolean.class, blockPosClass));
            Class<?> blockGetterClass = Class.forName("net.minecraft.world.level.BlockGetter", true, nmsClassLoader);
            blockGetterGetBlockState = lookup.findVirtual(
                blockGetterClass, "getBlockState", MethodType.methodType(blockStateClass, blockPosClass));
            Class<?> chunkAccessClass = Class.forName("net.minecraft.world.level.chunk.ChunkAccess", true, nmsClassLoader);
            Class<?> chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos", true, nmsClassLoader);
            chunkAccessGetPos = lookup.findVirtual(chunkAccessClass, "getPos", MethodType.methodType(chunkPosClass));
            chunkPosXField = chunkPosClass.getField("x");
            chunkPosZField = chunkPosClass.getField("z");
        } catch (Throwable ignored) {
            levelGetBlockStateIfLoadedAndInBounds = null;
            levelGetBlockStateIfLoaded = null;
            levelIsLoadedAndInBounds = null;
            levelGetWorldBorder = null;
            worldBorderIsWithinBounds = null;
            blockGetterGetBlockState = null;
            chunkAccessGetPos = null;
            chunkPosXField = null;
            chunkPosZField = null;
        }

        // PoiAccess (Paper-specific)
        Class<?> poiAccessClass = Class.forName("io.papermc.paper.util.PoiAccess", true, nmsClassLoader);
        Class<?> poiManagerClass = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiManager", true, nmsClassLoader);
        Class<?> occupancyClass = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiManager$Occupancy", true, nmsClassLoader);
        poiManagerGetType = lookup.findVirtual(poiManagerClass, "getType", MethodType.methodType(Optional.class, blockPosClass));
        poiManagerExists = lookup.findVirtual(poiManagerClass, "exists",
            MethodType.methodType(boolean.class, blockPosClass, Predicate.class));
        poiAccessFindNearest = lookup.findStatic(poiAccessClass, "findNearestPoiPositions",
            MethodType.methodType(void.class,
                poiManagerClass, Predicate.class, Predicate.class, blockPosClass,
                int.class, double.class, occupancyClass, boolean.class, int.class, List.class));
        poiAccessFindAny = lookup.findStatic(poiAccessClass, "findAnyPoiPosition",
            MethodType.methodType(blockPosClass,
                poiManagerClass, Predicate.class, Predicate.class, blockPosClass,
                int.class, occupancyClass, boolean.class));
        poiAccessFindClosest = lookup.findStatic(poiAccessClass, "findClosestPoiDataPosition",
            MethodType.methodType(blockPosClass,
                poiManagerClass, Predicate.class, Predicate.class, blockPosClass,
                int.class, double.class, occupancyClass, boolean.class));
        Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair", true, nmsClassLoader);
        poiAccessFindClosestWithType = lookup.findStatic(poiAccessClass, "findClosestPoiDataTypeAndPosition",
            MethodType.methodType(pairClass,
                poiManagerClass, Predicate.class, Predicate.class, blockPosClass,
                int.class, double.class, occupancyClass, boolean.class));
    }

    public static int getCurrentTick() {
        if (!initialized || initFailed) {
            return 0;
        }
        try {
            return (int) currentTickGetter.invokeExact();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static Object getBrainMemory(Object brain, Object memoryType) {
        if (!initialized || initFailed) {
            return Optional.empty();
        }
        try {
            return brainGetMemory.invoke(brain, memoryType);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    // Container methods

    public static int getContainerSize(Object container) {
        try {
            return (int) getContainerSize.invoke(container);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static Object getItem(Object container, int slot) {
        try {
            return getItem.invoke(container, slot);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isWorldlyContainer(Object container) {
        return worldlyContainerClass != null && worldlyContainerClass.isInstance(container);
    }

    public static int[] getSlotsForFace(Object worldlyContainer, int directionOrdinal) {
        try {
            Object direction = directionValues[directionOrdinal];
            return (int[]) getSlotsForFace.invoke(worldlyContainer, direction);
        } catch (Throwable t) {
            return new int[0];
        }
    }

    public static boolean isCompoundContainer(Object container) {
        return compoundContainerClass != null && compoundContainerClass.isInstance(container);
    }

    public static Object getContainer1(Object compound) {
        try {
            return getContainer1.invoke(compound);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object getContainer2(Object compound) {
        try {
            return getContainer2.invoke(compound);
        } catch (Throwable t) {
            return null;
        }
    }

    // ItemStack methods

    public static boolean isItemEmpty(Object itemStack) {
        try {
            return (boolean) itemIsEmpty.invoke(itemStack);
        } catch (Throwable t) {
            return true;
        }
    }

    public static int getItemCount(Object itemStack) {
        try {
            return (int) itemGetCount.invoke(itemStack);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int getItemMaxStackSize(Object itemStack) {
        try {
            return (int) itemGetMaxStackSize.invoke(itemStack);
        } catch (Throwable t) {
            return 64;
        }
    }

    // Path methods

    public static Object copyPath(Object path) {
        try {
            return pathCopy.invoke(path);
        } catch (Throwable t) {
            return null;
        }
    }

    // Entity methods

    public static int getEntityId(Object entity) {
        if (entity == null) {
            return 0;
        }
        try {
            return (int) entityGetId.invoke(entity);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static java.util.UUID getEntityUuid(Object entity) {
        if (entity == null || entityGetUuid == null) {
            return null;
        }
        try {
            return (java.util.UUID) entityGetUuid.invoke(entity);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object getNavigationMob(Object navigation) {
        if (navigation == null || pathNavGetMob == null) {
            return null;
        }
        try {
            return pathNavGetMob.invoke(navigation);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object getEntityBlockPos(Object entity) {
        if (entity == null || entityBlockPosition == null) {
            return null;
        }
        try {
            return entityBlockPosition.invoke(entity);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object getEntityLevel(Object entity) {
        if (entity == null || entityGetLevel == null) {
            return null;
        }
        try {
            return entityGetLevel.invoke(entity);
        } catch (Throwable t) {
            return null;
        }
    }

    // BlockPos methods

    public static long blockPosAsLong(Object blockPos) {
        try {
            return (long) blockPosAsLong.invoke(blockPos);
        } catch (Throwable t) {
            return 0L;
        }
    }

    // Level / world access methods

    public static Object getBlockStateIfLoadedAndInBounds(Object level, Object blockPos) {
        if (!initialized || initFailed || levelGetBlockStateIfLoadedAndInBounds == null) {
            return null;
        }
        try {
            return levelGetBlockStateIfLoadedAndInBounds.invoke(level, blockPos);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object getBlockStateIfLoaded(Object level, Object blockPos) {
        if (!initialized || initFailed || levelGetBlockStateIfLoaded == null) {
            return null;
        }
        try {
            return levelGetBlockStateIfLoaded.invoke(level, blockPos);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isLoadedAndInBounds(Object level, Object blockPos) {
        if (!initialized || initFailed || levelIsLoadedAndInBounds == null) {
            return false;
        }
        try {
            return (boolean) levelIsLoadedAndInBounds.invoke(level, blockPos);
        } catch (Throwable t) {
            return false;
        }
    }

    public static Object getBlockState(Object blockGetter, Object blockPos) {
        if (!initialized || initFailed || blockGetterGetBlockState == null) {
            return null;
        }
        try {
            return blockGetterGetBlockState.invoke(blockGetter, blockPos);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Boolean isWithinWorldBorder(Object level, Object blockPos) {
        if (!initialized || initFailed || levelGetWorldBorder == null || worldBorderIsWithinBounds == null) {
            return null;
        }
        try {
            Object worldBorder = levelGetWorldBorder.invoke(level);
            if (worldBorder == null) {
                return null;
            }
            return (boolean) worldBorderIsWithinBounds.invoke(worldBorder, blockPos);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object getChunkPos(Object chunk) {
        if (!initialized || initFailed || chunkAccessGetPos == null) {
            return null;
        }
        try {
            return chunkAccessGetPos.invoke(chunk);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Integer getChunkPosX(Object chunkPos) {
        if (!initialized || initFailed || chunkPosXField == null || chunkPos == null) {
            return null;
        }
        try {
            return chunkPosXField.getInt(chunkPos);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Integer getChunkPosZ(Object chunkPos) {
        if (!initialized || initFailed || chunkPosZField == null || chunkPos == null) {
            return null;
        }
        try {
            return chunkPosZField.getInt(chunkPos);
        } catch (Throwable t) {
            return null;
        }
    }

    // PoiAccess methods

    public static void findNearestPoiPositions(Object poiManager, Object villagePlaceType, Object positionPredicate,
                                               Object sourcePosition, int range, double maxDistanceSquared,
                                               Object occupancy, boolean load, int max, Object ret) {
        try {
            poiAccessFindNearest.invoke(poiManager, villagePlaceType, positionPredicate, sourcePosition,
                range, maxDistanceSquared, occupancy, load, max, ret);
        } catch (Throwable t) {
            log("findNearestPoiPositions failed: " + t.getMessage());
        }
    }

    public static Object findAnyPoiPosition(Object poiManager, Object villagePlaceType, Object positionPredicate,
                                            Object sourcePosition, int range, Object occupancy, boolean load) {
        try {
            return poiAccessFindAny.invoke(poiManager, villagePlaceType, positionPredicate, sourcePosition,
                range, occupancy, load);
        } catch (Throwable t) {
            log("findAnyPoiPosition failed: " + t.getMessage());
            return null;
        }
    }

    public static Object findClosestPoiDataPosition(Object poiManager, Object villagePlaceType, Object positionPredicate,
                                                    Object sourcePosition, int range, double maxDistanceSquared,
                                                    Object occupancy, boolean load) {
        try {
            return poiAccessFindClosest.invoke(poiManager, villagePlaceType, positionPredicate, sourcePosition,
                range, maxDistanceSquared, occupancy, load);
        } catch (Throwable t) {
            log("findClosestPoiDataPosition failed: " + t.getMessage());
            return null;
        }
    }

    public static Object findClosestPoiDataTypeAndPosition(Object poiManager, Object villagePlaceType, Object positionPredicate,
                                                           Object sourcePosition, int range, double maxDistanceSquared,
                                                           Object occupancy, boolean load) {
        try {
            return poiAccessFindClosestWithType.invoke(poiManager, villagePlaceType, positionPredicate, sourcePosition,
                range, maxDistanceSquared, occupancy, load);
        } catch (Throwable t) {
            log("findClosestPoiDataTypeAndPosition failed: " + t.getMessage());
            return null;
        }
    }

    public static Object getPoiType(Object poiManager, Object blockPos) {
        if (!initialized || initFailed || poiManagerGetType == null || poiManager == null || blockPos == null) {
            return Optional.empty();
        }
        try {
            return poiManagerGetType.invoke(poiManager, blockPos);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public static boolean poiManagerExists(Object poiManager, Object blockPos, Object predicate) {
        if (!initialized || initFailed || poiManagerExists == null || poiManager == null || blockPos == null) {
            return false;
        }
        try {
            return (boolean) poiManagerExists.invoke(poiManager, blockPos, predicate);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void log(String message) {
        System.out.println("[NmsReflect] " + message);
    }
}
