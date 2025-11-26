package com.example.mypdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

enum class DeviceType {
    PHONE,
    TABLET,
    TABLET_LARGE
}

@Composable
fun rememberDeviceType(): DeviceType {
    val configuration = LocalConfiguration.current
    val w = configuration.screenWidthDp

    return when {
        w >= 1000 -> DeviceType.TABLET_LARGE
        w >= 600 -> DeviceType.TABLET
        else -> DeviceType.PHONE
    }
}
