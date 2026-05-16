# Technical Documentation
# Hwixel — Android Application

**Version:** 1.0  
**Package:** `edu.bluejack252.hwixel`  
**Language:** Kotlin  
**Min SDK:** 35 (Android 15)  
**Architecture:** MVVM  

---

## 1. Architecture Overview

Hwixel uses the MVVM (Model-View-ViewModel) architecture pattern. All screens are Fragments managed by the Jetpack Navigation Component. There are three layers: View, ViewModel, and Repository.

```
┌─────────────────────────────────────────────────────────┐
│                        View Layer                        │
│         Activity / Fragment / XML Layout                 │
│   Observes LiveData. No business logic.                  │
└────────────────────┬────────────────────────────────────┘
                     │ observes / calls
┌────────────────────▼────────────────────────────────────┐
│                    ViewModel Layer                        │
│     Holds UI state as LiveData. Calls Repository.        │
│     Survives configuration changes.                      │
└────────────────────┬────────────────────────────────────┘
                     │ calls
┌────────────────────▼────────────────────────────────────┐
│                   Repository Layer                        │
│   Single source of truth. Fetches from Firebase RTDB     │
│   or local Room cache depending on connectivity.         │
└───────┬─────────────────────────────────┬───────────────┘
        │                                 │
┌───────▼──────────┐           ┌──────────▼──────────────┐
│  Remote Source   │           │     Local Source         │
│ Firebase Auth    │           │  Room Database (SQLite)  │
│ Firebase RTDB    │           │  SharedPreferences       │
└──────────────────┘           └─────────────────────────┘
```

### Rules

- Fragments and Activities must not contain business logic.
- ViewModels must not hold a reference to Context, Activity, or Fragment.
- Repositories are the only layer that accesses Firebase or Room directly.
- ViewModels expose `MutableLiveData` privately and `LiveData` publicly to the View.

---

## 2. Project Structure

```
edu.bluejack252.hwixel/
├── data/
│   ├── model/               # Data classes: User, Project, Task, etc.
│   ├── repository/          # Repository implementations
│   ├── source/
│   │   ├── remote/          # Firebase RTDB and Auth helpers
│   │   └── local/           # Room DAOs and Database class
│   └── mapper/              # Extension functions: Entity <-> Domain model
├── ui/
│   ├── auth/
│   │   ├── login/           # LoginFragment + LoginViewModel
│   │   └── register/        # RegisterFragment + RegisterViewModel
│   ├── dashboard/           # DashboardFragment + DashboardViewModel
│   ├── project/
│   │   ├── hub/             # ProjectHubFragment + ProjectHubViewModel
│   │   ├── tasks/           # TaskBoardFragment + TaskBoardViewModel
│   │   ├── taskdetail/      # TaskDetailFragment + TaskDetailViewModel
│   │   ├── taskedit/        # CreateEditTaskFragment + CreateEditTaskViewModel
│   │   ├── analytics/       # AnalyticsFragment + AnalyticsViewModel
│   │   ├── attendance/      # AttendanceFragment + AttendanceViewModel
│   │   ├── evaluation/      # PeerEvalFragment + PeerEvalViewModel
│   │   ├── members/         # MembersFragment + MembersViewModel
│   │   └── files/           # FileRepoFragment + FileRepoViewModel
│   ├── notifications/       # NotificationsFragment + NotificationsViewModel
│   └── profile/             # ProfileFragment + ProfileViewModel
└── util/
    ├── extensions/          # Kotlin extension functions
    ├── validators/          # Email and password validation helpers
    └── constants/           # Status strings, badge names, etc.
```

---

## 3. Tech Stack

| Technology | Notes |
|---|---|
| Kotlin | App language |
| Android Min SDK | 35 |
| Target SDK | 35 |
| Firebase Auth | Email/password only. No SMS, no phone auth. |
| Firebase Realtime Database | Real-time data sync |
| Room | Local SQLite cache for offline support |
| Kotlin Coroutines | Async operations in ViewModels and Repositories |
| LiveData | Reactive UI state observed by Fragments |
| Jetpack Navigation | Fragment routing with Safe Args |
| Material Design 3 | UI components and theming |
| MPAndroidChart | Pie and bar charts on the Analytics page |
| Glide | Image loading for user avatars |
| OkHttp | HTTP client for the Groq AI API call |
| Groq API | Free AI inference running open-source models (Llama 3.3, Gemma 2) |
| WorkManager | Scheduled local deadline reminder notifications |
| FCM | Firebase Cloud Messaging for push notifications |
| JUnit4 | Unit test runner |
| Mockito-Kotlin | Mocking in unit tests |

