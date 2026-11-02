package com.example.ui.results

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.QuestionEntity
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ProfessionalBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ResultsViewModel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
  attemptId: Long,
  resultsViewModel: ResultsViewModel,
  onNavigateToDashboard: () -> Unit,
  onPracticeAgain: () -> Unit
) {
  LaunchedEffect(attemptId) {
    resultsViewModel.loadAttempt(attemptId)
  }

  val attempt by resultsViewModel.attempt.collectAsState()
  val questions by resultsViewModel.questions.collectAsState()

  BackHandler {
    onNavigateToDashboard()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Examination Results",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
          )
        },
        navigationIcon = {
          IconButton(onClick = onNavigateToDashboard) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground,
          navigationIconContentColor = MaterialTheme.colorScheme.onBackground
        )
      )
    }
  ) { innerPadding ->
    if (attempt == null) {
      Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }
    } else {
      val att = attempt!!
      val answersJson = try {
        JSONObject(att.answersJson)
      } catch (e: Exception) {
        JSONObject()
      }

      val meta = answersJson.optJSONObject("_meta")
      val isMockExam = (meta?.optBoolean("is_mock_exam", false) == true) ||
          att.examTitle.contains("Mock", ignoreCase = true) ||
          att.examTitle.contains("Assessment", ignoreCase = true) ||
          att.examTitle.contains("Continuous", ignoreCase = true) ||
          att.examTitle.contains("CBT", ignoreCase = true) ||
          att.examTitle.contains("Exam", ignoreCase = true) ||
          (meta?.optString("session_id", "")?.isNotBlank() == true) ||
          att.examId != null
      val revealMode = meta?.optString("reveal_mode", "INSTANT") ?: "INSTANT"
      val deadlineTimestamp = meta?.optLong("deadline_timestamp", 0L) ?: 0L
      val deadlineString = meta?.optString("deadline_string", "") ?: ""
      val studentName = meta?.optString("student_name", "") ?: ""
      val sessionId = meta?.optString("session_id", "Session 1") ?: "Session 1"
      val autoSubmittedReason = meta?.optString("auto_submitted_reason", "") ?: ""

      val isDeadlinePassed = deadlineTimestamp > 0L && System.currentTimeMillis() >= deadlineTimestamp
      val isScoresConfidential = isMockExam && (revealMode.equals("DEADLINE", ignoreCase = true)) && !isDeadlinePassed

      val minutesUsed = att.timeSpentSeconds / 60
      val secondsUsed = att.timeSpentSeconds % 60

      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        if (isScoresConfidential) {
          // Official Confidential Submission Receipt for Withheld Mock CBT
          item {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Box(
                  modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.12f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    Icons.Default.AssignmentTurnedIn,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(40.dp)
                  )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                  text = "Mock CBT Submission Confirmed",
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Official Departmental Examination Receipt",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                  border = BorderStroke(1.dp, BorderSubtle)
                ) {
                  Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReceiptRow("Candidate", studentName.ifBlank { "Registered Student" })
                    ReceiptRow("Course", att.courseTitle)
                    ReceiptRow("Examination", att.examTitle)
                    ReceiptRow("Session Batch", sessionId)
                    ReceiptRow("Time Spent", "%02d:%02d".format(minutesUsed, secondsUsed))
                    ReceiptRow("Status", "Securely Logged on Database")
                  }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp),
                  color = WarningAmber.copy(alpha = 0.12f),
                  border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f))
                ) {
                  Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                      Icons.Default.Lock,
                      contentDescription = null,
                      tint = Color(0xFFB45309),
                      modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = "Score & Corrections Withheld",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFB45309)
                      )
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        text = "In accordance with examination policy, individual scores are held confidential until the submission deadline${if (deadlineString.isNotBlank()) " ($deadlineString)" else ""}. Your answers are recorded in the central database and will be compiled into the official departmental PDF score sheet for the admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                    }
                  }
                }
              }
            }
          }

          item {
            Button(
              onClick = onNavigateToDashboard,
              modifier = Modifier.fillMaxWidth().height(48.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
              ),
              shape = RoundedCornerShape(10.dp)
            ) {
              Text("Return to Dashboard")
            }
          }
        } else {
          // Standard Score Hero Card for Instant Reveal / Practice / Post-Deadline
          item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = att.examTitle,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = att.courseTitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (autoSubmittedReason == "TERMINATED_APP_SWITCH") {
                  Spacer(modifier = Modifier.height(12.dp))
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ErrorRed.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Default.Lock, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(22.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Column {
                        Text("Exam Terminated by System", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ErrorRed)
                        Text("App-switch detected during live examination (Anti-Cheating Policy). Answers were automatically submitted.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface)
                      }
                    }
                  }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Circular Percentage Badge
                val scoreColor = when {
                  att.score >= 70 -> SuccessGreen
                  att.score >= 50 -> WarningAmber
                  else -> ErrorRed
                }

                Box(
                  modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(scoreColor.copy(alpha = 0.12f)),
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = "${att.score}%",
                      style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                      color = scoreColor
                    )
                    Text(
                      text = if (att.score >= 70) "DISTINCTION" else if (att.score >= 50) "PASSED" else "NEEDS REVIEW",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                      color = scoreColor
                    )
                  }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                  text = "${att.correctAnswers} / ${att.totalQuestions} Correct",
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Breakdown Row
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceAround
                ) {
                  StatItem("Correct", "${att.correctAnswers}", SuccessGreen)
                  StatItem("Incorrect", "${att.incorrectAnswers}", ErrorRed)
                  StatItem("Unanswered", "${att.unansweredQuestions}", Color(0xFF64748B))
                  StatItem("Time", "%02d:%02d".format(minutesUsed, secondsUsed), ProfessionalBlue)
                }
              }
            }
          }

          // Action Buttons
          item {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              if (isMockExam) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = Color(0xFF0F766E).copy(alpha = 0.12f),
                  border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.35f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      Icons.Default.CheckCircle,
                      contentDescription = null,
                      tint = Color(0xFF0F766E),
                      modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Official One-Attempt Policy: Your examination submission for $sessionId has been officially recorded. No re-attempts are permitted until your administrator publishes a new exam session.",
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                }

                Button(
                  onClick = onNavigateToDashboard,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("results_return_dashboard_btn"),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                  ),
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Return to Dashboard", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
              } else {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  OutlinedButton(
                    onClick = onPracticeAgain,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Practice Again")
                  }

                  Button(
                    onClick = onNavigateToDashboard,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Text("Dashboard")
                  }
                }
              }
            }
          }

          // Review Header
          item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
              Text(
                text = "Detailed Question Review & Explanations",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
              )
              Text(
                text = "Complete answer breakdown, clinical rationales, and correct options for all questions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          // List of question reviews
          if (questions.isEmpty()) {
            item {
              Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(20.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                  Spacer(modifier = Modifier.height(10.dp))
                  Text(
                    text = "Loading detailed question explanations...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          } else {
            itemsIndexed(questions) { index, question ->
              val answerInfo = if (answersJson.has(question.id.toString())) {
                answersJson.getJSONObject(question.id.toString())
              } else {
                null
              }

              val chosen = answerInfo?.optString("chosen", "") ?: ""
              val isCorrect = answerInfo?.optBoolean("isCorrect", false) ?: false

              QuestionReviewCard(
                questionNumber = index + 1,
                question = question,
                chosenOption = chosen,
                isCorrect = isCorrect
              )
            }
          }

          item {
            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      }
    }
  }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = color
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
fun QuestionReviewCard(
  questionNumber: Int,
  question: QuestionEntity,
  chosenOption: String,
  isCorrect: Boolean
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("question_review_card_$questionNumber"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(
      1.5.dp,
      if (chosenOption.isEmpty()) Color(0xFFCBD5E1) else if (isCorrect) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
          Text(
            text = "Question $questionNumber",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (chosenOption.isEmpty()) {
            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Unanswered", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
          } else if (isCorrect) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Correct (+1)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
          } else {
            Icon(Icons.Default.Cancel, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Incorrect (0)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ErrorRed)
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Radiographic Diagram or Question Image if available
      if (!question.imageUrl.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            com.example.ui.components.QuestionDiagramImage(
              imageUrl = question.imageUrl,
              contentDescription = "Question illustration",
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(6.dp)),
              contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Clinical Reference / Diagram",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Text(
        text = question.questionText,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Complete Options List with Explanatory Highlighting
      val optionsList = listOf(
        "A" to question.optionA,
        "B" to question.optionB,
        "C" to question.optionC,
        "D" to question.optionD
      )

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        optionsList.forEach { (optKey, optText) ->
          if (optText.isNotBlank()) {
            val isCandidateChoice = chosenOption.equals(optKey, ignoreCase = true)
            val isCorrectChoice = question.correctAnswer.equals(optKey, ignoreCase = true)

            val borderColor = when {
              isCorrectChoice -> SuccessGreen
              isCandidateChoice && !isCorrect -> ErrorRed
              else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }

            val bgColor = when {
              isCorrectChoice -> SuccessGreen.copy(alpha = 0.12f)
              isCandidateChoice && !isCorrect -> ErrorRed.copy(alpha = 0.10f)
              else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }

            val tagLabel = when {
              isCorrectChoice && isCandidateChoice -> "Your Choice • Correct"
              isCorrectChoice -> "Correct Answer"
              isCandidateChoice -> "Your Choice (Incorrect)"
              else -> ""
            }

            val tagColor = when {
              isCorrectChoice -> SuccessGreen
              isCandidateChoice -> ErrorRed
              else -> Color.Transparent
            }

            val tagIcon = when {
              isCorrectChoice -> Icons.Default.CheckCircle
              isCandidateChoice -> Icons.Default.Cancel
              else -> null
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = bgColor,
              border = BorderStroke(if (isCorrectChoice || isCandidateChoice) 1.5.dp else 1.dp, borderColor),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                      if (isCorrectChoice) SuccessGreen
                      else if (isCandidateChoice) ErrorRed
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = optKey,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isCorrectChoice || isCandidateChoice) Color.White else MaterialTheme.colorScheme.onSurface
                  )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = optText,
                    style = MaterialTheme.typography.bodySmall.copy(
                      fontWeight = if (isCorrectChoice || isCandidateChoice) FontWeight.SemiBold else FontWeight.Normal,
                      lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                  )

                  if (tagLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      if (tagIcon != null) {
                        Icon(tagIcon, contentDescription = null, tint = tagColor, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                      }
                      Text(
                        text = tagLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tagColor
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Answer Comparison Summary
      Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Your Answer:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = if (chosenOption.isNotEmpty()) "Option $chosenOption" else "None Selected",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (chosenOption.isEmpty()) Color(0xFF94A3B8) else if (isCorrect) SuccessGreen else ErrorRed
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text("Correct Answer:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = "Option ${question.correctAnswer}",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = SuccessGreen
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Explanation Box
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
          .padding(12.dp)
      ) {
        Column {
          Text(
            text = "Academic Explanation & Clinical Rationale:",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = question.explanation.ifBlank { "Standard radiographic clinical rationale and syllabus guidelines apply." },
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
fun ReceiptRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}
