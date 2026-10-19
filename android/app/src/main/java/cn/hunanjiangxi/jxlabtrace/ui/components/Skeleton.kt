package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * Skeleton：加载占位（呼吸动画的圆角块）。不承载业务语义。
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = Tokens.Size.touchComfort,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(720),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    Box(
        modifier = modifier
            .alpha(alpha)
            .height(height)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Tokens.Color.Status.neutralSoft),
    )
}

/** 多行骨架块组合。 */
@Composable
fun SkeletonLines(
    modifier: Modifier = Modifier,
    lines: Int = 3,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(lines) { index ->
            Skeleton(
                modifier = Modifier.padding(vertical = Tokens.Space.space1),
                height = if (index == 0) Tokens.Size.touchMin / 2 else Tokens.Size.touchMin / 3,
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun SkeletonPreview() {
    JxPreviewTheme {
        SkeletonLines(
            modifier = Modifier.padding(Tokens.Space.space4),
            lines = 3,
        )
    }
}
