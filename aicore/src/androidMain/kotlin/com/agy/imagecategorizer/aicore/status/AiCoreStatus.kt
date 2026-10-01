package com.agy.imagecategorizer.aicore.status

/**
 * State of Android AICore on the current device.
 */
sealed interface AiCoreStatus {

    /** Status has not been queried yet. */
    data object Unchecked : AiCoreStatus

    /** Checking AICore service and model availability. */
    data object Checking : AiCoreStatus

    /**
     * AICore and Gemini Nano model are ready for on-device inference.
     */
    data object Ready : AiCoreStatus

    /**
     * Model is actively downloading to the device.
     *
     * @param bytesDownloaded Current number of bytes downloaded, or -1 if unknown.
     * @param totalBytes Total bytes to download, or -1 if unknown.
     */
    data class Downloading(
        val bytesDownloaded: Long = -1L,
        val totalBytes: Long = -1L,
    ) : AiCoreStatus {
        val progressPercentage: Int?
            get() = if (totalBytes > 0 && bytesDownloaded >= 0) {
                ((bytesDownloaded.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
            } else null
    }

    /**
     * The device does not meet the hardware or Android OS requirements for AICore.
     * (E.g. API level < 31, non-supported chipset, or missing system service).
     */
    data class Unsupported(val reason: String) : AiCoreStatus

    /**
     * AICore system service or Private Compute Services app needs an update from Google Play.
     */
    data class NeedsUpdate(val message: String) : AiCoreStatus

    /**
     * The model is unavailable due to disk space or service busy state.
     */
    data class Unavailable(val reason: String) : AiCoreStatus

    /**
     * An unexpected error occurred while communicating with AICore.
     */
    data class Error(val message: String, val cause: Throwable? = null) : AiCoreStatus
}
