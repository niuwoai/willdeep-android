package com.willdeep.android.ui

import androidx.annotation.DrawableRes
import com.willdeep.android.R

@DrawableRes
internal fun remoteComputerIcon(platform: String): Int = when (platform) {
    "macos" -> R.drawable.ic_platform_macos
    "linux" -> R.drawable.ic_platform_linux
    "windows" -> R.drawable.ic_platform_windows
    else -> R.drawable.ic_computer
}