**Not used:** Hilt, Retrofit, Firebase Emulator Suite. These do not contribute to any scoring item and add unnecessary complexity.

---

## 4. Dependency Injection (ViewModelFactory)

Hilt is not used. Each ViewModel that requires a Repository uses a `ViewModelProvider.Factory`. Repositories are created once as singletons via a `ServiceLocator` object.

```kotlin
// ServiceLocator.kt
object ServiceLocator {
    private var projectRepository: ProjectRepository? = null

    fun getProjectRepository(context: Context): ProjectRepository {
        return projectRepository ?: ProjectRepositoryImpl(
            firebaseSource = ProjectFirebaseSource(),
            localDao = HwixelDatabase.getInstance(context).projectDao()
        ).also { projectRepository = it }
    }
}

// LoginViewModelFactory.kt
class LoginViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LoginViewModel(authRepository) as T
    }
}

// Usage in LoginFragment
private val viewModel: LoginViewModel by viewModels {
    LoginViewModelFactory(AuthRepositoryImpl(FirebaseAuth.getInstance()))
}
```

---

## 5. Firebase Setup

### 5.1 Authentication

Only email/password is enabled. Phone, Google, and all other providers are disabled. Client-side password rules: minimum 8 characters, at least one uppercase letter, at least one special character.

### 5.2 Firebase Realtime Database Schema

```
hwixel-db/
├── users/{userId}/
│   ├── name: String
│   ├── studentId: String
│   ├── email: String
│   ├── avatarUrl: String
│   ├── totalProjectsCompleted: Int
│   ├── averagePeerRating: Float
│   └── badges: List<String>
│
├── projects/{projectId}/
│   ├── name: String
│   ├── description: String
│   ├── goals: String
│   ├── dueDate: Long
│   ├── createdBy: String
│   ├── completionPercentage: Float
│   └── members/{userId}/
│       ├── role: String
│       ├── status: String          ("active" | "inactive")
│       └── contributionScore: Float
│
├── tasks/{projectId}/{taskId}/
│   ├── title: String
│   ├── description: String
│   ├── status: String              ("todo" | "in_progress" | "review" | "done")
│   ├── priority: String            ("low" | "medium" | "high")
│   ├── deadline: Long
│   ├── assignees: List<String>
│   ├── attachments: List<Attachment>
│   ├── comments/{commentId}/
│   │   ├── authorId: String
│   │   ├── content: String
│   │   └── timestamp: Long
│   ├── subtasks/{subtaskId}/
│   │   ├── title: String
│   │   └── isDone: Boolean
│   └── history/{historyId}/
│       ├── actorId: String
│       ├── action: String
│       └── timestamp: Long
│
├── attendance/{projectId}/{sessionId}/
│   ├── date: Long
│   ├── nextSessionDate: Long
│   └── records/{userId}/
│       └── present: Boolean
│
├── evaluations/{projectId}/{periodId}/
│   ├── isOpen: Boolean
│   ├── openedAt: Long
│   └── submissions/{evaluatorId}/{evaluateeId}/
│       ├── communication: Int
│       ├── quality: Int
│       ├── reliability: Int
│       ├── effort: Int
│       └── feedback: String
│
├── files/{projectId}/{fileId}/
│   ├── label: String
│   ├── url: String
│   ├── type: String                ("drive" | "github" | "other")
│   └── versionNotes: String
│
└── notifications/{userId}/{notificationId}/
    ├── type: String
    ├── message: String
    ├── timestamp: Long
    ├── isRead: Boolean
    └── referenceId: String
```

### 5.3 Firebase Security Rules

