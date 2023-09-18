package io.github.gaming32.modloadingscreen.api;

import java.util.StringJoiner;

public final class AvailableFeatures {
    /**
     * @since 1.0.3
     * @see LoadingScreenApi#getFinalEntrypoints
     */
    @SuppressWarnings("PointlessBitwiseExpression")
    public static final long FINAL_ENTRYPOINTS = 1L << 0;

    /**
     * @since 1.0.3
     * @see LoadingScreenApi#isHeadless
     */
    public static final long HEADLESS_CHECK = 1L << 1;

    /**
     * @since 1.0.3
     * @see LoadingScreenApi#isUsingIpc
     */
    @Deprecated
    public static final long IPC_CHECK = 1L << 2;

    /**
     * @since 1.0.3
     * @see LoadingScreenApi#getActiveProgressBars
     * @see LoadingScreenApi#getProgress
     */
    public static final long GET_PROGRESS = 1L << 3;

    /**
     * @since 1.0.3
     * @see LoadingScreenApi#isOpen
     */
    public static final long OPEN_CHECK = 1L << 4;

    /**
     * @since 1.0.4
     * @see LoadingScreenApi#getCustomProgressBar
     * @see CustomProgressBar
     */
    public static final long CUSTOM_PROGRESS_BARS = 1L << 5;

    /**
     * All the features that should be available on version 1.0.3.
     *
     * @since 1.0.3
     */
    public static final long V1_0_3 = FINAL_ENTRYPOINTS | HEADLESS_CHECK | IPC_CHECK | GET_PROGRESS | OPEN_CHECK;

    /**
     * All the features that should be available on version 1.0.4.
     *
     * @since 1.0.4
     */
    public static final long V1_0_4 = V1_0_3 | CUSTOM_PROGRESS_BARS;

    private static final long MIN_FEATURE = FINAL_ENTRYPOINTS;
    private static final long MAX_FEATURE = CUSTOM_PROGRESS_BARS;

    private AvailableFeatures() {
    }

    public static String toString(long features) {
        if (Long.bitCount(features) <= 1L) {
            switch ((int)features) {
                case (int)FINAL_ENTRYPOINTS:
                    return "FINAL_ENTRYPOINTS";
                case (int)HEADLESS_CHECK:
                    return "HEADLESS_CHECK";
                case (int)IPC_CHECK:
                    return "IPC_CHECK";
                case (int)GET_PROGRESS:
                    return "GET_PROGRESS";
                case (int)OPEN_CHECK:
                    return "OPEN_CHECK";
                case (int)CUSTOM_PROGRESS_BARS:
                    return "CUSTOM_PROGRESS_BARS";
            }
            return "";
        }

        final StringJoiner sj = new StringJoiner(", ");
        for (long feature = MIN_FEATURE; feature <= MAX_FEATURE; feature <<= 1) {
            if (hasFeatures(features, feature)) {
                sj.add(toString(feature));
            }
        }
        return sj.toString();
    }

    /**
     * @see LoadingScreenApi#hasFeatures
     */
    public static boolean hasFeatures(long supportedFeatures, long requestedFeatures) {
        return (supportedFeatures & requestedFeatures) != 0;
    }
}
