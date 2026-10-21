package org.zenith.tvos.ui.dock

import android.graphics.Bitmap

/**
 * Lightweight immutable representation of an installed Android TV application.
 */
data class AppItem(
    val packageName: String,
    val activityClassName: String,
    val label: String,
    val iconBitmap: Bitmap? = null,
    val isSystemApp: Boolean = false,
    val category: AppCategory = AppCategory.OTHER
)
