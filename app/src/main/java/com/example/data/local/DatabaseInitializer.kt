package com.example.data.local

import com.example.data.local.entity.CourseEntity
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.FacultyEntity
import com.example.data.local.entity.TopicEntity
import com.example.data.local.entity.UniversityEntity
import com.example.data.local.entity.UserEntity
import com.example.data.util.PasswordSecurity
import com.example.data.util.LevelClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {

  suspend fun populateInitialData(database: AppDatabase) {
    withContext(Dispatchers.IO) {
      val academicDao = database.academicDao()
      val questionDao = database.questionDao()

      val examDao = database.examDao()
      val materialDao = database.studyMaterialDao()
      val libraryDao = database.courseLibraryDao()

      // 1. Purge only demo-tagged items as requested without touching legitimate courses
      try {
        val existingQuestions = questionDao.getAllQuestionsList()
        for (dq in existingQuestions) {
          if (dq.isDemo || dq.questionText.startsWith("[Demo Question]") || dq.questionText.contains("demo", ignoreCase = true)) {
            questionDao.deleteQuestion(dq)
          }
        }
      } catch (_: Exception) {}

      try {
        val existingMaterials = materialDao.getAllMaterialsList()
        val anaCourse = academicDao.getCoursesList().firstOrNull { it.code.trim().replace(" ", "").uppercase() == "ANA201" }
        val anaId = anaCourse?.id
        for (dm in existingMaterials) {
          if (dm.courseId != anaId || dm.title.contains("demo", ignoreCase = true)) {
            materialDao.deleteMaterial(dm)
          }
        }
      } catch (_: Exception) {}

      try {
        val existingExams = examDao.getAllExamsList()
        val anaCourse = academicDao.getCoursesList().firstOrNull { it.code.trim().replace(" ", "").uppercase() == "ANA201" }
        val anaId = anaCourse?.id
        for (de in existingExams) {
          if (de.courseId != anaId || de.title.contains("demo", ignoreCase = true)) {
            examDao.deleteExam(de)
          }
        }
      } catch (_: Exception) {}

      try {
        val existingLibraries = libraryDao.getAllLibrariesList()
        val anaCourse = academicDao.getCoursesList().firstOrNull { it.code.trim().replace(" ", "").uppercase() == "ANA201" }
        val anaId = anaCourse?.id
        for (dl in existingLibraries) {
          if (dl.courseId != anaId || dl.name.contains("demo", ignoreCase = true)) {
            libraryDao.deleteLibrary(dl)
          }
        }
      } catch (_: Exception) {}

      // 2. Ensure basic academic organizational hierarchy exists for University of Maiduguri
      val universities = academicDao.getUniversitiesList()
      var uniId: Long = 1
      if (universities.isEmpty()) {
        uniId = academicDao.insertUniversity(
          UniversityEntity(
            name = "University of Maiduguri",
            shortName = "UNIMAID",
            status = "Active"
          )
        )
      } else {
        uniId = universities.first().id
      }

      val faculties = academicDao.getFacultiesListByUniversity(uniId)
      var facultyId: Long = 1
      if (faculties.isEmpty()) {
        facultyId = academicDao.insertFaculty(
          FacultyEntity(
            id = 1,
            universityId = uniId,
            name = "College of Medical Sciences"
          )
        )
      } else {
        facultyId = faculties.first().id
      }

      val departments = academicDao.getDepartmentsListByFaculty(facultyId)
      var deptId: Long = 1
      if (departments.isEmpty()) {
        deptId = academicDao.insertDepartment(
          DepartmentEntity(
            id = 1,
            facultyId = facultyId,
            name = "Medical Radiography"
          )
        )
      } else {
        deptId = departments.first().id
      }

      // 3. Populate strictly ANA 201: GROSS ANATOMY OF UPPER AND LOWER LIMBS mapped to authoritative Supabase remote ID
      val authoritativeCourse = CourseEntity(
        id = 1L,
        departmentId = deptId,
        code = "ANA 201",
        title = "GROSS ANATOMY OF UPPER AND LOWER LIMBS",
        level = "200 Level",
        semester = "First Semester",
        description = "GROSS ANATOMY OF UPPER AND LOWER LIMBS",
        remoteId = "5b1a35b9-ffc6-4427-8d72-0006d1ba6704"
      )

      val existingByRemote = academicDao.getCourseByRemoteId(authoritativeCourse.remoteId!!)
      val existingByCode = academicDao.getCourseByCode(authoritativeCourse.code)
      if (existingByRemote == null && existingByCode == null) {
        academicDao.insertCourse(authoritativeCourse)
      } else {
        val targetId = existingByRemote?.id ?: existingByCode?.id ?: 1L
        academicDao.insertCourse(authoritativeCourse.copy(id = targetId))
      }

      // Ensure foundational courses exist for 300L, 400L, 500L Radiography students and admins
      val coreCourses = listOf(
        CourseEntity(
          departmentId = deptId,
          code = "RAD 301",
          title = "RADIOGRAPHIC TECHNIQUES & PROCEDURES",
          level = "300 Level",
          semester = "First Semester",
          description = "General and departmental radiographic positioning and techniques."
        ),
        CourseEntity(
          departmentId = deptId,
          code = "RAD 401",
          title = "RADIATION PROTECTION, DOSIMETRY & RADIOBIOLOGY",
          level = "400 Level",
          semester = "First Semester",
          description = "Principles of radiation safety, radiation physics, and biological effects."
        ),
        CourseEntity(
          departmentId = deptId,
          code = "RAD 501",
          title = "ADVANCED IMAGING, CT/MRI & CLINICAL RADIOGRAPHY",
          level = "500 Level",
          semester = "First Semester",
          description = "Cross-sectional imaging, computed tomography, magnetic resonance, and clinical interventions."
        )
      )
      for (core in coreCourses) {
        if (academicDao.getCourseByCode(core.code) == null) {
          academicDao.insertCourse(core)
        }
      }

      // 2. Ensure Default Authenticated Admin & Student Accounts exist if not present
      val userDao = database.userDao()
      val adminEmail = "admin@medrad.edu.ng"
      if (userDao.getUserByEmail(adminEmail) == null) {
        userDao.insertUser(
          UserEntity(
            fullName = "Chief Radiographer / Admin",
            email = adminEmail,
            phoneNumber = "+2348000000001",
            passwordHash = PasswordSecurity.hashPassword("MedRadAdmin2026!"),
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
        )
      }

      val studentEmail = "student@medrad.edu.ng"
      if (userDao.getUserByEmail(studentEmail) == null) {
        userDao.insertUser(
          UserEntity(
            fullName = "Khadija Usman",
            email = studentEmail,
            phoneNumber = "+2348000000002",
            passwordHash = PasswordSecurity.hashPassword("Student123!"),
            role = "STUDENT",
            university = "University of Maiduguri",
            faculty = "College of Medical Sciences",
            department = "Medical Radiography",
            level = "200 Level",
            semester = "First Semester",
            isPremium = false
          )
        )
      }

      // 4. Meticulously preserve student academic levels:
      // Normalize level string formatting without altering user-selected levels (100L - 600L).
      // Never overwrite a student's chosen level on startup!
      try {
        val existingStudents = userDao.getAllUsersList()
        for (st in existingStudents) {
          if (st.role == "STUDENT") {
            if (st.level.isBlank()) {
              userDao.updateUser(st.copy(level = "200 Level"))
            } else {
              val normalized = LevelClassifier.normalizeLevel(st.level)
              if (normalized != st.level) {
                userDao.updateUser(st.copy(level = normalized))
              }
            }
          }
        }
      } catch (_: Exception) {}
    }
  }
}
