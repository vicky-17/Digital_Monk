package com.digitalmonk.app.core.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TimeUtils {
    private val dayKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun dayKey(date: LocalDate): String {
        return date.format(dayKeyFormatter)
    }

    fun todayKey(): String {
        return dayKey(LocalDate.now())
    }
}
