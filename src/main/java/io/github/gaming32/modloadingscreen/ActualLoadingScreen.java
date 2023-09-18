package io.github.gaming32.modloadingscreen;

import com.formdev.flatlaf.FlatDarkLaf;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import static io.github.gaming32.modloadingscreen.ModLoadingScreen.ACTUAL_LOADING_SCREEN;

public class ActualLoadingScreen {
    private static final boolean IS_IPC_CLIENT = Boolean.getBoolean("mlsipc.present");
    private static final Set<String> IGNORED_BUILTIN = new HashSet<>(Collections.singleton("java"));
    public static final Set<String> FINAL_ENTRYPOINTS = new HashSet<>(Arrays.asList(
        "client", "server", "client_init", "server_init"
    ));
    public static final boolean IS_HEADLESS = GraphicsEnvironment.isHeadless();
    public static final boolean ENABLE_IPC =
        !IS_IPC_CLIENT && !IS_HEADLESS && !Boolean.getBoolean("mod-loading-screen.disableIpc");

    // Unlike progressBars, this is populated on both the IPC client and IPC server, allowing it to be used from the API
    public static final Map<String, Integer> progress = new LinkedHashMap<>();
    private static final Map<String, JProgressBar> progressBars = new LinkedHashMap<>();
    private static JFrame dialog;
    private static JLabel label;
    private static JProgressBar memoryBar;
    private static DataOutputStream ipcOut;
    private static PrintStream logFile;
    private static Thread memoryThread;
    private static boolean titleSet;

    private static boolean runningOnQuilt;
    private static Path configDir;

    private static boolean enableMemoryDisplay = true;

    public static void startLoadingScreen(boolean fabricReady) {
        if (IS_HEADLESS) {
            println("Mod Loading Screen is on a headless environment. Only some logging will be performed.");
            return;
        }
        println("Opening loading screen");

        if (IS_IPC_CLIENT) {
            runningOnQuilt = Boolean.getBoolean("mlsipc.quilt");
            configDir = Paths.get(System.getProperty("mlsipc.config"));
        } else {
            if (fabricReady) {
                runningOnQuilt = FabricLoader.getInstance().isModLoaded("quilt_loader");
                configDir = FabricLoader.getInstance().getConfigDir().resolve("mod-loading-screen");
            } else {
                // This code is certainly something. I don't have any ideas for improvement, though.
                final String javaCommand = System.getProperty("sun.java.command");
                final int spaceIndex = javaCommand.indexOf(' ');
                String mainClass = spaceIndex == -1 ? javaCommand : javaCommand.substring(0, spaceIndex);
                if (mainClass.equals("net.fabricmc.devlaunchinjector.Main")) { // Fabric + Quilt Loom
                    mainClass = System.getProperty("fabric.dli.main");
                }
                runningOnQuilt = mainClass.contains(".quiltmc.");

                configDir = Paths.get("config/mod-loading-screen").toAbsolutePath(); // Not the most robust, I know
            }
        }

        FINAL_ENTRYPOINTS.add(runningOnQuilt ? "quilt_loader" : "fabricloader");

        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            println("Failed to create config dir", e);
        }
        loadConfig();

        if (ENABLE_IPC) {
            final Path runDir = FabricLoader.getInstance().getGameDir().resolve(".cache/mod-loading-screen");
            final Path flatlafDestPath = runDir.resolve("flatlaf.jar");
            try {
                Files.createDirectories(flatlafDestPath.getParent());
                Files.copy(
                    FabricLoader.getInstance()
                        .getModContainer("mod-loading-screen")
                        .orElseThrow(AssertionError::new)
                        .getRootPaths().get(0)
                        .resolve("META-INF/jars/flatlaf-3.0.jar"),
                    flatlafDestPath, StandardCopyOption.REPLACE_EXISTING
                );
                println("Extracted flatlaf.jar");
                ipcOut = new DataOutputStream(
                    new ProcessBuilder(
                        System.getProperty("java.home") + "/bin/java",
                        "-Dmlsipc.present=true",
                        "-Dmlsipc.quilt=" + runningOnQuilt,
                        "-Dmlsipc.config=" + configDir,
                        "-cp", String.join(
                            File.pathSeparator,
                            FabricLoader.getInstance()
                                .getModContainer("mod-loading-screen")
                                .orElseThrow(AssertionError::new)
                                .getOrigin()
                                .getPaths().get(0)
                                .toString(),
                            flatlafDestPath.toString()
                        ),
                        ACTUAL_LOADING_SCREEN.replace('/', '.')
                    )
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .redirectInput(ProcessBuilder.Redirect.PIPE)
                        .directory(runDir.toFile())
                        .start()
                        .getOutputStream()
                );
            } catch (Exception e) {
                println("Failed to setup IPC client. Aborting.", e);
                return;
            }
            if (fabricReady) {
                setFabricTitle();
            }
            startMemoryThread();
            return;
        }

