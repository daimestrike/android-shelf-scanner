package ru.shelfscanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.shelfscanner.data.model.ScannedCode

class ActiveScanSessionTest {
    @Test
    fun calculatesDetectionRateFromUniqueCodesAndActualCount() {
        val session = ActiveScanSession(
            codes = List(19) { ScannedCode(value = "code_$it") },
            actualCount = 20,
        )

        assertEquals(95.0, session.detectionRate!!, 0.001)
    }

    @Test
    fun doesNotCalculateRateWithoutActualCount() {
        assertNull(ActiveScanSession().detectionRate)
    }
}
