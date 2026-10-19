package cn.hunanjiangxi.jxlabtrace.ui.theme

import androidx.compose.foundation.shape.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * 浅色主题（light-only，车间强光高对比）。
 * 色值/字号一律来自 [Tokens]（design/tokens.json 生成物），禁止手写。
 */
object JxLabTheme {

    fun lightColorScheme() = lightColorScheme(
        primary = Tokens.Color.Brand.primary,
        onPrimary = Tokens.Color.Brand.onPrimary,
        primaryContainer = Tokens.Color.Brand.primarySoft,
        onPrimaryContainer = Tokens.Color.Brand.primary,
        background = Tokens.Color.Surface.canvas,
        onBackground = Tokens.Color.Text.primary,
        surface = Tokens.Color.Surface.card,
        onSurface = Tokens.Color.Text.primary,
        surfaceVariant = Tokens.Color.Surface.sunken,
        onSurfaceVariant = Tokens.Color.Text.secondary,
        outline = Tokens.Color.Surface.border,
        outlineVariant = Tokens.Color.Surface.sunken,
        error = Tokens.Color.Status.danger,
        onError = Tokens.Color.Text.inverse,
        errorContainer = Tokens.Color.Status.dangerSoft,
        onErrorContainer = Tokens.Color.Status.danger,
    )

    val typography: Typography
        @Composable @ReadOnlyComposable get() = Typography(
            displayLarge = style(Tokens.Type.Display.size, Tokens.Type.Display.lineHeight, Tokens.Type.Display.weight),
            headlineLarge = style(Tokens.Type.H1.size, Tokens.Type.H1.lineHeight, Tokens.Type.H1.weight),
            headlineMedium = style(Tokens.Type.H2.size, Tokens.Type.H2.lineHeight, Tokens.Type.H2.weight),
            titleMedium = style(Tokens.Type.H3.size, Tokens.Type.H3.lineHeight, Tokens.Type.H3.weight),
            bodyLarge = style(Tokens.Type.Body.size, Tokens.Type.Body.lineHeight, Tokens.Type.Body.weight),
            bodyMedium = style(Tokens.Type.BodyStrong.size, Tokens.Type.BodyStrong.lineHeight, Tokens.Type.BodyStrong.weight),
            labelMedium = style(Tokens.Type.Label.size, Tokens.Type.Label.lineHeight, Tokens.Type.Label.weight),
        )

    val shapes: Shapes
        get() = Shapes(
            small = RoundedCornerShape(Tokens.Radius.sm),
            medium = RoundedCornerShape(Tokens.Radius.md),
            large = RoundedCornerShape(Tokens.Radius.lg),
        )

    private fun style(size: TextUnit, lineHeight: TextUnit, weight: Int): TextStyle = TextStyle(
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = FontWeight(weight),
    )
}
