package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/** 状态语义（颜色永远与文字同时出现——状态双编码）。 */
enum class PillTone { Success, Warning, Danger, Info, Neutral }

/**
 * StatusPill：状态胶囊。颜色 + 圆点 + 文案双编码，非交互（无需 56dp 触控区）。
 */
@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
) {
    val (solid, soft) = toneColors(tone)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Tokens.Radius.pill))
            .background(soft)
            .padding(horizontal = Tokens.Space.space3, vertical = Tokens.Space.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Size.iconSm / 2)
                .clip(CircleShape)
                .background(solid),
        )
        Text(
            text = text,
            color = solid,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

internal fun toneColors(tone: PillTone): Pair<Color, Color> = when (tone) {
    PillTone.Success -> Tokens.Color.Status.success to Tokens.Color.Status.successSoft
    PillTone.Warning -> Tokens.Color.Status.warning to Tokens.Color.Status.warningSoft
    PillTone.Danger -> Tokens.Color.Status.danger to Tokens.Color.Status.dangerSoft
    PillTone.Info -> Tokens.Color.Status.info to Tokens.Color.Status.infoSoft
    PillTone.Neutral -> Tokens.Color.Status.neutral to Tokens.Color.Status.neutralSoft
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun StatusPillPreview() {
    JxPreviewTheme {
        Row(
            modifier = Modifier.padding(Tokens.Space.space4),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
        ) {
            StatusPill("进行中", PillTone.Warning)
            StatusPill("已完成", PillTone.Success)
            StatusPill("异常", PillTone.Danger)
            StatusPill("待处理", PillTone.Neutral)
        }
    }
}
