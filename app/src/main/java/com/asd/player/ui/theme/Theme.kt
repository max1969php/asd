package com.asd.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette da fotocopia di fanzine: nero toner, carta riciclata, giallo evidenziatore, rosso spray. */
object Asd {
    val Toner = Color(0xFF0B0B0B)
    val Asphalt = Color(0xFF1A1A1A)
    val Concrete = Color(0xFF2A2A2A)
    val Paper = Color(0xFFEDE8DA)
    val Smudge = Color(0xFF8A877E)
    val Acid = Color(0xFFD7FF1F)
    val Spray = Color(0xFFFF2B2B)
}

private val Colors = darkColorScheme(
    primary = Asd.Acid,
    onPrimary = Asd.Toner,
    secondary = Asd.Spray,
    onSecondary = Asd.Paper,
    tertiary = Asd.Paper,
    background = Asd.Toner,
    onBackground = Asd.Paper,
    surface = Asd.Toner,
    onSurface = Asd.Paper,
    surfaceVariant = Asd.Asphalt,
    onSurfaceVariant = Asd.Smudge,
    surfaceContainer = Asd.Asphalt,
    surfaceContainerHigh = Asd.Concrete,
    surfaceContainerHighest = Asd.Concrete,
    outline = Asd.Paper,
    outlineVariant = Asd.Concrete,
    error = Asd.Spray,
    onError = Asd.Paper,
)

/** Titoli "da stencil" in sans nero pesante, tutto il resto in monospace da macchina da scrivere. */
val Stencil = FontFamily.SansSerif
val Typewriter = FontFamily.Monospace

private val AsdTypography = Typography(
    displayLarge = TextStyle(fontFamily = Stencil, fontWeight = FontWeight.Black, fontSize = 64.sp, letterSpacing = (-2).sp, lineHeight = 60.sp),
    headlineLarge = TextStyle(fontFamily = Stencil, fontWeight = FontWeight.Black, fontSize = 36.sp, letterSpacing = (-1).sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = Stencil, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Stencil, fontWeight = FontWeight.Black, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Stencil, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Typewriter, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = Typewriter, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = Typewriter, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = Typewriter, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = Typewriter, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = Typewriter, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp),
)

private val Square = RoundedCornerShape(0.dp)

@Composable
fun AsdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = AsdTypography,
        shapes = Shapes(Square, Square, Square, Square, Square),
        content = content,
    )
}
