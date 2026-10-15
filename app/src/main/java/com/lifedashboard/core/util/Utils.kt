package com.lifedashboard.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object DateUtils {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun LocalDate.toStartOfDayMillis(): Long =
        atStartOfDay(zone).toInstant().toEpochMilli()

    fun LocalDate.toEndOfDayMillis(): Long =
        atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    fun Long.toLocalDateTime(): LocalDateTime =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

    fun weekStartMillis(): Long = LocalDate.now()
        .with(java.time.DayOfWeek.MONDAY)
        .toStartOfDayMillis()

    fun daysAgoMillis(days: Int): Long = LocalDate.now()
        .minusDays(days.toLong())
        .toStartOfDayMillis()
}

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
}
