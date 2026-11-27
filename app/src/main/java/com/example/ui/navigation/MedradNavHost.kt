package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.MedradAppContainer
import com.example.data.local.entity.ExamEntity
import com.example.data.local.entity.UserEntity
import com.example.ui.about.AboutAppScreen
import com.example.ui.about.TermsAndPrivacyScreen
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.RegisterScreen
import com.example.ui.components.MedicalRadiographyEmblem
import com.example.ui.cbt.CbtScreen
import com.example.ui.courses.CoursesScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.materials.StudyMaterialsScreen
import com.example.ui.progress.ProgressScreen
import com.example.ui.notifications.NotificationsScreen
import androidx.compose.material.icons.filled.Notifications
import com.example.ui.results.ResultsScreen
import com.example.ui.subscription.SubscriptionScreen
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.ProfessionalBlue
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.CbtViewModel
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.ResultsViewModel
import com.example.ui.viewmodel.SubscriptionViewModel
import com.example.ui.scholars.DiscoverScholarsScreen
import com.example.ui.feeds.MedicalFeedsScreen
import com.example.ui.tutors.TutorDirectoryScreen
import com.example.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch

enum class ScreenRoute {
  WELCOME,
  LOGIN,
  REGISTER,
  DASHBOARD,
  COURSES,
  MATERIALS,
  PROGRESS,
  SUBSCRIPTION,
  ADMIN,
  CBT_SESSION,
  RESULTS,
  ABOUT,
  TERMS,
  NOTIFICATIONS,
  SCHOLARS,
  FEEDS,
  TUTORS
}

