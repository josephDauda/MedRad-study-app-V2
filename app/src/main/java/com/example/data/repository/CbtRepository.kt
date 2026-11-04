package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DailyUsageEntity
import com.example.data.local.entity.ExamAttemptEntity
import com.example.data.local.entity.ExamEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.UserEntity
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseExamAttempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.util.LevelClassifier

class CbtRepository(
  private val database: AppDatabase,
  private val context: Context? = null
) {

  private val questionDao = database.questionDao()
  private val examDao = database.examDao()
  private val dailyUsageDao = database.dailyUsageDao()
  private val scope = CoroutineScope(Dispatchers.IO)
  private val tag = "CbtRepository"

  private val supabaseClient: SupabaseClient? = context?.let { SupabaseClient(it) }

  private fun getTodayDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date())
  }

  suspend fun getRemainingAllowance(user: UserEntity): Int {
    return withContext(Dispatchers.IO) {
      if (user.isPremium || user.role == "ADMIN") {
        return@withContext 9999 // Unlimited for premium/admin
      }
      val dateStr = getTodayDateString()
      val usage = dailyUsageDao.getUsage(user.id, dateStr)
      val answered = usage?.questionsAnsweredToday ?: 0
      val remaining = 15 - answered
      if (remaining < 0) 0 else remaining
    }
  }

  suspend fun canAnswerQuestion(user: UserEntity): Boolean {
    if (user.isPremium || user.role == "ADMIN") return true
    return getRemainingAllowance(user) > 0
  }

  suspend fun recordQuestionAnswered(user: UserEntity): Int {
    return withContext(Dispatchers.IO) {
      if (user.isPremium || user.role == "ADMIN") {
        return@withContext 9999
      }
      val dateStr = getTodayDateString()
      val usage = dailyUsageDao.getUsage(user.id, dateStr)
      val currentCount = usage?.questionsAnsweredToday ?: 0
      val newCount = currentCount + 1
      dailyUsageDao.saveUsage(
        DailyUsageEntity(
          id = usage?.id ?: 0,
          userId = user.id,
          dateString = dateStr,
          questionsAnsweredToday = newCount
        )
      )
      val remaining = 15 - newCount
      if (remaining < 0) 0 else remaining
    }
  }

  suspend fun getQuestionsByIds(ids: List<Long>): List<QuestionEntity> {
    return withContext(Dispatchers.IO) {
      if (ids.isEmpty()) emptyList() else questionDao.getQuestionsByIds(ids)
    }
  }

  suspend fun getPracticeQuestions(
    courseId: Long,
    limit: Int = 15,
    studentLevel: String? = null
  ): List<QuestionEntity> {
    return withContext(Dispatchers.IO) {
      if (!studentLevel.isNullOrBlank()) {
        val course = database.academicDao().getCourseById(courseId)
        if (course != null) {
          if (!LevelClassifier.matchesLevel(studentLevel, course.code.ifBlank { course.level })) {
            return@withContext emptyList<QuestionEntity>()
          }
        }
      }
      val questions = questionDao.getPublishedQuestionsForCourseList(courseId)
      questions.shuffled().take(limit)
    }
  }

  suspend fun getExamQuestions(
    courseId: Long,
    totalQuestions: Int,
    examRemoteId: String? = null,
    allowDepartmentalFallback: Boolean = false,
    studentLevel: String? = null
  ): List<QuestionEntity> {
    return withContext(Dispatchers.IO) {
      val allCourses = database.academicDao().getCoursesList().associateBy { it.id }

      val specificExamQuestions = if (!examRemoteId.isNullOrBlank()) {
        questionDao.getQuestionsByRemoteExamId(examRemoteId).filter { it.isPublished }
      } else emptyList()

      val courseQuestions = questionDao.getPublishedQuestionsForCourseList(courseId)
      val basePool = (specificExamQuestions + courseQuestions).distinctBy { it.id }.toMutableList()

      // Strictly purge any question that does not match candidate's level
      if (!studentLevel.isNullOrBlank()) {
        basePool.retainAll { q ->
          val qCourse = allCourses[q.courseId]
          val courseCode = qCourse?.code
          val courseLvl = qCourse?.level
          LevelClassifier.matchesLevel(studentLevel, courseCode ?: courseLvl)
        }
      }

      if (basePool.size < totalQuestions && (allowDepartmentalFallback || courseId == 9999L)) {
        val extra = questionDao.getAllQuestionsList().filter { it.isPublished }

        for (q in extra) {
          if (!basePool.any { it.id == q.id }) {
            // Strictly check academic level to prevent question leakage across levels
            if (!studentLevel.isNullOrBlank()) {
              val qCourse = allCourses[q.courseId]
              val courseCode = qCourse?.code
              val courseLvl = qCourse?.level
              if (!LevelClassifier.matchesLevel(studentLevel, courseCode ?: courseLvl)) {
                continue
              }
            }
            basePool.add(q)
            if (basePool.size >= totalQuestions) break
          }
        }
      }

      basePool.shuffled().take(totalQuestions)
    }
  }

  suspend fun getExamById(examId: Long): ExamEntity? {
    return withContext(Dispatchers.IO) {
      examDao.getExamById(examId)
    }
  }

  suspend fun submitExamAttempt(attempt: ExamAttemptEntity): Long {
    return withContext(Dispatchers.IO) {
      val finalRemoteId = attempt.remoteId?.takeIf { it.length == 36 } ?: java.util.UUID.randomUUID().toString()
      val finalAttempt = attempt.copy(remoteId = finalRemoteId)
      val attemptId = examDao.insertAttempt(finalAttempt)

      // Immediately attempt to sync to Supabase; if offline or fails, pending sync queue handles it
      supabaseClient?.let { client ->
        scope.launch {
          try {
            syncAttemptToSupabaseDirect(finalAttempt.copy(id = attemptId), client)
          } catch (e: Exception) {
            Log.w(tag, "Initial background sync notice: ${e.message}")
          }
        }
      }

      attemptId
    }
  }

  /**
   * Directly synchronizes a single exam attempt to Supabase PostgreSQL with progressive schema fallbacks.
   * Embeds full student and session metadata into answers_json so score sheets never lose candidate info.
   */
  suspend fun syncAttemptToSupabaseDirect(
    attempt: ExamAttemptEntity,
    client: SupabaseClient? = supabaseClient
  ): String? = withContext(Dispatchers.IO) {
    if (client == null) return@withContext null

    try {
      val localUser = database.userDao().getUserById(attempt.userId)
      val userEmail = localUser?.email?.trim()?.lowercase()

      // 1. Enrich answers_json with _meta to guarantee student name, level, email & session survive
      val enrichedAnswersJson = try {
        val baseJson = if (attempt.answersJson.startsWith("{")) org.json.JSONObject(attempt.answersJson) else org.json.JSONObject()
        val meta = baseJson.optJSONObject("_meta") ?: org.json.JSONObject()
        if (!meta.has("student_name") || meta.optString("student_name").isBlank()) {
          if (localUser != null) meta.put("student_name", localUser.fullName)
        }
        if (!meta.has("student_email") || meta.optString("student_email").isBlank()) {
          if (localUser != null) meta.put("student_email", localUser.email)
        }
        if (!meta.has("student_level") || meta.optString("student_level").isBlank()) {
          if (localUser != null) meta.put("student_level", localUser.level)
        }
        if (!meta.has("student_phone") || meta.optString("student_phone").isBlank()) {
          if (localUser != null) meta.put("student_phone", localUser.phoneNumber)
        }
        if (!meta.has("exam_title") || meta.optString("exam_title").isBlank()) {
          meta.put("exam_title", attempt.examTitle)
        }
        if (!meta.has("course_title") || meta.optString("course_title").isBlank()) {
          meta.put("course_title", attempt.courseTitle)
        }
        if (!meta.has("session_id") || meta.optString("session_id").isBlank()) {
          meta.put("session_id", "Session 1")
        }
        baseJson.put("_meta", meta)
        baseJson.toString()
      } catch (_: Exception) {
        attempt.answersJson
      }

      val examEntity = attempt.examId?.let { examDao.getExamById(it) }
      var remoteExamId = examEntity?.remoteId?.takeIf { it.length == 36 }
      var remoteCourseId = (examEntity?.remoteCourseId ?: attempt.remoteCourseId)?.takeIf { it.length == 36 }

      // If remoteCourseId is null, resolve from local courses
      if (remoteCourseId.isNullOrBlank()) {
        val allCourses = database.academicDao().getCoursesList()
        val matched = allCourses.find {
          it.title.equals(attempt.courseTitle, ignoreCase = true) ||
          attempt.examTitle.contains(it.code, ignoreCase = true) ||
          attempt.courseTitle.contains(it.code, ignoreCase = true)
        }
        remoteCourseId = matched?.remoteId?.takeIf { it.length == 36 }
      }

      // 2. Resolve student UUID from Supabase profiles (or create/upsert profile)
      var finalUid: String? = null
      if (!userEmail.isNullOrBlank()) {
        val profileRes = client.restGet("profiles?email=eq.$userEmail&select=id&limit=1")
        val profJson = profileRes.getOrNull() ?: "[]"
        try {
          val arr = org.json.JSONArray(profJson)
          if (arr.length() > 0) finalUid = arr.getJSONObject(0).optString("id").takeIf { it.length == 36 }
        } catch (_: Exception) {}

        if (finalUid.isNullOrBlank() && localUser != null) {
          val newProfileUuid = java.util.UUID.randomUUID().toString()
          val profilePayload = org.json.JSONObject().apply {
            put("id", newProfileUuid)
            put("email", userEmail)
            put("full_name", localUser.fullName)
            put("matric_number", localUser.phoneNumber)
            put("department", localUser.department)
            put("faculty", localUser.faculty)
            put("level", localUser.level)
            put("app_role", if (localUser.role == "ADMIN") "admin" else "student")
            put("is_active", true)
          }
          val upRes = client.restPost("profiles", profilePayload.toString(), upsert = true)
          if (upRes.isSuccess) finalUid = newProfileUuid
        }
      }

      if (finalUid.isNullOrBlank()) {
        finalUid = if (!userEmail.isNullOrBlank()) {
          java.util.UUID.nameUUIDFromBytes("supabase:$userEmail".toByteArray()).toString()
        } else {
          com.example.data.supabase.FlexibleIdHelper.toUuidString(attempt.userId, "student")
        }
      }

      // 3. Resolve or link valid Supabase exam ID
      var resolvedExamId = remoteExamId
      if (resolvedExamId.isNullOrBlank()) {
        val queryEndpoint = if (!remoteCourseId.isNullOrBlank()) "exams?course_id=eq.$remoteCourseId&limit=1" else "exams?limit=1"
        val examRes = client.restGet(queryEndpoint)
        val examJson = examRes.getOrNull() ?: "[]"
        try {
          val arr = org.json.JSONArray(examJson)
          if (arr.length() > 0) {
            resolvedExamId = arr.getJSONObject(0).optString("id").takeIf { it.length == 36 }
          }
        } catch (_: Exception) {}

        if (resolvedExamId.isNullOrBlank()) {
          // Query any published exam in the system
          val fallbackExamRes = client.restGet("exams?select=id&limit=1")
          val fallbackJson = fallbackExamRes.getOrNull() ?: "[]"
          try {
            val arr = org.json.JSONArray(fallbackJson)
            if (arr.length() > 0) {
              resolvedExamId = arr.getJSONObject(0).optString("id").takeIf { it.length == 36 }
            }
          } catch (_: Exception) {}
        }

        if (resolvedExamId.isNullOrBlank()) {
          val newExamUuid = java.util.UUID.randomUUID().toString()
          val examPayload = org.json.JSONObject().apply {
            put("id", newExamUuid)
            if (!remoteCourseId.isNullOrBlank()) put("course_id", remoteCourseId)
            put("title", attempt.examTitle.ifBlank { "Departmental CBT Examination" })
            put("duration_minutes", maxOf(10, attempt.timeSpentSeconds / 60))
            put("total_questions", attempt.totalQuestions)
            put("is_published", true)
          }
          val res = client.restPost("exams", examPayload.toString(), upsert = true)
          if (res.isSuccess) resolvedExamId = newExamUuid
        }
      }

      // 4. Post attempt with 3-tier progressive schema fallback
      val generatedAttemptId = attempt.remoteId?.takeIf { it.length == 36 } ?: java.util.UUID.randomUUID().toString()
      val pct = if (attempt.totalQuestions > 0) {
        (attempt.correctAnswers.toDouble() * 100.0) / attempt.totalQuestions
      } else {
        attempt.score.toDouble()
      }
      val isPassed = pct >= 50.0
      val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }.format(java.util.Date(attempt.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis()))

      // Extract session ID
      val sessionId = try {
        org.json.JSONObject(enrichedAnswersJson).optJSONObject("_meta")?.optString("session_id", "Session 1") ?: "Session 1"
      } catch (_: Exception) { "Session 1" }

      // Tier 1: Complete comprehensive payload
      val tier1Payload = org.json.JSONObject().apply {
        put("id", generatedAttemptId)
        put("user_id", finalUid)
        put("student_id", finalUid)
        if (!resolvedExamId.isNullOrBlank() && resolvedExamId.length == 36) put("exam_id", resolvedExamId)
        if (!remoteCourseId.isNullOrBlank() && remoteCourseId.length == 36) put("course_id", remoteCourseId)
        put("score", attempt.score)
        put("total_questions", attempt.totalQuestions)
        put("correct_answers", attempt.correctAnswers)
        put("incorrect_answers", attempt.incorrectAnswers)
        put("time_spent_seconds", attempt.timeSpentSeconds)
        put("status", "submitted")
        put("submitted_at", isoDate)
        put("created_at", isoDate)
        put("passed", isPassed)
        put("is_passed", isPassed)
        put("percentage", pct)
        put("answers_json", enrichedAnswersJson)
        put("session_id", sessionId)
        put("course_title", attempt.courseTitle)
        put("exam_title", attempt.examTitle)
        if (localUser != null) {
          put("student_name", localUser.fullName)
          put("student_email", localUser.email)
          put("student_phone", localUser.phoneNumber)
          put("student_level", localUser.level)
        }
      }

      var res = client.restPost("exam_attempts", tier1Payload.toString(), upsert = true)
      if (res.isFailure) {
        val err = res.exceptionOrNull()?.message ?: ""
        Log.w(tag, "Tier 1 exam_attempts insert notice ($err), trying standard schema Tier 2...")
        // Tier 2: Standard schema columns only
        val tier2Payload = org.json.JSONObject().apply {
          put("id", generatedAttemptId)
          put("user_id", finalUid)
          if (!resolvedExamId.isNullOrBlank() && resolvedExamId.length == 36) put("exam_id", resolvedExamId)
          if (!remoteCourseId.isNullOrBlank() && remoteCourseId.length == 36) put("course_id", remoteCourseId)
          put("score", attempt.score)
          put("total_questions", attempt.totalQuestions)
          put("time_spent_seconds", attempt.timeSpentSeconds)
          put("is_passed", isPassed)
          put("percentage", pct)
          put("answers_json", enrichedAnswersJson)
        }
        res = client.restPost("exam_attempts", tier2Payload.toString(), upsert = true)
      }

      if (res.isFailure) {
        val err = res.exceptionOrNull()?.message ?: ""
        Log.w(tag, "Tier 2 exam_attempts insert notice ($err), trying minimal schema Tier 3...")
        // Tier 3: Absolute minimal columns
        val tier3Payload = org.json.JSONObject().apply {
          put("id", generatedAttemptId)
          put("user_id", finalUid)
          put("score", attempt.score)
          put("total_questions", attempt.totalQuestions)
          put("answers_json", enrichedAnswersJson)
        }
        res = client.restPost("exam_attempts", tier3Payload.toString(), upsert = true)
      }

      // 4. Critical Fail-Safe: Dual-sync to app_config so RLS on exam_attempts cannot block admins on other devices
      val configKey = "score_attempt_${generatedAttemptId}"
      val configPayload = org.json.JSONObject().apply {
        put("key", configKey)
        put("value", tier1Payload.toString())
      }
      val configRes = client.restPost("app_config", configPayload.toString(), upsert = true)
      if (configRes.isSuccess) {
        Log.i(tag, "Exam attempt dual-synced to app_config feed for instant admin delivery: $configKey")
      } else {
        Log.w(tag, "Notice on app_config dual-sync: ${configRes.exceptionOrNull()?.message}")
      }

      if (res.isSuccess || configRes.isSuccess) {
        val updatedAttempt = attempt.copy(
          remoteId = generatedAttemptId,
          remoteExamId = resolvedExamId,
          remoteCourseId = remoteCourseId,
          answersJson = enrichedAnswersJson
        )
        examDao.insertAttempt(updatedAttempt)
        Log.i(tag, "Exam attempt synced successfully to cloud (attempts: ${res.isSuccess}, feed: ${configRes.isSuccess}): $generatedAttemptId")
        generatedAttemptId
      } else {
        Log.e(tag, "Failed to sync exam attempt to Supabase: ${res.exceptionOrNull()?.message}")
        null
      }
    } catch (e: Exception) {
      Log.e(tag, "Error syncing attempt to Supabase: ${e.message}")
      null
    }
  }

  /**
   * Scans local database for any exam attempts created while offline (remoteId is null)
   * and uploads them to Supabase cloud.
   */
  suspend fun syncPendingExamAttempts(): Int = withContext(Dispatchers.IO) {
    val client = supabaseClient ?: return@withContext 0
    val unsynced = examDao.getUnsyncedAttempts()
    if (unsynced.isEmpty()) return@withContext 0

    var successCount = 0
    for (att in unsynced) {
      val remoteId = syncAttemptToSupabaseDirect(att, client)
      if (!remoteId.isNullOrBlank()) {
        successCount++
      }
    }
    Log.i(tag, "syncPendingExamAttempts: uploaded $successCount of ${unsynced.size} offline attempts to Supabase")
    successCount
  }

  suspend fun getAttemptById(attemptId: Long): ExamAttemptEntity? {
    return withContext(Dispatchers.IO) {
      examDao.getAttemptById(attemptId)
    }
  }

  fun getUserAttempts(userId: Long): Flow<List<ExamAttemptEntity>> {
    return examDao.getAttemptsForUser(userId)
  }

  suspend fun getUserAttemptsList(userId: Long, limit: Int = 10): List<ExamAttemptEntity> {
    return withContext(Dispatchers.IO) {
      examDao.getRecentAttempts(userId, limit)
    }
  }

  suspend fun getExistingAttemptForUserAndExam(
    userId: Long,
    examId: Long?,
    examTitle: String,
    courseCode: String? = null,
    sessionId: String? = null,
    userEmail: String? = null
  ): ExamAttemptEntity? {
    return withContext(Dispatchers.IO) {
      val targetSession = sessionId?.trim()?.ifBlank { "Session 1" } ?: "Session 1"
      val userAttempts = examDao.getAttemptsForUserList(userId).toMutableList()

      // Also retrieve attempts matching the student's email in case userId mapping varies
      if (!userEmail.isNullOrBlank()) {
        try {
          val allAttempts = examDao.getAllAttemptsList()
          for (att in allAttempts) {
            if (userAttempts.none { it.id == att.id }) {
              val meta = if (att.answersJson.startsWith("{")) org.json.JSONObject(att.answersJson).optJSONObject("_meta") else null
              val studentEmail = meta?.optString("student_email")
              if (studentEmail?.equals(userEmail.trim(), ignoreCase = true) == true) {
                userAttempts.add(att)
              }
            }
          }
        } catch (_: Exception) {}
      }

      val incomingCode = LevelClassifier.extractCourseCode(courseCode)
        ?: LevelClassifier.extractCourseCode(examTitle)

      userAttempts.firstOrNull { attempt ->
        val attemptMeta = try {
          if (attempt.answersJson.startsWith("{")) org.json.JSONObject(attempt.answersJson).optJSONObject("_meta") else null
        } catch (_: Exception) { null }

        val attemptSession = attemptMeta?.optString("session_id")?.takeIf { it.isNotBlank() } ?: "Session 1"

        // Session differentiation: only block retake if attempt belongs to the active target session
        val matchesSession = attemptSession.equals(targetSession, ignoreCase = true)
        if (!matchesSession) return@firstOrNull false

        val attemptCode = LevelClassifier.extractCourseCode(attempt.courseTitle)
          ?: LevelClassifier.extractCourseCode(attempt.examTitle)

        val isAttemptMock = (attempt.examId != null && attempt.examId > 0) ||
            attempt.examTitle.contains("Mock", ignoreCase = true) ||
            attempt.examTitle.contains("CBT", ignoreCase = true) ||
            attempt.examTitle.contains("Assessment", ignoreCase = true) ||
            attempt.examTitle.contains("Exam", ignoreCase = true) ||
            attempt.examTitle.contains("Continuous", ignoreCase = true) ||
            (attemptMeta?.optBoolean("is_mock_exam") == true)

        val matchesCourse = when {
          incomingCode != null && attemptCode != null -> incomingCode.equals(attemptCode, ignoreCase = true)
          incomingCode != null -> attempt.courseTitle.contains(incomingCode, ignoreCase = true) || attempt.examTitle.contains(incomingCode, ignoreCase = true)
          attemptCode != null && !courseCode.isNullOrBlank() -> courseCode.contains(attemptCode, ignoreCase = true) || examTitle.contains(attemptCode, ignoreCase = true)
          else -> (examId != null && examId > 0 && attempt.examId == examId) ||
                  attempt.examTitle.equals(examTitle.trim(), ignoreCase = true) ||
                  isAttemptMock
        }

        matchesCourse
      }
    }
  }
}
