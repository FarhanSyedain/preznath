package com.yimberzol.preznath

import com.google.common.truth.Truth.assertThat
import com.yimberzol.preznath.data.CorpusLoader
import com.yimberzol.preznath.data.TfidfSimilarityIndex
import org.junit.Test

class TfidfSimilarityTest {
    private val index = TfidfSimilarityIndex(CorpusLoader.builtin())

    @Test
    fun kycMessageMatchesScamTemplate() {
        val matches = index.nearest(
            "Your SBI account will be blocked. Update KYC immediately via the link.",
            k = 3,
        )
        assertThat(matches).isNotEmpty()
        assertThat(matches.first().isScam).isTrue()
        assertThat(matches.first().similarity).isGreaterThan(0.15)
    }

    @Test
    fun dinnerMessageMatchesLegit() {
        val matches = index.nearest("Hey, sending you 500 for dinner, check your GPay.", k = 3)
        assertThat(matches.first().isScam).isFalse()
    }
}
