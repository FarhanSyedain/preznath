package com.yimberzol.preznath

import com.google.common.truth.Truth.assertThat
import com.yimberzol.preznath.data.HandleRegistry
import org.junit.Test

class HandleRegistryTest {
    private val registry = HandleRegistry()

    @Test
    fun knownHandlesResolve() {
        assertThat(registry.isKnown("oksbi")).isTrue()
        assertThat(registry.isKnown("ybl")).isTrue()
        assertThat(registry.bankFor("okicici")).contains("ICICI")
    }

    @Test
    fun lookalikeDetectsOneEdit() {
        val look = registry.isLookalike("okaxi")
        assertThat(look).isNotNull()
        assertThat(look!!.first).isEqualTo("okaxis")
    }

    @Test
    fun unknownFarHandleNotLookalike() {
        assertThat(registry.isLookalike("zzzzzzzz")).isNull()
    }

    @Test
    fun levenshteinBasics() {
        assertThat(HandleRegistry.levenshtein("okaxis", "okaxi")).isEqualTo(1)
        assertThat(HandleRegistry.levenshtein("ybl", "ybl")).isEqualTo(0)
    }
}
