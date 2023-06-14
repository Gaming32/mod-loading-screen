package io.github.gaming32.modloadingscreen;

import com.formdev.flatlaf.FlatDarkLaf;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class ActualLoadingScreen {
    private static final boolean RUNNING_ON_QUILT = FabricLoader.getInstance().isModLoaded("quilt_loader");
    private static final boolean QSL_HACK = RUNNING_ON_QUILT &&
        FabricLoader.getInstance()
            .getModContainer("quilt_base")
            .map(c -> {
                try {
                    return VersionPredicate.parse(">=5.0.0-beta.4").test(c.getMetadata().getVersion());
                } catch (VersionParsingException e) {
                    throw new AssertionError(e);
                }
            })
            .orElse(false);
    private static final Set<String> IGNORED_BUILTIN = new HashSet<>(Arrays.asList(
        RUNNING_ON_QUILT ? "quilt_loader" : "fabricloader", "java"
    ));
    public static final Set<String> FINAL_ENTRYPOINTS = new HashSet<>(Arrays.asList(
        "client", "server", "client_init", "server_init"
    ));

    private static final Map<String, JProgressBar> progressBars = new LinkedHashMap<>();
    private static JFrame dialog;
    private static JLabel label;

    public static void startLoadingScreen() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[ModLoadingScreen] Mod Loading Screen is on a headless environment. Only some logging will be performed.");
            return;
        }

        System.out.println("[ModLoadingScreen] Opening loading screen");

        final String gameNameAndVersion = FabricLoader.getInstance()
            .getAllMods()
            .stream()
            .filter(m -> m.getMetadata().getType().equals("builtin"))
            .filter(m -> !IGNORED_BUILTIN.contains(m.getMetadata().getId()))
            .findFirst()
            .map(m -> m.getMetadata().getName() + ' ' + m.getMetadata().getVersion())
            .orElse("Unknown Game");

        FlatDarkLaf.setup();
        UIManager.getDefaults().put("ProgressBar.horizontalSize", new Dimension(146, 18));
        UIManager.getDefaults().put("ProgressBar.font", UIManager.getFont("ProgressBar.font").deriveFont(18f));
        UIManager.getDefaults().put("ProgressBar.selectionForeground", new Color(255, 255, 255));

        dialog = new JFrame();
        dialog.setTitle("Loading " + gameNameAndVersion);
        dialog.setResizable(false);

        final ImageIcon icon = new ImageIcon(ClassLoader.getSystemResource(
            "assets/mod-loading-screen/" + (RUNNING_ON_QUILT ? "quilt-banner.png" : "aof4.png")
        ));
        icon.setImage(icon.getImage().getScaledInstance(960, 540, Image.SCALE_SMOOTH));
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
        System.out.println("[ModLoadingScreen] Preparing loading screen for entrypoint '" + name + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = new JProgressBar(0, FabricLoader.getInstance().getEntrypointContainers(name, type).size());
        progressBar.setStringPainted(true);
        setLabel(progressBar, name, type, null);
        progressBars.put(name, progressBar);
        label.add(progressBar, BorderLayout.SOUTH);
        dialog.pack();
    }

    public static void beforeSingleEntrypoint(String typeName, Class<?> typeType, EntrypointContainer<?> entrypoint) {
        System.out.println("[ModLoadingScreen] Calling entrypoint container for mod '" + entrypoint.getProvider().getMetadata().getId() + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = progressBars.get(typeName);
        if (progressBar == null) return;
        progressBar.setValue(progressBar.getValue() + 1);
        setLabel(progressBar, typeName, typeType, entrypoint.getProvider().getMetadata().getName());
    }

    public static void afterEntrypointType(String name) {
        System.out.println("[ModLoadingScreen] Finished loading screen for entrypoint '" + name + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = progressBars.remove(name);
        if (progressBar == null) return;
        label.remove(progressBar);
        dialog.pack();
    }

    public static void maybeCloseAfter(String type) {
        if (
            dialog != null &&
            FINAL_ENTRYPOINTS.contains(type) &&
            // Hack workaround for differently-named Quilt entrypoints
            (
                !QSL_HACK ||
                FabricLoader.getInstance().getEntrypointContainers(type + "_init", Object.class).isEmpty()
            )
        ) {
            dialog.dispose();
            dialog = null;
            progressBars.clear();
        }
    }

    private static void setLabel(JProgressBar progressBar, String typeName, Class<?> typeType, @Nullable String modName) {
        final StringBuilder message = new StringBuilder("Loading '").append(typeName)
            .append("' (").append(typeType.getSimpleName()).append(") \u2014 ")
            .append(progressBar.getValue()).append('/').append(progressBar.getMaximum());
        if (modName != null) {
            message.append(" \u2014 ").append(modName);
        }
        progressBar.setString(message.toString());
    }
}
