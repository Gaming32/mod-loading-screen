package io.github.gaming32.modloadingscreen.api;

import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.awt.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.function.Consumer;

public final class LoadingScreenApi {
    static final Map<String, CustomProgressBar> CUSTOM_PROGRESS_BARS = new HashMap<>();

    private static final long FEATURES;
    private static final MethodHandle FINAL_ENTRYPOINTS;
    private static final MethodHandle IS_HEADLESS;
    private static final MethodHandle ENABLE_IPC;
    private static final MethodHandle PROGRESS;
    private static final MethodHandle IS_OPEN;
    private static final MethodHandle CREATE_CUSTOM_PROGRESS_BAR;
    private static final MethodHandle CUSTOM_PROGRESS_BAR_OP;

    private static final MethodHandle FABRIC_0_14_23_INVOKE_ENTRYPOINTS;

    static {
        long features = 0;
        MethodHandle finalEntrypoints = null;
        MethodHandle isHeadless = null;
        MethodHandle enableIpc = null;
        MethodHandle progress = null;
        MethodHandle isOpen = null;
        MethodHandle createCustomProgressBar = null;
        MethodHandle customProgressBarOp = null;

        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            final Class<?> alsClass = ClassLoader.getSystemClassLoader().loadClass(
                "io.github.gaming32.modloadingscreen.ActualLoadingScreen"
            );

            try {
                finalEntrypoints = lookup.findStaticGetter(alsClass, "FINAL_ENTRYPOINTS", Set.class);
                features |= AvailableFeatures.FINAL_ENTRYPOINTS;
            } catch (Exception e) {
                loadFailed(">=1.0.3", AvailableFeatures.FINAL_ENTRYPOINTS, e);
            }

            try {
                isHeadless = lookup.findStaticGetter(alsClass, "IS_HEADLESS", boolean.class);
                features |= AvailableFeatures.HEADLESS_CHECK;
            } catch (Exception e) {
                loadFailed(">=1.0.3", AvailableFeatures.HEADLESS_CHECK, e);
            }

            try {
                enableIpc = lookup.findStaticGetter(alsClass, "ENABLE_IPC", boolean.class);
                features |= AvailableFeatures.IPC_CHECK;
            } catch (Exception e) {
                loadFailed(">=1.0.3", AvailableFeatures.IPC_CHECK, e);
            }

            try {
                progress = lookup.findStaticGetter(alsClass, "progress", Map.class);
                features |= AvailableFeatures.GET_PROGRESS;
            } catch (Exception e) {
                loadFailed(">=1.0.3", AvailableFeatures.GET_PROGRESS, e);
            }

            try {
                isOpen = lookup.findStatic(alsClass, "isOpen", MethodType.methodType(boolean.class));
                features |= AvailableFeatures.OPEN_CHECK;
            } catch (Exception e) {
                loadFailed(">=1.0.3", AvailableFeatures.OPEN_CHECK, e);
            }

            try {
                createCustomProgressBar = lookup.findStatic(
                    alsClass, "createCustomProgressBar",
                    MethodType.methodType(void.class, String.class, String.class, int.class)
                );
                customProgressBarOp = lookup.findStatic(
                    alsClass, "customProgressBarOp",
                    MethodType.methodType(void.class, String[].class)
                );
                features |= AvailableFeatures.CUSTOM_PROGRESS_BARS;
            } catch (Exception e) {
                createCustomProgressBar = null;
                loadFailed(">=1.0.4", AvailableFeatures.CUSTOM_PROGRESS_BARS, e);
            }

            System.out.println("[ModLoadingScreen] API loaded with features: " + AvailableFeatures.toString(features));
        } catch (Exception e) {
            final String message = "[ModLoadingScreen] Failed to load LoadingScreenApi. No API features are available.";
            if (FabricLoader.getInstance().isModLoaded("mod-loading-screen")) {
                System.err.println(message);
                e.printStackTrace();
            } else {
                // This API could be called with Mod Loading Screen simply absent, in which case this is *not* an error
                // condition
                System.out.println(message);
                System.out.println("[ModLoadingScreen] This is not an error, because Mod Loading Screen is not installed.");
            }
        }

        FEATURES = features;
        FINAL_ENTRYPOINTS = finalEntrypoints;
        IS_HEADLESS = isHeadless;
        ENABLE_IPC = enableIpc;
        PROGRESS = progress;
        IS_OPEN = isOpen;
        CREATE_CUSTOM_PROGRESS_BAR = createCustomProgressBar;
        CUSTOM_PROGRESS_BAR_OP = customProgressBarOp;

