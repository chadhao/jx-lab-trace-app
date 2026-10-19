package cn.hunanjiangxi.jxlabtrace.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 业务矢量图标集（24 业务 + 5 通用）。
 * 单色构造，使用处经 Icon(tint = …) 跟随令牌着色（域色/状态色）。
 * 路径为简单几何形，保证 24dp 下清晰可辨。
 */
object JxIcons {

    // ---- 业务域 24 ----
    val RecvTruck: ImageVector = icon(
        "RecvTruck",
        "M3,6h11v8H3z M14,9h3.5L21,12.5V14h-7z " +
            "M6,16.5m-1.75,0a1.75,1.75 0 1,0 3.5,0a1.75,1.75 0 1,0 -3.5,0 " +
            "M15.5,16.5m-1.75,0a1.75,1.75 0 1,0 3.5,0a1.75,1.75 0 1,0 -3.5,0",
    )

    val Weigh: ImageVector = icon(
        "Weigh",
        "M11,3h2v3.2l5.4,7.3H5.6L11,6.2z M3,14.5h18V18H3z M7,18h2v3H7z M15,18h2v3h-2z M5,21h14v1.4H5z",
    )

    val Bag: ImageVector = icon(
        "Bag",
        "M8,3h8v2h-1.2l1.4,15.2A2.6,2.6 0 0,1 13.6,23h-3.2a2.6,2.6 0 0,1 -2.6,-2.8L9.2,5H8z M9.5,9h5v1.8h-5z",
    )

    val LabelPrint: ImageVector = icon(
        "LabelPrint",
        "M7,3h10v4H7z M3,7h18v8h-4v-4H7v4H3z M7,15h10v6H7z M9.5,17.5h5v1.4h-5z",
    )

    val Reprint: ImageVector = icon(
        "Reprint",
        "M7,3h10v4H7z M3,7h18v8h-4v-4H7v4H3z M7,15h10v6H7z M16.5,1.5l4.2,2.2-4.2,2.2v-1.5h-3v-1.4h3z",
    )

    val TruckReturn: ImageVector = icon(
        "TruckReturn",
        "M13,7h4l3,4v4h-2.2 M3,12h8v2H3z M9,8l-4,4 4,4v-2.6h4V10.6H9z M13,15h4v3.5a1.75,1.75 0 1,1 -3.5,0z",
    )

    val Sample: ImageVector = icon(
        "Sample",
        "M9.5,2h5v2h-1.2l1.6,14.4A3.4,3.4 0 0,1 11.5,22h-1a3.4,3.4 0 0,1 -3.4,-3.6L8.7,4H9.5z " +
            "M10.2,12h3.6l.7,6.2a1.9,1.9 0 0,1 -5,0z",
    )

    val Batch: ImageVector = icon(
        "Batch",
        "M12,2.5l9,4.8-9,4.8-9,-4.8z M3.4,12.2L12,16.8l8.6,-4.6 1.6,.9L12,18.7l-10.2,-5.6z M3.4,16.2L12,20.8l8.6,-4.6 1.6,.9L12,22.7l-10.2,-5.6z",
    )

    val Feed: ImageVector = icon(
        "Feed",
        "M11,2h2v8.2l3,-3 1.5,1.5L12,15.2 6.5,8.7 8,7.2l3,3z M4,17h16v2.2H4z M6,19.8h12V22H6z",
    )

    val OpLog: ImageVector = icon(
        "OpLog",
        "M5,4.5l9.5,7.5L5,19.5z M16.5,4.5H20v15h-3.5z",
    )

    val FgBag: ImageVector = icon(
        "FgBag",
        "M8,3h8v2h-1.2l1.4,15.2A2.6,2.6 0 0,1 13.6,23h-3.2a2.6,2.6 0 0,1 -2.6,-2.8L9.2,5H8z " +
            "M8.6,11.8l2.3,2.3 4.6,-4.6 1.4,1.4 -6,6 -3.7,-3.7z",
    )

    val LoadScan: ImageVector = icon(
        "LoadScan",
        "M3,7V5a2,2 0 0,1 2,-2h2v2.2H5.2V7z M17,3h2a2,2 0 0,1 2,2v2h-2.2V5.2H17z " +
            "M3,17v2a2,2 0 0,0 2,2h2v-2.2H5.2V17z M19,17v2a2,2 0 0,1 -2,2h-2v-2.2h1.8V17z " +
            "M6,10.5h12v2.2H6z",
    )

    val ShipOut: ImageVector = icon(
        "ShipOut",
        "M3,20V5h4.2v15z M16.8,20V5H21v15z M8,11h8V8.2l4.2,3.8-4.2,3.8V13H8z",
    )

    val TraceForward: ImageVector = icon(
        "TraceForward",
        "M4.5,12m-2.5,0a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0 " +
            "M11.5,12m-2.5,0a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0 " +
            "M7,11h2v2H7z M14.5,8.5l4.5,3.5-4.5,3.5z",
    )

    val TraceBackward: ImageVector = icon(
        "TraceBackward",
        "M12.5,12m-2.5,0a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0 " +
            "M19.5,12m-2.5,0a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0 " +
            "M15,11h2v2h-2z M9.5,8.5L5,12l4.5,3.5z",
    )