```json
{
  "rules": {
    "users": {
      "$userId": {
        ".read": "$userId === auth.uid",
        ".write": "$userId === auth.uid"
      }
    },
    "projects": {
      "$projectId": {
        ".read": "auth != null && data.child('members').child(auth.uid).exists()",
        ".write": "auth != null && data.child('members').child(auth.uid).exists()"
      }
    },
    "tasks": {
      "$projectId": {
        ".read": "auth != null && root.child('projects').child($projectId).child('members').child(auth.uid).exists()",
        ".write": "auth != null && root.child('projects').child($projectId).child('members').child(auth.uid).exists()"
      }
    },
    "attendance": {
      "$projectId": {
        ".read": "auth != null && root.child('projects').child($projectId).child('members').child(auth.uid).exists()",
        ".write": "auth != null && root.child('projects').child($projectId).child('members').child(auth.uid).child('role').val() === 'Team Lead'"
      }
    },
    "evaluations": {
      "$projectId": {
        ".read": "auth != null && root.child('projects').child($projectId).child('members').child(auth.uid).exists()",
        ".write": "auth != null && root.child('projects').child($projectId).child('members').child(auth.uid).exists()"
      }
    },
    "notifications": {
      "$userId": {
        ".read": "$userId === auth.uid",
        ".write": "$userId === auth.uid"
      }
    }
  }
}
```

---

## 6. Room Local Database (Offline Cache)

Room caches core data for offline reading. The app reads from Room immediately on screen load and syncs from Firebase in the background.

### 6.1 Entities

```kotlin
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val goals: String,
    val dueDate: Long,
    val createdBy: String,
    val completionPercentage: Float,
    val lastSynced: Long
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val deadline: Long,
    val assigneesJson: String,
    val lastSynced: Long
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val studentId: String,
    val email: String,
    val avatarUrl: String
)
```

### 6.2 DAOs

```kotlin
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects")
    fun observeAll(): LiveData<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}
```

All other DAOs (TaskDao, UserDao) follow the same pattern.

---

## 7. Repository Pattern

```kotlin
interface ProjectRepository {
    fun observeProjects(): LiveData<List<Project>>
    suspend fun createProject(project: Project): Result<Unit>
    suspend fun updateProject(project: Project): Result<Unit>
}

class ProjectRepositoryImpl(
    private val firebaseSource: ProjectFirebaseSource,
    private val localDao: ProjectDao
) : ProjectRepository {

    override fun observeProjects(): LiveData<List<Project>> =
        localDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun createProject(project: Project): Result<Unit> = runCatching {
        firebaseSource.createProject(project)
        localDao.upsert(project.toEntity())
    }
}
```

---

## 8. ViewModel Pattern

```kotlin
class TaskBoardViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> = _tasks

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadTasks(projectId: String) {
        viewModelScope.launch {
            taskRepository.observeTasks(projectId).observeForever { result ->
                _tasks.value = result
            }
        }
    }
}
```

Screens with multiple states use a sealed class:

```kotlin
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
```

---

## 9. Navigation

Single `MainActivity` hosts a `NavHostFragment`. Two nav graphs: `auth_nav_graph.xml` (start: LoginFragment) and `main_nav_graph.xml` (start: DashboardFragment). The bottom navigation bar controls the three top-level destinations: Dashboard, Notifications, Profile.

---

## 10. AI Integration — Team Health (Groq API)

The Team Health feature on the Contribution Analytics page uses the **Groq API**. It is free (no billing required for the free tier) and runs open-source models including Llama 3.3 and Gemma 2.

**Model:** `llama-3.3-70b-versatile`  
**Endpoint:** `POST https://api.groq.com/openai/v1/chat/completions`  
**Format:** OpenAI-compatible JSON  
**Library:** OkHttp (already in the project)

### 10.1 API Key Storage

```
# local.properties (never committed to git)
groq.api.key=gsk_xxxxxxxxxxxxxxxxxxxx
```

```groovy
// build.gradle (app module)
buildConfigField("String", "GROQ_API_KEY", "\"${localProperties['groq.api.key']}\"")
```

### 10.2 HTTP Call

```kotlin
class GroqApiSource {

    private val client = OkHttpClient()

    suspend fun getTeamHealth(members: List<MemberStats>): Result<TeamHealthResult> = runCatching {
        val stats = members.joinToString("\n") { m ->
            "- ${m.name}: assigned=${m.tasksAssigned}, completed=${m.tasksCompleted}, overdue=${m.tasksOverdue}"
        }
        val prompt = "Analyze this student group project workload and return ONLY a JSON object with: " +
            "status (Healthy, Mild Imbalance, or Severe Imbalance), summary (one sentence), " +
            "recommendations (array of up to 3 short strings). Data:\n$stats"

        val body = """
            {
              "model": "llama-3.3-70b-versatile",
              "messages": [{ "role": "user", "content": "${prompt.replace("\"", "\\\"")}" }],
              "max_tokens": 300,
              "temperature": 0.3
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        parseResponse(response.body?.string() ?: error("Empty response"))
    }
}
```

