package com.yimberzol.preznath

import com.google.common.truth.Truth.assertThat
import com.yimberzol.preznath.data.OfflineAnalyzer
import com.yimberzol.preznath.domain.Message
import com.yimberzol.preznath.domain.VerdictLabel
import org.junit.Test

/**
 * Three golden demo messages — same set as the deck live-demo slide.
 * Offline analyzer only (no network, no ONNX required).
 */
class OfflineAnalyzerGoldenTest {

    private val analyzer = OfflineAnalyzer(stageDelayMs = 0L)

    @Test
    fun kycBlockSms_isFraud() {
        val result = analyzer.analyze(
            Message(
                text = "Dear customer your SBI YONO account will be blocked today. Update KYC immediately: http://bit.ly/sbi-kyc-update or call 9876543210",
            ),
        )
        assertThat(result.verdict.label).isEqualTo(VerdictLabel.FRAUD)
        assertThat(result.verdict.score).isGreaterThan(65)
        assertThat(result.engine).startsWith("offline/")
    }

    @Test
    fun hindiElectricity_isSuspiciousOrFraud() {
        val result = analyzer.analyze(
            Message(
                text = "आपका बिजली कनेक्शन आज रात 9:30 बजे काट दिया जाएगा। बिल अपडेट करने के लिए तुरंत कॉल करें 9123456789",
            ),
        )
        assertThat(result.verdict.label).isAnyOf(VerdictLabel.SUSPICIOUS, VerdictLabel.FRAUD)
        assertThat(result.verdict.score).isAtLeast(30)
        assertThat(result.language).isEqualTo("hi")
    }

    @Test
    fun dinnerPayment_isSafe() {
        val result = analyzer.analyze(
            Message(
                text = "Hey, sending you 500 for dinner, check your GPay",
                upiId = "priya.s@okicici",
            ),
        )
        assertThat(result.verdict.label).isEqualTo(VerdictLabel.SAFE)
        assertThat(result.verdict.score).isLessThan(30)
    }
}
