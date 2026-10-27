package com.yimberzol.preznath

import com.google.common.truth.Truth.assertThat
import com.yimberzol.preznath.data.EntityExtractor
import com.yimberzol.preznath.data.HandleRegistry
import com.yimberzol.preznath.domain.EntityKind
import org.junit.Test

class EntityExtractorTest {
    private val extractor = EntityExtractor(HandleRegistry())

    @Test
    fun extractsKnownVpa() {
        val entities = extractor.extract("Pay ravi@oksbi now")
        val vpa = entities.first { it.kind == EntityKind.UPI_VPA }
        assertThat(vpa.value).isEqualTo("ravi@oksbi")
        assertThat(vpa.valid).isTrue()
    }

    @Test
    fun extractsLookalikeHandle() {
        val entities = extractor.extract("Send to fraud@okaxi")
        val vpa = entities.first { it.kind == EntityKind.UPI_VPA }
        assertThat(vpa.valid).isFalse()
        assertThat(vpa.note).contains("lookalike")
    }

    @Test
    fun extractsShortenedUrlAndAmount() {
        val entities = extractor.extract("Update KYC at bit.ly/fake for Rs 500")
        assertThat(entities.any { it.kind == EntityKind.URL && it.note?.contains("shortened") == true }).isTrue()
        assertThat(entities.any { it.kind == EntityKind.AMOUNT }).isTrue()
    }

    @Test
    fun extractsIndianPhone() {
        val entities = extractor.extract("Call 98765 43210 immediately")
        assertThat(entities.any { it.kind == EntityKind.PHONE && it.value == "9876543210" }).isTrue()
    }
}
