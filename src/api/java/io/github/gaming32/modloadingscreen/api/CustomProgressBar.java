package io.github.gaming32.modloadingscreen.api;

import java.io.Closeable;
import java.util.Objects;

/**
 * A reference to a custom progress bar. Recommended to be used with try-with-resources.
 *
 * @see LoadingScreenApi#getCustomProgressBar
 */
public final class CustomProgressBar implements Closeable {
    private final String id;
    private final boolean isReal;
    private boolean closed;

    private String title;
    private int progress;
    private int minimum;
    private int maximum;

    CustomProgressBar(String id, boolean isReal, String title, int maximum) {
        this.id = id;
        this.isReal = isReal;
        this.title = title;
        this.maximum = maximum;
    }

    public String getId() {
        return id;
    }

    public boolean isClosed() {
        return !LoadingScreenApi.CUSTOM_PROGRESS_BARS.containsKey(id);
    }

    private void checkClosed() {
        if (isClosed()) {
            throw new IllegalStateException("CustomProgressBar is closed!");
        }
    }

    /**
     * Close the progress bar and remove it from the loading screen.
     */
    @Override
    public void close() {
        if (LoadingScreenApi.CUSTOM_PROGRESS_BARS.remove(id) == this) {
            LoadingScreenApi.customProgressBarOp(id, "close");
        }
    }

    /**
     * Sets the progress of the progress bar. The new progress will be clamped to the {@code [minimum, maximum]} range.
     * @param progress The new progress.
     */
    public void setProgress(int progress) {
        progress = Math.min(maximum, Math.max(minimum, progress));
        checkClosed();
        this.progress = progress;
        LoadingScreenApi.customProgressBarOp(id, "progress", Integer.toString(progress));
    }

    /**
     * Gets the current progress of the progress bar.
     */
    public int getProgress() {
        return progress;
    }

    /**
     * Steps the progress bar forward one step.
     *
     * @return The new progress of the progress bar, which may be clamped into range.
     */
    public int step() {
        return step(1);
    }

    /**
     * Steps the progress bar forward {@code n} steps.
     *
     * @return The new progress of the progress bar, which may be clamped into range.
     */
    public int step(int n) {
        setProgress(progress + n);
        return progress;
    }

    /**
     * Sets the maximum value of the progress bar.
     * @throws IllegalArgumentException If {@code maximum < minimum}
     */
    public void setMaximum(int maximum) {
        if (maximum < minimum) {
            throw new IllegalArgumentException("maximum may not be less than minimum");
        }
        checkClosed();
        this.maximum = maximum;
        LoadingScreenApi.customProgressBarOp(id, "maximum", Integer.toString(maximum));
    }

    /**
     * Gets the maximum value of the progress bar.
     */
    public int getMaximum() {
        return maximum;
    }


    /**
     * Sets the minimum value of the progress bar.
     * @throws IllegalArgumentException If {@code minimum > maximum}
     */
    public void setMinimum(int minimum) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("minimum may not be greater than maximum");
        }
        checkClosed();
        this.minimum = minimum;
        LoadingScreenApi.customProgressBarOp(id, "minimum", Integer.toString(minimum));
    }


    /**
     * Gets the minimum value of the progress bar.
     */
    public int getMinimum() {
        return minimum;
    }

    /**
     * Sets the display text of the progress bar.
     */
    public void setTitle(String title) {
        Objects.requireNonNull(title, "title");
        checkClosed();
        this.title = title;
        LoadingScreenApi.customProgressBarOp(id, "title", title);
    }

    /**
     * Gets the display text of the progress bar.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns whether the progress bar is a real progress bar that can show up. This returns {@code true} if a version
     * of Mod Loading Screen that supports custom progress bars is installed.
     */
    public boolean isReal() {
        return isReal;
    }
}
