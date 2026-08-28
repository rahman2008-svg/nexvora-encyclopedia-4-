package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = KnowledgePrimaryLight,
    onPrimary = Color(0xFF002F6C),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = KnowledgePrimaryLight,
    secondary = KnowledgeSecondaryLight,
    onSecondary = Color(0xFF003830),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = KnowledgeSecondaryLight,
    tertiary = KnowledgeTertiaryLight,
    onTertiary = Color(0xFF452200),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = KnowledgeTertiaryLight,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = DarkOutline,
    outlineVariant = Color(0xFF1E293B)
)

private val LightColorScheme = lightColorScheme(
    primary = KnowledgePrimary,
    onPrimary = Color.White,
    primaryContainer = KnowledgePrimaryContainer,
    onPrimaryContainer = KnowledgeOnPrimaryContainer,
    secondary = KnowledgeSecondary,
    onSecondary = Color.White,
    secondaryContainer = KnowledgeSecondaryContainer,
    onSecondaryContainer = KnowledgeOnSecondaryContainer,
    tertiary = KnowledgeTertiary,
    onTertiary = Color.White,
    tertiaryContainer = KnowledgeTertiaryContainer,
    onTertiaryContainer = KnowledgeOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF64748B),
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun NexVoraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
