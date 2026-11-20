package com.trieur.screenshots.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DateBucketsTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 20)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun buckets() {
        assertEquals("Aujourd'hui", dateBucket(at(2026, 11, 20, 0), today, zone))
        assertEquals("Hier", dateBucket(at(2026, 11, 19, 23), today, zone))
        assertEquals("Cette semaine", dateBucket(at(2026, 11, 14), today, zone))
        assertEquals("Novembre 2026", dateBucket(at(2026, 11, 13), today, zone))
        assertEquals("Août 2026", dateBucket(at(2026, 8, 25), today, zone))
    }
}
