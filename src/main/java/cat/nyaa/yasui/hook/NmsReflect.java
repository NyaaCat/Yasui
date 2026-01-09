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

    // Entity
    private static volatile MethodHandle entityGetId;

    // BlockPos
    private static volatile MethodHandle blockPosAsLong;

    // PoiAccess
    private static volatile MethodHandle poiAccessFindNearest;
    private static volatile MethodHandle poiManagerGetType;

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

        // Entity
        Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity", true, nmsClassLoader);
        entityGetId = lookup.findVirtual(entityClass, "getId", MethodType.methodType(int.class));

        // BlockPos
        Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos", true, nmsClassLoader);
        blockPosAsLong = lookup.findVirtual(blockPosClass, "asLong", MethodType.methodType(long.class));

        // PoiAccess (Paper-specific)
        Class<?> poiAccessClass = Class.forName("io.papermc.paper.util.PoiAccess", true, nmsClassLoader);
        Class<?> poiManagerClass = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiManager", true, nmsClassLoader);
        Class<?> occupancyClass = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiManager$Occupancy", true, nmsClassLoader);
        poiManagerGetType = lookup.findVirtual(poiManagerClass, "getType", MethodType.methodType(Optional.class, blockPosClass));
        poiAccessFindNearest = lookup.findStatic(poiAccessClass, "findNearestPoiPositions",
            MethodType.methodType(void.class,
                poiManagerClass, Predicate.class, Predicate.class, blockPosClass,
                int.class, double.class, occupancyClass, boolean.class, int.class, List.class));
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

    // BlockPos methods

    public static long blockPosAsLong(Object blockPos) {
        try {
            return (long) blockPosAsLong.invoke(blockPos);
        } catch (Throwable t) {
            return 0L;
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

    private static void log(String message) {
        System.out.println("[NmsReflect] " + message);
    }
}
