package cat.nyaa.yasui.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class YasuiAgent {
    private static final String HOPPER_CLASS = "net/minecraft/world/level/block/entity/HopperBlockEntity";
    private static final String HOPPER_METHOD = "isFullContainer";
    private static final String HOPPER_DESC = "(Lnet/minecraft/world/Container;Lnet/minecraft/core/Direction;)Z";
    // Hook uses Object and int ordinal to avoid classloader issues
    private static final String HOPPER_HOOK_DESC = "(Ljava/lang/Object;I)Z";

    private static final String PATHNAV_CLASS = "net/minecraft/world/entity/ai/navigation/PathNavigation";
    private static final String PATHNAV_METHOD = "createPath";
    private static final String PATHNAV_DESC = "(Ljava/util/Set;Lnet/minecraft/world/entity/Entity;IZIF)Lnet/minecraft/world/level/pathfinder/Path;";
    // Hook uses Object types to avoid classloader issues
    private static final String PATHNAV_HOOK_GET_DESC = "(Ljava/lang/Object;Ljava/util/Set;Ljava/lang/Object;IZIF)Ljava/lang/Object;";
    private static final String PATHNAV_HOOK_STORE_DESC = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Set;Ljava/lang/Object;IZIF)Ljava/lang/Object;";

    private static final String ACQUIRE_POI_CLASS = "net/minecraft/world/entity/ai/behavior/AcquirePoi";
    private static final String POI_ACCESS_OWNER = "io/papermc/paper/util/PoiAccess";
    private static final String POI_ACCESS_METHOD = "findNearestPoiPositions";
    private static final String POI_ACCESS_DESC = "(Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;IDLnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;ZILjava/util/List;)V";
    // Hook uses Object types to avoid classloader issues
    private static final String POI_HOOK_DESC = "(Ljava/lang/Object;Ljava/util/function/Predicate;Ljava/util/function/Predicate;Ljava/lang/Object;IDLjava/lang/Object;ZILjava/util/List;)V";
    private static final String HOOK_CLASS_PREFIX = "cat/nyaa/yasui/hook/";
    private static final List<JarFile> bootstrapJars = new ArrayList<>();

    private YasuiAgent() {}

    public static void agentmain(String agentArgs, Instrumentation inst) {
        if (agentArgs != null && !agentArgs.isBlank()) {
            appendHookJarToBootstrap(inst, agentArgs);
        }
        if (!inst.isRetransformClassesSupported()) {
            log("Retransform not supported; hooks disabled");
            return;
        }

        YasuiTransformer transformer = new YasuiTransformer();
        inst.addTransformer(transformer, true);
        try {
            retransform(inst, HOPPER_CLASS);
            retransform(inst, PATHNAV_CLASS);
            retransform(inst, ACQUIRE_POI_CLASS);

            if (transformer.hopperTransformed()) {
                markHookActive("cat.nyaa.yasui.hook.HopperFullCache", "markHookActive");
                log("Hopper full-check hook installed");
            }
            if (transformer.pathNavTransformed()) {
                markHookActive("cat.nyaa.yasui.hook.PathfindingCache", "markHookActive");
                log("Pathfinding cache hook installed");
            }
            if (transformer.poiSearchTransformed()) {
                markHookActive("cat.nyaa.yasui.hook.PoiSearchCache", "markHookActive");
                log("AcquirePoi cache hook installed");
            }
        } catch (Throwable t) {
            log("Hook install failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
        } finally {
            // Keep transformer installed to handle classes loaded after attach.
        }
    }

    private static void retransform(Instrumentation inst, String className) {
        String name = className.replace('/', '.');
        Class<?> target = null;
        for (Class<?> loaded : inst.getAllLoadedClasses()) {
            if (loaded.getName().equals(name)) {
                target = loaded;
                break;
            }
        }
        if (target == null) {
            log("Retransform skipped for " + className + ": class not loaded");
            return;
        }
        if (!inst.isModifiableClass(target)) {
            log("Retransform skipped for " + className + ": not modifiable");
            return;
        }
        try {
            inst.retransformClasses(target);
        } catch (Throwable t) {
            log("Retransform failed for " + className + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    private static void appendHookJarToBootstrap(Instrumentation inst, String jarPath) {
        try {
            File hookJar = buildHookJar(jarPath);
            JarFile jarFile = new JarFile(hookJar);
            inst.appendToBootstrapClassLoaderSearch(jarFile);
            synchronized (bootstrapJars) {
                bootstrapJars.add(jarFile);
            }
            log("Appended hook JAR to bootstrap classloader: " + hookJar.getAbsolutePath());
        } catch (Exception e) {
            log("Failed to append hook jar to bootstrap: " + e.getMessage());
        }
    }

    private static File buildHookJar(String jarPath) throws IOException {
        File sourceJar = new File(jarPath);
        if (!sourceJar.isFile()) {
            sourceJar = new File(YasuiAgent.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath());
        }
        File hookJar = File.createTempFile("yasui-hooks-", ".jar");
        hookJar.deleteOnExit();
        try (JarFile source = new JarFile(sourceJar);
             JarOutputStream out = new JarOutputStream(new FileOutputStream(hookJar))) {
            Enumeration<JarEntry> entries = source.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(HOOK_CLASS_PREFIX) || !name.endsWith(".class")) {
                    continue;
                }
                JarEntry copy = new JarEntry(name);
                out.putNextEntry(copy);
                try (java.io.InputStream in = source.getInputStream(entry)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                out.closeEntry();
            }
        }
        return hookJar;
    }

    private static void markHookActive(String className, String methodName) {
        try {
            Class<?> cacheClass = Class.forName(className, true, ClassLoader.getSystemClassLoader());
            Method method = cacheClass.getMethod(methodName);
            method.invoke(null);
        } catch (Throwable t) {
            log("Failed to mark hook active for " + className + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    private static void log(String message) {
        System.out.println("[YasuiAgent] " + message);
    }

    private static final class YasuiTransformer implements ClassFileTransformer {
        private volatile boolean hopperTransformed = false;
        private volatile boolean pathNavTransformed = false;
        private volatile boolean poiSearchTransformed = false;

        @Override
        public byte[] transform(Module module, ClassLoader loader, String className,
                                Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
            if (HOPPER_CLASS.equals(className)) {
                return transformHopper(classfileBuffer, loader);
            }
            if (PATHNAV_CLASS.equals(className)) {
                return transformPathNavigation(classfileBuffer, loader);
            }
            if (ACQUIRE_POI_CLASS.equals(className)) {
                return transformAcquirePoi(classfileBuffer, loader);
            }
            return null;
        }

        private byte[] transformHopper(byte[] classfileBuffer, ClassLoader loader) {
            try {
                ClassReader reader = new ClassReader(classfileBuffer);
                ClassWriter writer = newClassWriter(reader, loader);
                ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                    private int methodAccess = -1;
                    private boolean hasTarget = false;

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        if (HOPPER_METHOD.equals(name) && HOPPER_DESC.equals(descriptor)) {
                            hasTarget = true;
                            methodAccess = access;
                            return null;
                        }
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }

                    @Override
                    public void visitEnd() {
                        if (hasTarget) {
                            MethodVisitor mv = super.visitMethod(methodAccess, HOPPER_METHOD, HOPPER_DESC, null, null);
                            mv.visitCode();
                            // Load Container (as Object)
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            // Load Direction and call ordinal() to get int
                            mv.visitVarInsn(Opcodes.ALOAD, 1);
                            mv.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL,
                                "net/minecraft/core/Direction",
                                "ordinal",
                                "()I",
                                false
                            );
                            // Call hook with (Object, int) signature
                            mv.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "cat/nyaa/yasui/hook/HopperFullCache",
                                "isFullContainer",
                                HOPPER_HOOK_DESC,
                                false
                            );
                            mv.visitInsn(Opcodes.IRETURN);
                            mv.visitMaxs(0, 0);
                            mv.visitEnd();
                            hopperTransformed = true;
                        }
                        super.visitEnd();
                    }
                };
                reader.accept(visitor, 0);
                return writer.toByteArray();
            } catch (Throwable t) {
                log("Hopper transformer failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
                return null;
            }
        }

        private byte[] transformPathNavigation(byte[] classfileBuffer, ClassLoader loader) {
            try {
                ClassReader reader = new ClassReader(classfileBuffer);
                ClassWriter writer = newClassWriter(reader, loader);
                ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (!PATHNAV_METHOD.equals(name) || !PATHNAV_DESC.equals(descriptor)) {
                            return mv;
                        }
                        pathNavTransformed = true;
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // Load this (PathNavigation) as Object
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                // Load Set (unchanged)
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                // Load Entity as Object
                                super.visitVarInsn(Opcodes.ALOAD, 2);
                                // Load primitives
                                super.visitVarInsn(Opcodes.ILOAD, 3);
                                super.visitVarInsn(Opcodes.ILOAD, 4);
                                super.visitVarInsn(Opcodes.ILOAD, 5);
                                super.visitVarInsn(Opcodes.FLOAD, 6);
                                // Call hook with Object types
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "cat/nyaa/yasui/hook/PathfindingCache",
                                    "getCached",
                                    PATHNAV_HOOK_GET_DESC,
                                    false
                                );
                                super.visitInsn(Opcodes.DUP);
                                super.visitJumpInsn(Opcodes.IFNULL, continueLabel);
                                // Cast Object back to Path for return
                                super.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/world/level/pathfinder/Path");
                                super.visitInsn(Opcodes.ARETURN);
                                super.visitLabel(continueLabel);
                                super.visitInsn(Opcodes.POP);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.ARETURN) {
                                    // Stack has Path on top, need to call storeAndReturn
                                    // Load this (PathNavigation) as Object
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    // Load Set
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    // Load Entity as Object
                                    super.visitVarInsn(Opcodes.ALOAD, 2);
                                    // Load primitives
                                    super.visitVarInsn(Opcodes.ILOAD, 3);
                                    super.visitVarInsn(Opcodes.ILOAD, 4);
                                    super.visitVarInsn(Opcodes.ILOAD, 5);
                                    super.visitVarInsn(Opcodes.FLOAD, 6);
                                    // Call hook with Object types
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "cat/nyaa/yasui/hook/PathfindingCache",
                                        "storeAndReturn",
                                        PATHNAV_HOOK_STORE_DESC,
                                        false
                                    );
                                    // Cast Object back to Path for return
                                    super.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/world/level/pathfinder/Path");
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                };
                reader.accept(visitor, 0);
                return writer.toByteArray();
            } catch (Throwable t) {
                log("PathNavigation transformer failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
                return null;
            }
        }

        private byte[] transformAcquirePoi(byte[] classfileBuffer, ClassLoader loader) {
            try {
                ClassReader reader = new ClassReader(classfileBuffer);
                ClassWriter writer = newClassWriter(reader, loader);
                boolean[] changed = new boolean[] {false};
                ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                                if (opcode == Opcodes.INVOKESTATIC
                                    && POI_ACCESS_OWNER.equals(owner)
                                    && POI_ACCESS_METHOD.equals(name)
                                    && POI_ACCESS_DESC.equals(desc)) {
                                    changed[0] = true;
                                    // Redirect to our hook with Object-based signature
                                    super.visitMethodInsn(
                                        opcode,
                                        "cat/nyaa/yasui/hook/PoiSearchCache",
                                        name,
                                        POI_HOOK_DESC,
                                        false
                                    );
                                    return;
                                }
                                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                            }
                        };
                    }

                    @Override
                    public void visitEnd() {
                        if (changed[0]) {
                            poiSearchTransformed = true;
                        }
                        super.visitEnd();
                    }
                };
                reader.accept(visitor, 0);
                return changed[0] ? writer.toByteArray() : null;
            } catch (Throwable t) {
                log("AcquirePoi transformer failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
                return null;
            }
        }

        private boolean hopperTransformed() {
            return hopperTransformed;
        }

        private boolean pathNavTransformed() {
            return pathNavTransformed;
        }

        private boolean poiSearchTransformed() {
            return poiSearchTransformed;
        }
    }

    private static ClassWriter newClassWriter(ClassReader reader, ClassLoader loader) {
        return new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                ClassLoader resolveLoader = loader != null ? loader : ClassLoader.getSystemClassLoader();
                try {
                    Class<?> class1 = Class.forName(type1.replace('/', '.'), false, resolveLoader);
                    Class<?> class2 = Class.forName(type2.replace('/', '.'), false, resolveLoader);
                    if (class1.isAssignableFrom(class2)) {
                        return type1;
                    }
                    if (class2.isAssignableFrom(class1)) {
                        return type2;
                    }
                    if (class1.isInterface() || class2.isInterface()) {
                        return "java/lang/Object";
                    }
                    do {
                        class1 = class1.getSuperclass();
                    } while (class1 != null && !class1.isAssignableFrom(class2));
                    return class1 != null ? class1.getName().replace('.', '/') : "java/lang/Object";
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
    }
}
