package cn.hunanjiangxi.jxlabtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cn.hunanjiangxi.jxlabtrace.auth.LoginWebViewActivity
import cn.hunanjiangxi.jxlabtrace.auth.Me
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.print.AndroidBluetoothSpp
import cn.hunanjiangxi.jxlabtrace.print.LabelRenderer
import cn.hunanjiangxi.jxlabtrace.print.PrintSession
import cn.hunanjiangxi.jxlabtrace.print.PrinterStatusModel
import cn.hunanjiangxi.jxlabtrace.prod.BatchCreateViewModel
import cn.hunanjiangxi.jxlabtrace.prod.FgPrintViewModel
import cn.hunanjiangxi.jxlabtrace.prod.FeedViewModel
import cn.hunanjiangxi.jxlabtrace.prod.OperationViewModel
import cn.hunanjiangxi.jxlabtrace.prod.ProdRepository
import cn.hunanjiangxi.jxlabtrace.prod.ServerClock
import cn.hunanjiangxi.jxlabtrace.recv.BagPrintViewModel
import cn.hunanjiangxi.jxlabtrace.recv.NoticesViewModel
import cn.hunanjiangxi.jxlabtrace.recv.RecvRepository
import cn.hunanjiangxi.jxlabtrace.recv.WeighViewModel
import cn.hunanjiangxi.jxlabtrace.rpt.ReportRepository
import cn.hunanjiangxi.jxlabtrace.rpt.ReportViewModel
import cn.hunanjiangxi.jxlabtrace.sample.SampleRepository
import cn.hunanjiangxi.jxlabtrace.ship.LoadViewModel
import cn.hunanjiangxi.jxlabtrace.ship.ShipRepository
import cn.hunanjiangxi.jxlabtrace.trace.TraceRepository
import cn.hunanjiangxi.jxlabtrace.trace.TraceViewModel
import cn.hunanjiangxi.jxlabtrace.ui.components.EmptyState
import cn.hunanjiangxi.jxlabtrace.ui.components.TabBar
import cn.hunanjiangxi.jxlabtrace.ui.components.TabBarItem
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.home.HOME_TABS
import cn.hunanjiangxi.jxlabtrace.ui.home.HomeEntryId
import cn.hunanjiangxi.jxlabtrace.ui.home.HomeScreen
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.printer.PrinterScreen
import cn.hunanjiangxi.jxlabtrace.ui.prod.BatchCreateScreen
import cn.hunanjiangxi.jxlabtrace.ui.prod.FgPrintScreen
import cn.hunanjiangxi.jxlabtrace.ui.prod.FeedScreen
import cn.hunanjiangxi.jxlabtrace.ui.prod.OperationScreen
import cn.hunanjiangxi.jxlabtrace.ui.recv.BagPrintScreen
import cn.hunanjiangxi.jxlabtrace.ui.recv.NoticesScreen
import cn.hunanjiangxi.jxlabtrace.ui.recv.WeighScreen
import cn.hunanjiangxi.jxlabtrace.ui.rpt.ReportScreen
import cn.hunanjiangxi.jxlabtrace.ui.sample.SampleScreen
import cn.hunanjiangxi.jxlabtrace.ui.ship.LoadScreen
import cn.hunanjiangxi.jxlabtrace.ui.theme.JxLabTheme
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens
import cn.hunanjiangxi.jxlabtrace.ui.trace.TraceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 登录路由纯函数：sid 空白 ⇒ 应去登录屏（MIMO-TASK-LOGIN-FLOW-FIX F-2）。
 * null / 空串 / 纯空白均视为无会话。
 */
fun shouldRouteToLogin(sid: String?): Boolean = sid.isNullOrBlank()

/** 首页流转偏好（S02 导航宿主）：收货链确认车次的落点，供「建袋打码」直入恢复上下文。 */
private const val HOME_FLOW_PREFS = "home_flow"
private const val KEY_LAST_TRUCK = "last_truck_id"

/** 打印机地址偏好（与 S15 PrinterScreen 同源口径）。 */
private const val PRINTER_PREFS = "printer_memory"
private const val KEY_PRINTER_ADDRESS = "address"

/** 生产批状态字面量（进行中筛选，后端中文状态口径）。 */
private const val BATCH_ACTIVE_STATUS = "进行中"

