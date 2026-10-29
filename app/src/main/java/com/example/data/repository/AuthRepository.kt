package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserEntity
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseProfile
import com.example.data.util.LevelClassifier
import com.example.data.util.PasswordSecurity
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthRepository(private val context: Context, private val database: AppDatabase) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences("medrad_auth_prefs", Context.MODE_PRIVATE)

  val supabaseClient = SupabaseClient(context)
  private val scope = CoroutineScope(Dispatchers.IO)
  private val tag = "AuthRepository"

  private val _currentUser = MutableStateFlow<UserEntity?>(null)
  val currentUser: Flow<UserEntity?> = _currentUser.asStateFlow()

  data class RemoteUserRecord(
    val email: String,
    val fullName: String,
    val phone: String,
    val level: String,
    val department: String,
    val faculty: String,
    val role: String,
    val passwordHash: String? = null,
    val isActive: Boolean = true
  )

  suspend fun fetchRemoteUser(email: String): RemoteUserRecord? {
    val trimmedEmail = email.trim().lowercase()

    // 1. Check app_config student_user_<email> (authoritative cloud database store)
    try {
      val configRes = supabaseClient.restGet("app_config?key=eq.student_user_$trimmedEmail&select=*")
      if (configRes.isSuccess) {
        val json = configRes.getOrNull() ?: "[]"
        val array = JSONArray(json)
        if (array.length() > 0) {
          val obj = array.getJSONObject(0)
          val valStr = obj.optString("value", "")
          if (valStr.isNotBlank() && valStr.startsWith("{")) {
            val userObj = JSONObject(valStr)
            val em = userObj.optString("email", trimmedEmail).lowercase()
            return RemoteUserRecord(
              email = em,
              fullName = userObj.optString("full_name", userObj.optString("fullName", "Student")),
              phone = userObj.optString("phone", userObj.optString("matric_number", "")),
              level = userObj.optString("level", "100 Level"),
              department = userObj.optString("department", "Medical Radiography"),
              faculty = userObj.optString("faculty", "College of Medical Sciences"),
              role = userObj.optString("role", userObj.optString("app_role", "STUDENT")).uppercase(),
              passwordHash = userObj.optString("password_hash", "").ifBlank { null },
              isActive = userObj.optBoolean("is_active", true)
            )
          }
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "Notice checking app_config user: ${e.message}")
    }

    // 2. Check profiles table in Supabase
    try {
      val profRes = supabaseClient.restGet("profiles?email=eq.$trimmedEmail&select=*")
      if (profRes.isSuccess) {
        val json = profRes.getOrNull() ?: "[]"
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, SupabaseProfile::class.java)
        val list = supabaseClient.moshi.adapter<List<SupabaseProfile>>(type).fromJson(json) ?: emptyList()
        val p = list.firstOrNull()
        if (p != null) {
          return RemoteUserRecord(
            email = p.email?.trim()?.lowercase() ?: trimmedEmail,
            fullName = p.fullName ?: "Student",
            phone = p.matricNumber ?: "",
            level = p.level ?: "100 Level",
            department = p.department ?: "Medical Radiography",
            faculty = p.faculty ?: "College of Medical Sciences",
            role = if (p.appRole?.equals("admin", ignoreCase = true) == true) "ADMIN" else "STUDENT",
            passwordHash = null,
            isActive = p.isActive
          )
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "Notice checking profiles: ${e.message}")
    }

    return null
  }

  suspend fun checkExistingSession() {
    withContext(Dispatchers.IO) {
      val savedUserId = prefs.getLong("logged_in_user_id", -1L)
      if (savedUserId != -1L) {
        val user = database.userDao().getUserById(savedUserId)
        if (user != null) {
          _currentUser.value = user
          // Check if cloud session needs token refresh
          if (supabaseClient.isSessionExpired()) {
            val refreshRes = supabaseClient.authRefreshToken()
            if (refreshRes.isFailure) {
              Log.w(tag, "Supabase session refresh notice: ${refreshRes.exceptionOrNull()?.message}")
            }
          }

          // Authoritative Database Validation:
          val trimmedEmail = user.email.trim().lowercase()
          if (trimmedEmail != "admin@medrad.edu.ng") {
            try {
              val remoteUser = fetchRemoteUser(trimmedEmail)
              if (remoteUser == null) {
                // Confirm if database is reachable before auto-signing out to prevent offline lockout
                val ping = supabaseClient.restGet("app_config?select=key&limit=1")
                if (ping.isSuccess) {
                  // User does not exist on database (or was deleted manually)
                  Log.w(tag, "User $trimmedEmail was deleted or does not exist on database. Purging session & auto-signing out.")
                  database.userDao().deleteUser(user)
                  logout()
                  return@withContext
                }
              } else {
                // Keep local user in sync with latest database profile (level, name, role)
                val normalizedLevel = LevelClassifier.normalizeLevel(remoteUser.level)
                val updated = user.copy(
                  fullName = remoteUser.fullName.ifBlank { user.fullName },
                  phoneNumber = remoteUser.phone.ifBlank { user.phoneNumber },
                  department = remoteUser.department.ifBlank { user.department },
                  faculty = remoteUser.faculty.ifBlank { user.faculty },
                  level = normalizedLevel,
                  role = remoteUser.role,
                  isActive = remoteUser.isActive,
                  isPremium = if (remoteUser.role == "ADMIN") true else user.isPremium
                )
                database.userDao().updateUser(updated)
                _currentUser.value = updated
              }
            } catch (e: Exception) {
              Log.w(tag, "Notice checking database profile during session check: ${e.message}")
            }
          } else {
            syncProfileFromSupabase(user.email)
          }
        } else {
          _currentUser.value = null
          prefs.edit().remove("logged_in_user_id").apply()
        }
      } else {
        _currentUser.value = null
        prefs.edit().remove("logged_in_user_id").apply()
      }
    }
  }

  suspend fun login(email: String, password: String): Result<UserEntity> {
    return withContext(Dispatchers.IO) {
      try {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isBlank() || password.isBlank()) {
          return@withContext Result.failure(Exception("Please enter your email and password."))
        }

        val localUser = database.userDao().getUserByEmail(trimmedEmail)

        // Built-in Admin quick access fallback
        if (trimmedEmail == "admin@medrad.edu.ng" && password == "MedRadAdmin2026!") {
          val admin = localUser ?: run {
            val newAdmin = UserEntity(
              fullName = "Chief Radiographer / Admin",
              email = trimmedEmail,
              phoneNumber = "+2348000000001",
              passwordHash = PasswordSecurity.hashPassword(password),
              role = "ADMIN",
              adminRole = "PRIMARY",
              permissions = "ALL",
              university = "University of Maiduguri",
              faculty = "College of Medical Sciences",
              department = "Medical Radiography",
              level = "Staff / Faculty",
              semester = "Full Session",
              isPremium = true
            )
            val id = database.userDao().insertUser(newAdmin)
            newAdmin.copy(id = id)
          }
          prefs.edit().putLong("logged_in_user_id", admin.id).apply()
          _currentUser.value = admin
          return@withContext Result.success(admin)
        }

        // Built-in Demo student fallback
        if (trimmedEmail == "student@medrad.edu.ng" && password == "Student123!") {
          val student = localUser ?: run {
            val newStudent = UserEntity(
              fullName = "Khadija Usman",
              email = trimmedEmail,
              phoneNumber = "+2348000000002",
              passwordHash = PasswordSecurity.hashPassword(password),
              role = "STUDENT",
              university = "University of Maiduguri",
              faculty = "College of Medical Sciences",
              department = "Medical Radiography",
              level = "100 Level",
              semester = "First Semester",
              isPremium = false
            )
            val id = database.userDao().insertUser(newStudent)
            newStudent.copy(id = id)
          }
          prefs.edit().putLong("logged_in_user_id", student.id).apply()
          _currentUser.value = student
          return@withContext Result.success(student)
        }

        // Authoritative Database Check:
        val remoteUser = fetchRemoteUser(trimmedEmail)
        val isDatabaseOnline = supabaseClient.restGet("app_config?select=key&limit=1").isSuccess

        // If database is reachable and user does not exist in the database users list:
        if (isDatabaseOnline && remoteUser == null) {
          if (localUser != null) {
            database.userDao().deleteUser(localUser)
          }
          return@withContext Result.failure(
            Exception("Account does not exist on the database. Please register first to create your account.")
          )
        }

        // Password Verification:
        // 1. Check against remote password hash from database (highest authority across all phones)
        val isRemotePasswordMatch = remoteUser?.passwordHash != null && PasswordSecurity.verifyPassword(password, remoteUser.passwordHash)

        // 2. Check via Supabase Auth (if account exists in auth)
        var cloudAuthSuccess = false
        try {
          val authResult = supabaseClient.authSignIn(trimmedEmail, password)
          if (authResult.isSuccess) {
            cloudAuthSuccess = true
            val authResp = authResult.getOrThrow()
            supabaseClient.saveSession(authResp.accessToken, authResp.refreshToken, authResp.user?.id, authResp.expiresIn)
          }
        } catch (_: Exception) {}

        // 3. Check against local password hash (for offline mode)
        val isLocalPasswordMatch = localUser != null && PasswordSecurity.verifyPassword(password, localUser.passwordHash)

        if (!isRemotePasswordMatch && !cloudAuthSuccess && !isLocalPasswordMatch) {
          return@withContext Result.failure(Exception("Incorrect password for $trimmedEmail. Please try again."))
        }

        // User authenticated! Resolve profile details
        val resolvedRole = remoteUser?.role ?: (localUser?.role ?: "STUDENT")
        val resolvedName = remoteUser?.fullName?.ifBlank { localUser?.fullName ?: "Radiography Scholar" } ?: (localUser?.fullName ?: "Radiography Scholar")
        val resolvedLevel = if (!remoteUser?.level.isNullOrBlank()) {
          LevelClassifier.normalizeLevel(remoteUser.level)
        } else (localUser?.level ?: "100 Level")
        val resolvedPhone = remoteUser?.phone?.ifBlank { localUser?.phoneNumber ?: "" } ?: (localUser?.phoneNumber ?: "")
        val resolvedDept = remoteUser?.department?.ifBlank { localUser?.department ?: "Medical Radiography" } ?: (localUser?.department ?: "Medical Radiography")
        val resolvedFaculty = remoteUser?.faculty?.ifBlank { localUser?.faculty ?: "College of Medical Sciences" } ?: (localUser?.faculty ?: "College of Medical Sciences")

        val userEntity = if (localUser == null) {
          val newUser = UserEntity(
            fullName = resolvedName,
            email = trimmedEmail,
            phoneNumber = resolvedPhone,
            passwordHash = PasswordSecurity.hashPassword(password),
            role = resolvedRole,
            university = "University of Maiduguri",
            faculty = resolvedFaculty,
            department = resolvedDept,
            level = resolvedLevel,
            semester = "First Semester",
            isPremium = resolvedRole == "ADMIN"
          )
          val id = database.userDao().insertUser(newUser)
          newUser.copy(id = id)
        } else {
          val updated = localUser.copy(
            fullName = resolvedName,
            phoneNumber = resolvedPhone,
            role = resolvedRole,
            department = resolvedDept,
            faculty = resolvedFaculty,
            level = resolvedLevel,
            passwordHash = PasswordSecurity.hashPassword(password),
            isPremium = if (resolvedRole == "ADMIN") true else localUser.isPremium
          )
          database.userDao().updateUser(updated)
          updated
        }

        val checked = checkAndUpdatePremiumExpiry(userEntity)
        prefs.edit().putLong("logged_in_user_id", checked.id).apply()
        _currentUser.value = checked
        return@withContext Result.success(checked)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }

  fun getGoogleOAuthUrl(): String = supabaseClient.getGoogleOAuthUrl()

  suspend fun signInWithGoogle(
    email: String,
    fullName: String = "Student",
    idToken: String? = null
  ): Result<UserEntity> = withContext(Dispatchers.IO) {
    try {
      val trimmedEmail = email.trim().lowercase()
      if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
        return@withContext Result.failure(Exception("Please provide a valid Google/Gmail address."))
      }

      // Check if user already exists in remote Supabase profiles
      var existingRemoteProfile: SupabaseProfile? = null
      try {
        val checkRes = supabaseClient.restGet("profiles?email=eq.$trimmedEmail&select=*")
        checkRes.onSuccess { json ->
          val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, SupabaseProfile::class.java)
          val list = supabaseClient.moshi.adapter<List<SupabaseProfile>>(type).fromJson(json)
          existingRemoteProfile = list?.firstOrNull()
        }
      } catch (_: Exception) {}

      val authRes = supabaseClient.authSignInWithGoogleAccount(trimmedEmail, fullName, idToken)
      val authUser = authRes.getOrNull()?.user
      val profileId = existingRemoteProfile?.id ?: authUser?.id ?: java.util.UUID.nameUUIDFromBytes("google:$trimmedEmail".toByteArray()).toString()
      val verifiedRole = if (existingRemoteProfile?.appRole?.equals("admin", ignoreCase = true) == true || trimmedEmail == "admin@medrad.edu.ng") "ADMIN" else "STUDENT"
      var user = database.userDao().getUserByEmail(trimmedEmail)
      if (user == null) {
        val newUser = UserEntity(
          fullName = existingRemoteProfile?.fullName ?: fullName.trim().ifBlank { "Radiography Scholar" },
          email = trimmedEmail,
          phoneNumber = "",
          passwordHash = "", // Never ask or store Google password
          role = verifiedRole,
          university = "University of Maiduguri",
          faculty = existingRemoteProfile?.faculty ?: "College of Medical Sciences",
          department = existingRemoteProfile?.department ?: "Medical Radiography",
          level = existingRemoteProfile?.level ?: "100 Level",
          semester = "1st Semester",
          isPremium = verifiedRole == "ADMIN"
        )
        val id = database.userDao().insertUser(newUser)
        user = newUser.copy(id = id)
      } else {
        val updated = user.copy(
          fullName = existingRemoteProfile?.fullName ?: user.fullName,
          role = verifiedRole,
          isPremium = if (verifiedRole == "ADMIN") true else user.isPremium
        )
        database.userDao().updateUser(updated)
        user = updated
      }

      val updatedUser = checkAndUpdatePremiumExpiry(user)
      prefs.edit().putLong("logged_in_user_id", updatedUser.id).apply()
      _currentUser.value = updatedUser

      // Synchronize/upsert profiles in Supabase with verified UUID
      try {
        val profileDto = SupabaseProfile(
          id = profileId,
          fullName = updatedUser.fullName,
          email = trimmedEmail,
          level = updatedUser.level,
          department = updatedUser.department,
          faculty = updatedUser.faculty,
          appRole = updatedUser.role,
          isActive = true
        )
        val json = supabaseClient.moshi.adapter(SupabaseProfile::class.java).toJson(profileDto)
        supabaseClient.restPost("profiles", json, upsert = true)
      } catch (e: Exception) {
        Log.w(tag, "Supabase profile sync notice on Google login: ${e.message}")
      }

      Result.success(updatedUser)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun handleOAuthCallback(uri: Uri): Result<UserEntity> = withContext(Dispatchers.IO) {
    try {
      val fragment = uri.fragment
      val queryParams = mutableMapOf<String, String>()
      if (!fragment.isNullOrBlank()) {
        fragment.split("&").forEach { pair ->
          val parts = pair.split("=", limit = 2)
          if (parts.size == 2) {
            queryParams[parts[0]] = parts[1]
          }
        }
      }
      val accessToken = queryParams["access_token"] ?: uri.getQueryParameter("access_token")
      if (accessToken.isNullOrBlank()) {
        return@withContext Result.failure(Exception("No access token received from Google OAuth"))
      }

      supabaseClient.saveSession(accessToken, null)
      val userResult = supabaseClient.authGetUser(accessToken)
      val authUser = userResult.getOrNull()
      val email = authUser?.email?.trim()?.lowercase()
        ?: return@withContext Result.failure(Exception("Unable to retrieve verified email from Google session"))
      val fullName = (authUser.userMetadata?.get("full_name") as? String)?.trim()?.ifBlank { "Radiography Scholar" } ?: "Radiography Scholar"

      supabaseClient.saveSession(accessToken, authUser.id)

      // Query profiles table in Supabase for verified cloud role
      var verifiedRole = "STUDENT"
      try {
        val profileRes = supabaseClient.restGet("profiles?id=eq.${authUser.id}&select=*")
        profileRes.onSuccess { json ->
          val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, SupabaseProfile::class.java)
          val list = supabaseClient.moshi.adapter<List<SupabaseProfile>>(type).fromJson(json)
          val profile = list?.firstOrNull()
          if (profile != null) {
            if (profile.appRole?.equals("admin", ignoreCase = true) == true) {
              verifiedRole = "ADMIN"
            }
          } else {
            // Profile doesn't exist yet, insert into Supabase with STUDENT role
            val newProfile = SupabaseProfile(
              id = authUser.id,
              fullName = fullName,
              email = email,
              appRole = "STUDENT",
              isActive = true
            )
            val profileJson = supabaseClient.moshi.adapter(SupabaseProfile::class.java).toJson(newProfile)
            supabaseClient.restPost("profiles", profileJson, upsert = true)
          }
        }
      } catch (e: Exception) {
        Log.w(tag, "Profile role verification notice: ${e.message}")
      }

      var localUser = database.userDao().getUserByEmail(email)
      if (localUser == null) {
        val newUser = UserEntity(
          fullName = fullName,
          email = email,
          phoneNumber = "",
          passwordHash = "",
          role = verifiedRole,
          university = "University of Nigeria, Nsukka",
          faculty = "Faculty of Health Sciences & Technology",
          department = "Medical Radiography & Radiological Science",
          level = "100 Level",
          semester = "1st Semester",
          isPremium = verifiedRole == "ADMIN"
        )
        val id = database.userDao().insertUser(newUser)
        localUser = newUser.copy(id = id)
      } else {
        localUser = localUser.copy(
          fullName = fullName.ifBlank { localUser.fullName },
          role = if (localUser.role == "ADMIN" || verifiedRole == "ADMIN") "ADMIN" else "STUDENT"
        )
        database.userDao().updateUser(localUser)
      }

      prefs.edit().putLong("logged_in_user_id", localUser.id).apply()
      _currentUser.value = localUser
      Result.success(localUser)
    } catch (e: Exception) {
      Log.e(tag, "handleOAuthCallback error: ${e.message}")
      Result.failure(e)
    }
  }

  suspend fun register(
    fullName: String,
    email: String,
    phoneNumber: String,
    password: String,
    university: String,
    faculty: String,
    department: String,
    level: String,
    semester: String
  ): Result<UserEntity> {
    return withContext(Dispatchers.IO) {
      try {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPhone = phoneNumber.trim()
        if (password.length < 6) {
          return@withContext Result.failure(Exception("Personal password must be at least 6 characters."))
        }

        val normalizedLevel = LevelClassifier.normalizeLevel(level.trim())

        // 1. Authoritative check: verify if user already exists on the database
        val existingRemote = fetchRemoteUser(trimmedEmail)
        if (existingRemote != null) {
          return@withContext Result.failure(Exception("An account with this email ($trimmedEmail) is already registered on the database. Please sign in instead."))
        }

        val resolvedRole = if (trimmedEmail == "admin@medrad.edu.ng") "ADMIN" else "STUDENT"
        val passwordHash = PasswordSecurity.hashPassword(password)

        // 2. Authoritatively store student account on the Supabase cloud database
        val userJson = JSONObject().apply {
          put("id", "student_${java.util.UUID.randomUUID()}")
          put("full_name", fullName.trim())
          put("email", trimmedEmail)
          put("phone", trimmedPhone)
          put("matric_number", trimmedPhone)
          put("level", normalizedLevel)
          put("department", department.trim())
          put("faculty", faculty.trim())
          put("university", university.trim())
          put("role", resolvedRole)
          put("app_role", if (resolvedRole == "ADMIN") "admin" else "student")
          put("password_hash", passwordHash)
          put("is_active", true)
          put("created_at", System.currentTimeMillis())
        }

        val configPayload = JSONObject().apply {
          put("key", "student_user_$trimmedEmail")
          put("value", userJson.toString())
          put("description", "Student Account: ${fullName.trim()} ($normalizedLevel)")
        }

        val configPostResult = supabaseClient.restPost("app_config", configPayload.toString(), upsert = true)
        if (configPostResult.isFailure) {
          return@withContext Result.failure(
            Exception("Could not store account in the database. Please verify your internet connection and try again.")
          )
        }

        // 3. Optional sync to Supabase Auth and profiles table (non-blocking if rate limited)
        try {
          val metadata = mapOf(
            "full_name" to fullName.trim(),
            "department" to department.trim(),
            "faculty" to faculty.trim(),
            "level" to normalizedLevel,
            "phone" to trimmedPhone
          )
          val authRes = supabaseClient.authSignUp(trimmedEmail, password, metadata)
          val authUserId = authRes.getOrNull()?.user?.id
          if (authUserId != null) {
            val profileDto = SupabaseProfile(
              id = authUserId,
              fullName = fullName.trim(),
              email = trimmedEmail,
              matricNumber = trimmedPhone.ifBlank { null },
              level = normalizedLevel,
              department = department.trim(),
              faculty = faculty.trim(),
              appRole = if (resolvedRole == "ADMIN") "admin" else "student",
              isActive = true
            )
            val pJson = supabaseClient.moshi.adapter(SupabaseProfile::class.java).toJson(profileDto)
            supabaseClient.restPost("profiles", pJson, upsert = true)
          }
        } catch (e: Exception) {
          Log.w(tag, "Optional auth signup notice (non-fatal): ${e.message}")
        }

        // 4. Cache into Room for local offline accessibility
        val existing = database.userDao().getUserByEmail(trimmedEmail)
        val created = if (existing != null) {
          val updated = existing.copy(
            fullName = fullName.trim(),
            email = trimmedEmail,
            phoneNumber = trimmedPhone,
            passwordHash = passwordHash,
            role = existing.role,
            university = university.trim(),
            faculty = faculty.trim(),
            department = department.trim(),
            level = normalizedLevel,
            semester = semester.trim()
          )
          database.userDao().updateUser(updated)
          updated
        } else {
          val newUser = UserEntity(
            fullName = fullName.trim(),
            email = trimmedEmail,
            phoneNumber = trimmedPhone,
            passwordHash = passwordHash,
            role = resolvedRole,
            university = university.trim(),
            faculty = faculty.trim(),
            department = department.trim(),
            level = normalizedLevel,
            semester = semester.trim(),
            isPremium = resolvedRole == "ADMIN"
          )
          val id = database.userDao().insertUser(newUser)
          newUser.copy(id = id)
        }

        prefs.edit().putLong("logged_in_user_id", created.id).apply()
        _currentUser.value = created
        Result.success(created)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }

  suspend fun resetPassword(email: String, newPassword: String): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      try {
        val trimmedEmail = email.trim().lowercase()
        val user = database.userDao().getUserByEmail(trimmedEmail)
        val newHash = PasswordSecurity.hashPassword(newPassword)

        if (user != null) {
          database.userDao().updateUser(user.copy(passwordHash = newHash))
        }

        // Always update remote database in app_config
        try {
          val cfgRes = supabaseClient.restGet("app_config?key=eq.student_user_$trimmedEmail&select=*")
          val cfgJson = cfgRes.getOrNull()
          val studentObj = if (!cfgJson.isNullOrBlank()) {
            try {
              val arr = JSONArray(cfgJson)
              if (arr.length() > 0) JSONObject(arr.getJSONObject(0).optString("value", "{}")) else JSONObject()
            } catch (_: Exception) { JSONObject() }
          } else JSONObject()

          studentObj.put("password_hash", newHash)
          studentObj.put("email", trimmedEmail)
          studentObj.put("updated_at", System.currentTimeMillis())

          val cfgPayload = JSONObject().apply {
            put("key", "student_user_$trimmedEmail")
            put("value", studentObj.toString())
            put("description", "Student Account: ${user?.fullName ?: trimmedEmail}")
          }
          supabaseClient.restPost("app_config", cfgPayload.toString(), upsert = true)
        } catch (e: Exception) {
          Log.w(tag, "Failed to update password in app_config: ${e.message}")
        }

        Result.success(true)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }

  suspend fun updateProfile(
    userId: Long,
    newFullName: String,
    newPhoneNumber: String,
    newLevel: String? = null
  ): Result<UserEntity> {
    return withContext(Dispatchers.IO) {
      try {
        val user = database.userDao().getUserById(userId)
          ?: return@withContext Result.failure(Exception("User account not found."))
        val normalizedLevel = if (!newLevel.isNullOrBlank()) com.example.data.util.LevelClassifier.normalizeLevel(newLevel) else user.level
        val updated = user.copy(
          fullName = newFullName.trim().ifEmpty { user.fullName },
          phoneNumber = newPhoneNumber.trim(),
          level = normalizedLevel
        )
        // 1. Immediately update Room database so it is saved locally on device
        database.userDao().updateUser(updated)
        _currentUser.value = updated

        // 2. Authoritatively synchronize to Supabase app_config student_user_<email>
        val userEmail = user.email.trim().lowercase()
        try {
          val cfgRes = supabaseClient.restGet("app_config?key=eq.student_user_$userEmail&select=*")
          val cfgJson = cfgRes.getOrNull()
          val studentObj = if (!cfgJson.isNullOrBlank()) {
            try {
              val arr = JSONArray(cfgJson)
              if (arr.length() > 0) JSONObject(arr.getJSONObject(0).optString("value", "{}")) else JSONObject()
            } catch (_: Exception) { JSONObject() }
          } else JSONObject()

          studentObj.put("full_name", updated.fullName)
          studentObj.put("phone", updated.phoneNumber)
          studentObj.put("matric_number", updated.phoneNumber)
          studentObj.put("level", updated.level)
          studentObj.put("email", userEmail)
          studentObj.put("department", updated.department)
          studentObj.put("faculty", updated.faculty)
          studentObj.put("university", updated.university)
          studentObj.put("role", updated.role)
          studentObj.put("app_role", if (updated.role == "ADMIN") "admin" else "student")
          studentObj.put("updated_at", System.currentTimeMillis())

          val cfgPayload = JSONObject().apply {
            put("key", "student_user_$userEmail")
            put("value", studentObj.toString())
            put("description", "Student Account: ${updated.fullName} (${updated.level})")
          }
          supabaseClient.restPost("app_config", cfgPayload.toString(), upsert = true)
          Log.i(tag, "Profile level updated in app_config: $userEmail -> ${updated.level}")
        } catch (e: Exception) {
          Log.w(tag, "Failed to update student in app_config: ${e.message}")
        }

        // 3. Synchronize to Supabase profiles table using verified remote ID and direct PATCH
        try {
          var existingProfileId: String? = null
          val checkRes = supabaseClient.restGet("profiles?email=eq.$userEmail&limit=1")
          val checkJson = checkRes.getOrNull() ?: "[]"
          try {
            val arr = JSONArray(checkJson)
            if (arr.length() > 0) {
              existingProfileId = arr.getJSONObject(0).optString("id")
            }
          } catch (_: Exception) {}

          val targetProfileId = existingProfileId
            ?: supabaseClient.getUserId()
            ?: com.example.data.supabase.FlexibleIdHelper.toUuidString(user.id, "user")

          val profilePayload = JSONObject().apply {
            put("id", targetProfileId)
            put("email", userEmail)
            put("full_name", updated.fullName)
            put("matric_number", updated.phoneNumber)
            put("level", updated.level)
            put("department", updated.department)
            put("faculty", updated.faculty)
            put("app_role", if (updated.role == "ADMIN") "admin" else "student")
            put("is_active", updated.isActive)
          }

          val postRes = supabaseClient.restPost("profiles", profilePayload.toString(), upsert = true)
          // Also execute direct patch by email to guarantee database row is updated
          val patchObj = JSONObject().apply {
            put("full_name", updated.fullName)
            put("matric_number", updated.phoneNumber)
            put("level", updated.level)
          }
          supabaseClient.restPatch("profiles?email=eq.$userEmail", patchObj.toString())
          Log.i(tag, "Profile level updated in Supabase successfully: $userEmail -> ${updated.level} (upsert success=${postRes.isSuccess})")
        } catch (e: Exception) {
          Log.w(tag, "Supabase profile sync notice: ${e.message}")
        }

        Result.success(updated)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }

  suspend fun changePassword(userId: Long, currentPass: String, newPass: String): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      try {
        val user = database.userDao().getUserById(userId)
          ?: return@withContext Result.failure(Exception("User account not found."))
        if (!PasswordSecurity.verifyPassword(currentPass, user.passwordHash)) {
          return@withContext Result.failure(Exception("Current password is incorrect."))
        }
        if (newPass.length < 6) {
          return@withContext Result.failure(Exception("New password must be at least 6 characters."))
        }
        val newHash = PasswordSecurity.hashPassword(newPass)
        val updated = user.copy(passwordHash = newHash)
        database.userDao().updateUser(updated)
        _currentUser.value = updated

        // Sync new password hash to app_config
        val userEmail = user.email.trim().lowercase()
        try {
          val cfgRes = supabaseClient.restGet("app_config?key=eq.student_user_$userEmail&select=*")
          val cfgJson = cfgRes.getOrNull()
          val studentObj = if (!cfgJson.isNullOrBlank()) {
            try {
              val arr = JSONArray(cfgJson)
              if (arr.length() > 0) JSONObject(arr.getJSONObject(0).optString("value", "{}")) else JSONObject()
            } catch (_: Exception) { JSONObject() }
          } else JSONObject()

          studentObj.put("password_hash", newHash)
          studentObj.put("email", userEmail)
          studentObj.put("updated_at", System.currentTimeMillis())

          val cfgPayload = JSONObject().apply {
            put("key", "student_user_$userEmail")
            put("value", studentObj.toString())
            put("description", "Student Account: ${user.fullName} (${user.level})")
          }
          supabaseClient.restPost("app_config", cfgPayload.toString(), upsert = true)
        } catch (e: Exception) {
          Log.w(tag, "Failed to update password hash in app_config: ${e.message}")
        }

        Result.success(true)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }

  suspend fun logout() {
    withContext(Dispatchers.IO) {
      prefs.edit().remove("logged_in_user_id").apply()
      supabaseClient.clearSession()
      _currentUser.value = null
    }
  }

  suspend fun refreshCurrentUser() {
    withContext(Dispatchers.IO) {
      val user = _currentUser.value ?: return@withContext
      val freshUser = database.userDao().getUserById(user.id)
      if (freshUser != null) {
        val updated = checkAndUpdatePremiumExpiry(freshUser)
        _currentUser.value = updated
      }
    }
  }

  private suspend fun syncProfileFromSupabase(email: String) {
    try {
      val res = supabaseClient.restGet("profiles?email=eq.$email&select=*")
      res.onSuccess { json ->
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, SupabaseProfile::class.java)
        val list = supabaseClient.moshi.adapter<List<SupabaseProfile>>(type).fromJson(json)
        val profile = list?.firstOrNull() ?: return@onSuccess
        val localUser = database.userDao().getUserByEmail(email) ?: return@onSuccess

        val role = if (profile.appRole?.equals("admin", ignoreCase = true) == true) "ADMIN" else "STUDENT"
        val updated = localUser.copy(
          fullName = profile.fullName ?: localUser.fullName,
          department = profile.department ?: localUser.department,
          faculty = profile.faculty ?: localUser.faculty,
          level = profile.level ?: localUser.level,
          role = role,
          isPremium = if (role == "ADMIN") true else localUser.isPremium
        )
        database.userDao().updateUser(updated)
        if (_currentUser.value?.id == localUser.id) {
          _currentUser.value = updated
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "Failed to sync profile from Supabase: ${e.message}")
    }
  }

  private suspend fun checkAndUpdatePremiumExpiry(user: UserEntity): UserEntity {
    if (user.isPremium && user.role != "ADMIN") {
      val expiry = user.premiumExpiry
      if (expiry != null && System.currentTimeMillis() > expiry) {
        database.userDao().updatePremiumStatus(user.id, false, null, null)
        return user.copy(isPremium = false, premiumPlan = null, premiumExpiry = null)
      }
    }
    return user
  }
}
