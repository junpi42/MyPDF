package com.example.mypdf

import androidx.compose.ui.res.painterResource
import com.example.mypdf.Language
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private enum class OnboardingStep {
    Welcome,
    Login,
    Register,
    SelectLanguage,
    Theme,
    Daltonic,
    Loading
}

@Composable
fun OnboardingDialog(
    initialLanguage: Language,
    onFinish: (Language, Boolean, Boolean) -> Unit
) {
    // State for the onboarding flow
    var currentLanguage by rememberSaveable { mutableStateOf(initialLanguage) }
    var isDarkMode by rememberSaveable { mutableStateOf(false) }
    var isDaltonic by rememberSaveable { mutableStateOf(false) }
    var currentStep by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }

    // Registration/Login state
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // We use a derived state for strings so they update immediately when language changes
    val s = stringsFor(currentLanguage)

    // Dynamic background color based on step and theme selection
    val baseColor = if (currentStep == OnboardingStep.Theme && isDarkMode) {
        Color(0xFF1C1B1F) // Dark background preview
    } else if (currentStep == OnboardingStep.Theme && !isDarkMode) {
        Color(0xFFFFFBFE) // Light background preview
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (currentStep == OnboardingStep.Theme && isDarkMode) {
        Color(0xFFE6E1E5)
    } else if (currentStep == OnboardingStep.Theme && !isDarkMode) {
        Color(0xFF1C1B1F)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // Loading effect
    LaunchedEffect(currentStep) {
        if (currentStep == OnboardingStep.Loading) {
            delay(2000) // Simulate configuration
            onFinish(currentLanguage, isDarkMode, isDaltonic)
        }
    }

    Dialog(
        onDismissRequest = {}, // Prevent dismissal without finishing
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Animated Background
            AnimatedGradientBackground(baseColor = baseColor)

            // Botón X en la esquina superior derecha: grande y visible
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = { onFinish(currentLanguage, isDarkMode, isDaltonic) },
                    modifier = Modifier
                        .size(56.dp)
                        .border(width = 2.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f), shape = MaterialTheme.shapes.small)
                        .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar onboarding",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "OnboardingWizard",
                    modifier = Modifier.weight(1f, fill = false) // Allow content to take space but not force full height
                ) { step ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        when (step) {
                            OnboardingStep.Welcome -> {
                                WelcomeStep(
                                    s = s,
                                    onLogin = { currentStep = OnboardingStep.Login },
                                    onRegister = { currentStep = OnboardingStep.Register },
                                    onGuest = { currentStep = OnboardingStep.SelectLanguage }
                                )
                            }
                            OnboardingStep.Login -> {
                                Text(
                                    text = s.loginTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = contentColor,
                                    textAlign = TextAlign.Center
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = { Text(s.email) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text(s.password) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation()
                                    )
                                }
                                Button(
                                    onClick = { currentStep = OnboardingStep.Loading },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(25.dp)
                                ) {
                                    Text(s.login)
                                }

                                // Divider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(modifier = Modifier.weight(1f))
                                    Text(
                                        text = s.orSeparator,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider(modifier = Modifier.weight(1f))
                                }

                                // Google Sign In
                                OutlinedButton(
                                    onClick = { currentStep = OnboardingStep.Loading },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(28.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_google_logo),
                                        contentDescription = "Google Logo",
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = s.googleSignIn,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                TextButton(
                                    onClick = { currentStep = OnboardingStep.Welcome },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(s.backDescription)
                                }
                            }
                            OnboardingStep.Register -> {
                                Text(
                                    text = s.registrationTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = contentColor,
                                    textAlign = TextAlign.Center
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = { username = it },
                                        label = { Text(s.username) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = { Text(s.email) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text(s.password) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation()
                                    )
                                }
                                Button(
                                    onClick = { currentStep = OnboardingStep.SelectLanguage }, // Continue to settings
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(25.dp)
                                ) {
                                    Text(s.next)
                                }
                                TextButton(
                                    onClick = { currentStep = OnboardingStep.Welcome },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(s.backDescription)
                                }
                            }
                            OnboardingStep.SelectLanguage -> {
                                Text(
                                    text = s.chooseLanguage,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = contentColor,
                                    textAlign = TextAlign.Center
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    LanguageOption(
                                        language = Language.EN,
                                        selected = currentLanguage == Language.EN,
                                        onClick = { currentLanguage = Language.EN }
                                    )
                                    LanguageOption(
                                        language = Language.ES,
                                        selected = currentLanguage == Language.ES,
                                        onClick = { currentLanguage = Language.ES }
                                    )
                                    LanguageOption(
                                        language = Language.FR,
                                        selected = currentLanguage == Language.FR,
                                        onClick = { currentLanguage = Language.FR }
                                    )
                                    LanguageOption(
                                        language = Language.IT,
                                        selected = currentLanguage == Language.IT,
                                        onClick = { currentLanguage = Language.IT }
                                    )
                                }
                                Button(
                                    onClick = { currentStep = OnboardingStep.Theme },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Text(s.next, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            OnboardingStep.Theme -> {
                                Text(
                                    text = s.chooseTheme,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = contentColor,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ThemeCard(
                                        dark = false,
                                        selected = !isDarkMode,
                                        onClick = { isDarkMode = false },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ThemeCard(
                                        dark = true,
                                        selected = isDarkMode,
                                        onClick = { isDarkMode = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Button(
                                    onClick = { currentStep = OnboardingStep.Daltonic },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Text(s.next, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            OnboardingStep.Daltonic -> {
                                Text(
                                    text = s.chooseDaltonism,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = contentColor,
                                    textAlign = TextAlign.Center
                                )

                                // Contenedor centrado para preview + switch
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        StaticPdfTopBarPreview(
                                            isDaltonic = isDaltonic,
                                            modifier = Modifier
                                                .scale(1.3f)
                                                .padding(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = s.daltonismOption,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = contentColor
                                        )
                                        Switch(
                                            checked = isDaltonic,
                                            onCheckedChange = { isDaltonic = it }
                                        )
                                    }
                                }

                                Button(
                                    onClick = { currentStep = OnboardingStep.Loading },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Text(s.finish, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            OnboardingStep.Loading -> {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${s.welcomeUser} $username",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = s.configuring,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Persistent Disclaimer (only show on settings steps)
                if (currentStep == OnboardingStep.SelectLanguage || currentStep == OnboardingStep.Theme || currentStep == OnboardingStep.Daltonic) {
                    Text(
                        text = s.onboardingDisclaimer,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = if (currentStep == OnboardingStep.Theme && isDarkMode) Color(0xFFE6E1E5).copy(alpha = 0.7f)
                                else if (currentStep == OnboardingStep.Theme && !isDarkMode) Color(0xFF1C1B1F).copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }
    }
}

@Composable
fun AnimatedGradientBackground(baseColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "background_anim")
    val t by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t"
    )


    val color2 = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)

    // Animate gradient center
    val x = 0.5f + 0.3f * cos(t)
    val y = 0.5f + 0.3f * sin(t)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(color2, baseColor),
                    center = Offset(x * 2000f, y * 2000f), // Approximate screen size scaling
                    radius = 1500f
                )
            )
    )
}

@Composable
private fun LanguageOption(
    language: Language,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when(language) {
                    Language.EN -> "English"
                    Language.ES -> "Español"
                    Language.FR -> "Français"
                    Language.IT -> "Italiano"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ThemeCard(
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(0.7f)
            .border(2.dp, borderColor, MaterialTheme.shapes.medium),
        elevation = CardDefaults.cardElevation(if (selected) 8.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (dark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE))
        ) {
            // Mock UI
            Column(Modifier.padding(12.dp)) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (dark) Color(0xFFD0BCFF) else Color(0xFF6750A4))
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (dark) Color(0xFF49454F) else Color(0xFFE7E0EC))
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(60.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (dark) Color(0xFF49454F) else Color(0xFFE7E0EC))
                )
            }

            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                )
            }

            Text(
                text = if (dark) "Dark" else "Light",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                color = if (dark) Color.White else Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StaticPdfTopBarPreview(
    isDaltonic: Boolean,
    modifier: Modifier = Modifier
) {
    // We wrap it in a Box to scale it
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // We use a fixed width for the preview to ensure it looks like a phone screen top
        Box(
            modifier = Modifier
                .width(420.dp) // Wider as requested
                .height(100.dp) // Enough for top bar + tuner
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // 1. The Top Bar
            StyledTopBar(
                onBack = {},
                tunerOn = true,
                concertModeOn = false,
                onTunerClick = {},
                onConcertClick = {},
                darkMode = false
            )

            // 2. The Tuner (Static)
            StaticTunnerSmall(
                note = "DO",
                cents = 0.0,
                isDaltonic = isDaltonic,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (64.dp - 44.dp) / 2) // Centered in 64dp top bar roughly
                    .fillMaxWidth(0.7f)
                    .height(44.dp)
            )
        }
    }
}

@Composable
private fun StaticTunnerSmall(
    note: String,
    cents: Double,
    isDaltonic: Boolean,
    modifier: Modifier = Modifier
) {
    // Define the 3 palette colors
    val flatColor = if (isDaltonic) Color(0xFFFF00FF) else Color(0xFF1E88E5)
    val inTuneColor = if (isDaltonic) Color(0xFF00BCD4) else Color(0xFF4CAF50)
    val sharpColor = if (isDaltonic) Color(0xFFFF9800) else Color(0xFFE53935)

    val markerColor = Color.White

    val normalized = (cents / 50.0).toFloat().coerceIn(-1f, 1f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Gray), // Fallback
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val sectionWidth = w / 3f

            // Draw 3 background sections
            drawRect(
                color = flatColor,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(sectionWidth, h)
            )
            drawRect(
                color = inTuneColor,
                topLeft = Offset(sectionWidth, 0f),
                size = androidx.compose.ui.geometry.Size(sectionWidth, h)
            )
            drawRect(
                color = sharpColor,
                topLeft = Offset(sectionWidth * 2, 0f),
                size = androidx.compose.ui.geometry.Size(sectionWidth, h)
            )

            val centerX = w / 2f
            val range = w * 0.42f
            val x = centerX + normalized * range

            val triW = h * 0.32f
            val triH = h * 0.30f

            val inTuneCents = 12.0
            val maxCents = 50.0
            val normLimit = (inTuneCents / maxCents).toFloat()

            val tickLen = h * 0.18f
            val tickStroke = h * 0.04f
            val halfStroke = tickStroke / 2f

            val topTri = Path().apply {
                moveTo(x - triW / 2f, -halfStroke)
                lineTo(x + triW / 2f, -halfStroke)
                lineTo(x, triH)
                close()
            }

            val bottomTri = Path().apply {
                moveTo(x - triW / 2f, h + halfStroke)
                lineTo(x + triW / 2f, h + halfStroke)
                lineTo(x, h - triH)
                close()
            }

            drawPath(topTri, color = markerColor)
            drawPath(bottomTri, color = markerColor)

            val xLeft = centerX - normLimit * range
            val xRight = centerX + normLimit * range

            drawLine(
                color = markerColor,
                start = Offset(xLeft, -halfStroke),
                end = Offset(xLeft, tickLen),
                strokeWidth = tickStroke
            )
            drawLine(
                color = markerColor,
                start = Offset(xRight, -halfStroke),
                end = Offset(xRight, tickLen),
                strokeWidth = tickStroke
            )

            drawLine(
                color = markerColor,
                start = Offset(xLeft, h + halfStroke),
                end = Offset(xLeft, h - tickLen),
                strokeWidth = tickStroke
            )
            drawLine(
                color = markerColor,
                start = Offset(xRight, h + halfStroke),
                end = Offset(xRight, h - tickLen),
                strokeWidth = tickStroke
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = note,
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WelcomeStep(
    s: AppStrings,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onGuest: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        // Logo & Header
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(1000)) + slideInVertically { 50 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(1f)
                        .aspectRatio(1.2f)
                )

            }
        }

        // Actions
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(1000, delayMillis = 300)) + slideInVertically { 50 }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Primary CTA: Create Account
                Button(
                    onClick = onRegister,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = s.registrationTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Secondary CTA: Login
                Button(
                    onClick = onLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(
                        text = s.login,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Guest Option
                TextButton(
                    onClick = onGuest,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = s.continueGuest,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