/** BatchFlow 解析哨兵：未解析 / 无进行中批 / 加载失败。 */
private const val BATCH_UNRESOLVED = 0L
private const val BATCH_NONE = -1L
private const val BATCH_LOAD_FAILED = -2L

/** 入口屏推入路由（单 Activity + Compose 状态路由，不引 Navigation 库）。 */
private sealed interface PushRoute {
    /** S04 到货确认（链根：确认后推 S05）。 */
    object Notices : PushRoute

    /** S05 过磅。 */
    data class Weigh(val truckId: Long) : PushRoute

    /** S06 建袋打码（truckId<=0 ⇒ 先导空态引导去收货）。 */
    data class BagPrint(val truckId: Long) : PushRoute

    /** S12 装车出场。 */
    object Load : PushRoute

    /** S07 取样。 */
    object Sample : PushRoute

    /** S08 建批。 */
    object BatchCreate : PushRoute

    /** S09/S10/S11 批次作用域屏（batchId=0 ⇒ 宿主解析进行中批）。 */
    data class BatchFlow(val stage: BatchStage, val batchId: Long) : PushRoute

    /** S14 报表（只读）。 */
    object Report : PushRoute
}

private enum class BatchStage { FEED, OPERATION, FG_PRINT }

/** 宿主上下文（跨组合稳定的引用集合，避免巨型参数表）。 */
private class HostCtx(
    val app: JxLabTraceApp,
    val printerStatus: PrinterStatusModel,
    val printOne: suspend (String) -> ApiResult<Unit>,
    /** S03 扫码接通前的延后提示（MIMO-TASK-S02-NAV 本期不含扫码）。 */
    val scanDeferred: () -> Unit,
    val onGoPrinter: () -> Unit,
)

/**
 * 占位主页 → 现场首页 + 底部导航宿主（MIMO-TASK-S02-NAV）。
 *
 * 路由模型（简化路由：单 Activity + Compose 状态，不引 Navigation 库）：
 * - sid 存在 ⇒ TabBar 三 Tab「现场/追溯/我的」（现场=HomeScreen，我的=PrinterScreen）；
 * - 入口屏全屏推入（返回键回 Home 现场 Tab），TabBar 在推入屏上隐藏；
 * - 会话失效（/api/me 经原位重授权后仍 401）⇒ 清 sid 回 S01 登录。
 */
class MainActivity : ComponentActivity() {

    /** 防抖位：登录屏已拉起、结果未回期间，onResume 不再重复 launch。 */
    private var awaitingLoginResult = false

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 登录屏已关（成功 / 失败确认 / back）：清防抖位，由 onResume 按 sid 决定是否再拉起
        awaitingLoginResult = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = JxLabTheme.lightColorScheme(),
                typography = JxLabTheme.typography,
                shapes = JxLabTheme.shapes,
            ) {
                AppShell(
                    versionName = BuildConfig.VERSION_NAME,
                    onSessionDead = ::expireSession,
                )
            }
        }
        routeToLoginIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        routeToLoginIfNeeded()
    }

    private fun routeToLoginIfNeeded() {
        if (awaitingLoginResult) return
        val app = application as JxLabTraceApp
        if (!shouldRouteToLogin(app.sidStore.read())) return
        awaitingLoginResult = true
        loginLauncher.launch(Intent(this, LoginWebViewActivity::class.java))
    }

    /**
     * 会话失效出口（/api/me 401 且原位重授权未挽回）：清 sid + 过期 WebView 会话 Cookie，
     * 回 S01 登录（与登录屏「我知道了」的清理口径同源）。
     */
    private fun expireSession() {
        val app = application as JxLabTraceApp
        app.sidStore.write(null)
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setCookie(BuildConfig.BASE_URL, "jx_sid=; Max-Age=0")
            cm.flush()
        }
        if (awaitingLoginResult) return
        awaitingLoginResult = true
        loginLauncher.launch(Intent(this, LoginWebViewActivity::class.java))
    }
}

