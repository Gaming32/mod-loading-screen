package io.github.gaming32.modloadingscreen;

import com.formdev.flatlaf.FlatDarkLaf;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.*;

public class ActualLoadingScreen {
    private static final boolean RUNNING_ON_QUILT = FabricLoader.getInstance().isModLoaded("quilt_loader");
    private static final Set<String> IGNORED_BUILTIN = Set.of(RUNNING_ON_QUILT ? "quilt_loader" : "fabricloader", "java");
    public static final Set<String> FINAL_ENTRYPOINTS = new HashSet<>(List.of("client", "server", "client_init", "server_init"));

    private static final Map<String, JProgressBar> progressBars = new LinkedHashMap<>();
    private static JFrame dialog;
    private static JLabel label;

    public static void startLoadingScreen() throws MalformedURLException {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Mod Loading Screen is on a headless environment. Only some logging will be performed.");
            return;
        }

        System.out.println("Opening loading screen");

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

        final ImageIcon icon = new ImageIcon(new URL(
            RUNNING_ON_QUILT
                ? "https://raw.githubusercontent.com/QuiltMC/art/master/banners/png/quilt-banner.png"
                : "https://raw.githubusercontent.com/FabricMC/fabricmc.net/main/assets/aof4.png"
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
        System.out.println("Preparing loading screen for entrypoint '" + name + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = new JProgressBar(0, FabricLoader.getInstance().getEntrypointContainers(name, type).size());
        progressBar.setStringPainted(true);
        setLabel(progressBar, name, type, null);
        progressBars.put(name, progressBar);
        label.add(progressBar, BorderLayout.SOUTH);
        dialog.pack();
    }

    public static void beforeSingleEntrypoint(String typeName, Class<?> typeType, EntrypointContainer<?> entrypoint) {
        System.out.println("Calling entrypoint container for mod '" + entrypoint.getProvider().getMetadata().getId() + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = progressBars.get(typeName);
        if (progressBar == null) return;
        progressBar.setValue(progressBar.getValue() + 1);
        setLabel(progressBar, typeName, typeType, entrypoint.getProvider().getMetadata().getName());
    }

    public static void afterEntrypointType(String name) {
        System.out.println("Finished loading screen for entrypoint '" + name + "'");
        if (dialog == null) return;

        final JProgressBar progressBar = progressBars.get(name);
        if (progressBar == null) return;
        label.remove(progressBar);
        dialog.pack();
    }

    public static void maybeCloseAfter(String type) {
        if (
            FINAL_ENTRYPOINTS.contains(type) // &&
            // Not doing this because of https://github.com/QuiltMC/quilt-standard-libraries/issues/249
            // // Hack workaround for differently-named Quilt entrypoints
            // FabricLoader.getInstance().getEntrypointContainers(type + "_init", Object.class).isEmpty()
        ) {
            dialog.dispose();
            dialog = null;
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
