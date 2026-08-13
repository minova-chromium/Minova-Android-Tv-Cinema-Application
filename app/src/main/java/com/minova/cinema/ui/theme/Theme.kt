package com.minova.cinema.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

val MinovaNightDeep = Color(0xFF080C12)
val MinovaBlack = Color(0xFF0D121A)
val MinovaSurface = Color(0xFF17202C)
val MinovaSurfaceRaised = Color(0xFF253242)
val MinovaSteel = Color(0xFF718195)
val MinovaMuted = Color(0xFFA7B5C5)
val MinovaWhite = Color(0xFFF7FAFC)
val MinovaCobalt = Color(0xFF1769FF)
val MinovaBlue = Color(0xFF0798F2)
val MinovaCyan = Color(0xFF16D8E4)
val MinovaTeal = Color(0xFF24D3B5)
val MinovaCoral = Color(0xFFFF6B70)
val MinovaAmber = Color(0xFFF4B84A)
val MinovaGreen = Color(0xFF48D597)

// Compatibility aliases for screens that use status colors.
val MinovaGold = MinovaAmber
val MinovaRed = MinovaCoral

private val MinovaColors = darkColorScheme(
    primary = MinovaCyan,
    onPrimary = MinovaBlack,
    secondary = MinovaTeal,
    onSecondary = MinovaBlack,
    background = MinovaNightDeep,
    onBackground = MinovaWhite,
    surface = MinovaSurface,
    onSurface = MinovaWhite,
)

private val MinovaTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        letterSpacing = (-0.35).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
)

@Composable
fun MinovaCinemaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MinovaColors,
        typography = MinovaTypography,
        content = content,
    )
}