        MethodHandle invokeEntrypoints = null;
        try {
            invokeEntrypoints = lookup.findVirtual(
                FabricLoader.class, "invokeEntrypoints",
                MethodType.methodType(void.class, String.class, Class.class, Consumer.class)
            );
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            System.err.println("[ModLoadingScreen] Failed to find FabricLoader.invokeEntrypoints");
            e.printStackTrace();
        }
        FABRIC_0_14_23_INVOKE_ENTRYPOINTS = invokeEntrypoints;
    }

    private static void loadFailed(String mlsVersionRequired, long feature, Exception e) {
        FabricLoader.getInstance()
            .getModContainer("mod-loading-screen")
            .ifPresent(container -> {
                try {
                    final Version version = container.getMetadata().getVersion();
                    if (VersionPredicate.parse(mlsVersionRequired).test(version)) {
                        System.err.println(
                            "[ModLoadingScreen] Failed to load feature \"" +
                                AvailableFeatures.toString(feature) +
                                "\" from the API!"
                        );
                        System.err.println("[ModLoadingScreen] This should not have happened on the version " + version);
                        System.err.println("[ModLoadingScreen] This feature should be compatible with " + mlsVersionRequired);
                        e.printStackTrace();
                    }
                } catch (VersionParsingException versionParsingException) {
                    throw new RuntimeException(versionParsingException);
                }
            });
    }

    private LoadingScreenApi() {
    }

    /**
     * Returns the features of the API that are available to use, as a bit mask of flags from
     * {@link AvailableFeatures}. Any features not available will default to no-op fallback implementations.
     *
     * @see AvailableFeatures
     */
    public static long getFeatures() {
        return FEATURES;
    }

    /**
     * Returns whether the bitmask of features is supported.
     *
     * @param requestedFeatures A bitmask of feature flags from {@link AvailableFeatures}
     *
     * @return Whether all the requested features are available
     *
     * @see AvailableFeatures
     * @see AvailableFeatures#hasFeatures
     */
    public static boolean hasFeatures(long requestedFeatures) {
        return AvailableFeatures.hasFeatures(FEATURES, requestedFeatures);
    }

    /**
     * Invokes an entrypoint with a clean API. If Mod Loading Screen is available, its progress will show up in the
     * loading screen. If you are developing a Quilt mod, you should use {@code EntrypointUtil} instead.
     *
     * @throws RuntimeException If any entrypoints threw an exception
     *
     * @apiNote This feature is <i>always</i> available, regardless of the return value of {@link #getFeatures}.
     * Calling this without Mod Loading Screen will work always, but just won't show up in the (non-existent) loading
     * screen.
     */
    public static <T> void invokeEntrypoint(String name, Class<T> type, Consumer<? super T> invoker) throws RuntimeException {
        if (FABRIC_0_14_23_INVOKE_ENTRYPOINTS != null) {
            try {
                FABRIC_0_14_23_INVOKE_ENTRYPOINTS.invoke(name, type, invoker);
            } catch (Throwable t) {
                rethrow(t);
            }
            return;
        }
        try {
            EntrypointUtils.invoke(name, type, invoker);
        } catch (RuntimeException e) {
            // Quilt bug! Quilt's EntrypointExceptions are never converted to Fabric's!
            // https://github.com/QuiltMC/quilt-loader/issues/366
            // Fixed in Quilt since 0.21.0-beta.4. Fix kept for backwards compat.
            Class<?> clazz = e.getClass();
            while (clazz != null) {
                if (clazz.getName().equals("org.quiltmc.loader.api.entrypoint.EntrypointException")) {
                    try {
                        throw new EntrypointException((String)clazz.getDeclaredMethod("getKey").invoke(e), e);
                    } catch (ReflectiveOperationException ex) {
                        e.addSuppressed(ex);
                        throw e;
                    }
                }
                clazz = clazz.getSuperclass();
            }
            throw e;
        }
    }

    /**
     * Returns a set of "final entrypoint" names. "Final entrypoints" are entrypoints that, when finished invoking,
     * will close the loading screen. You can use the return value to add or remove entrypoints so that they don't
     * exit the loading screen, and you can add your own entrypoints that should close the loading screen instead. Be
     * careful about mod compatibility when using this!
     *
     * @apiNote If {@link #getFeatures} doesn't include {@link AvailableFeatures#FINAL_ENTRYPOINTS}, this will return
     * an empty {@link Set} that does nothing when modified, and changes to it will not be seen by other mods.
     *
     * @return The mutable set of "final entrypoints".
     *
     * @see AvailableFeatures#FINAL_ENTRYPOINTS
     *
     * @since 1.0.3
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getFinalEntrypoints() {
        if (FINAL_ENTRYPOINTS == null) {
            return new HashSet<>();
        }
        try {
            return (Set<String>)FINAL_ENTRYPOINTS.invokeExact();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    /**
     * Returns whether the Mod Loading Screen (and the game in general) is running in a headless environment. If
     * {@link #getFeatures} doesn't include {@link AvailableFeatures#HEADLESS_CHECK}, this will return the value of
     * {@link GraphicsEnvironment#isHeadless}.
     *
     * @return {@code true} if running in a headless environment.
     *
     * @see GraphicsEnvironment#isHeadless
     * @see AvailableFeatures#HEADLESS_CHECK
     *
     * @since 1.0.3
     */
    public static boolean isHeadless() {
        if (IS_HEADLESS == null) {
            return GraphicsEnvironment.isHeadless();
        }
        try {
            return (boolean)IS_HEADLESS.invokeExact();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    /**
     * Returns whether IPC is being used for the loading screen, and hasn't been disabled with
     * {@code mod-loading-screen.disableIpc}. If {@link #isHeadless} returns {@code true}, this will return
     * {@code false}. If {@link #getFeatures} doesn't include {@link AvailableFeatures#IPC_CHECK}, this will return
     * {@code false}.
     *
     * @return {@code true} IPC is being used for the loading screen.
     *
     * @deprecated Non-IPC may be removed in the future, at which point this will always return the opposite of
     * {@link #isHeadless}
     *
     * @see AvailableFeatures#IPC_CHECK
     *
     * @since 1.0.3
     */
    @Deprecated
    public static boolean isUsingIpc() {
        if (ENABLE_IPC == null) {
            return false;
        }
        try {
            return (boolean)ENABLE_IPC.invokeExact();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> getAllProgress() {
        if (PROGRESS == null) {
            return Collections.emptyMap();
        }
        try {
            return (Map<String, Integer>)PROGRESS.invokeExact();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    /**
     * Returns an {@link Set} of progress bar names. This will be updated dynamically when bars are updated. If
     * {@link #getFeatures} doesn't include {@link AvailableFeatures#GET_PROGRESS}, this will return an empty set.
     *
     * @apiNote Custom progress bars start with {@code "custom:"}, followed by their ID. If {@link #getFeatures}
     * includes {@link AvailableFeatures#CUSTOM_PROGRESS_BARS}, entrypoint progress bars start with
     * {@code "entrypoint:"}
     *
     * @see AvailableFeatures#GET_PROGRESS
     *
     * @since 1.0.3
     */
    @UnmodifiableView
    public static Set<String> getActiveProgressBars() {
        final Set<String> bars = getAllProgress().keySet();
        return bars == Collections.EMPTY_SET ? bars : Collections.unmodifiableSet(bars);
    }

    /**
     * Returns the current progress of a progress bar, or {@code null} if there is no such progress bar. If
     * {@link #getFeatures} doesn't include {@link AvailableFeatures#GET_PROGRESS}, this will always return
     * {@code null}.
     *
     * @param barName The name of the progress bar. In the case of entrypoints, this is the name of the entrypoint.
     *
     * @apiNote Custom progress bars start with {@code "custom:"}, followed by their ID. If {@link #getFeatures}
     * includes {@link AvailableFeatures#CUSTOM_PROGRESS_BARS}, entrypoint progress bars start with
     * {@code "entrypoint:"}
     *
     * @see AvailableFeatures#GET_PROGRESS
     *
     * @since 1.0.3
     */
    @Nullable
    public static Integer getProgress(@NotNull String barName) {
        return getAllProgress().get(barName);
    }

    /**
     * Returns whether a loading screen is currently active. If {@link #getFeatures} doesn't include
     * {@link AvailableFeatures#OPEN_CHECK}, this will always return {@code false}.
     *
     * @return {@code true} if there is a loading screen open.
     *
     * @see AvailableFeatures#OPEN_CHECK
     *
     * @since 1.0.3
     */
    public static boolean isOpen() {
        if (IS_OPEN == null) {
            return false;
        }
        try {
            return (boolean)IS_OPEN.invokeExact();
        } catch (Throwable t) {
            return rethrow(t);
        }
    }

    /**
     * Creates a custom progress bar.
     * @param title The title of the progress bar. This is the full string to display.
     * @param max The maximum value of the progress bar. It will <i>not</i> be removed automatically when this is
     *            reached.
     * @return The {@link CustomProgressBar} reference to the progress bar.
     */
    public static CustomProgressBar getCustomProgressBar(@NotNull String id, @NotNull String title, int max) {
        CustomProgressBar bar = CUSTOM_PROGRESS_BARS.get(id);
        if (bar == null) {
            CUSTOM_PROGRESS_BARS.put(id, bar = createCustomProgressBar(id, title, max));
        }
        bar.setTitle(title);
        bar.setMaximum(max);
        return bar;
    }

    private static CustomProgressBar createCustomProgressBar(String id, String title, int max) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        if (CREATE_CUSTOM_PROGRESS_BAR == null) {
            return new CustomProgressBar(id, false, title, max);
        }
        try {
            CREATE_CUSTOM_PROGRESS_BAR.invokeExact(id, title, max);
        } catch (Throwable t) {
            rethrow(t);
        }
        return new CustomProgressBar(id, true, title, max);
    }

    static void customProgressBarOp(String... args) {
        if (CUSTOM_PROGRESS_BAR_OP == null) return;
        try {
            CUSTOM_PROGRESS_BAR_OP.invokeExact(args);
        } catch (Throwable t) {
            rethrow(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R rethrow(Throwable t) throws T {
        throw (T)t;
    }
}
