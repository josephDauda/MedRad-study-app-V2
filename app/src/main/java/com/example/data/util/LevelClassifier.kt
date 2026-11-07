package com.example.data.util

import com.example.data.local.entity.UserEntity

object LevelClassifier {

  /**
   * Normalizes any academic level representation to a standardized format:
   * e.g. "100", "100L", "Level 100", "1" -> "100 Level"
   *      "200", "200L", "Level 200", "2" -> "200 Level"
   *      "300", "300L", "Level 300", "3" -> "300 Level"
   *      "400", "400L", "Level 400", "4" -> "400 Level"
   *      "500", "500L", "Level 500", "5" -> "500 Level"
   *      "600", "600L", "Level 600", "6" -> "600 Level"
   */
  fun normalizeLevel(raw: String?): String {
    if (raw.isNullOrBlank()) return "200 Level"
    val clean = raw.trim()
    if (clean.equals("All", ignoreCase = true) || clean.equals("All Levels", ignoreCase = true) || clean.equals("ALL", ignoreCase = true)) {
      return "All Levels"
    }
    val match = Regex("""([1-9])\d{2}""").find(clean)
      ?: Regex("""\b([1-9])\b""").find(clean)
    val digit = match?.groupValues?.getOrNull(1) ?: return if (clean.contains("1")) "100 Level" else "200 Level"
    return "${digit}00 Level"
  }

  /**
   * Resolves the exact academic level assigned to a secondary administrator.
   * Returns null if user is Primary Admin or not an admin.
   * Returns "All Levels" if assigned to all levels.
   * Otherwise returns "100 Level", "200 Level", "300 Level", "400 Level", "500 Level", or "600 Level".
   */
  fun getSecondaryAdminLevel(admin: UserEntity?): String? {
    if (admin == null || admin.role != "ADMIN") return null
    val isPrimary = admin.email.equals("admin@medrad.edu.ng", ignoreCase = true)
    if (isPrimary) return null

    val raw = when {
      !admin.level.isNullOrBlank() && !admin.level.contains("Staff", ignoreCase = true) -> admin.level
      !admin.assignedCourses.isNullOrBlank() -> admin.assignedCourses
      else -> null
    }
    if (raw.isNullOrBlank() || raw.contains("All", ignoreCase = true)) return "All Levels"
    return normalizeLevel(raw)
  }

  /**
   * Determines academic level from a Course Code.
   * e.g. "BIO 101" -> "100 Level"
   *      "MTH 101" -> "100 Level"
   *      "ANA 201" -> "200 Level"
   *      "PHS 201" -> "200 Level"
   *      "RAD 301" -> "300 Level"
   *      "RAD 401" -> "400 Level"
   *      "RAD 501" -> "500 Level"
   *      "RAD 601" -> "600 Level"
   *      "RAD 701" -> "700 Level"
   */
  fun getLevelFromCourseCode(courseCode: String?): String? {
    if (courseCode.isNullOrBlank()) return null
    val clean = courseCode.trim().uppercase()
    val match = Regex("""([1-9])\d{2}""").find(clean)
    if (match != null) {
      val firstDigit = match.groupValues[1]
      return "${firstDigit}00 Level"
    }
    for (i in 1..7) {
      if (clean.contains("${i}00") || clean.contains("LEVEL $i") || clean.contains("${i}L")) {
        return "${i}00 Level"
      }
    }
    return null
  }

  /**
   * Checks if an item (course, question, material, exam) matches the user's level.
   * Primary admins match all levels.
   * Secondary admins match only their assigned level.
   * Students match only their registered level.
   */
  fun matchesUserLevel(
    user: UserEntity?,
    courseCode: String?,
    explicitLevel: String? = null
  ): Boolean {
    if (user == null) return true
    if (user.role == "ADMIN") {
      val secLevel = getSecondaryAdminLevel(user) ?: return true // Primary Admin matches all
      if (secLevel == "All Levels") return true
      val targetLevel = explicitLevel?.let { normalizeLevel(it) } ?: getLevelFromCourseCode(courseCode)
      return targetLevel != null && secLevel == targetLevel
    }

    // Student: strictly match their registered level
    val studentLevel = normalizeLevel(user.level)
    val targetLevel = explicitLevel?.let { normalizeLevel(it) } ?: getLevelFromCourseCode(courseCode)
    if (targetLevel == null) return false
    return studentLevel == targetLevel
  }

  /**
   * Checks if an item level matches a target level string (e.g. "100 Level" vs "200 Level")
   */
  fun matchesLevel(targetLevel: String?, itemCodeOrLevel: String?): Boolean {
    if (targetLevel.isNullOrBlank() || targetLevel.equals("All", ignoreCase = true) || targetLevel.equals("All Levels", ignoreCase = true)) return true
    val normTarget = normalizeLevel(targetLevel)
    val normItem = if (itemCodeOrLevel != null && itemCodeOrLevel.contains("Level", ignoreCase = true)) {
      normalizeLevel(itemCodeOrLevel)
    } else {
      getLevelFromCourseCode(itemCodeOrLevel)
    }
    if (normItem == null) return false
    return normTarget == normItem
  }

  /**
   * Extracts standard 6-character course code from arbitrary string, e.g. "RAD 401 — Exam" -> "RAD 401"
   */
  fun extractCourseCode(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val match = Regex("""\b([A-Za-z]{3}\s*\d{3})\b""").find(text)
    return match?.groupValues?.getOrNull(1)?.uppercase()?.replace(Regex("""\s+"""), " ")
  }

  /**
   * Extracts level integer (e.g. 100, 200, 300)
   */
  fun getLevelInt(levelStr: String?): Int {
    val norm = normalizeLevel(levelStr)
    return norm.substring(0, 3).toIntOrNull() ?: 200
  }
}
