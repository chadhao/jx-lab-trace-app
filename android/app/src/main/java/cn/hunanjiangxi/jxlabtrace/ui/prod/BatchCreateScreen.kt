package cn.hunanjiangxi.jxlabtrace.ui.prod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.prod.BatchCreateUiState
import cn.hunanjiangxi.jxlabtrace.prod.BatchCreateViewModel
import cn.hunanjiangxi.jxlabtrace.prod.BatchForm
import cn.hunanjiangxi.jxlabtrace.prod.CreateBatchBody
import cn.hunanjiangxi.jxlabtrace.prod.ProdBatch
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.EmptyState
import cn.hunanjiangxi.jxlabtrace.ui.components.FieldCard
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.Skeleton
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S08 生产 · 建批（M7-1）：必填 0 —— 默认沿用上批客户/配方；
 * 批号由后端生成只读；改默认值须显式点开修改（防手滑换料）。
 */
@Composable
fun BatchCreateScreen(
    viewModel: BatchCreateViewModel,
    onCreated: (batchId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()

    when (val s = state) {
        BatchCreateUiState.Loading -> Column(modifier = modifier.fillMaxSize()) {
            TopBar(title = "建生产批")
            Skeleton(Modifier.padding(Tokens.Space.space4))
        }

        is BatchCreateUiState.Error -> Column(modifier = modifier.fillMaxSize()) {
            TopBar(title = "建生产批")
            EmptyState(
                title = "建批页加载失败",
                hint = s.message,
                modifier = Modifier.padding(Tokens.Space.space6),
            )
            BigButton(text = "重试", onClick = viewModel::load, style = BigButtonStyle.Quiet)
        }

        is BatchCreateUiState.Created -> {
            androidx.compose.runtime.LaunchedEffect(s.batch.id) { onCreated(s.batch.id) }
            Column(modifier = modifier.fillMaxSize()) {
                TopBar(title = "建生产批")
                FieldCard(
                    label = "批号（系统生成）",
                    value = s.batch.human.ifBlank { s.batch.code },
                    valueColor = Tokens.Color.Brand.primary,
                )
                FieldCard(label = "状态", value = s.batch.status)
                BigButton(text = "去投料", onClick = { onCreated(s.batch.id) })
            }
        }

        is BatchCreateUiState.Editing -> BatchCreateForm(
            previous = s.previous,
            form = s.form,
            message = message,
            onUpdate = viewModel::updateField,
            onRebuild = viewModel::rebuild,
            onSubmit = viewModel::submit,
            modifier = modifier,
        )
    }
}

@Composable
private fun BatchCreateForm(
    previous: ProdBatch?,
    form: CreateBatchBody,
    message: String?,
    onUpdate: ((CreateBatchBody) -> CreateBatchBody) -> Unit,
    onRebuild: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingCustomer by remember { mutableStateOf(false) }
    var editingInput by remember { mutableStateOf(false) }
    var editingOutput by remember { mutableStateOf(false) }
    val errors = BatchForm.validate(form)

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "建生产批")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            Text(
                text = BatchForm.previousSummary(previous),
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.labelLarge,
            )

            // 客户（沿用上批；显式修改）
            EditableIdField(
                label = "客户（沿用上批）",
                value = form.customer_id,
                editing = editingCustomer,
                onToggle = { editingCustomer = it },
                onValue = { v -> onUpdate { it.copy(customer_id = v) } },
            )
            // 投入物料（配方）
            EditableIdField(
                label = "投入物料 · 配方（沿用上批）",
                value = form.input_material_id,
                editing = editingInput,
                onToggle = { editingInput = it },
                onValue = { v -> onUpdate { it.copy(input_material_id = v) } },
            )
            // 计划产出物料
            EditableIdField(
                label = "计划产出物料（沿用上批）",
                value = form.planned_output_material_id,
                editing = editingOutput,
                onToggle = { editingOutput = it },
                onValue = { v -> onUpdate { it.copy(planned_output_material_id = v) } },
            )

            // 班组＝登录人所属（只读展示；后端按会话定操作人）
            FieldCard(label = "班组（登录人所属）", value = "当前登录班组")

            // 批号只读
            FieldCard(
                label = "批号（系统生成）",
                value = "提交后生成",
                hint = "不可手输",
                valueColor = Tokens.Color.Text.tertiary,
            )

            Text(
                text = "提交后批号与配方立即生效；投料前如需作废重建请到 PC 端。",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelMedium,
            )

            if (message != null) {
                Text(
                    text = message,
                    color = Tokens.Color.Status.danger,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (errors.isNotEmpty()) {
                errors.forEach { e ->
                    Text(
                        text = "· $e",
                        color = Tokens.Color.Status.danger,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            BigButton(text = "确认建批", onClick = onSubmit, enabled = errors.isEmpty())
            BigButton(text = "清空重建（不提交）", onClick = onRebuild, style = BigButtonStyle.Quiet)
        }
    }
}

@Composable
private fun EditableIdField(
    label: String,
    value: Long,
    editing: Boolean,
    onToggle: (Boolean) -> Unit,
    onValue: (Long) -> Unit,
) {
    if (editing) {
        OutlinedTextField(
            value = if (value == 0L) "" else value.toString(),
            onValueChange = { v -> onValue(v.filter { it.isDigit() }.take(12).toLongOrNull() ?: 0L) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                androidx.compose.material3.TextButton(onClick = { onToggle(false) }) {
                    Text("完成", color = Tokens.Color.Brand.primary)
                }
            },
        )
    } else {
        FieldCard(
            label = label,
            value = if (value <= 0L) "未设置" else "#$value",
            hint = if (value > 0L) "点击修改" else "必填",
        )
        BigButton(
            text = "修改",
            style = BigButtonStyle.Quiet,
            onClick = { onToggle(true) },
        )
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun BatchCreatePreview() {
    JxPreviewTheme {
        BatchCreateForm(
            previous = ProdBatch(
                id = 7,
                human = "1C-CG-9407-9408-261020-07-000-000-0",
                status = "已完成",
                customer_id = 9407,
                input_material_id = 11,
                planned_output_material_id = 12,
            ),
            form = BatchForm.defaultsFrom(
                ProdBatch(customer_id = 9407, input_material_id = 11, planned_output_material_id = 12),
            ),
            message = null,
            onUpdate = {},
            onRebuild = {},
            onSubmit = {},
        )
    }
}
