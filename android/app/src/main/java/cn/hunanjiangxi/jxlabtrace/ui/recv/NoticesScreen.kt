package cn.hunanjiangxi.jxlabtrace.ui.recv

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.recv.DialActions
import cn.hunanjiangxi.jxlabtrace.recv.NoNoticeModel
import cn.hunanjiangxi.jxlabtrace.recv.NoticesUiState
import cn.hunanjiangxi.jxlabtrace.recv.NoticesViewModel
import cn.hunanjiangxi.jxlabtrace.recv.RecvNotice
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.ConfirmDialog
import cn.hunanjiangxi.jxlabtrace.ui.components.EmptyState
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.Skeleton
import cn.hunanjiangxi.jxlabtrace.ui.components.StatusPill
import cn.hunanjiangxi.jxlabtrace.ui.components.PillTone
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.recv.ReturnFlow
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S04 到货确认（M05）：选预报 → 确认对话框 → POST arrivals；
 * 无预报 ⇒ 拦截模型（零写请求 + 拨号）。
 */
@Composable
fun NoticesScreen(
    viewModel: NoticesViewModel,
    onConfirmed: (truckId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val confirming by viewModel.confirming.collectAsState()
    val confirmed by viewModel.confirmed.collectAsState()
    val message by viewModel.message.collectAsState()

    confirmed?.let { truck ->
        onConfirmed(truck.id)
        viewModel.clearConfirmed()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "到货确认", actions = {
            Box(
                modifier = Modifier
                    .padding(horizontal = Tokens.Space.space3),
            ) {
                Text(
                    text = "刷新",
                    color = Tokens.Color.Brand.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        })
        when (val s = state) {
            NoticesUiState.Loading -> Skeleton(Modifier.padding(Tokens.Space.space4))
            is NoticesUiState.Error -> EmptyState(
                title = "预报加载失败",
                hint = s.message,
                modifier = Modifier.padding(Tokens.Space.space6),
            )
            is NoticesUiState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Tokens.Space.space4,
                    vertical = Tokens.Space.space3,
                ),
            ) {
                if (s.pending.isEmpty()) {
                    item {
                        EmptyState(
                            title = "今日暂无待确认车次",
                            hint = "下拉刷新仍无？请使用无预报登记流程",
                        )
                    }
                }
                items(s.pending, key = { it.id }) { notice ->
                    NoticeCard(notice = notice, onConfirm = { viewModel.askConfirm(notice) })
                }
                if (s.arrived.isNotEmpty()) {
                    item {
                        Text(
                            text = "已完成",
                            color = Tokens.Color.Text.tertiary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = Tokens.Space.space3),
                        )
                    }
                }
                items(s.arrived, key = { "done-${it.id}" }) { notice ->
                    NoticeCard(notice = notice, onConfirm = null)
                }
            }
        }
    }

    confirming?.let { notice ->
        ConfirmDialog(
            title = "确认到货？",
            message = "${notice.plate_no} · ${notice.customer_name} · ${notice.material_name}\n" +
                "确认后生成车码，进入过磅。",
            confirmText = "确认到货",
            onConfirm = viewModel::confirmArrival,
            onDismiss = viewModel::cancelConfirm,
            danger = false,
        )
    }

    message?.let { msg ->
        ConfirmDialog(
            title = "操作失败",
            message = msg,
            confirmText = "知道了",
            onConfirm = viewModel::clearMessage,
            onDismiss = viewModel::clearMessage,
            danger = false,
        )
    }
}

@Composable
private fun NoticeCard(notice: RecvNotice, onConfirm: (() -> Unit)?) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Tokens.Space.space3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "${notice.plate_no} · 第 ${notice.seq_no} 车",
                color = Tokens.Color.Text.primary,
                style = MaterialTheme.typography.titleSmall,
            )
            if (onConfirm != null) {
                Text(
                    text = "确认到货 ›",
                    color = Tokens.Color.Brand.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                StatusPill(text = notice.status, tone = PillTone.Success)
            }
        }
        Text(
            text = "${notice.customer_name} · ${notice.material_name}",
            color = Tokens.Color.Text.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (onConfirm != null) {
            BigButton(text = "确认到货", onClick = onConfirm)
        }
    }
}

/**
 * 无预报拦截页（S04 ② · M5-1）：原因 + 最近 3 条 + 一键拨号。
 * 结构上无写请求 —— 只消费 [NoNoticeModel]（只读）。
 */
@Composable
fun NoNoticeInterceptScreen(
    plate: String,
    model: NoNoticeModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val recent = model.recentNotices(plate)
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "无预报登记")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
        ) {
            Text(
                text = "该车牌今日无预报，暂不能收货",
                color = Tokens.Color.Status.danger,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "请先电话联系综合运营部补预报，补录后下拉刷新本页即可继续",
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            BigButton(
                text = "一键拨号 ${DialActions.OPERATIONS_PHONE}",
                onClick = {
                    val dial = model.dialIntent()
                    context.startActivity(Intent(dial.action, Uri.parse(dial.uri)))
                },
            )
            Text(
                text = "该车牌最近 ${recent.size} 条预报",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelLarge,
            )
            recent.forEach { notice ->
                Text(
                    text = "${notice.arrive_date.ifBlank { notice.notice_no }} · " +
                        "${notice.customer_name} · ${notice.status}",
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun NoticeCardPreview() {
    JxPreviewTheme {
        NoticeCard(
            notice = RecvNotice(
                plate_no = "湘F·A1234",
                seq_no = 2,
                customer_name = "岳阳观盛",
                material_name = "石油焦（硫<1%）",
                status = "预报",
            ),
            onConfirm = {},
        )
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Composable
private fun ReturnConfirmPreview() {
    JxPreviewTheme {
        Column {
            Text(ReturnFlow.CONFIRM_TITLE, style = MaterialTheme.typography.titleLarge)
            Text(ReturnFlow.CONFIRM_MESSAGE, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
