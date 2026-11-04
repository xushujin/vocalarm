package com.vocalarm.app.domain

import java.util.Locale

/** Engine-agnostic view of a device TTS voice, which keeps the selection logic unit testable. */
data class VoiceDescriptor(
    val name: String,
    val localeTag: String,
    val features: Set<String> = emptySet(),
) {
    val isChinese: Boolean
        get() = localeTag.lowercase(Locale.ROOT).startsWith("zh")

    val displayName: String
        get() = name.substringAfterLast('/').ifBlank { name }
}

enum class VoiceGender { Female, Male, Unknown }

object VoiceCatalog {

    private val FEMALE_MARKERS = listOf(
        "female", "woman", "girl", "#f", "_f", "女", "xiaoxiao", "xiaoyi", "xiaomo", "xiaoxuan",
        "huihui", "yaoyao", "tingting", "meijia", "sinji", "salli", "joanna", "amy", "emma",
    )
    private val MALE_MARKERS = listOf(
        "male", "man", "boy", "#m", "_m", "男", "yunxi", "yunjian", "yunyang", "kangkang",
        "liang", "zhiwei", "matthew", "brian", "russell",
    )

    fun genderOf(descriptor: VoiceDescriptor): VoiceGender {
        val haystack = buildString {
            append(descriptor.name.lowercase(Locale.ROOT))
            append(' ')
            append(descriptor.features.joinToString(" ").lowercase(Locale.ROOT))
        }
        // "female" contains "male", so the female markers must win first.
        if (FEMALE_MARKERS.any { haystack.contains(it) }) return VoiceGender.Female
        if (MALE_MARKERS.any { haystack.contains(it) }) return VoiceGender.Male
        return VoiceGender.Unknown
    }

    /**
     * @return the voice that should be used, or null when the engine default should be kept.
     */
    fun resolve(
        preset: VoicePreset,
        customName: String?,
        voices: List<VoiceDescriptor>,
    ): VoiceDescriptor? {
        if (preset == VoicePreset.Default || voices.isEmpty()) return null
        if (preset == VoicePreset.Custom) {
            return voices.firstOrNull { it.name == customName }
        }
        val wanted = if (preset == VoicePreset.Female) VoiceGender.Female else VoiceGender.Male
        val candidates = voices.filter { genderOf(it) == wanted }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.isChinese } ?: candidates.first()
    }

    fun sortedForDisplay(voices: List<VoiceDescriptor>): List<VoiceDescriptor> =
        voices.sortedWith(
            compareByDescending<VoiceDescriptor> { it.isChinese }
                .thenBy { genderRank(genderOf(it)) }
                .thenBy { it.name },
        )

    private fun genderRank(gender: VoiceGender): Int = when (gender) {
        VoiceGender.Female -> 0
        VoiceGender.Male -> 1
        VoiceGender.Unknown -> 2
    }
}
