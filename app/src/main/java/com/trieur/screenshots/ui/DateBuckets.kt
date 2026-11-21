package com.trieur.screenshots.ui

import com.trieur.screenshots.tr
import com.trieur.screenshots.isFrench

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", if (isFrench) Locale.FRENCH else Locale.ENGLISH)

/** Titre de section pour une date : « Aujourd'hui », « Hier », « Cette semaine », puis « Novembre 2026 ». */
fun dateBucket(millis: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return when {
        !day.isBefore(today) -> tr("Aujourd'hui", "Today")
        day == today.minusDays(1) -> tr("Hier", "Yesterday")
        day.isAfter(today.minusDays(7)) -> tr("Cette semaine", "This week")
        else -> MONTH.format(day).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    }
}
