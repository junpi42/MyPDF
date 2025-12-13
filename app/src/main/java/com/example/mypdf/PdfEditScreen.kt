package com.example.mypdf

import androidx.compose.runtime.Composable
import java.io.File

@Composable
fun PdfEditScreen(
    deviceType: DeviceType,
    file: File,
    onBack: () -> Unit,
    isDarkMode: Boolean,
    isDaltonic: Boolean,
    onToggleDaltonic: () -> Unit,
    language: Language,
    tutorialState: TutorialState,
    onTutorialStateChange: (TutorialState) -> Unit,
    onTutorialComplete: () -> Unit,
    googleAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount? = null,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    PdfViewerScreen(
        deviceType = deviceType,
        file = file,
        onBack = onBack,
        isDarkMode = isDarkMode,
        isDaltonic = isDaltonic,
        onToggleDaltonic = onToggleDaltonic,
        language = language,
        tutorialState = tutorialState,
        onTutorialStateChange = onTutorialStateChange,
        onTutorialComplete = onTutorialComplete,
        googleAccount = googleAccount,
        onSignIn = onSignIn,
        onSignOut = onSignOut
    )
}