/** 记忆体里的打印桥（S06/S11 连打队列口：码 → 位图 → 蓝牙 TSPL）。 */
private fun buildPrintOne(context: Context): suspend (String) -> ApiResult<Unit> {
    val session = PrintSession(AndroidBluetoothSpp(context))
    val prefs = context.getSharedPreferences(PRINTER_PREFS, Context.MODE_PRIVATE)
    return { code ->
        withContext(Dispatchers.IO) {
            val address = prefs.getString(KEY_PRINTER_ADDRESS, null).orEmpty()
            when {
                address.isBlank() -> ApiResult.Failure(
                    ApiError.Biz("PRINT_NO_DEVICE", "未连接打印机，请先到「我的」页连接打印机"),
                )

                code.length != 27 -> ApiResult.Failure(
                    ApiError.Biz("PRINT_BAD_CODE", "标签码格式非法，无法渲染标签"),
                )

                else -> {
                    // 版式字段由裸码直接取（对象类型字符位；人读行走裸串），不依赖扫码解析层
                    val bits = LabelRenderer.render(
                        bareCode = code,
                        humanLine = code,
                        objectType = when (code.getOrNull(1)) {
                            'D', 'E' -> "FG"
                            'B' -> "BAG"
                            else -> "OBJ"
                        },
                        dateText = "",
                        seqText = "",
                    )
                    val send = session.sendLabel(address, bits)
                    if (send is PrintSession.PrintSendResult.Sent) {
                        ApiResult.Success(Unit)
                    } else {
                        val reason = (send as PrintSession.PrintSendResult.Failed).reason
                        ApiResult.Failure(ApiError.Biz("PRINT_SEND", reason))
                    }
                }
            }
        }
    }
}

private fun lastTruckId(context: Context): Long = runCatching {
    context.getSharedPreferences(HOME_FLOW_PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_LAST_TRUCK, 0L)
}.getOrDefault(0L)

private fun rememberTruckId(context: Context, truckId: Long) {
    runCatching {
        context.getSharedPreferences(HOME_FLOW_PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_TRUCK, truckId).apply()
    }
}

private fun tabIcon(id: String) = when (id) {
    "trace" -> JxIcons.TraceForward
    "me" -> JxIcons.Me
    else -> JxIcons.Home
}

private fun tabTint(id: String) = when (id) {
    "trace" -> Tokens.Color.Domain.Trace.base
    else -> Tokens.Color.Brand.primary
}

@Composable
private fun AppShell(versionName: String, onSessionDead: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as JxLabTraceApp }
    val session = app.session

    var tab by remember { mutableStateOf("home") }
    val pushStack = remember { mutableStateListOf<PushRoute>() }
    var meState by remember { mutableStateOf(app.meRepository.cached()) }
    val printerStatus = remember { PrinterStatusModel() }

    val printOne = remember(context) { buildPrintOne(context) }
    val scanDeferred: () -> Unit = {
        Toast.makeText(context, "扫码功能随后续任务接通", Toast.LENGTH_SHORT).show()
    }
    val hostCtx = remember(app, printerStatus, printOne, context) {
        HostCtx(
            app = app,
            printerStatus = printerStatus,
            printOne = printOne,
            scanDeferred = scanDeferred,
            onGoPrinter = {
                pushStack.clear()
                tab = "me"
            },
        )
    }
    val traceVm = remember(app) { TraceViewModel(TraceRepository(app.apiClient), session) }

    // /api/me：先出缓存再刷新；原位重授权后仍 401 ⇒ 会话失效回 S01
    LaunchedEffect(Unit) {
        val result = session.withSessionRetry { app.meRepository.refresh() }
        if (result is ApiResult.Success) {
            meState = result.data
        } else if ((result as ApiResult.Failure).error is ApiError.Unauthorized) {
            pushStack.clear()
            tab = "home"
            onSessionDead()
        }
    }

    // 返回栈：推入屏逐级回退；无推入且非现场 Tab ⇒ 回现场（TabBar 所在的 Home）
    BackHandler(enabled = pushStack.isNotEmpty() || tab != "home") {
        if (pushStack.isNotEmpty()) {
            pushStack.removeAt(pushStack.lastIndex)
        } else {
            tab = "home"
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = Tokens.Color.Surface.canvas) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val me = meState ?: Me()
                when {
                    pushStack.isNotEmpty() -> PushHost(
                        route = pushStack.last(),
                        ctx = hostCtx,
                        meName = me.name,
                        onPush = { pushStack.add(it) },
                        onReplaceTop = { pushStack[pushStack.lastIndex] = it },
                    )

                    tab == "trace" -> TraceScreen(
                        viewModel = traceVm,
                        onStartScan = scanDeferred,
                    )

                    tab == "me" -> PrinterScreen(statusModel = printerStatus)

                    else -> HomeScreen(
                        me = me,
                        versionName = versionName,
                        onEntry = { id ->
                            if (id == HomeEntryId.TRACE) {
                                // 追溯为角色无关的 Tab 级目的地，不进推入栈
                                tab = "trace"
                            } else {
                                entryRoute(id, context)?.let(pushStack::add)
                            }
                        },
                    )
                }
            }
            // TabBar 仅在 Tab 宿主层显示：入口屏推入期间隐藏（MIMO-TASK-S02-NAV）
            if (pushStack.isEmpty()) {
                TabBar(
                    items = HOME_TABS.map { spec ->
                        TabBarItem(
                            id = spec.id,
                            label = spec.label,
                            icon = tabIcon(spec.id),
                            tint = tabTint(spec.id),
                        )
                    },
                    selectedId = tab,
                    onSelect = { tab = it },
                )
            }
        }
    }
}

