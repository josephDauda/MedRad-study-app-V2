package com.example.data.local

import com.example.data.local.entity.CourseEntity
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.FacultyEntity
import com.example.data.local.entity.TopicEntity
import com.example.data.local.entity.QuestionEntity
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
        for (dm in existingMaterials) {
          if (dm.title.contains("demo", ignoreCase = true)) {
            materialDao.deleteMaterial(dm)
          }
        }
      } catch (_: Exception) {}

      try {
        val existingExams = examDao.getAllExamsList()
        for (de in existingExams) {
          if (de.title.contains("demo", ignoreCase = true)) {
            examDao.deleteExam(de)
          }
        }
      } catch (_: Exception) {}

      try {
        val existingLibraries = libraryDao.getAllLibrariesList()
        for (dl in existingLibraries) {
          if (dl.name.contains("demo", ignoreCase = true)) {
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

      // Ensure foundational courses exist for 100L, 300L, 400L, 500L Radiography students and admins
      val coreCourses = listOf(
        CourseEntity(
          departmentId = deptId,
          code = "RAD 101",
          title = "INTRODUCTION TO MEDICAL RADIOGRAPHY",
          level = "100 Level",
          semester = "First Semester",
          description = "Introduction to Medical Radiography, radiation history and healthcare delivery."
        ),
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

      // Ensure foundational radiographic questions exist for each academic level (100L, 300L, 400L, 500L)
      val allCoreCourses = listOf("RAD 101", "RAD 301", "RAD 401", "RAD 501")
      for (cCode in allCoreCourses) {
        val course = academicDao.getCourseByCode(cCode)
        if (course != null) {
          val count = questionDao.getQuestionsCountByCourse(course.id)
          if (count == 0) {
            val qList = when (cCode) {
              "RAD 101" -> listOf(
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "Who discovered X-rays on November 8, 1895?",
                  optionA = "Wilhelm Conrad Röntgen",
                  optionB = "Marie Curie",
                  optionC = "Thomas Edison",
                  optionD = "Henri Becquerel",
                  correctAnswer = "A",
                  explanation = "Wilhelm Conrad Röntgen discovered X-rays in Würzburg, Germany on November 8, 1895.",
                  assessmentTitle = "RAD 101 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "What does the radiation protection principle ALARA stand for?",
                  optionA = "As Low As Reasonably Achievable",
                  optionB = "All Levels Allow Radiation Action",
                  optionC = "Always Limit Ambient Radiation Amount",
                  optionD = "As Long As Radiologists Agree",
                  correctAnswer = "A",
                  explanation = "ALARA stands for As Low As Reasonably Achievable in clinical radiation safety.",
                  assessmentTitle = "RAD 101 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "Which modality employs radiofrequency pulses and strong magnetic fields without ionizing radiation?",
                  optionA = "Magnetic Resonance Imaging (MRI)",
                  optionB = "Computed Tomography (CT)",
                  optionC = "Fluoroscopy",
                  optionD = "Mammography",
                  correctAnswer = "A",
                  explanation = "MRI uses non-ionizing magnetic fields and radiofrequency pulses.",
                  assessmentTitle = "RAD 101 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "What is the primary function of the collimator in radiographic equipment?",
                  optionA = "To restrict the beam dimensions and minimize patient exposure",
                  optionB = "To amplify tube potential (kVp)",
                  optionC = "To rotate the anode disc",
                  optionD = "To convert alternating current to direct current",
                  correctAnswer = "A",
                  explanation = "Collimation restricts beam geometry to the region of interest, reducing scatter and dose.",
                  assessmentTitle = "RAD 101 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "In the standard anatomical position, how are the palms oriented?",
                  optionA = "Facing anteriorly (forward)",
                  optionB = "Facing posteriorly (backward)",
                  optionC = "Facing medially toward the trunk",
                  optionD = "Facing laterally away from the body",
                  correctAnswer = "A",
                  explanation = "In the anatomical position, the body is erect with palms facing forward.",
                  assessmentTitle = "RAD 101 Question Bank"
                )
              )
              "RAD 301" -> listOf(
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "For a true lateral projection of the knee, how many degrees should the central ray be angled cephalad?",
                  optionA = "5 to 7 degrees",
                  optionB = "15 to 20 degrees",
                  optionC = "0 degrees (perpendicular)",
                  optionD = "25 to 30 degrees",
                  correctAnswer = "A",
                  explanation = "A 5-7 degree cephalad angle accounts for the adductor tubercle and prevents joint space obscuration.",
                  assessmentTitle = "RAD 301 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "Which projection of the chest best demonstrates small pleural effusion when a patient cannot stand?",
                  optionA = "Lateral decubitus with affected side down",
                  optionB = "Anteroposterior supine",
                  optionC = "Lordotic view",
                  optionD = "Lateral decubitus with affected side up",
                  correctAnswer = "A",
                  explanation = "Fluid gravitates to the dependent pleural space with the affected side positioned down.",
                  assessmentTitle = "RAD 301 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "What standard SID is recommended for PA chest radiography to minimize cardiac magnification?",
                  optionA = "180 cm (72 inches)",
                  optionB = "100 cm (40 inches)",
                  optionC = "120 cm (48 inches)",
                  optionD = "150 cm (60 inches)",
                  correctAnswer = "A",
                  explanation = "A 72-inch (180 cm) SID minimizes cardiac magnification and geometric unsharpness.",
                  assessmentTitle = "RAD 301 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "Which baseline is perpendicular to the image receptor in the PA Caldwell skull view?",
                  optionA = "Orbitomeatal line (OML)",
                  optionB = "Infraorbitomeatal line (IOML)",
                  optionC = "Glabellomeatal line (GML)",
                  optionD = "Acanthiomeatal line (AML)",
                  correctAnswer = "A",
                  explanation = "The OML is perpendicular to the IR with a 15-degree caudal CR angle in Caldwell projection.",
                  assessmentTitle = "RAD 301 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "The iliac crest corresponds to which vertebral landmark?",
                  optionA = "L4-L5 intervertebral space",
                  optionB = "L1-L2 level",
                  optionC = "T12 level",
                  optionD = "S1-S2 level",
                  correctAnswer = "A",
                  explanation = "The highest point of the iliac crest lies at the level of L4-L5.",
                  assessmentTitle = "RAD 301 Question Bank"
                )
              )
              "RAD 401" -> listOf(
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "What is the annual occupational effective dose limit recommended by the ICRP for radiation workers?",
                  optionA = "20 mSv per year averaged over 5 years (max 50 mSv in any single year)",
                  optionB = "100 mSv per year",
                  optionC = "5 mSv per year",
                  optionD = "1 mSv per year",
                  correctAnswer = "A",
                  explanation = "ICRP Publication 103 sets 20 mSv/year averaged over 5 years for occupational workers.",
                  assessmentTitle = "RAD 401 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "Which SI unit expresses radiation absorbed dose?",
                  optionA = "Gray (Gy)",
                  optionB = "Sievert (Sv)",
                  optionC = "Roentgen (R)",
                  optionD = "Curie (Ci)",
                  correctAnswer = "A",
                  explanation = "The Gray (Gy, 1 J/kg) is the SI unit of absorbed dose; Sievert is equivalent/effective dose.",
                  assessmentTitle = "RAD 401 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "The inverse square law dictates that doubling the distance from an X-ray source changes radiation intensity to:",
                  optionA = "One-fourth (1/4) of original intensity",
                  optionB = "One-half (1/2) of original intensity",
                  optionC = "Double (2x) the original intensity",
                  optionD = "Unchanged",
                  correctAnswer = "A",
                  explanation = "Intensity is inversely proportional to the square of the distance (I2 = I1 * (d1/d2)^2).",
                  assessmentTitle = "RAD 401 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "Which radiation-induced bio-effect exhibits no safe threshold and has probability proportional to dose?",
                  optionA = "Stochastic effect (e.g., radiation carcinogenesis)",
                  optionB = "Deterministic effect (tissue reaction)",
                  optionC = "Acute radiation sickness",
                  optionD = "Epilation",
                  correctAnswer = "A",
                  explanation = "Stochastic effects have no threshold; probability increases linearly with dose without safe cutoff.",
                  assessmentTitle = "RAD 401 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "What standard lead equivalence is required for protective lead aprons under general diagnostic conditions?",
                  optionA = "0.25 mm to 0.5 mm Pb equivalent",
                  optionB = "0.05 mm Pb equivalent",
                  optionC = "2.0 mm Pb equivalent",
                  optionD = "1.5 mm Pb equivalent",
                  correctAnswer = "A",
                  explanation = "Diagnostic protective lead aprons require 0.25 to 0.50 mm lead equivalence.",
                  assessmentTitle = "RAD 401 Question Bank"
                )
              )
              "RAD 501" -> listOf(
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "By definition in Computed Tomography, what is the Hounsfield Unit (HU) of pure water?",
                  optionA = "0 HU",
                  optionB = "-1000 HU",
                  optionC = "+1000 HU",
                  optionD = "+50 HU",
                  correctAnswer = "A",
                  explanation = "Water is calibrated to 0 HU; air is -1000 HU and dense cortical bone is ~+1000 to +3000 HU.",
                  assessmentTitle = "RAD 501 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "In Magnetic Resonance Imaging (MRI), T1 relaxation time corresponds to which process?",
                  optionA = "Spin-lattice (longitudinal) magnetization recovery",
                  optionB = "Spin-spin (transverse) magnetization decay",
                  optionC = "Gradient echo refocusing",
                  optionD = "Inversion recovery nulling",
                  correctAnswer = "A",
                  explanation = "T1 measures longitudinal magnetization recovery along the z-axis (spin-lattice relaxation).",
                  assessmentTitle = "RAD 501 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "Which contrast medium is standard for intravenous paramagnetic contrast in MRI examinations?",
                  optionA = "Gadolinium-based contrast agent (GBCA)",
                  optionB = "High-osmolar iodinated monomer",
                  optionC = "Barium sulfate suspension",
                  optionD = "Iohexol",
                  correctAnswer = "A",
                  explanation = "Gadolinium chelates enhance T1 shortening in vascularized and disrupted blood-brain barrier tissues.",
                  assessmentTitle = "RAD 501 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "In helical CT, what is the pitch if the table travels 15 mm per 360-degree rotation with a 10 mm beam collimation?",
                  optionA = "1.5",
                  optionB = "0.67",
                  optionC = "1.0",
                  optionD = "2.0",
                  correctAnswer = "A",
                  explanation = "Pitch = Table movement per rotation / Beam collimation = 15 / 10 = 1.5.",
                  assessmentTitle = "RAD 501 Question Bank"
                ),
                QuestionEntity(
                  courseId = course.id,
                  topicId = 1L,
                  questionText = "Which CT artifact results from the preferential attenuation of lower-energy photons through dense structures?",
                  optionA = "Beam hardening artifact",
                  optionB = "Ring artifact",
                  optionC = "Aliasing artifact",
                  optionD = "Partial volume effect",
                  correctAnswer = "A",
                  explanation = "Beam hardening causes cupping or dark streaking artifacts between dense bony structures.",
                  assessmentTitle = "RAD 501 Question Bank"
                )
              )
              else -> emptyList()
            }
            for (q in qList) {
              questionDao.insertQuestion(q)
            }
          }
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

      // 3. Purge all demo/test accounts from local Room database (keeping only real Supabase accounts & admin@medrad.edu.ng)
      try {
        val existingUsers = userDao.getAllUsersList()
        for (u in existingUsers) {
          val email = u.email.trim().lowercase()
          if (email == "student@medrad.edu.ng" ||
              email.startsWith("test_radiographer") ||
              email.startsWith("test_robolectric") ||
              email == "halima.abubakar@medrad.edu.ng" ||
              (u.fullName.contains("Fatima Bello", ignoreCase = true) && email.startsWith("test_"))) {
            userDao.deleteUser(u)
          } else if (email == "princejoe.prj@gmail.com") {
            userDao.updateUser(
              u.copy(
                role = "ADMIN",
                adminRole = "SECONDARY",
                assignedCourses = "200 Level",
                permissions = "materials,questions,libraries,announcements,exams"
              )
            )
          } else if (email == "jd267904@gmail.com") {
            userDao.updateUser(
              u.copy(
                role = "ADMIN",
                adminRole = "SECONDARY",
                assignedCourses = "100 Level",
                permissions = "materials,questions,libraries,announcements,exams"
              )
            )
          } else if (u.role.equals("ADMIN", ignoreCase = true) && email != "admin@medrad.edu.ng" && u.adminRole == "PRIMARY") {
            userDao.updateUser(u.copy(adminRole = "SECONDARY"))
          }
        }
      } catch (_: Exception) {}

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
