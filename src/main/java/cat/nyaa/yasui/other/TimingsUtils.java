package cat.nyaa.yasui.other;

import cat.nyaa.nyaacore.utils.ReflectionUtils;
import cat.nyaa.yasui.I18n;
import cat.nyaa.yasui.Yasui;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TimingsUtils {
    public static boolean isSpigot = false;
    public static boolean isPaper = false;
    private static Class<?> paper_TimingsManager;
    private static Field paper_TimingsManager_TIMING_MAP;
    private static Class<?> paper_TimingIdentifier;
    private static Class<?> paper_TimingHandler;
    private static Field paper_TimingIdentifier_name;
    private static Class<?> paper_TimingData;
    private static Field paper_TimingHandler_record;
    private static Field paper_TimingData_totalTime;
    private static Field paper_TimingData_count;
    private static Class<?> nms_World;
    private static Field nms_World_timings;

    private static Class<?> spigot_CustomTimingsHandler;
    private static Field spigot_CustomTimingsHandler_totalTime;
    private static Field spigot_CustomTimingsHandler_count;
    private static Field spigot_CustomTimingsHandler_name;
    private static Class<?> spigot_SpigotTimings;
    private static Field spigot_SpigotTimings_entityTypeTimingMap;
    private static Field spigot_SpigotTimings_tileEntityTypeTimingMap;
    private static Field paper_TimingHandler_identifier;

    public static void init() {
        if (isPaper || isSpigot) {
            return;
        }
        try {
            nms_World = Class.forName("net.minecraft.world.level.World");
            nms_World_timings = nms_World.getField("timings");
            paper_TimingsManager = Class.forName("co.aikar.timings.TimingsManager");
            paper_TimingsManager_TIMING_MAP = paper_TimingsManager.getDeclaredField("TIMING_MAP");
            paper_TimingsManager_TIMING_MAP.setAccessible(true);
            paper_TimingIdentifier = Class.forName("co.aikar.timings.TimingIdentifier");
            paper_TimingIdentifier_name = paper_TimingIdentifier.getDeclaredField("name");
            paper_TimingIdentifier_name.setAccessible(true);
            paper_TimingHandler = Class.forName("co.aikar.timings.TimingHandler");
            paper_TimingHandler_identifier = paper_TimingHandler.getDeclaredField("identifier");
            paper_TimingHandler_identifier.setAccessible(true);
            paper_TimingData = Class.forName("co.aikar.timings.TimingData");
            paper_TimingHandler_record = paper_TimingHandler.getDeclaredField("record");
            paper_TimingHandler_record.setAccessible(true);
            paper_TimingData_totalTime = paper_TimingData.getDeclaredField("totalTime");
            paper_TimingData_count = paper_TimingData.getDeclaredField("count");
            paper_TimingData_totalTime.setAccessible(true);
            paper_TimingData_count.setAccessible(true);
            isPaper = true;
            return;
        } catch (ClassNotFoundException e) {
            //e.printStackTrace();
        } catch (NoSuchFieldException e) {
            //e.printStackTrace();
        }
        try {
            spigot_SpigotTimings = ReflectionUtils.getOBCClass("SpigotTimings");
            spigot_SpigotTimings_entityTypeTimingMap = spigot_SpigotTimings.getField("entityTypeTimingMap");
            spigot_SpigotTimings_tileEntityTypeTimingMap = spigot_SpigotTimings.getField("tileEntityTypeTimingMap");
            spigot_CustomTimingsHandler = Class.forName("org.spigotmc.CustomTimingsHandler");
            spigot_CustomTimingsHandler_totalTime = spigot_CustomTimingsHandler.getDeclaredField("totalTime");
            spigot_CustomTimingsHandler_totalTime.setAccessible(true);
            spigot_CustomTimingsHandler_count = spigot_CustomTimingsHandler.getDeclaredField("count");
            spigot_CustomTimingsHandler_count.setAccessible(true);
            spigot_CustomTimingsHandler_name = spigot_CustomTimingsHandler.getDeclaredField("name");
            spigot_CustomTimingsHandler_name.setAccessible(true);
            isSpigot = true;
        } catch (ClassNotFoundException e) {
            //e.printStackTrace();
        } catch (NoSuchFieldException e) {
            //e.printStackTrace();
        }
    }

    static public void printWorldTimings(World world, CommandSender sender) {
        init();
        try {
            Object worldTimings = getWorldTimings(world);
            Map<Object, Long> tmp = new HashMap<>();
            for (Field f : worldTimings.getClass().getFields()) {
                Object v = f.get(worldTimings);
                if (v.getClass() == spigot_CustomTimingsHandler) {
                    tmp.put(f, (Long) spigot_CustomTimingsHandler_totalTime.get(v));
                } else if (v.getClass() == paper_TimingHandler) {
                    Object record = paper_TimingHandler_record.get(v);
                    tmp.put(f, (Long) paper_TimingData_totalTime.get(record));
                }
            }
            for (Object obj : sortByValue(tmp)) {
                Field f = (Field) obj;
                Object v = f.get(worldTimings);
                if (v.getClass() == spigot_CustomTimingsHandler) {
                    sendData(sender, (String) spigot_CustomTimingsHandler_name.get(v), spigot_CustomTimingsHandler_count.get(v), (Long) spigot_CustomTimingsHandler_totalTime.get(v));
                } else if (v.getClass() == paper_TimingHandler) {
                    Object record = paper_TimingHandler_record.get(v);
                    sendData(sender, (String) paper_TimingIdentifier_name.get(paper_TimingHandler_identifier.get(v)), paper_TimingData_count.get(record), (Long) paper_TimingData_totalTime.get(record));
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static void printSpigotTimingsMap(CommandSender sender, Object obj) throws IllegalAccessException {
        Map<String, Object> map = (Map<String, Object>) obj;
        HashMap<Object, Long> tmp = new HashMap<>();
        for (String k : map.keySet()) {
            tmp.put(k, (Long) spigot_CustomTimingsHandler_totalTime.get(map.get(k)));
        }
        for (Object k : sortByValue(tmp)) {
            Object v = map.get(k);
            sendData(sender, (String) spigot_CustomTimingsHandler_name.get(v), spigot_CustomTimingsHandler_count.get(v), (Long) spigot_CustomTimingsHandler_totalTime.get(v));
        }
    }

    public static void printEntityTimings(CommandSender sender) {
        init();
        if (isPaper) {
            printPaperTimings("## tickEntity ", sender);
        } else if (isSpigot) {
            try {
                printSpigotTimingsMap(sender, spigot_SpigotTimings_entityTypeTimingMap.get(null));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static void printTileEntityTimings(CommandSender sender) {
        init();
        if (isPaper) {
            printPaperTimings("## tickTileEntity ", sender);
        } else if (isSpigot) {
            try {
                printSpigotTimingsMap(sender, spigot_SpigotTimings_tileEntityTypeTimingMap.get(null));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private static void printPaperTimings(String prefix, CommandSender sender) {
        try {
            Map<Object, Object> TIMING_MAP = (Map<Object, Object>) paper_TimingsManager_TIMING_MAP.get(null);
            Map<Object, Long> tmp = new HashMap<>();
            for (Object k : TIMING_MAP.keySet()) {
                String id = (String) paper_TimingIdentifier_name.get(k);
                if (id.startsWith(prefix)) {
                    Object record = paper_TimingHandler_record.get(TIMING_MAP.get(k));
                    tmp.put(k, (Long) paper_TimingData_totalTime.get(record));
                }
            }
            for (Object k : sortByValue(tmp)) {
                Object v = TIMING_MAP.get(k);
                Object record = paper_TimingHandler_record.get(v);
                sendData(sender, (String) paper_TimingIdentifier_name.get(paper_TimingHandler_identifier.get(v)), paper_TimingData_count.get(record), (Long) paper_TimingData_totalTime.get(record));
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static void sendData(CommandSender sender, String id, Object count, long time) {
        if (isPaper) {
            id = id.replaceAll("net.minecraft.server." + ReflectionUtils.getVersion(), "nms.");
        }
        sender.sendMessage(I18n.format("user.timings.data", id, count, TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS)));
    }

    private static Object getWorldTimings(World bukkitWorld) {
        Class<?> craftWorld = ReflectionUtils.getOBCClass("CraftWorld");
        Method getHandle = ReflectionUtils.getMethod(craftWorld, "getHandle");
        try {
            return nms_World_timings.get(getHandle.invoke(bukkitWorld));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Set<Object> sortByValue(Map<Object, Long> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(Yasui.INSTANCE.config.top_listing)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new)).keySet();
    }
}
