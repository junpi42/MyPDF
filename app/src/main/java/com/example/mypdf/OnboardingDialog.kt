package com.example.mypdf

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingDialog(
    initialLanguage: Language,
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onFinish: (Language, Boolean) -> Unit
) {
    // State for the onboarding flow
    var currentLanguage by remember { mutableStateOf(initialLanguage) }
    // isDarkMode is now passed in
    var isDaltonic by remember { mutableStateOf(false) }
    
    // Steps: 0 -> Language, 1 -> Theme, 2 -> Daltonism
    var step by remember { mutableStateOf(0) }
    
    // We use a derived state for strings so they update immediately when language changes
    val s = stringsFor(currentLanguage)

    // Dynamic Background Animation
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val color1 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.primaryContainer,
        targetValue = MaterialTheme.colorScheme.tertiaryContainer,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color1"
    )
    val color2 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.surfaceContainer,
        targetValue = MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color2"
    )

    Dialog(
        onDismissRequest = {}, // Prevent dismissal without finishing
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(color1, color2)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .systemBarsPadding(), // Ensure content isn't behind status/nav bars
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Welcome Header (Bouncy Entrance)
                Spacer(Modifier.height(48.dp))
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                
                AnimatedVisibility(
                    visible = visible,
                    enter = scaleIn(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    ) + fadeIn() + slideInVertically { -it }
                ) {
                    Text(
                        text = s.welcomeTitle,
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(Modifier.height(32.dp))

                // 2. Main Content (Fluid Transitions)
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { width -> width } + fadeIn() + scaleIn(initialScale = 0.9f)).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut() + scaleOut(targetScale = 0.9f)
                                )
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn() + scaleIn(initialScale = 0.9f)).togetherWith(
                                    slideOutHorizontally { width -> width } + fadeOut() + scaleOut(targetScale = 0.9f)
                                )
                            }.using(SizeTransform(clip = false))
                        },
                        label = "stepTransition"
                    ) { targetStep ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            when (targetStep) {
                                0 -> { // Language
                                    Text(s.chooseLanguage, style = MaterialTheme.typography.headlineMedium)
                                    Spacer(Modifier.height(48.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        LanguageOption(
                                            label = "English",
                                            selected = currentLanguage == Language.EN,
                                            onClick = { currentLanguage = Language.EN }
                                        )
                                        LanguageOption(
                                            label = "Español",
                                            selected = currentLanguage == Language.ES,
                                            onClick = { currentLanguage = Language.ES }
                                        )
                                    }
                                }
                                1 -> { // Theme
                                    Text(s.chooseTheme, style = MaterialTheme.typography.headlineMedium)
                                    Spacer(Modifier.height(48.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                        ThemePreviewOption(
                                            dark = false,
                                            selected = !isDarkMode,
                                            onClick = { onThemeChange(false) },
                                            label = "Light"
                                        )
                                        ThemePreviewOption(
                                            dark = true,
                                            selected = isDarkMode,
                                            onClick = { onThemeChange(true) },
                                            label = "Dark"
                                        )
                                    }
                                }
                                2 -> { // Daltonism
                                    Text(s.daltonismOption, style = MaterialTheme.typography.headlineMedium)
                                    Spacer(Modifier.height(48.dp))
                                    
                                    // Animated Switch Container
                                    val scale by animateFloatAsState(
                                        targetValue = if (isDaltonic) 1.1f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        label = "switchScale"
                                    )
                                    
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Switch(
                                            checked = isDaltonic,
                                            onCheckedChange = { isDaltonic = it },
                                            modifier = Modifier
                                                .scale(1.5f)
                                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        if (isDaltonic) {
                                            Text("Enabled", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Footer (Next Button + Disclaimer)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (step < 2) {
                                step++
                            } else {
                                onFinish(currentLanguage, isDaltonic)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.large),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = if (step < 2) s.next else s.finish,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (step < 2) {
                            Spacer(Modifier.width(12.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    Text(
                        text = s.onboardingDisclaimer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        label = "border"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 8.dp else 2.dp,
        modifier = Modifier
            .size(140.dp, 100.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                if (selected) {
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewOption(
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp, 180.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (selected) 4.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(if (dark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE))
                .clickable(onClick = onClick)
        ) {
            Column(Modifier.padding(16.dp)) {
                // Mock UI
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (dark) Color(0xFFD0BCFF) else Color(0xFF6750A4))
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (dark) Color(0xFF49454F) else Color(0xFFE7E0EC))
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(70.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (dark) Color(0xFF49454F) else Color(0xFFE7E0EC))
                )
                Spacer(Modifier.weight(1f))
                // FAB mock
                Box(
                    Modifier
                        .size(32.dp)
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (dark) Color(0xFFD0BCFF) else Color(0xFF6750A4))
                )
            }
            
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// Helper for shadow
fun Modifier.shadow(
    elevation: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    clip: Boolean = elevation > 0.dp,
    ambientColor: Color = androidx.compose.ui.graphics.Color.Black,
    spotColor: Color = androidx.compose.ui.graphics.Color.Black,
): Modifier = this.then(
    Modifier.graphicsLayer {
        this.shadowElevation = elevation.toPx()
        this.shape = shape
        this.clip = clip
        this.ambientShadowColor = ambientColor
        this.spotShadowColor = spotColor
    }
)
