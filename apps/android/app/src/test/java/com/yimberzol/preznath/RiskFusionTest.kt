package com.yimberzol.preznath

import com.google.common.truth.Truth.assertThat
import com.yimberzol.preznath.application.RiskFusion
import com.yimberzol.preznath.domain.Entity
import com.yimberzol.preznath.domain.EntityKind
import com.yimberzol.preznath.domain.Signal
import com.yimberzol.preznath.domain.VerdictLabel
import org.junit.Test

class RiskFusionTest {
    @Test
    fun lowScoreIsSafe() {
        val signals = listOf(
            Signal("spam", "Spam", 0.1, 0.10, "clean"),
            Signal("phishing", "Phishing", 0.1, 0.20, "clean"),
            Signal("intent", "Intent", 0.1, 0.20, "clean"),
            Signal("similarity", "Sim", 0.1, 0.20, "clean"),
            Signal("rules", "Rules", 0.0, 0.15, "none", details = mapOf("rules" to emptyList<String>())),
            Signal("entities", "Ent", 0.0, 0.15, "none"),
        )
        val v = RiskFusion.fuse(signals, emptyList())
        assertThat(v.label).isEqualTo(VerdictLabel.SAFE)
        assertThat(v.score).isLessThan(30)
    }

    @Test
    fun credentialAskHardFloorIsFraud() {
        val signals = listOf(
            Signal(
                "rules", "Rules", 1.0, 0.15, "otp",
                details = mapOf("rules" to listOf("Asks you to share or enter an OTP, PIN or CVV.")),
            ),
            Signal("spam", "Spam", 0.2, 0.10, "x"),
            Signal("phishing", "Phishing", 0.2, 0.20, "x"),
            Signal("intent", "Intent", 0.2, 0.20, "x"),
            Signal("similarity", "Sim", 0.2, 0.20, "x"),
            Signal("entities", "Ent", 0.2, 0.15, "x"),
        )
        val v = RiskFusion.fuse(signals, emptyList())
        assertThat(v.score).isAtLeast(70)
        assertThat(v.label).isEqualTo(VerdictLabel.FRAUD)
    }

    @Test
    fun lookalikeEntityHardFloor() {
        val entities = listOf(
            Entity(EntityKind.UPI_VPA, "x@okaxi", false, "lookalike of @okaxis"),
        )
        val signals = listOf(
            Signal("rules", "Rules", 0.0, 0.15, "none", details = mapOf("rules" to emptyList<String>())),
            Signal("spam", "Spam", 0.1, 0.10, "x"),
            Signal("phishing", "Phishing", 0.1, 0.20, "x"),
            Signal("intent", "Intent", 0.1, 0.20, "x"),
            Signal("similarity", "Sim", 0.1, 0.20, "x"),
            Signal("entities", "Ent", 0.9, 0.15, "lookalike"),
        )
        val v = RiskFusion.fuse(signals, entities)
        assertThat(v.score).isAtLeast(70)
        assertThat(v.label).isEqualTo(VerdictLabel.FRAUD)
    }
}
