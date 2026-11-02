package com.example.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarDayItem(
  val date: Date,
  val dayName: String,       // "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
  val dayNumber: Int,        // 1..31
  val monthName: String,     // "Oct", "Nov"
  val monthNumber: Int,      // 1..12
  val year: Int,             // e.g. 2026
  val isToday: Boolean,
  val dateKey: String        // "yyyy-MM-dd"
)

object CalendarUtil {

  /**
   * Returns the 7 days of the week (Monday to Sunday) containing [referenceDate] plus [weekOffset] weeks.
   * Ensures 100% calendar accuracy across month boundaries, leap years, and year-ends.
   */
  fun getWeekDays(
    referenceDate: Calendar = Calendar.getInstance(),
    weekOffset: Int = 0
  ): List<CalendarDayItem> {
    val todayCal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)

    // Base calendar set to midnight
    val baseCal = (referenceDate.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    // Determine days since Monday
    // Calendar.DAY_OF_WEEK: Sunday=1, Monday=2, Tuesday=3, Wednesday=4, Thursday=5, Friday=6, Saturday=7
    val dayOfWeek = baseCal.get(Calendar.DAY_OF_WEEK)
    val daysSinceMonday = (dayOfWeek - Calendar.MONDAY + 7) % 7

    // Start on Monday of the requested week
    val mondayCal = (baseCal.clone() as Calendar).apply {
      add(Calendar.DAY_OF_YEAR, -daysSinceMonday + (weekOffset * 7))
    }

    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val result = ArrayList<CalendarDayItem>(7)
    for (i in 0 until 7) {
      val dayCal = (mondayCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, i)
      }
      val isToday = dayCal.get(Calendar.YEAR) == todayYear &&
                    dayCal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear

      result.add(
        CalendarDayItem(
          date = dayCal.time,
          dayName = dayNames[i],
          dayNumber = dayCal.get(Calendar.DAY_OF_MONTH),
          monthName = monthFormat.format(dayCal.time),
          monthNumber = dayCal.get(Calendar.MONTH) + 1,
          year = dayCal.get(Calendar.YEAR),
          isToday = isToday,
          dateKey = keyFormat.format(dayCal.time)
        )
      )
    }
    return result
  }

  /**
   * Generates a descriptive header label for a 7-day week (e.g. "November 2026", "Oct – Nov 2026").
   */
  fun getWeekHeaderLabel(days: List<CalendarDayItem>): String {
    if (days.isEmpty()) return ""
    val first = days.first()
    val last = days.last()

    val fullMonthFormat = SimpleDateFormat("MMMM", Locale.getDefault())

    return when {
      first.year != last.year -> {
        "${first.monthName} ${first.year} – ${last.monthName} ${last.year}"
      }
      first.monthNumber != last.monthNumber -> {
        "${first.monthName} – ${last.monthName} ${first.year}"
      }
      else -> {
        val monthFull = fullMonthFormat.format(first.date)
        "$monthFull ${first.year}"
      }
    }
  }

  /**
   * Formats today's date key ("yyyy-MM-dd").
   */
  fun getTodayKey(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
  }
}