### 10.3 Response Parsing

Extract `choices[0].message.content` from the response JSON, then parse it as a JSON object using `org.json.JSONObject` (part of the Android SDK, no extra dependency needed).

---

## 11. Localization

```
res/
├── values/strings.xml          # English (default)
└── values-id/strings.xml       # Bahasa Indonesia
```

All user-visible strings go in `strings.xml`. No hardcoded strings in layouts or code. Language selection on the Profile page saves the locale to `SharedPreferences` and applies it via `AppCompatDelegate.setApplicationLocales()`.

---

## 12. Notifications

| Type | Trigger | Delivery |
|---|---|---|
| Task assignment | Task assignees field updated | FCM push |
| Mention | Comment contains @userId | FCM push |
| Deadline 24h | 24 hours before deadline | WorkManager local |
| Deadline 1h | 1 hour before deadline | WorkManager local |
| Deadline 15min | 15 minutes before deadline | WorkManager local |
| Eval period open | `isOpen` set to true | FCM push |
| Eval period close | `isOpen` set to false | FCM push |
| Member invite | User added to project members | FCM push |

Notification badge count uses the standard Android `NotificationManagerCompat` badge API.

---

## 13. Unit Testing

### Scope

All ViewModel and Repository classes must have unit tests. Espresso covers the login and task creation flows as basic instrumented tests.

| Tool | Use |
|---|---|
| JUnit4 | Test runner |
| Mockito-Kotlin | Mock Repository dependencies |
| kotlinx-coroutines-test | Test suspend functions |
| Espresso | Basic UI tests |

### ViewModel Test Pattern

```kotlin
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mock()
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        viewModel = LoginViewModel(authRepository)
    }

    @Test
    fun `login with valid credentials emits Success`() = runTest {
        whenever(authRepository.login("test@email.com", "Password1!"))
            .thenReturn(Result.success(Unit))
        viewModel.login("test@email.com", "Password1!")
        assertEquals(LoginUiState.Success, viewModel.uiState.value)
    }

    @Test
    fun `invalid email emits Error state`() = runTest {
        viewModel.login("not-an-email", "Password1!")
        assertTrue(viewModel.uiState.value is LoginUiState.Error)
    }
}
```

### Repository Test Pattern

```kotlin
class ProjectRepositoryTest {

    private val firebaseSource: ProjectFirebaseSource = mock()
    private val localDao: ProjectDao = mock()
    private lateinit var repository: ProjectRepositoryImpl

    @Before
    fun setup() {
        repository = ProjectRepositoryImpl(firebaseSource, localDao)
    }

    @Test
    fun `createProject writes to Firebase and local Room cache`() = runTest {
        val project = Project(id = "1", name = "Test Project")
        repository.createProject(project)
        verify(firebaseSource).createProject(project)
        verify(localDao).upsert(project.toEntity())
    }
}
```

---

## 14. Git Best Practices

### Branch Strategy

| Branch | Purpose |
|---|---|
| `main` | Production-ready. Protected. Merge only from release branches. |
| `develop` | Integration branch. All feature branches merge here. |
| `feature/xyz` | One branch per screen or feature. |
| `fix/xyz` | Bug fix branches. |
| `release/x.x` | Release preparation. |

### Commit Convention

```
<type>(<scope>): <short description>

Types: feat, fix, refactor, test, docs, chore, style
Scope: auth, dashboard, tasks, analytics, attendance, evaluation, members, files, notifications, profile

Examples:
feat(tasks): add kanban board view toggle
fix(auth): correct email validation regex
test(dashboard): add unit tests for DashboardViewModel
```

### Rules

- No direct commits to `main` or `develop`. All changes go through pull requests.
- At least one reviewer approves before merge.
- Feature branches are deleted after merge.
- `local.properties`, `google-services.json`, and API keys are never committed.

---

## 15. Play Store Deployment

- Target SDK 35 or higher.
- Release build signed with a keystore.
- App bundle (`.aab`) format for Play Store upload.
- R8 minification and obfuscation enabled for the release build.
- Prohibited manifest permissions: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `SEND_SMS`, `RECEIVE_SMS`, `BILLING`.
