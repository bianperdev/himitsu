package me.kafuuneko.rpclient.ui.theme

import androidx.compose.ui.graphics.Color

// Base color scheme tokens live in HimitsuColorScheme.kt; this file keeps the semantic accents.

// 功能图标与状态提示共用的强调色
val AccentSkyColor = Color(0xFF0EA5E9)
val AccentEmeraldColor = Color(0xFF10B981)
val AccentIndigoColor = Color(0xFF6366F1)
val AccentPinkColor = Color(0xFFEC4899)
val AccentAmberColor = Color(0xFFF59E0B)
val AccentBlueColor = Color(0xFF3B82F6)
val AccentVioletColor = Color(0xFF8B5CF6)
val AccentRedColor = Color(0xFFEF4444)

// 角色头像与强调色彩
val CharacterAccentColors = listOf(
    Color(0xFF3B66F5),
    Color(0xFF0D9488),
    Color(0xFFEA580C),
    Color(0xFF8B5CF6),
    Color(0xFFE11D48)
)
val DefaultCharacterAccentColor = CharacterAccentColors.first()
val NarratorAvatarColor = Color(0xFF64748B)

// 模型配置状态颜色
val ProviderDisabledColor = AccentRedColor
val ProviderPendingColor = AccentAmberColor
val ProviderAvailableColor = AccentEmeraldColor

// Token 消耗统计与图表语义色彩
val TokenUsageInputColor = Color(0xFF6366F1)
val TokenUsageOutputColor = Color(0xFFF59E0B)
val TokenUsageCachedColor = Color(0xFF10B981)
val TokenUsageReasoningColor = Color(0xFFEC4899)
val TokenUsageLatencyColor = Color(0xFF0EA5E9)

// 模型消耗排行榜徽章色彩
val RankGoldBadgeColor = Color(0xFFF59E0B)
val RankGoldTextColor = Color(0xFFD97706)
val RankSilverBadgeColor = Color(0xFF94A3B8)
val RankSilverTextColor = Color(0xFF475569)
val RankBronzeBadgeColor = Color(0xFFD97706)
val RankBronzeTextColor = Color(0xFFB45309)

/**
 * 根据名称稳定生成会话列表使用的浅色强调色。
 */
fun getMacaronColor(name: String): Color {
    val hue = kotlin.math.abs(name.hashCode() % 360).toFloat()
    return Color.hsl(hue, saturation = 0.60f, lightness = 0.80f)
}
