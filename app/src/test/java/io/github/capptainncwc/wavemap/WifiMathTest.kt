package io.github.capptainncwc.wavemap

import io.github.capptainncwc.wavemap.survey.Heatmap
import io.github.capptainncwc.wavemap.survey.Metric
import io.github.capptainncwc.wavemap.survey.Project
import io.github.capptainncwc.wavemap.survey.Reading
import io.github.capptainncwc.wavemap.survey.SurveyPoint
import io.github.capptainncwc.wavemap.survey.Target
import io.github.capptainncwc.wavemap.survey.VirtualAp
import io.github.capptainncwc.wavemap.wifi.Band
import io.github.capptainncwc.wavemap.wifi.WifiMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiMathTest {
    @Test fun channels() {
        assertEquals(1, WifiMath.channel(2412))
        assertEquals(6, WifiMath.channel(2437))
        assertEquals(13, WifiMath.channel(2472))
        assertEquals(14, WifiMath.channel(2484))
        assertEquals(36, WifiMath.channel(5180))
        assertEquals(165, WifiMath.channel(5825))
        assertEquals(1, WifiMath.channel(5955))
        assertEquals(37, WifiMath.channel(6135))
        assertEquals(Band.GHZ_6, WifiMath.band(6135))
        assertEquals(Band.GHZ_5, WifiMath.band(5180))
    }

    @Test fun security() {
        assertEquals("Open", WifiMath.security("[ESS]"))
        assertEquals("WPA2", WifiMath.security("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("WPA2/WPA3", WifiMath.security("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals("WPA3", WifiMath.security("[RSN-SAE-CCMP][ESS]"))
        assertEquals("WPA/WPA2", WifiMath.security("[WPA-PSK-CCMP+TKIP][WPA2-PSK-CCMP+TKIP][ESS]"))
        assertEquals("WPA2 Enterprise", WifiMath.security("[RSN-EAP-CCMP][ESS]"))
        assertEquals("WEP", WifiMath.security("[WEP][ESS]"))
    }

    @Test fun overlap() {
        assertTrue(WifiMath.overlaps(2412, 20, 2427, 20))   // ch 1 and 4
        assertFalse(WifiMath.overlaps(2412, 20, 2437, 20))  // ch 1 and 6
        assertTrue(WifiMath.overlaps(5210, 80, 5180, 20))   // 36-48 @80 vs 36
        assertFalse(WifiMath.overlaps(5180, 20, 5200, 20))  // 36 and 40
    }

    @Test fun pointMetrics() {
        val p = SurveyPoint(
            "p", 0.5f, 0.5f, 0, listOf(
                Reading("aa", "home", -50, 2437),
                Reading("bb", "neighbour", -60, 2442),  // overlapping ch 7
                Reading("cc", "far", -80, 2462),        // ch 11, no overlap
            ),
        )
        assertEquals(-50f, Heatmap.value(p, Metric.SIGNAL, Target.Any))
        assertEquals(45f, Heatmap.value(p, Metric.SNR, Target.Any))
        assertEquals(10f, Heatmap.value(p, Metric.SIR, Target.Any), 0.01f)
        assertEquals(2f, Heatmap.value(p, Metric.AP_COUNT, Target.Any))
        assertEquals(-80f, Heatmap.value(p, Metric.SIGNAL, Target.Ssid("far")))
        assertEquals(-100f, Heatmap.value(p, Metric.SIGNAL, Target.Bssid("zz", "")))
    }

    @Test fun plannedFallsWithDistance() {
        val project = Project(
            "x", "t", 0, 1000, 500, widthMeters = 20f,
            aps = listOf(VirtualAp("a", 0.5f, 0.5f, "AP", 20, 2437)),
        )
        val near = Heatmap.planned(project, 0.55f, 0.25f)  // 1 m away
        val far = Heatmap.planned(project, 0.95f, 0.25f)   // 9 m away
        assertEquals(-20.2f, near, 0.5f)
        assertTrue(far < near - 25)
    }
}
