package me.kafuuneko.rpclient.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Base color scheme tokens live in HimitsuColorScheme.kt; this file keeps the semantic accents.
// Accents are composable getters so every call site follows the active theme without changes.

private val accents: HimitsuAccents
    @Composable
    @ReadOnlyComposable
    get() = LocalHimitsuColors.current.accents

// 功能图标与状态提示共用的强调色
val AccentSkyColor: Color @Composable @ReadOnlyComposable get() = accents.sky
val AccentEmeraldColor: Color @Composable @ReadOnlyComposable get() = accents.emerald
val AccentIndigoColor: Color @Composable @ReadOnlyComposable get() = accents.indigo
val AccentPinkColor: Color @Composable @ReadOnlyComposable get() = accents.pink
val AccentAmberColor: Color @Composable @ReadOnlyComposable get() = accents.amber
val AccentBlueColor: Color @Composable @ReadOnlyComposable get() = accents.blue
val AccentVioletColor: Color @Composable @ReadOnlyComposable get() = accents.violet
val AccentRedColor: Color @Composable @ReadOnlyComposable get() = accents.red

// 角色头像与强调色彩
// Stored in UI state by view models, so these stay constant; RpAvatar adapts them for readable text.
val CharacterAccentColors = listOf(
    Color(0xFF6A4FD8),
    Color(0xFF0E7C84),
    Color(0xFF9A5B12),
    Color(0xFFB83A78),
    Color(0xFF4A63C8)
)
val DefaultCharacterAccentColor = CharacterAccentColors.first()
val NarratorAvatarColor = Color(0xFF8A80A0)

// 模型配置状态颜色
val ProviderDisabledColor: Color @Composable @ReadOnlyComposable get() = accents.red
val ProviderPendingColor: Color @Composable @ReadOnlyComposable get() = accents.amber
val ProviderAvailableColor: Color @Composable @ReadOnlyComposable get() = LocalHimitsuColors.current.success

// Token 消耗统计与图表语义色彩
val TokenUsageInputColor: Color @Composable @ReadOnlyComposable get() = accents.indigo
val TokenUsageOutputColor: Color @Composable @ReadOnlyComposable get() = accents.amber
val TokenUsageCachedColor: Color @Composable @ReadOnlyComposable get() = accents.emerald
val TokenUsageReasoningColor: Color @Composable @ReadOnlyComposable get() = accents.pink
val TokenUsageLatencyColor: Color @Composable @ReadOnlyComposable get() = accents.sky

// 模型消耗排行榜徽章色彩
val RankGoldBadgeColor: Color @Composable @ReadOnlyComposable get() = accents.amber
val RankGoldTextColor: Color @Composable @ReadOnlyComposable get() = accents.amber
val RankSilverBadgeColor: Color @Composable @ReadOnlyComposable get() = accents.silver
val RankSilverTextColor: Color @Composable @ReadOnlyComposable get() = accents.silver
val RankBronzeBadgeColor: Color @Composable @ReadOnlyComposable get() = accents.bronze
val RankBronzeTextColor: Color @Composable @ReadOnlyComposable get() = accents.bronze

/**
 * 根据名称稳定生成会话列表使用的浅色强调色。
 *
 * Used for tints, stripes and avatars; text drawn in this color goes through [readableAccent].
 */
fun getMacaronColor(name: String): Color {
    val hue = kotlin.math.abs(name.hashCode() % 360).toFloat()
    return Color.hsl(hue, saturation = 0.55f, lightness = 0.70f)
}
