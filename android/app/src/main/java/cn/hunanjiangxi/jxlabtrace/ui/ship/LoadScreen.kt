package cn.hunanjiangxi.jxlabtrace.ui.ship

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ship.LoadUiState
import cn.hunanjiangxi.jxlabtrace.ship.LoadViewModel
import cn.hunanjiangxi.jxlabtrace.ship.ShipSummary
import cn.hunanjiangxi.jxlabtrace.ship.Shipment
import cn.hunanjiangxi.jxlabtrace.ship.ShipmentItem
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.EmptyState
import cn.hunanjiangxi.jxlabtrace.ui.components.FieldCard
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.PillTone
import cn.hunanjiangxi.jxlabtrace.ui.components.Skeleton
import cn.hunanjiangxi.jxlabtrace.ui.components.StatusPill
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S12 出货 · 装车归集与出场（M10-1/M10-2）：
 * - 进入即连续扫码（宿主挂 ScanViewModel，confirmLabel="归集此袋"）；
 * - 汇总（袋数/净重）实时累加；重复袋本地即时拦截；
 * - 「完成装车 · 出场登记」⇒ 车牌/司机录入（后端必填）⇒ 确认出场。
 */
@Composable
fun LoadScreen(
    viewModel: LoadViewModel,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()

    when (val s = state) {
        is LoadUiState.Error -> Column(modifier = modifier.fillMaxSize()) {
            TopBar(title = "装车")
            EmptyState(
                title = "装车页加载失败",
                hint = s.message,
                modifier = Modifier.padding(Tokens.Space.space6),
            )
        }

        is LoadUiState.Departed -> Column(modifier = modifier.fillMaxSize()) {
            TopBar(title = "出场登记 · ${s.shipment.shipment_no}")
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space6),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
            ) {
                StatusPill(text = "已出场", tone = PillTone.Success)
                FieldCard(label = "出货单", value = s.shipment.shipment_no)
                FieldCard(label = "客户", value = s.shipment.customer_name.ifBlank { "—" })
                FieldCard(label = "车牌", value = s.shipment.plate_no.ifBlank { "—" })
                FieldCard(label = "司机", value = s.shipment.driver.ifBlank { "—" })
                FieldCard(label = "出场时间", value = s.shipment.ship_at ?: "—")
                Text(
                    text = "出场即出库；事后撤销须走 PC 端审批（APP 无入口）",
                    color = Tokens.Color.Text.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        is LoadUiState.DepartReady -> DepartSheet(
            shipment = s.shipment,
            summary = s.summary,
            onConfirm = viewModel::confirmDepart,
            onBack = viewModel::backToCollecting,
            modifier = modifier,
        )

        is LoadUiState.Ready -> Column(modifier = modifier.fillMaxSize()) {
            TopBar(title = "装车归集")
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space6),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
            ) {
                Text(
                    text = "扫第一袋成品吨袋码即建出货单",
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                BigButton(text = "开始扫码归集", onClick = onStartScan)
            }
        }

        is LoadUiState.Collecting -> Column(modifier = modifier.fillMaxSize()) {
            TopBar(title = "装车 · ${s.shipment.shipment_no}")
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                // KPI：汇总实时（M10-2）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Text(
                            text = "${s.summary.bagCount}",
                            color = Tokens.Color.Text.primary,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = buildString {
                                append("已扫袋数")
                                s.summary.netWeightTons?.let { append(" · 净重 ${"%.1f".format(it)} t") }
                            },
                            color = Tokens.Color.Text.tertiary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    StatusPill(text = "归集中", tone = PillTone.Info)
                }
                Text(
                    text = "客户 ${s.shipment.customer_name.ifBlank { "由首袋推定" }}",
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                message?.let {
                    Text(
                        text = it,
                        color = Tokens.Color.Status.danger,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                BigButton(text = "继续扫码", onClick = onStartScan)
                BigButton(
                    text = "完成装车 · 出场登记",
                    onClick = viewModel::goDepart,
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
                ) {
                    items(s.items, key = { it.bag_code }) { item ->
                        FieldCard(
                            label = "成品吨袋",
                            value = item.bag_human.ifBlank { item.bag_code },
                            hint = item.fg_lot_code.ifBlank { null },
                        )
                    }
                }
            }
        }
    }

    // 消息自动清除交宿主（与 FeedScreen 同口径：仅展示）
}

/** 出场登记表单（plate_no/driver 后端必填非空；出场时间由服务端写）。 */
@Composable
private fun DepartSheet(
    shipment: Shipment,
    summary: ShipSummary,
    onConfirm: (plateNo: String, driver: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var plate by remember { mutableStateOf(shipment.plate_no) }
    var driver by remember { mutableStateOf(shipment.driver) }
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "出场登记 · ${shipment.shipment_no}")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            FieldCard(
                label = "汇总",
                value = "${summary.bagCount} 袋" +
                    (summary.netWeightTons?.let { " · 净重 ${"%.1f".format(it)} t" } ?: ""),
            )
            FieldCard(label = "客户", value = shipment.customer_name.ifBlank { "—" })
            OutlinedTextField(
                value = plate,
                onValueChange = { plate = it },
                label = { Text("车牌（必填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = driver,
                onValueChange = { driver = it },
                label = { Text("司机（必填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "出场时间自动取服务端时间；出场即出库，撤销须走 PC 端审批",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelMedium,
            )
            BigButton(
                text = "确认出场",
                onClick = { onConfirm(plate, driver) },
                enabled = plate.isNotBlank() && driver.isNotBlank(),
            )
            BigButton(text = "返回归集", onClick = onBack, style = BigButtonStyle.Quiet)
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun LoadScreenPreview() {
    JxPreviewTheme {
        DepartSheet(
            shipment = Shipment(id = 1, shipment_no = "CH261020-001", customer_name = "岳阳观盛"),
            summary = ShipSummary(bagCount = 40, netWeightTons = 39.8),
            onConfirm = { _, _ -> },
            onBack = {},
        )
    }
}