    val BatchFile: ImageVector = icon(
        "BatchFile",
        "M6,2.5h8l4.5,4.5V21.5H6z M13.5,4.2V7.5H17z M9,11h6v1.8H9z M9,14.5h6v1.8H9z",
    )

    val Report: ImageVector = icon(
        "Report",
        "M4,20.5h17v1.8H4z M5,11.5h3.2v8H5z M10.4,6h3.2v13.5h-3.2z M15.8,14.5H19v5h-3.2z",
    )

    val Scan: ImageVector = icon(
        "Scan",
        "M3,8V5.5A2.5,2.5 0 0,1 5.5,3H8v2.2H5.7V8z M16,3h2.5A2.5,2.5 0 0,1 21,5.5V8h-2.2V5.7H16z " +
            "M3,16v2.5A2.5,2.5 0 0,0 5.5,21H8v-2.2H5.7V16z M19,16v2.5a2.5,2.5 0 0,1 -2.5,2.5H16v-2.2h2.3V16z " +
            "M3,11h18v2.2H3z",
    )

    val Exception: ImageVector = icon(
        "Exception",
        "M12,2.6L22.2,21H1.8z M11,9.2h2V15h-2z M11,16.4h2v2.2h-2z",
        fillType = PathFillType.EvenOdd,
    )

    val NetworkOff: ImageVector = icon(
        "NetworkOff",
        "M12,3.2m-8.8,0a8.8,8.8 0 1,0 17.6,0a8.8,8.8 0 1,0 -17.6,0 " +
            "M5.4,4.4L4,5.8l14.2,14.2 1.4,-1.4z",
        fillType = PathFillType.EvenOdd,
    )

    val Phone: ImageVector = icon(
        "Phone",
        "M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c.27,-.27 .67,-.36 1.02,-.24 1.12,.37 2.33,.57 3.57,.57 .55,0 1,.45 1,1V20c0,.55 -.45,1 -1,1C10.58,21 3,13.42 3,3c0,-.55 .45,-1 1,-1h3.5c.55,0 1,.45 1,1 0,1.25 .2,2.45 .57,3.57 .11,.35 .03,.74 -.25,1.02l-2.2,2.2z",
    )

    val Empty: ImageVector = icon(
        "Empty",
        "M5,3.5h14l2.2,6.5V20.5H2.8V10z M9.2,11.5h5.6v1.8H9.2z M2.8,10h5.6l1.1,2.2h5l1.1,-2.2h5.6",
    )

    val Intercept: ImageVector = icon(
        "Intercept",
        "M12,2.2l8.5,3.2v6.1c0,5.4 -3.7,9.6 -8.5,11.5 -4.8,-1.9 -8.5,-6.1 -8.5,-11.5V5.4z " +
            "M11,7h2v5h5v2h-5v5h-2v-5H6v-2h5z",
        fillType = PathFillType.EvenOdd,
    )

    val Me: ImageVector = icon(
        "Me",
        "M12,4.2m-4.2,0a4.2,4.2 0 1,0 8.4,0a4.2,4.2 0 1,0 -8.4,0 " +
            "M4,20.2c0,-3.4 3.8,-5.6 8,-5.6s8,2.2 8,5.6V22H4z",
    )

    // ---- 通用 ----
    val Back: ImageVector = icon(
        "Back",
        "M20,11H7.8l5.6,-5.6L12,4l-8,8 8,8 1.4,-1.4L7.8,13H20v-2z",
    )

    val Backspace: ImageVector = icon(
        "Backspace",
        "M22,3H7.2L1.5,12l5.7,9H22a1,1 0 0,0 1,-1V4a1,1 0 0,0 -1,-1z " +
            "M9.8,7.5L11.3,6l3.9,3.9L19.1,6l1.5,1.5 -3.9,3.9 3.9,3.9 -1.5,1.5 -3.9,-3.9 -3.9,3.9L9.8,13.3z",
        fillType = PathFillType.EvenOdd,
    )

    val Home: ImageVector = icon(
        "Home",
        "M12,3l9,8h-3v9h-4.2v-6h-3.6v6H6v-9H3z",
    )

    val Success: ImageVector = icon(
        "Success",
        "M12,2.2m-9.8,0a9.8,9.8 0 1,0 19.6,0a9.8,9.8 0 1,0 -19.6,0 " +
            "M10.4,15.6L6.9,12.1 5.5,13.5 10.4,18.4 18.9,9.9 17.5,8.5z",
        fillType = PathFillType.EvenOdd,
    )

    val Info: ImageVector = icon(
        "Info",
        "M12,2.2m-9.8,0a9.8,9.8 0 1,0 19.6,0a9.8,9.8 0 1,0 -19.6,0 " +
            "M11,6.5h2v2.2h-2z M11,10h2v7.2h-2z",
        fillType = PathFillType.EvenOdd,
    )

    /** 手电筒（S03 取景右上角，M04）。 */
    val Torch: ImageVector = icon(
        "Torch",
        "M13,2 5,14h6l-1,8 8,-12h-6z",
    )

    private fun icon(
        name: String,
        pathData: String,
        fillType: PathFillType = PathFillType.NonZero,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
            pathFillType = fillType,
        )
    }.build()
}