        FlatDarkLaf.setup();
        UIManager.getDefaults().put("ProgressBar.horizontalSize", new Dimension(146, 18));
        UIManager.getDefaults().put("ProgressBar.font", UIManager.getFont("ProgressBar.font").deriveFont(18f));
        UIManager.getDefaults().put("ProgressBar.selectionForeground", new Color(255, 255, 255));

        dialog = new JFrame();
        if (fabricReady) {
            setFabricTitle();
        } else {
            dialog.setTitle(runningOnQuilt ? "Loading Quilt" : "Loading Fabric");
        }
        dialog.setResizable(false);

        try {
            dialog.setIconImage(ImageIO.read(
                ClassLoader.getSystemResource("assets/mod-loading-screen/icon.png"))
            );
        } catch (Exception e) {
            println("Failed to load icon.png", e);
        }

        ImageIcon icon;
        try {
            final Path backgroundPath = configDir.resolve("background.png");
            icon = new ImageIcon(
                Files.exists(backgroundPath)
                    ? backgroundPath.toUri().toURL()
                    : ClassLoader.getSystemResource("assets/mod-loading-screen/" + (runningOnQuilt ? "quilt-banner.png" : "aof4.png"))
            );
            icon.setImage(icon.getImage().getScaledInstance(960, 540, Image.SCALE_SMOOTH));
        } catch (Exception e) {
            println("Failed to load background.png", e);
            icon = null;
        }
        label = new JLabel(icon);
        final BoxLayout layout = new BoxLayout(label, BoxLayout.Y_AXIS);
        label.setLayout(layout);
        label.add(Box.createVerticalGlue());
        dialog.add(label);

        if (enableMemoryDisplay) {
            memoryBar = new JProgressBar();
            memoryBar.setStringPainted(true);
            dialog.add(memoryBar, BorderLayout.NORTH);
        }

        dialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        startMemoryThread();
    }

    private static void loadConfig() {
        final Path configFile = configDir.resolve("config.properties");

        final Properties configProperties = new Properties();
        try (InputStream is = Files.newInputStream(configFile)) {
            configProperties.load(is);
        } catch (NoSuchFileException ignored) {
        } catch (Exception e) {
            println("Failed to load config", e);
        }

        if (configProperties.getProperty("enableMemoryDisplay") != null) {
            enableMemoryDisplay = Boolean.parseBoolean(configProperties.getProperty("enableMemoryDisplay"));
        }

        configProperties.clear();
        configProperties.setProperty("enableMemoryDisplay", Boolean.toString(enableMemoryDisplay));

        try (OutputStream os = Files.newOutputStream(configFile)) {
            configProperties.store(os,
                "To use a custom background image, create a file named background.png in this folder. The recommended size is 960x540."
            );
        } catch (Exception e) {
            println("Failed to write config", e);
        }
    }

    private static void startMemoryThread() {
        if (IS_IPC_CLIENT || !enableMemoryDisplay) return;
        updateMemoryUsage();
        memoryThread = new Thread(() -> {
            while (true) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    break;
                }
                updateMemoryUsage();
            }
        }, "MemoryUsageListener");
        memoryThread.setDaemon(true);
        memoryThread.start();
    }

    public static void setTitleFromMetadata(String name, String version) {
        if (titleSet) return;
        titleSet = true;
        setTitle("Loading " + name + ' ' + version);
    }

    private static void setFabricTitle() {
        FabricLoader.getInstance()
            .getAllMods()
            .stream()
            .map(ModContainer::getMetadata)
            .filter(m -> m.getType().equals("builtin"))
            .filter(m -> !IGNORED_BUILTIN.contains(m.getId()))
            .findFirst()
            .ifPresent(m -> setTitleFromMetadata(m.getName(), m.getVersion().getFriendlyString()));
    }

    private static void setTitle(String title) {
        if (sendIpc(6, title)) return;
        if (dialog != null) {
            dialog.setTitle(title);
        }
    }

    public static void beforeEntrypointType(String name, Class<?> type) {
        beforeEntrypointType(
            name,
            type.getSimpleName(),
            FabricLoader.getInstance().getEntrypointContainers(name, type).size()
        );
    }

    private static void beforeEntrypointType(String name, String type, int entrypointCount) {
        final String fullId = "entrypoint:" + name;
        progress.put(fullId, 0);

        if (sendIpc(0, name, type, Integer.toString(entrypointCount))) return;

        println("Preparing loading screen for entrypoint '" + name + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = new JProgressBar(0, entrypointCount);
        progressBar.setStringPainted(true);
        setLabel(progressBar, name, type, null);
        progressBars.put(fullId, progressBar);
        label.add(progressBar, BorderLayout.SOUTH);
        dialog.pack();
    }

    public static void beforeSingleEntrypoint(String typeName, String typeType, String modId, String modName) {
        final String fullId = "entrypoint:" + typeName;
        final Integer oldProgress = progress.get(fullId);
        progress.put(fullId, oldProgress != null ? oldProgress + 1 : 1);

        if (sendIpc(1, typeName, typeType, modId, modName)) return;

        println("Calling entrypoint container for mod '" + modId + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = progressBars.get(fullId);
        if (progressBar == null) return;
        progressBar.setValue(progress.get(fullId));
        setLabel(progressBar, typeName, typeType, modName);
    }

    public static void afterEntrypointType(String name) {
        final String fullId = "entrypoint:" + name;
        progress.remove(fullId);

        if (sendIpc(2, name)) return;

        println("Finished loading screen for entrypoint '" + name + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = progressBars.remove(fullId);
        if (progressBar == null) return;
        label.remove(progressBar);
        dialog.pack();
    }

    public static void maybeCloseAfter(String type) {
        if (!isOpen()) return;
        if (
            !FINAL_ENTRYPOINTS.contains(type) ||
                (
                    runningOnQuilt &&
                    FabricLoader.getInstance()
                        .getModContainer("quilt_base")
                        .map(c -> {
                            try {
                                return VersionPredicate.parse(">=5.0.0-beta.4").test(c.getMetadata().getVersion());
                            } catch (Exception e) {
                                throw new AssertionError(e);
                            }
                        })
                        .orElse(false) &&
                    !FabricLoader.getInstance().getEntrypointContainers(type + "_init", Object.class).isEmpty()
                )
        ) return;
        close();
    }

    public static void createCustomProgressBar(String id, String title, int max) {
        final String fullId = "custom:" + id;
        progress.put(fullId, 0);

        if (sendIpc(4, id, title, Integer.toString(max))) return;
        if (dialog == null) return;

        final JProgressBar progressBar = new JProgressBar(0, max);
        progressBar.setStringPainted(true);
        progressBar.setString(title);
        progressBars.put(fullId, progressBar);
        label.add(progressBar, BorderLayout.SOUTH);
        dialog.pack();
    }

    public static void customProgressBarOp(String... args) {
        final String fullId = "custom:" + args[0];
        switch (args[1]) {
            case "progress":
                progress.put(fullId, Integer.parseInt(args[2]));
                break;
            case "close":
                progress.remove(fullId);
                break;
        }

        if (sendIpc(5, args)) return;
        if (dialog == null) return;

        if (args[1].equals("close")) {
            label.remove(progressBars.remove(fullId));
            dialog.pack();
            return;
        }

        final JProgressBar progressBar = progressBars.get(fullId);
        switch (args[1]) {
            case "progress":
                progressBar.setValue(Integer.parseInt(args[2]));
                break;
            case "maximum":
                progressBar.setMaximum(Integer.parseInt(args[2]));
                break;
            case "minimum":
                progressBar.setMinimum(Integer.parseInt(args[2]));
                break;
            case "title":
                progressBar.setString(args[2]);
                break;
            case "indeterminate":
                progressBar.setIndeterminate(Boolean.parseBoolean(args[2]));
                break;
        }
    }

    private static void close() {
        if (memoryThread != null) {
            memoryThread.interrupt();
        }
        sendIpc(255);
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
            progress.clear();
            progressBars.clear();
        }
        if (ipcOut != null) {
            try {
                ipcOut.close();
            } catch (IOException e) {
                println("Failed to close ipcOut", e);
            }
            ipcOut = null;
        }
    }

    public static boolean isOpen() {
        return dialog != null || ipcOut != null;
    }

    private static void updateMemoryUsage() {
        if (IS_IPC_CLIENT || !enableMemoryDisplay) return;

        final Runtime runtime = Runtime.getRuntime();
        final long usage = runtime.totalMemory() - runtime.freeMemory();
        final long total = runtime.maxMemory();

        if (sendIpc(3, Long.toString(usage), Long.toString(total))) return;

        updateMemoryUsage0(usage, total);
    }

    private static void updateMemoryUsage0(long usage, long total) {
        if (memoryBar == null) return;

        final double bytesPerMb = 1024L * 1024L;
        final int usageMb = (int)Math.round(usage / bytesPerMb);
        final int totalMb = (int)Math.round(total / bytesPerMb);

        memoryBar.setMaximum(totalMb);
        memoryBar.setValue(usageMb);
        memoryBar.setString(usageMb + " MB / " + totalMb + " MB");
    }

    private static void setLabel(JProgressBar progressBar, String typeName, String typeType, @Nullable String modName) {
        final StringBuilder message = new StringBuilder("Loading '").append(typeName)
            .append("' (").append(typeType).append(") \u2014 ")
            .append(progressBar.getValue()).append('/').append(progressBar.getMaximum());
        if (modName != null) {
            message.append(" \u2014 ").append(modName);
        }
        progressBar.setString(message.toString());
    }

    private static void println(String message) {
        println(message, null);
    }

    private static void println(String message, Throwable t) {
        final String prefix = IS_IPC_CLIENT
            ? "[ModLoadingScreen (IPC client)] "
            : ENABLE_IPC
                ? "[ModLoadingScreen (IPC server)] "
                : "[ModLoadingScreen] ";
        System.out.println(prefix + message);
        if (logFile != null) {
            logFile.println(message);
        }
        if (t != null) {
            t.printStackTrace();
            if (logFile != null) {
                t.printStackTrace(logFile);
            }
        }
    }

    private static boolean sendIpc(int id, String... args) {
        if (!ENABLE_IPC) {
            return false;
        }
        if (ipcOut != null) {
            try {
                //noinspection SynchronizeOnNonFinalField
                synchronized (ipcOut) {
                    ipcOut.writeByte(id);
                    ipcOut.writeByte(args.length);
                    for (final String arg : args) {
                        ipcOut.writeUTF(arg);
                    }
                    ipcOut.flush();
                }
            } catch (IOException e) {
                if (e.getMessage().equals("The pipe is being closed")) {
                    println("Exiting process due to IPC exit");
                    System.exit(0);
                }
                println("Failed to send IPC message (id " + id + "): " + String.join("\t", args), e);
            }
        }
        return true;
    }

    // IPC client
    public static void main(String[] args) {
        try (PrintStream logFile = new PrintStream("ipc-client-log.txt")) {
            ActualLoadingScreen.logFile = logFile;
            startLoadingScreen(false);
            final DataInputStream in = new DataInputStream(System.in);
            mainLoop:
            while (true) {
                final int packetId = in.readByte() & 0xff;
                final String[] packetArgs = new String[in.readByte()];
                for (int i = 0; i < packetArgs.length; i++) {
                    packetArgs[i] = in.readUTF();
                }
                switch (packetId) {
                    case 0:
                        beforeEntrypointType(packetArgs[0], packetArgs[1], Integer.parseInt(packetArgs[2]));
                        break;
                    case 1:
                        beforeSingleEntrypoint(packetArgs[0], packetArgs[1], packetArgs[2], packetArgs[3]);
                        break;
                    case 2:
                        afterEntrypointType(packetArgs[0]);
                        break;
                    case 3:
                        updateMemoryUsage0(Long.parseLong(packetArgs[0]), Long.parseLong(packetArgs[1]));
                        break;
                    case 4:
                        createCustomProgressBar(packetArgs[0], packetArgs[1], Integer.parseInt(packetArgs[2]));
                        break;
                    case 5:
                        customProgressBarOp(packetArgs);
                        break;
                    case 6:
                        setTitle(packetArgs[0]);
                        break;
                    case 255:
                        break mainLoop;
                }
            }
            println("IPC client exiting cleanly");
        } catch (Exception e) {
            println("Error in IPC client", e);
        }
        close();
    }
}