/** 首页入口 → 推入路由（追溯返回 null：由调用方切 Tab）。 */
private fun entryRoute(id: HomeEntryId, context: Context): PushRoute? = when (id) {
    HomeEntryId.TRACE -> null
    HomeEntryId.RECV -> PushRoute.Notices
    HomeEntryId.BAG -> PushRoute.BagPrint(lastTruckId(context))
    HomeEntryId.LOAD -> PushRoute.Load
    HomeEntryId.SAMPLE -> PushRoute.Sample
    HomeEntryId.BATCH -> PushRoute.BatchCreate
    HomeEntryId.FEED -> PushRoute.BatchFlow(BatchStage.FEED, BATCH_UNRESOLVED)
    HomeEntryId.OPERATION -> PushRoute.BatchFlow(BatchStage.OPERATION, BATCH_UNRESOLVED)
    HomeEntryId.FG_PRINT -> PushRoute.BatchFlow(BatchStage.FG_PRINT, BATCH_UNRESOLVED)
    HomeEntryId.REPORT -> PushRoute.Report
}

/** 推入屏宿主：按路由渲染对应业务屏（VM 宿主构造 + 进入即 load）。 */
@Composable
private fun PushHost(
    route: PushRoute,
    ctx: HostCtx,
    meName: String,
    onPush: (PushRoute) -> Unit,
    onReplaceTop: (PushRoute) -> Unit,
) {
    val app = ctx.app
    val session = app.session
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printerState by ctx.printerStatus.state.collectAsState()

    when (route) {
        PushRoute.Notices -> {
            val vm = remember { NoticesViewModel(RecvRepository(app.apiClient), session) }
            LaunchedEffect(vm) { vm.refresh() }
            NoticesScreen(
                viewModel = vm,
                onConfirmed = { truckId ->
                    rememberTruckId(context, truckId)
                    onPush(PushRoute.Weigh(truckId))
                },
            )
        }

        is PushRoute.Weigh -> {
            val vm = remember(route) {
                WeighViewModel(RecvRepository(app.apiClient), session, route.truckId)
            }
            LaunchedEffect(vm) { vm.load() }
            WeighScreen(
                viewModel = vm,
                onWeighed = { onPush(PushRoute.BagPrint(route.truckId)) },
            )
        }

        is PushRoute.BagPrint -> {
            if (route.truckId <= 0L) {
                Column(Modifier.fillMaxSize()) {
                    TopBar(title = "建袋打码")
                    EmptyState(
                        title = "先完成到货确认",
                        hint = "建袋打码需要已确认的车次；请先到「收货」完成到货确认与过磅。",
                        actionText = "去收货确认",
                        onAction = { onReplaceTop(PushRoute.Notices) },
                    )
                }
            } else {
                val vm = remember(route) {
                    BagPrintViewModel(RecvRepository(app.apiClient), session, route.truckId, ctx.printOne)
                }
                LaunchedEffect(vm) { vm.load(null) }
                BagPrintScreen(
                    viewModel = vm,
                    printerState = printerState,
                    onGoPrinter = ctx.onGoPrinter,
                )
            }
        }

        PushRoute.Load -> {
            val vm = remember { LoadViewModel(ShipRepository(app.apiClient), session) }
            LoadScreen(viewModel = vm, onStartScan = ctx.scanDeferred)
        }

        PushRoute.Sample -> {
            val repo = remember { SampleRepository(app.apiClient) }
            SampleScreen(
                locatedCode = "",
                locatedTargetId = 0L,
                onTake = { body ->
                    scope.launch {
                        val result = session.withSessionRetry { repo.take(body) }
                        val message = if (result is ApiResult.Success) {
                            "取样已提交"
                        } else {
                            (result as ApiResult.Failure).error.userMessage
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
                onScanTarget = ctx.scanDeferred,
            )
        }

        PushRoute.BatchCreate -> {
            val vm = remember { BatchCreateViewModel(ProdRepository(app.apiClient), session) }
            LaunchedEffect(vm) { vm.load() }
            BatchCreateScreen(
                viewModel = vm,
                onCreated = { batchId ->
                    onReplaceTop(PushRoute.BatchFlow(BatchStage.FEED, batchId))
                },
            )
        }

        is PushRoute.BatchFlow -> BatchFlowHost(
            route = route,
            ctx = ctx,
            meName = meName,
            onReplaceTop = onReplaceTop,
        )

        PushRoute.Report -> {
            val vm = remember { ReportViewModel(ReportRepository(app.apiClient), session) }
            LaunchedEffect(vm) { vm.refresh() }
            ReportScreen(viewModel = vm)
        }
    }
}

/** S09/S10/S11 宿主：解析进行中生产批后渲染批次作用域屏（无批/失败给结构空态，不造假数据）。 */
@Composable
private fun BatchFlowHost(
    route: PushRoute.BatchFlow,
    ctx: HostCtx,
    meName: String,
    onReplaceTop: (PushRoute) -> Unit,
) {
    val app = ctx.app
    val session = app.session
    val printerState by ctx.printerStatus.state.collectAsState()
    val stageTitle = when (route.stage) {
        BatchStage.FEED -> "投料"
        BatchStage.OPERATION -> "作业段"
        BatchStage.FG_PRINT -> "成品袋打码"
    }
    var resolved by remember(route) { mutableStateOf(route.batchId) }
    LaunchedEffect(resolved) {
        if (resolved == BATCH_UNRESOLVED) {
            val result = session.withSessionRetry {
                ProdRepository(app.apiClient).listBatches(BATCH_ACTIVE_STATUS)
            }
            resolved = if (result is ApiResult.Success) {
                result.data.firstOrNull()?.id ?: BATCH_NONE
            } else {
                BATCH_LOAD_FAILED
            }
        }
    }

    when {
        resolved == BATCH_UNRESOLVED -> Column(Modifier.fillMaxSize()) {
            TopBar(title = stageTitle)
            Text(
                text = "正在加载生产批…",
                color = Tokens.Color.Text.secondary,
                modifier = Modifier.padding(Tokens.Space.space6),
            )
        }

        resolved == BATCH_NONE -> Column(Modifier.fillMaxSize()) {
            TopBar(title = stageTitle)
            EmptyState(
                title = "暂无进行中生产批",
                hint = "投料 / 作业段 / 成品打码都需要进行中批；请先到「建批」创建。",
                actionText = "去建批",
                onAction = { onReplaceTop(PushRoute.BatchCreate) },
            )
        }

        resolved == BATCH_LOAD_FAILED -> Column(Modifier.fillMaxSize()) {
            TopBar(title = stageTitle)
            EmptyState(
                title = "生产批列表加载失败",
                hint = "请检查网络后重试。",
                actionText = "重试",
                onAction = { resolved = BATCH_UNRESOLVED },
            )
        }

        else -> {
            val repo = remember(resolved) { ProdRepository(app.apiClient) }
            val clock = remember(resolved) { ServerClock(app.apiClient) }
            when (route.stage) {
                BatchStage.FEED -> {
                    val vm = remember(resolved) {
                        FeedViewModel(repo, session, clock, resolved)
                    }
                    LaunchedEffect(vm) { vm.load() }
                    FeedScreen(viewModel = vm, onStartScan = ctx.scanDeferred)
                }

                BatchStage.OPERATION -> {
                    val vm = remember(resolved) {
                        OperationViewModel(repo, session, clock, resolved, operatorName = meName)
                    }
                    LaunchedEffect(vm) { vm.load() }
                    OperationScreen(viewModel = vm)
                }

                BatchStage.FG_PRINT -> {
                    val vm = remember(resolved) {
                        FgPrintViewModel(repo, session, resolved, ctx.printOne)
                    }
                    LaunchedEffect(vm) { vm.load() }
                    FgPrintScreen(
                        viewModel = vm,
                        printerState = printerState,
                        onGoPrinter = ctx.onGoPrinter,
                    )
                }
            }
        }
    }
}
