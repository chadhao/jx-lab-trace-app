// GENERATED — DO NOT EDIT
// 来源（唯一真相）：design/tokens.json
// 生成脚本：scripts/gen_tokens.py；一致性校验：scripts/check_tokens.py（CI 必跑）。
// 手改本文件会被 CI 打回。

package cn.hunanjiangxi.jxlabtrace.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设计令牌（由 design/tokens.json 生成；颜色/字号/间距/圆角/尺寸）。 */
object Tokens {
    object Color {
        object Brand {
            val primary = Color(0xFF0B4F9E)
            val primaryPressed = Color(0xFF083B76)
            val primarySoft = Color(0xFFE8F0FB)
            val onPrimary = Color(0xFFFFFFFF)
        }
        object Domain {
            object Receiving {
                val base = Color(0xFF1D4ED8)
                val soft = Color(0xFFE7EDFC)
                val on = Color(0xFFFFFFFF)
            }
            object Production {
                val base = Color(0xFFB54708)
                val soft = Color(0xFFFDF0E4)
                val on = Color(0xFFFFFFFF)
            }
            object Qc {
                val base = Color(0xFF067647)
                val soft = Color(0xFFE4F4EC)
                val on = Color(0xFFFFFFFF)
            }
            object Shipment {
                val base = Color(0xFF0E7490)
                val soft = Color(0xFFE2F1F5)
                val on = Color(0xFFFFFFFF)
            }
            object Trace {
                val base = Color(0xFF6D28D9)
                val soft = Color(0xFFEFE9FD)
                val on = Color(0xFFFFFFFF)
            }
        }
        object Status {
            val success = Color(0xFF067647)
            val successSoft = Color(0xFFE4F4EC)
            val warning = Color(0xFFB54708)
            val warningSoft = Color(0xFFFDF0E4)
            val danger = Color(0xFFB42318)
            val dangerSoft = Color(0xFFFDECEA)
            val info = Color(0xFF0B4F9E)
            val infoSoft = Color(0xFFE8F0FB)
            val neutral = Color(0xFF475467)
            val neutralSoft = Color(0xFFF2F4F7)
        }
        object Text {
            val primary = Color(0xFF101828)
            val secondary = Color(0xFF475467)
            val tertiary = Color(0xFF667085)
            val inverse = Color(0xFFFFFFFF)
            val disabled = Color(0xFF98A2B3)
        }
        object Surface {
            val canvas = Color(0xFFF2F4F7)
            val card = Color(0xFFFFFFFF)
            val sunken = Color(0xFFF9FAFB)
            val border = Color(0xFFD0D5DD)
            val borderStrong = Color(0xFF98A2B3)
            val scrim = Color(0x73101828)
        }
    }
    object Type {
        object Display {
            val size = 34.sp
            val lineHeight = 40.sp
            val weight = 700
        }
        object H1 {
            val size = 26.sp
            val lineHeight = 32.sp
            val weight = 700
        }
        object H2 {
            val size = 20.sp
            val lineHeight = 26.sp
            val weight = 700
        }
        object H3 {
            val size = 17.sp
            val lineHeight = 24.sp
            val weight = 600
        }
        object Body {
            val size = 15.sp
            val lineHeight = 22.sp
            val weight = 400
        }
        object BodyStrong {
            val size = 15.sp
            val lineHeight = 22.sp
            val weight = 600
        }
        object Label {
            val size = 13.sp
            val lineHeight = 18.sp
            val weight = 500
        }
        object Code {
            val size = 15.sp
            val lineHeight = 22.sp
            val weight = 600
        }
    }
    object Space {
        val space1 = 4.dp
        val space2 = 8.dp
        val space3 = 12.dp
        val space4 = 16.dp
        val space5 = 20.dp
        val space6 = 24.dp
        val space8 = 32.dp
        val space10 = 40.dp
        val space12 = 48.dp
    }
    object Radius {
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val pill = 999.dp
    }
    object Size {
        val touchMin = 56.dp
        val touchComfort = 64.dp
        val primaryButton = 60.dp
        val iconSm = 20.dp
        val iconMd = 24.dp
        val iconLg = 32.dp
        val entryIcon = 44.dp
        val tabbar = 64.dp
        val traceScanHero = 120.dp
        val topbar = 56.dp
    }
}
