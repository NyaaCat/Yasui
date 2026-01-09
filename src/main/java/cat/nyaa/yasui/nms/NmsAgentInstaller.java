package cat.nyaa.yasui.nms;

import cat.nyaa.yasui.Yasui;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

final class NmsAgentInstaller {
    private static final AtomicBoolean attempted = new AtomicBoolean(false);
    private static volatile String errorMessage;

    private NmsAgentInstaller() {}

    static boolean install(Yasui plugin) {
        if (!attempted.compareAndSet(false, true)) {
            return errorMessage == null;
        }
        try {
            Path jarPath = Path.of(plugin.getClass()
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());

            attachAgent(jarPath);
            errorMessage = null;
            return true;
        } catch (Throwable t) {
            errorMessage = t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    static String getErrorMessage() {
        return errorMessage;
    }

    private static void attachAgent(Path jarPath) throws Exception {
        String pid = Long.toString(ProcessHandle.current().pid());
        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Method attach = vmClass.getMethod("attach", String.class);
        Object vm = attach.invoke(null, pid);
        try {
            Method loadAgent = vmClass.getMethod("loadAgent", String.class, String.class);
            loadAgent.invoke(vm, jarPath.toString(), jarPath.toString());
        } finally {
            Method detach = vmClass.getMethod("detach");
            detach.invoke(vm);
        }
    }
}
