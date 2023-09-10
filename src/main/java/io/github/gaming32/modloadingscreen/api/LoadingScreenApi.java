package io.github.gaming32.modloadingscreen.api;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.awt.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static io.github.gaming32.modloadingscreen.ModLoadingScreen.ACTUAL_LOADING_SCREEN;

public class LoadingScreenApi {
    private static final boolean AVAILABLE;
    private static final MethodHandle FINAL_ENTRYPOINTS;
    private static final MethodHandle IS_HEADLESS;
    private static final MethodHandle ENABLE_IPC;
    private static final MethodHandle PROGRESS;
    private static final MethodHandle IS_OPEN;

    static {
        boolean available = true;
        MethodHandle finalEntrypoints = null;
        MethodHandle isHeadless = null;
        MethodHandle enableIpc = null;
        MethodHandle progress = null;
        MethodHandle isOpen = null;

        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            final Class<?> alsClass = ClassLoader.getSystemClassLoader().loadClass(
                ACTUAL_LOADING_SCREEN.replace('/', '.')
            );

            finalEntrypoints = lookup.findStaticGetter(alsClass, "FINAL_ENTRYPOINTS", Set.class);
            isHeadless = lookup.findStaticGetter(alsClass, "IS_HEADLESS", boolean.class);
            enableIpc = lookup.findStaticGetter(alsClass, "ENABLE_IPC", boolean.class);
            progress = lookup.findStaticGetter(alsClass, "progress", Map.class);
            isOpen = lookup.findStatic(alsClass, "isOpen", MethodType.methodType(boolean.class));
        } catch (Exception e) {
            available = false;

            final String message = "[ModLoadingScreen] Failed to load LoadingScreenApi. Using a no-op implementation.";
            if (FabricLoader.getInstance().isModLoaded("mod-loading-screen")) {
                System.err.println(message);
                e.printStackTrace();
            } else {
                // This API could be called with Mod Loading Screen simply absent, in which case this is *not* an error
                // condition
                System.out.println(message);
            }
        }

        AVAILABLE = available;
        FINAL_ENTRYPOINTS = finalEntrypoints;
        IS_HEADLESS = isHeadless;
        ENABLE_IPC = enableIpc;
        PROGRESS = progress;
        IS_OPEN = isOpen;
    }

    /**
     * Checks if Mod Loading Screen is installed and the API loaded successfully. If this method returns {@code false},
     * then the API has been replaced with a no-op implementation. One specific note is that {@link #invokeEntrypoint}
     * will work anyway, but simply won't show any loading progress.
     * @return {@code true} if a real implementation is active
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Invokes an entrypoint with a clean API. If Mod Loading Screen is available, its progress will show up in the
     * loading screen. If you are developing a Quilt mod, you should use {@code EntrypointUtil} instead.
     * @throws net.fabricmc.loader.api.EntrypointException If any entrypoints threw an exception
     */
    public static <T> void invokeEntrypoint(String name, Class<T> type, Consumer<? super T> invoker) {
        EntrypointUtils.invoke(name, type, invoker);
    }

    /**
     * Returns a set of "final entrypoint" names. "Final entrypoints" are entrypoints that, when finished invoking,
     * will close the loading screen. You can use the return value to add or remove entrypoints so that they don't
     * exit the loading screen, and you can add your own entrypoints that should close the loading screen instead. Be
     * careful about mod compatibility when using this!
     * @apiNote If {@link #isAvailable} returns {@code false}, this will return an empty {@link HashSet} that does
     * nothing when modified, and changes to it will not be seen by other mods.
     * @return The mutable set of "final entrypoints".
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getFinalEntrypoints() {
        if (FINAL_ENTRYPOINTS == null) {
            return new HashSet<>();
        }
        try {
            return (Set<String>)FINAL_ENTRYPOINTS.invoke();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    /**
     * Returns whether the Mod Loading Screen (and the game in general) is running in a headless environment. If
     * {@link #isAvailable} returns {@code false}, this will return the value of
     * {@link GraphicsEnvironment#isHeadless}.
     * @return {@code true} if running in a headless environment.
     * @see GraphicsEnvironment#isHeadless
     */
    public static boolean isHeadless() {
        if (IS_HEADLESS == null) {
            return GraphicsEnvironment.isHeadless();
        }
        try {
            return (boolean)IS_HEADLESS.invoke();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    /**
     * Returns whether IPC is being used for the loading screen, and hasn't been disabled with
     * {@code mod-loading-screen.disableIpc}. If {@link #isHeadless} returns {@code true}, this will return
     * {@code false}. If {@link #isAvailable} returns {@code false}, this will return false.
     * @return {@code true} IPC is being used for the loading screen.
     * @deprecated Non-IPC may be removed in the future, at which point this will always return the opposite of
     * {@link #isHeadless}
     */
    @Deprecated
    public static boolean isUsingIpc() {
        if (ENABLE_IPC == null) {
            return false;
        }
        try {
            return (boolean)ENABLE_IPC.invoke();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> getProgress() {
        if (PROGRESS == null) {
            return Collections.emptyMap();
        }
        try {
            return (Map<String, Integer>)PROGRESS.invoke();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    /**
     * Returns an {@link Set} of progress bar names. This will be updated dynamically when bars are updated.
     */
    @UnmodifiableView
    public static Set<String> getActiveProgressBars() {
        final Set<String> bars = getProgress().keySet();
        return bars == Collections.EMPTY_SET ? bars : Collections.unmodifiableSet(bars);
    }

    /**
     * Returns the current progress of a progress bar, or {@code null} if there is no such progress bar.
     * @param barName The name of the progress bar. In the case of entrypoints, this is the name of the entrypoint.
     */
    @Nullable
    public static Integer getProgress(String barName) {
        return getProgress().get(barName);
    }

    // TODO: Put a note in this when custom progress bars are added.
    /**
     * Returns whether a loading screen is currently active.
     * @return {@code true} if there is a loading screen open.
     */
    public static boolean isOpen() {
        if (IS_OPEN == null) {
            return false;
        }
        try {
            return (boolean)IS_OPEN.invoke();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R rethrow(Throwable t) throws T {
        throw (T)t;
    }
}
