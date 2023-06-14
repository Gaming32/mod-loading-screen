package io.github.gaming32.modloadingscreen;

import com.formdev.flatlaf.FlatDarkLaf;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static io.github.gaming32.modloadingscreen.ModLoadingScreen.ACTUAL_LOADING_SCREEN;

public class ActualLoadingScreen {
    private static final boolean IS_IPC_CLIENT = Boolean.getBoolean("mlsipc.present");
    private static final boolean RUNNING_ON_QUILT = Boolean.getBoolean("mlsipc.quilt") ||
        (!IS_IPC_CLIENT && FabricLoader.getInstance().isModLoaded("quilt_loader"));
    private static final Path CONFIG_DIR = IS_IPC_CLIENT
        ? Paths.get(System.getProperty("mlsipc.config"))
        : FabricLoader.getInstance().getConfigDir().resolve("mod-loading-screen");
    private static final Set<String> IGNORED_BUILTIN = new HashSet<>(Arrays.asList(
        RUNNING_ON_QUILT ? "quilt_loader" : "fabricloader", "java"
    ));
    private static final Set<String> FINAL_ENTRYPOINTS = new HashSet<>(Arrays.asList(
        "client", "server", "client_init", "server_init"
    ));
    private static final boolean ENABLE_IPC = !IS_IPC_CLIENT && !GraphicsEnvironment.isHeadless() && (
        isOnMac()
            ? !Boolean.getBoolean("mod-loading-screen.disableIpc")
            : Boolean.getBoolean("mod-loading-screen.enableIpc")
    );

    private static final Map<String, JProgressBar> progressBars = new LinkedHashMap<>();
    private static JFrame dialog;
    private static JLabel label;
    private static DataOutputStream ipcOut;
    private static PrintStream logFile;

    private static boolean isOnMac() {
        final String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return osName.contains("mac") || osName.contains("darwin");
    }

    public static void startLoadingScreen() {
        if (GraphicsEnvironment.isHeadless()) {
            println("Mod Loading Screen is on a headless environment. Only some logging will be performed.");
            return;
        }
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            println("Failed to create config dir", e);
        }

        println("Opening loading screen");

        final String gameNameAndVersion = IS_IPC_CLIENT
            ? System.getProperty("mlsipc.game")
            : FabricLoader.getInstance()
                .getAllMods()
                .stream()
                .filter(m -> m.getMetadata().getType().equals("builtin"))
                .filter(m -> !IGNORED_BUILTIN.contains(m.getMetadata().getId()))
                .findFirst()
                .map(m -> m.getMetadata().getName() + ' ' + m.getMetadata().getVersion())
                .orElse("Unknown Game");

        final Path readmePath = CONFIG_DIR.resolve("readme.txt");
        try {
            Files.write(readmePath, Collections.singleton(
                "Create a file named background.png in this folder to use a custom background image. The recommended size is 960x540."
            ));
        } catch (IOException e) {
            println("Failed to write readme.txt", e);
        }

        if (!IS_IPC_CLIENT && ENABLE_IPC) {
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
                        "-Dmlsipc.quilt=" + RUNNING_ON_QUILT,
                        "-Dmlsipc.game=" + gameNameAndVersion,
                        "-Dmlsipc.config=" + CONFIG_DIR,
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
            return;
        }

        FlatDarkLaf.setup();
        UIManager.getDefaults().put("ProgressBar.horizontalSize", new Dimension(146, 18));
        UIManager.getDefaults().put("ProgressBar.font", UIManager.getFont("ProgressBar.font").deriveFont(18f));
        UIManager.getDefaults().put("ProgressBar.selectionForeground", new Color(255, 255, 255));

        dialog = new JFrame();
        dialog.setTitle("Loading " + gameNameAndVersion);
        dialog.setResizable(false);

        ImageIcon icon;
        try {
            final Path backgroundPath = CONFIG_DIR.resolve("background.png");
            icon = new ImageIcon(
                Files.exists(backgroundPath)
                    ? backgroundPath.toUri().toURL()
                    : ClassLoader.getSystemResource("assets/mod-loading-screen/" + (RUNNING_ON_QUILT ? "quilt-banner.png" : "aof4.png"))
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

        dialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    public static void beforeEntrypointType(String name, Class<?> type) {
        beforeEntrypointType(
            name,
            type.getSimpleName(),
            FabricLoader.getInstance().getEntrypointContainers(name, type).size()
        );
    }

    private static void beforeEntrypointType(String name, String type, int entrypointCount) {
        if (sendIpc(0, name, type, Integer.toString(entrypointCount))) return;

        println("Preparing loading screen for entrypoint '" + name + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = new JProgressBar(0, entrypointCount);
        progressBar.setStringPainted(true);
        setLabel(progressBar, name, type, null);
        progressBars.put(name, progressBar);
        label.add(progressBar, BorderLayout.SOUTH);
        dialog.pack();
    }

    public static void beforeSingleEntrypoint(String typeName, String typeType, String modId, String modName) {
        if (sendIpc(1, typeName, typeType, modId, modName)) return;

        println("Calling entrypoint container for mod '" + modId + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = progressBars.get(typeName);
        if (progressBar == null) return;
        progressBar.setValue(progressBar.getValue() + 1);
        setLabel(progressBar, typeName, typeType, modName);
    }

    public static void afterEntrypointType(String name) {
        if (sendIpc(2, name)) return;

        println("Finished loading screen for entrypoint '" + name + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = progressBars.remove(name);
        if (progressBar == null) return;
        label.remove(progressBar);
        dialog.pack();
    }

    public static void maybeCloseAfter(String type) {
        if (
            !FINAL_ENTRYPOINTS.contains(type) ||
                (
                    RUNNING_ON_QUILT &&
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
        sendIpc(3);
        close();
    }

    private static void close() {
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
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
                ipcOut.writeByte(id);
                ipcOut.writeByte(args.length);
                for (final String arg : args) {
                    ipcOut.writeUTF(arg);
                }
                ipcOut.flush();
            } catch (IOException e) {
                if (e.getMessage().equals("The pipe is being closed")) {
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
            startLoadingScreen();
            final DataInputStream in = new DataInputStream(System.in);
            mainLoop:
            while (true) {
                final int packetId = in.readByte();
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
