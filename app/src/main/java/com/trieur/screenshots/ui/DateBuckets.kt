package com.trieur.screenshots.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

/** Titre de section pour une date : « Aujourd'hui », « Hier », « Cette semaine », puis « Novembre 2026 ». */
fun dateBucket(millis: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return when {
        !day.isBefore(today) -> "Aujourd'hui"
        day == today.minusDays(1) -> "Hier"
        day.isAfter(today.minusDays(7)) -> "Cette semaine"
        else -> MONTH.format(day).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    }
}
