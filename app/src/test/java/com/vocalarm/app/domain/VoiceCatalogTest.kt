package com.vocalarm.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCatalogTest {

    private val chineseFemale = VoiceDescriptor(
        name = "zh-cn-x-ccc-local",
        localeTag = "zh-CN",
        features = setOf("female"),
    )
    private val chineseMale = VoiceDescriptor(
        name = "zh-cn-x-abc-local",
        localeTag = "zh-CN",
        features = setOf("male"),
    )
    private val englishFemale = VoiceDescriptor(
        name = "en-us-x-sfg-local",
        localeTag = "en-US",
        features = setOf("female"),
    )
    private val unknown = VoiceDescriptor(name = "zh-cn-x-zzz-local", localeTag = "zh-CN")

    @Test
    fun femaleMarkerWinsOverMaleSubstring() {
        // "female" contains "male"; the resolver must not classify it as male.
        assertEquals(VoiceGender.Female, VoiceCatalog.genderOf(chineseFemale))
        assertEquals(VoiceGender.Male, VoiceCatalog.genderOf(chineseMale))
    }

    @Test
    fun chineseVoiceIsPreferredForPreset() {
        val resolved = VoiceCatalog.resolve(
            preset = VoicePreset.Female,
            customName = null,
            voices = listOf(englishFemale, chineseFemale),
        )
        assertEquals(chineseFemale, resolved)
    }

    @Test
    fun presetFallsBackToAnyMatchingGender() {
        val resolved = VoiceCatalog.resolve(
            preset = VoicePreset.Male,
            customName = null,
            voices = listOf(englishFemale, chineseMale.copy(localeTag = "en-GB")),
        )
        assertEquals("zh-cn-x-abc-local", resolved?.name)
    }

    @Test
    fun missingGenderReturnsNullSoEngineDefaultIsUsed() {
        assertNull(VoiceCatalog.resolve(VoicePreset.Male, null, listOf(chineseFemale, unknown)))
        assertNull(VoiceCatalog.resolve(VoicePreset.Female, null, emptyList()))
    }

    @Test
    fun customPresetMatchesExactNameOnly() {
        assertEquals(
            englishFemale,
            VoiceCatalog.resolve(VoicePreset.Custom, "en-us-x-sfg-local", listOf(chineseFemale, englishFemale)),
        )
        assertNull(VoiceCatalog.resolve(VoicePreset.Custom, "missing", listOf(chineseFemale)))
        assertNull(VoiceCatalog.resolve(VoicePreset.Custom, null, listOf(chineseFemale)))
    }

    @Test
    fun defaultPresetKeepsEngineDefault() {
        assertNull(VoiceCatalog.resolve(VoicePreset.Default, "zh-cn-x-ccc-local", listOf(chineseFemale)))
    }

    @Test
    fun displayNameStripsEnginePrefix() {
        assertEquals(
            "zh-cn-x-ccc-local",
            VoiceDescriptor(name = "engine/zh-cn-x-ccc-local", localeTag = "zh-CN").displayName,
        )
    }

    @Test
    fun displaySortingPrefersChinese() {
        val sorted = VoiceCatalog.sortedForDisplay(listOf(englishFemale, unknown, chineseMale))
        assertEquals(chineseMale, sorted.first())
        assertEquals(englishFemale, sorted.last())
    }
}
