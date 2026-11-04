package com.vocalarm.app.domain

/**
 * Version comparison for the GitHub release tag. Implements the parts of semantic versioning that
 * releases actually use here: numeric core, optional pre-release and build metadata.
 */
object VersionComparator {

    fun compare(left: String, right: String): Int {
        val a = parse(left)
        val b = parse(right)
        val size = maxOf(a.core.size, b.core.size)
        for (index in 0 until size) {
            val av = a.core.getOrElse(index) { 0 }
            val bv = b.core.getOrElse(index) { 0 }
            if (av != bv) return if (av > bv) 1 else -1
        }
        if (a.pre.isEmpty() && b.pre.isEmpty()) return 0
        if (a.pre.isEmpty()) return 1
        if (b.pre.isEmpty()) return -1
        val preSize = maxOf(a.pre.size, b.pre.size)
        for (index in 0 until preSize) {
            val av = a.pre.getOrNull(index) ?: return -1
            val bv = b.pre.getOrNull(index) ?: return 1
            val aNum = av.toIntOrNull()
            val bNum = bv.toIntOrNull()
            when {
                aNum != null && bNum != null -> if (aNum != bNum) return if (aNum > bNum) 1 else -1
                aNum != null -> return -1
                bNum != null -> return 1
                else -> {
                    val result = av.compareTo(bv, ignoreCase = true)
                    if (result != 0) return if (result > 0) 1 else -1
                }
            }
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    private fun parse(raw: String): Parsed {
        val withoutBuild = normalize(raw).substringBefore('+')
        val dash = withoutBuild.indexOf('-')
        val corePart = if (dash >= 0) withoutBuild.substring(0, dash) else withoutBuild
        val prePart = if (dash >= 0) withoutBuild.substring(dash + 1) else ""
        val core = corePart.split('.').map { it.toIntOrNull() ?: 0 }
        val pre = prePart.split('.').filter { it.isNotBlank() }
        return Parsed(core = core.ifEmpty { listOf(0) }, pre = pre)
    }

    private data class Parsed(val core: List<Int>, val pre: List<String>)
}

enum class RepeatKind { Once, Daily, Weekdays, Weekends, Custom }

object RepeatPlan {
    private val WEEKDAYS = setOf(
        java.time.DayOfWeek.MONDAY,
        java.time.DayOfWeek.TUESDAY,
        java.time.DayOfWeek.WEDNESDAY,
        java.time.DayOfWeek.THURSDAY,
        java.time.DayOfWeek.FRIDAY,
    )
    private val WEEKEND = setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)

    fun kind(days: Set<java.time.DayOfWeek>): RepeatKind = when {
        days.isEmpty() -> RepeatKind.Once
        days.size == 7 -> RepeatKind.Daily
        days == WEEKDAYS -> RepeatKind.Weekdays
        days == WEEKEND -> RepeatKind.Weekends
        else -> RepeatKind.Custom
    }

    fun ordered(days: Set<java.time.DayOfWeek>): List<java.time.DayOfWeek> =
        days.sortedBy { it.value }

    fun toggle(days: Set<java.time.DayOfWeek>, day: java.time.DayOfWeek): Set<java.time.DayOfWeek> =
        if (day in days) days - day else days + day
}
