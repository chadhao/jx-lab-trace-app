package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.scan.CameraGate
import cn.hunanjiangxi.jxlabtrace.scan.cameraGateOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M4-5：相机权限拒绝 ⇒ 取景层走 Denied 门禁（渲染「去设置」面板而非白屏）——
 * 逻辑单测在此；「去设置」面板另有三档宽 Preview（ScanScreen.kt CameraDeniedPreview）。
 */
class CameraGateTest {

    @Test
    fun `权限被拒 - 门禁为 Denied（展示去设置而非白屏）`() {
        assertEquals(CameraGate.Denied, cameraGateOf(hasCameraPermission = false))
    }

    @Test
    fun `权限已授 - 门禁为 Preview（相机取景）`() {
        assertEquals(CameraGate.Preview, cameraGateOf(hasCameraPermission = true))
    }
}