@Composable
fun MedradNavHost(
  container: MedradAppContainer
) {
  val authViewModel = remember { AuthViewModel(container.authRepository) }
  val dashboardViewModel = remember {
    DashboardViewModel(
      container.cbtRepository,
      container.academicRepository,
      container.subscriptionRepository,
      container.cloudSyncManager,
      container.notificationRepository
    )
  }
  val cbtViewModel = remember { CbtViewModel(container.cbtRepository, container.academicRepository, container.cloudSyncManager) }
  val resultsViewModel = remember { ResultsViewModel(container.cbtRepository, container.academicRepository) }
  val subscriptionViewModel = remember { SubscriptionViewModel(container.subscriptionRepository, container.authRepository) }
  val adminViewModel = remember { AdminViewModel(container.adminRepository, container.academicRepository, container.cloudSyncManager) }

  val currentUser by authViewModel.currentUser.collectAsState()
  val isSessionChecked by authViewModel.isSessionChecked.collectAsState()

  var currentRoute by rememberSaveable {
    mutableStateOf(if (container.authRepository.hasSavedSession()) ScreenRoute.DASHBOARD else ScreenRoute.WELCOME)
  }
  var currentAttemptId by rememberSaveable { mutableLongStateOf(0L) }
  val backStack = rememberSaveable(
    saver = Saver(
      save = { it.map { route -> route.name } },
      restore = { saved ->
        mutableStateListOf<ScreenRoute>().apply {
          addAll(saved.mapNotNull { name -> runCatching { ScreenRoute.valueOf(name) }.getOrNull() })
        }
      }
    )
  ) { mutableStateListOf<ScreenRoute>() }
  val focusManager = LocalFocusManager.current

  fun navigateTo(route: ScreenRoute) {
    if (route == currentRoute) return
    focusManager.clearFocus(force = true)
    if (route == ScreenRoute.DASHBOARD) {
      backStack.clear()
      currentRoute = ScreenRoute.DASHBOARD
    } else {
      if (backStack.isEmpty()) {
        backStack.add(currentRoute)
      } else {
        backStack.removeAll { it == route }
        backStack.add(currentRoute)
      }
      currentRoute = route
    }
  }

  fun popBackStack() {
    focusManager.clearFocus(force = true)
    if (backStack.isNotEmpty()) {
      currentRoute = backStack.removeAt(backStack.size - 1)
    } else if (currentUser != null && currentRoute != ScreenRoute.DASHBOARD) {
      currentRoute = ScreenRoute.DASHBOARD
    } else if (currentUser == null && currentRoute != ScreenRoute.WELCOME) {
      currentRoute = ScreenRoute.WELCOME
    }
  }

  // Intercept Android System Back when not at root/home destination
  BackHandler(
    enabled = (currentUser != null && currentRoute != ScreenRoute.DASHBOARD) ||
              (currentUser == null && currentRoute != ScreenRoute.WELCOME)
  ) {
    popBackStack()
  }

  // Check auth session safely in side-effect
  LaunchedEffect(currentUser, isSessionChecked, currentRoute) {
    if (!isSessionChecked) return@LaunchedEffect
    if (currentUser != null && (currentRoute == ScreenRoute.WELCOME || currentRoute == ScreenRoute.LOGIN || currentRoute == ScreenRoute.REGISTER)) {
      backStack.clear()
      currentRoute = ScreenRoute.DASHBOARD
    } else if (currentUser == null && currentRoute != ScreenRoute.LOGIN && currentRoute != ScreenRoute.REGISTER && currentRoute != ScreenRoute.ABOUT && currentRoute != ScreenRoute.TERMS && currentRoute != ScreenRoute.WELCOME) {
      backStack.clear()
      currentRoute = ScreenRoute.WELCOME
    }
  }

  // Graceful session restoration state: prevents flickering or premature redirect to Welcome on cold restart
  if (!isSessionChecked && container.authRepository.hasSavedSession()) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          MedicalRadiographyEmblem(
            modifier = Modifier.size(72.dp),
            showBorder = true
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "MedRad Study App",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Restoring authenticated session...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(18.dp))
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.5.dp
          )
        }
      }
    }
    return
  }

  AnimatedContent(
    targetState = currentRoute,
    transitionSpec = {
      (fadeIn(animationSpec = tween(280)) + slideInHorizontally(animationSpec = tween(280)) { fullWidth ->
        if (targetState.ordinal >= initialState.ordinal) fullWidth / 4 else -fullWidth / 4
      }) togetherWith (
        fadeOut(animationSpec = tween(180)) + slideOutHorizontally(animationSpec = tween(180)) { fullWidth ->
          if (targetState.ordinal >= initialState.ordinal) -fullWidth / 4 else fullWidth / 4
        }
      )
    },
    label = "GlobalRouteTransition"
  ) { activeRoute ->
    when (activeRoute) {
      ScreenRoute.WELCOME -> {
        WelcomeScreen(
          onNavigateToRegister = { navigateTo(ScreenRoute.REGISTER) },
          onNavigateToLogin = { navigateTo(ScreenRoute.LOGIN) },
          onNavigateToAbout = { navigateTo(ScreenRoute.ABOUT) }
        )
      }

      ScreenRoute.LOGIN -> {
        LoginScreen(
          authViewModel = authViewModel,
          onNavigateToRegister = { navigateTo(ScreenRoute.REGISTER) },
          onLoginSuccess = {
            backStack.clear()
            currentRoute = ScreenRoute.DASHBOARD
          },
          onNavigateBack = { popBackStack() }
        )
      }

      ScreenRoute.REGISTER -> {
        RegisterScreen(
          authViewModel = authViewModel,
          onNavigateToLogin = { navigateTo(ScreenRoute.LOGIN) },
          onRegisterSuccess = {
            backStack.clear()
            currentRoute = ScreenRoute.DASHBOARD
          },
          onNavigateBack = { popBackStack() }
        )
      }

      ScreenRoute.ABOUT -> {
        AboutAppScreen(
          cloudSyncManager = container.cloudSyncManager,
          onNavigateBack = { popBackStack() },
          onNavigateToTerms = { navigateTo(ScreenRoute.TERMS) }
        )
      }

      ScreenRoute.TERMS -> {
        TermsAndPrivacyScreen(
          onNavigateBack = { popBackStack() }
        )
      }

      ScreenRoute.CBT_SESSION -> {
        val user = currentUser
        if (user != null) {
          CbtScreen(
            user = user,
            cbtViewModel = cbtViewModel,
            onExamSubmitted = { attemptId ->
              currentAttemptId = attemptId
              navigateTo(ScreenRoute.RESULTS)
            },
            onNavigateBack = { popBackStack() },
            onNavigateToSubscription = { navigateTo(ScreenRoute.SUBSCRIPTION) }
          )
        }
      }

      ScreenRoute.RESULTS -> {
        ResultsScreen(
          attemptId = currentAttemptId,
          resultsViewModel = resultsViewModel,
          onNavigateToDashboard = { navigateTo(ScreenRoute.DASHBOARD) },
          onPracticeAgain = {
            val user = currentUser
            if (user != null) {
              cbtViewModel.startPractice(1L, user)
              navigateTo(ScreenRoute.CBT_SESSION)
            }
          }
        )
      }

      ScreenRoute.ADMIN -> {
        val user = currentUser
        if (user != null && user.role == "ADMIN") {
          AdminDashboardScreen(
            user = user,
            adminViewModel = adminViewModel,
            onNavigateBack = { popBackStack() }
          )
        } else if (isSessionChecked) {
          navigateTo(ScreenRoute.DASHBOARD)
        }
      }

      else -> {
        // Main authenticated app frame with modern bottom pill bar
        val user = currentUser
        if (user != null) {
          MainAuthenticatedScaffold(
            user = user,
            userProfilePreferences = container.userProfilePreferences,
            notificationRepository = container.notificationRepository,
            currentRoute = activeRoute,
            onNavigate = { route -> navigateTo(route) },
            onLogout = {
              authViewModel.logout()
              backStack.clear()
              currentRoute = ScreenRoute.WELCOME
            },
            content = {
              when (activeRoute) {
                ScreenRoute.DASHBOARD -> DashboardScreen(
                  user = user,
                  dashboardViewModel = dashboardViewModel,
                  onStartPractice = { courseId ->
                    cbtViewModel.startPractice(courseId, user)
                    navigateTo(ScreenRoute.CBT_SESSION)
                  },
                  onStartPracticeAdvanced = { courseId, count, title, code, qIds ->
                    cbtViewModel.startPractice(courseId, user, count, title, code, qIds)
                    navigateTo(ScreenRoute.CBT_SESSION)
                  },
                  onStartExam = { exam ->
                    cbtViewModel.startMockExam(exam, user)
                    navigateTo(ScreenRoute.CBT_SESSION)
                  },
                  onNavigateToMaterials = { navigateTo(ScreenRoute.MATERIALS) },
                  onNavigateToSubscription = { navigateTo(ScreenRoute.SUBSCRIPTION) },
                  onNavigateToResults = { attemptId ->
                    currentAttemptId = attemptId
                    navigateTo(ScreenRoute.RESULTS)
                  },
                  onNavigateToCourses = { navigateTo(ScreenRoute.COURSES) },
                  onNavigateToNotifications = { navigateTo(ScreenRoute.NOTIFICATIONS) }
                )
                ScreenRoute.COURSES -> CoursesScreen(
                  user = user,
                  academicRepository = container.academicRepository,
                  cloudSyncManager = container.cloudSyncManager,
                  onNavigateBack = { popBackStack() },
                  onStartCoursePractice = { courseId, code, title, fetchAllAvailable ->
                    cbtViewModel.startPractice(
                      courseId = courseId,
                      user = user,
                      customTitle = title,
                      customCode = code,
                      fetchAllAvailable = fetchAllAvailable
                    )
                    navigateTo(ScreenRoute.CBT_SESSION)
                  }
                )
                ScreenRoute.MATERIALS -> StudyMaterialsScreen(
                  user = user,
                  academicRepository = container.academicRepository,
                  cloudSyncManager = container.cloudSyncManager,
                  onNavigateBack = { popBackStack() },
                  onNavigateToSubscription = { navigateTo(ScreenRoute.SUBSCRIPTION) }
                )
                ScreenRoute.PROGRESS -> ProgressScreen(
                  user = user,
                  cbtRepository = container.cbtRepository,
                  authViewModel = authViewModel,
                  userProfilePreferences = container.userProfilePreferences,
                  cloudSyncManager = container.cloudSyncManager,
                  onNavigateBack = { popBackStack() },
                  onPracticeCourse = { courseId ->
                    cbtViewModel.startPractice(courseId, user)
                    navigateTo(ScreenRoute.CBT_SESSION)
                  },
                  onNavigateToResults = { attemptId ->
                    currentAttemptId = attemptId
                    navigateTo(ScreenRoute.RESULTS)
                  },
                  onNavigateToAbout = { navigateTo(ScreenRoute.ABOUT) },
                  onNavigateToAdmin = { navigateTo(ScreenRoute.ADMIN) }
                )
                ScreenRoute.SUBSCRIPTION -> SubscriptionScreen(
                  user = user,
                  subscriptionViewModel = subscriptionViewModel,
                  onNavigateBack = { popBackStack() }
                )
                ScreenRoute.SCHOLARS -> DiscoverScholarsScreen(container.cloudSyncManager.supabaseClient)
                ScreenRoute.FEEDS -> MedicalFeedsScreen(container.cloudSyncManager.supabaseClient)
                ScreenRoute.TUTORS -> TutorDirectoryScreen(container.cloudSyncManager.supabaseClient)
                ScreenRoute.NOTIFICATIONS -> NotificationsScreen(
                  user = user,
                  notificationRepository = container.notificationRepository,
                  onNavigateBack = { popBackStack() },
                  onNavigateToCbt = {
                    navigateTo(ScreenRoute.DASHBOARD)
                  }
                )
                else -> {}
              }
            }
          )
        } else if (!isSessionChecked) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = MedicalTeal)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAuthenticatedScaffold(
  user: UserEntity,
  userProfilePreferences: com.example.data.repository.UserProfilePreferences,
  notificationRepository: com.example.data.repository.NotificationRepository? = null,
  currentRoute: ScreenRoute,
  onNavigate: (ScreenRoute) -> Unit,
  onLogout: () -> Unit,
  content: @Composable () -> Unit
) {
  val profilePhotoUri by userProfilePreferences.profilePhotoUri.collectAsState()
  val unreadNotifications by (notificationRepository?.getUnreadCountFlow(user) ?: kotlinx.coroutines.flow.flowOf(0)).collectAsState(initial = 0)
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val navItems = buildList {
    add(Triple(ScreenRoute.DASHBOARD, Icons.Default.Dashboard, "Home"))
    add(Triple(ScreenRoute.COURSES, Icons.Default.School, "Courses"))
    add(Triple(ScreenRoute.SCHOLARS, Icons.Default.Search, "Discover Scholars"))
    add(Triple(ScreenRoute.FEEDS, Icons.Default.Description, "Medical Feeds"))
    add(Triple(ScreenRoute.TUTORS, Icons.Default.Assignment, "Tutor Directory"))
    add(Triple(ScreenRoute.MATERIALS, Icons.Default.MenuBook, "Study Materials"))
    add(Triple(ScreenRoute.PROGRESS, Icons.Default.Person, "Profile"))
    add(Triple(ScreenRoute.SUBSCRIPTION, Icons.Default.WorkspacePremium, "Premium"))
    add(Triple(ScreenRoute.NOTIFICATIONS, Icons.Default.Notifications, "Notifications"))
    if (user.role == "ADMIN") add(Triple(ScreenRoute.ADMIN, Icons.Default.AdminPanelSettings, "Admin Portal"))
    add(Triple(ScreenRoute.ABOUT, Icons.Outlined.Info, "About MedRad"))
  }
  val selectedTitle = navItems.firstOrNull { it.first == currentRoute }?.third ?: "MedRad Study"

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val isTablet = maxWidth >= 600.dp
    if (isTablet) {
      Row(Modifier.fillMaxSize()) {
        NavigationRail(
          containerColor = MaterialTheme.colorScheme.surface,
          header = {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(vertical = 14.dp).size(46.dp)) {
              Box(contentAlignment = Alignment.Center) { Image(painterResource(R.drawable.medrad_official_logo), "MedRad logo", modifier = Modifier.size(40.dp).clip(CircleShape)) }
            }
          }
        ) {
          navItems.filter { it.first != ScreenRoute.ABOUT }.forEach { item ->
            NavigationRailItem(
              selected = currentRoute == item.first,
              onClick = { onNavigate(item.first) },
              icon = { Icon(item.second, contentDescription = item.third) },
              label = { Text(item.third, fontSize = 10.sp) },
              colors = railItemColors()
            )
          }
          Spacer(Modifier.weight(1f))
          IconButton(onClick = onLogout, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", tint = MaterialTheme.colorScheme.error)
          }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
          Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(selectedTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
              IconButton(onClick = { onNavigate(ScreenRoute.NOTIFICATIONS) }) {
                Box { Icon(Icons.Default.Notifications, "Notifications"); if (unreadNotifications > 0) Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(Color(0xFFEF4444), CircleShape)) }
              }
            }
          }
          Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
      }
    } else {
      ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
              Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                  Box(contentAlignment = Alignment.Center) { Image(painterResource(R.drawable.medrad_official_logo), "MedRad logo", modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))) }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                  Text("MedRad Study", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                  Text("Study community", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(1.dp, WarningAmber), modifier = Modifier.size(42.dp)) {
                  if (profilePhotoUri != null) AsyncImage(model = profilePhotoUri, contentDescription = "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                  else Box(contentAlignment = Alignment.Center) {
                    Text(user.fullName.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("").ifBlank { "MR" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                  }
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                  Text(user.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                  Text(user.level, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                navItems.forEach { item ->
                  NavigationDrawerItem(
                    label = { Text(item.third) },
                    icon = { Icon(item.second, contentDescription = item.third) },
                    selected = currentRoute == item.first,
                    onClick = {
                      scope.launch { drawerState.close() }
                      onNavigate(item.first)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                      selectedContainerColor = MedicalTeal.copy(alpha = .12f),
                      selectedTextColor = MedicalTeal,
                      selectedIconColor = MedicalTeal,
                      unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                      unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  )
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              NavigationDrawerItem(
                label = { Text("Sign out") },
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", tint = MaterialTheme.colorScheme.error) },
                selected = false,
                onClick = onLogout,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(vertical = 8.dp),
                colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = MaterialTheme.colorScheme.error)
              )
            }
          }
        }
      ) {
        Scaffold(
          topBar = {
            TopAppBar(
              title = {
                Column {
                  Text(selectedTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                  Text("MedRad Study • ${user.fullName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
              },
              navigationIcon = {
                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Open navigation", tint = MedicalTeal) }
              },
              actions = {
                if (user.role == "ADMIN" && currentRoute != ScreenRoute.ADMIN) {
                  IconButton(onClick = { onNavigate(ScreenRoute.ADMIN) }) { Icon(Icons.Default.AdminPanelSettings, "Admin portal", tint = WarningAmber) }
                }
                IconButton(onClick = { onNavigate(ScreenRoute.NOTIFICATIONS) }) {
                  Box {
                    Icon(Icons.Default.Notifications, "Notifications")
                    if (unreadNotifications > 0) Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(Color(0xFFEF4444), CircleShape))
                  }
                }
              },
              colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
          },
          bottomBar = {
            Surface(
              shape = RoundedCornerShape(24.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
              shadowElevation = 7.dp,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).navigationBarsPadding().testTag("modern_floating_nav_bar")
            ) {
              Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                NavPillItem(currentRoute == ScreenRoute.DASHBOARD, Icons.Default.Dashboard, "Home") { onNavigate(ScreenRoute.DASHBOARD) }
                NavPillItem(currentRoute == ScreenRoute.COURSES, Icons.Default.School, "Courses") { onNavigate(ScreenRoute.COURSES) }
                NavPillItem(currentRoute == ScreenRoute.FEEDS, Icons.Default.Description, "Feeds") { onNavigate(ScreenRoute.FEEDS) }
                NavPillItem(currentRoute == ScreenRoute.SCHOLARS, Icons.Default.Search, "Scholars") { onNavigate(ScreenRoute.SCHOLARS) }
                NavPillItem(currentRoute == ScreenRoute.TUTORS, Icons.Default.Assignment, "Tutors") { onNavigate(ScreenRoute.TUTORS) }
              }
            }
          }
        ) { padding ->
          Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
      }
    }
  }
}

@Composable
private fun NavPillItem(
  selected: Boolean,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = if (selected) MedicalTeal.copy(alpha = .12f) else Color.Transparent,
    modifier = Modifier.height(44.dp)
  ) {
    Row(
      modifier = Modifier
        .padding(horizontal = if (selected) 12.dp else 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (selected) MedicalTeal else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
      if (selected) {
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MedicalTeal
        )
      }
    }
  }
}

@Composable
fun railItemColors() = NavigationRailItemDefaults.colors(
  selectedIconColor = MedicalTeal,
  selectedTextColor = MedicalTeal,
  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
  indicatorColor = MedicalTeal.copy(alpha = .12f)
)
