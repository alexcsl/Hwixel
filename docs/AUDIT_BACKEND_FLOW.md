# Backend & Flow Audit Report — Hwixel (TPA Mobile)

> **Scope**: All merged commits on `main` plus open PRs #2–#9 (Phases 4–12).  
> **Date**: 2026-05-19  
> **Auditor**: Claude Code (automated deep-read)  
> **Status**: READ-ONLY — no code changes made.

---

## Severity Legend

| Level | Meaning |
|---|---|
| 🔴 CRITICAL | Data loss, security breach, crash in normal use |
| 🟠 HIGH | Incorrect behaviour, silent failure, persistent memory leak |
| 🟡 MEDIUM | Performance problem, bad UX without data loss |
| 🔵 LOW | Code smell, maintainability concern, minor inconsistency |

---

## 1. Data Layer

### 1.1 🔴 `ProjectRepositoryImpl.observeProjectsForUser()` bypasses the filtered Firebase source

**File**: [`data/repository/ProjectRepository.kt:31`](../app/src/main/java/edu/bluejack252/hwixel/data/repository/ProjectRepository.kt)

```kotlin
override fun observeProjectsForUser(userId: String): LiveData<List<Project>> {
    return firebaseSource.observeProjects()   // ← ignores userId entirely
}
```

`ProjectFirebaseSource` has a correct `observeProjectsForUser(userId)` implementation with membership filtering, but `ProjectRepositoryImpl` never calls it. Every call to `observeProjectsForUser` fetches the **entire** project collection and returns unfiltered results. In the Dashboard, `DashboardViewModel` then applies an in-memory filter for membership, which hides the incorrect call — but on large datasets this downloads every project in the database.

**Fix**: Change the call to `firebaseSource.observeProjectsForUser(userId)`.

---

### 1.2 🔴 `TaskRepositoryImpl.createTask()` stores a blank `id` in Room

**File**: [`data/repository/TaskRepository.kt:54–58`](../app/src/main/java/edu/bluejack252\hwixel/data/repository/TaskRepository.kt)

```kotlin
override suspend fun createTask(task: Task): Result<Unit> = runCatching {
    firebaseSource.createTask(task)           // ← assigns push key internally, returns nothing
    localDao.upsert(task.toEntity())          // ← task.id may still be blank here
    recomputeCompletionPercentage(task.projectId)
}
```

`TaskFirebaseSource.createTask()` generates a Firebase push key and writes `task.copy(id = key)` to Firestore, but returns `Unit`. The original `task` object with a potentially blank `id` is then persisted to Room. Room's `@PrimaryKey` column receives `""`, which silently creates an orphaned entity. The entity is never matched again because subsequent lookups use the real Firebase key.

**Fix**: `firebaseSource.createTask()` should return the `Task` with the assigned key, and the repository should use the returned value.

---

### 1.3 🟠 Firebase `ValueEventListener` instances are never removed

**Files**: [`data/source/remote/ProjectFirebaseSource.kt`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/ProjectFirebaseSource.kt), [`data/source/remote/TaskFirebaseSource.kt`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/TaskFirebaseSource.kt), [`data/source/remote/UserFirebaseSource.kt`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/UserFirebaseSource.kt)

Every `observe*()` method creates a new `MutableLiveData` and attaches a `addValueEventListener`. The listener is never stored and `removeEventListener` is never called. Because `LiveData` from these methods is not itself a `MutableLiveData<T>` with a `removeObserver` hook, the Firebase listeners outlive the `ViewModel` and `Fragment` lifecycle. Each navigation to a screen that calls these methods adds another live listener to the same Firebase path — they accumulate across the session.

**Fix**: Wrap the Firebase listener in a `MediatorLiveData` or `LiveData` subclass that removes the listener in `onInactive()`. Alternatively, use `addListenerForSingleValueEvent` for one-shot reads and a Flow/channel approach for streaming, paired with lifecycle-aware cancellation.

---

### 1.4 🟠 `ServiceLocator` creates two separate `ProjectFirebaseSource` instances

**File**: [`data/ServiceLocator.kt:43–55`](../app/src/main/java/edu/bluejack252/hwixel/data/ServiceLocator.kt)

```kotlin
fun getProjectRepository(context: Context): ProjectRepository {
    return projectRepository ?: ProjectRepositoryImpl(
        firebaseSource = ProjectFirebaseSource(),   // instance A
        ...
    )
}

fun getTaskRepository(context: Context): TaskRepository {
    return taskRepository ?: TaskRepositoryImpl(
        ...
        projectSource = ProjectFirebaseSource()    // instance B — separate object!
    )
}
```

Instance A (used by `ProjectRepository`) and Instance B (passed as `projectSource` in `TaskRepositoryImpl`) are different objects. Both register their own `ValueEventListener` sets on the same Firebase paths. Every operation doubles the number of active Firebase connections.

**Fix**: Expose `ProjectFirebaseSource` as a singleton in `ServiceLocator` and share the reference.

---

### 1.5 🟠 `suspendCoroutine` used instead of `suspendCancellableCoroutine` for Firebase one-shot reads

**Files**: [`TaskFirebaseSource.kt:72–86`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/TaskFirebaseSource.kt), [`UserFirebaseSource.kt:71–85`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/UserFirebaseSource.kt)

```kotlin
override suspend fun fetchTasksOnce(projectId: String): List<Task> =
    suspendCoroutine { continuation ->          // ← not cancellable
        tasksRef.child(projectId).addListenerForSingleValueEvent(...)
    }
```

If the coroutine is cancelled (e.g. ViewModel cleared, fragment destroyed) while the Firebase call is in-flight, the continuation is never resumed. The Firebase listener remains attached and may call `resume()` on an already-cancelled coroutine, or simply leak. `suspendCancellableCoroutine` allows the caller to register a cancellation handler that removes the listener.

**Fix**: Replace both usages with `suspendCancellableCoroutine` and call `removeEventListener(listener)` in the cancellation handler.

---

### 1.6 🟡 `DashboardViewModel.loadDashboard()` subscribes to all tasks globally

**File**: [`ui/dashboard/DashboardViewModel.kt:50`](../app/src/main/java/edu/bluejack252/hwixel/ui/dashboard/DashboardViewModel.kt)

```kotlin
_uiState.addSource(taskRepository.observeAllTasks()) { value ->
    tasks = value
    publishState()
}
```

`observeAllTasks()` reads from `tasks/` which is the root of ALL tasks for ALL projects. In `TaskFirebaseSource`, this means iterating every project node at the root level. As the dataset grows, this downloads the entire task database on every Dashboard load. `DashboardViewModel` then filters tasks by `assignees.contains(userId)` client-side, which works but is extremely wasteful.

**Fix**: Either query tasks per user-project ID list (using the `userProjects` index), or filter on the Firebase query side using `.orderByChild()`.

---

### 1.7 🟡 `DashboardViewModel` 1-second ticker triggers full state rebuild every second

**File**: [`ui/dashboard/DashboardViewModel.kt:57–65`](../app/src/main/java/edu/bluejack252/hwixel/ui/dashboard/DashboardViewModel.kt)

```kotlin
private fun startTicker() {
    tickerJob = viewModelScope.launch {
        while (isActive) {
            _tick.value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
}
```

Every second, `_tick` posts to `MediatorLiveData`, which calls `publishState()`. `publishState()` re-runs `buildDeadlineItems()` (which filters and maps all tasks), `buildProjectItems()` (which filters and maps all projects), and `countPendingTasks()`, and posts the entire `DashboardUiState` to the UI. The `ListAdapter`'s `DiffUtil` mitigates the recycler cost, but the object allocation and comparison chain runs every second.

**Fix**: Use a delta-based update — only post a tick when at least one deadline crosses a minute boundary (not every second). Or isolate the countdown into a separate `LiveData<Map<String, CountdownParts>>` that the adapter handles directly.

---

### 1.8 🟡 `ScoreCalculator.calculate()` can produce values exceeding 100

**File**: [`util/ScoreCalculator.kt`](../app/src/main/java/edu/bluejack252/hwixel/util/ScoreCalculator.kt)

```kotlin
val weightFactor = 1f + (highPriorityCompleted * 0.2f)
return (completedTasks.toFloat() / totalAssigned) * 100f * weightFactor
```

A member with 5 high-priority tasks completed (`highPriorityCompleted = 5`) produces `weightFactor = 2.0`, meaning a score of 200. The `contributionScore` field has no documented maximum. Analytics UI displays it as a raw float; if used as a percentage or displayed in a progress bar elsewhere, this causes overflow or display corruption.

**Fix**: Apply `coerceIn(0f, 100f)` or document the unbounded range and update all UI that displays scores.

---

### 1.9 🟡 `MembersViewModel.inviteMember()` uses `.orEmpty()` for Firebase push key, risking collision

**File**: [`ui/project/members/MembersViewModel.kt:121`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/members/MembersViewModel.kt)

```kotlin
val notifKey = FirebaseDatabase.getInstance().reference.push().key.orEmpty()
userRepository.writeNotification(foundUser.id, notifKey, ...)
```

If the device is offline or the push key generation fails, `push().key` returns `null`. `.orEmpty()` converts this to `""`. The notification is then written to `notifications/{userId}/` (empty string child), which overwrites whatever notification was previously at that path node.

**Fix**: Guard against null/blank push key: `val notifKey = ... ?: return@launch` or throw an explicit error.

---

### 1.10 🟡 `MembersViewModel` action result "error" doesn't match the fragment's error prefix check

**File**: [`ui/project/members/MembersViewModel.kt:80`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/members/MembersViewModel.kt) and [`MembersFragment.kt:62–76`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/members/MembersFragment.kt)

```kotlin
// ViewModel:
_actionResult.value = if (result.isSuccess) "role_updated" else "error"
//                                                                ↑ no colon

// Fragment:
result.startsWith("error:") -> { ... }   // ← expects "error:" with colon
```

When `updateMemberRole()` or `toggleMemberStatus()` fails, the ViewModel posts `"error"` (no colon) but the fragment checks for `"error:"` (with colon). The failure falls to the `else` branch which shows `getString(R.string.error_generic)` instead of the detailed error message. Technically the user sees a generic error, but the error detail is lost.

---

### 1.11 🟡 `TaskBoardViewModel.statusUpdateResult` is exposed but never observed in the Fragment

**File**: [`ui/project/tasks/TaskBoardFragment.kt`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/tasks/TaskBoardFragment.kt) and [`TaskBoardViewModel.kt:24`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/tasks/TaskBoardViewModel.kt)

The ViewModel has:
```kotlin
private val _statusUpdateResult = MutableLiveData<Result<Unit>?>()
val statusUpdateResult: LiveData<Result<Unit>?> = _statusUpdateResult
fun consumeStatusResult() { _statusUpdateResult.value = null }
```

But `TaskBoardFragment` never observes `statusUpdateResult`. If `updateTaskStatus()` fails (e.g. network error), the user gets no feedback. The task card may optimistically appear to have changed status, but on reconnect the Firebase listener will reset it — silently and confusingly.

---

### 1.12 🔵 `ProjectMapper.encodeMembers()` uses a fragile custom pipe-delimited format

**File**: [`data/mapper/ProjectMapper.kt:33–53`](../app/src/main/java/edu/bluejack252/hwixel/data/mapper/ProjectMapper.kt)

```kotlin
listOf(userId, member.role, member.status, member.contributionScore.toString())
    .joinToString(separator = "|") { value -> encode(value) }
```

While each value is URL-encoded (making `|` safe), the format itself is a custom protocol. Any new field added to `ProjectMember` (e.g., `joinedAt`) requires updating both `encodeMembers` and `decodeMembers`, breaking the existing Room cache for any rows written before the change. Using `Gson` or `Moshi` to serialize to JSON is the conventional approach for Room type converters.

---

### 1.13 🔵 `AuthRepositoryImpl.login()` does not cache the user profile locally

**File**: [`data/repository/AuthRepository.kt:24–27`](../app/src/main/java/edu/bluejack252/hwixel/data/repository/AuthRepository.kt)

```kotlin
override suspend fun login(email: String, password: String): Result<Unit> = runCatching {
    authFirebaseSource.login(email, password)
    Unit
}
```

After a successful login on a fresh install, the local `users` table is empty. `UserRepository.observeUsers()` in ViewModels will be backed purely by the Firebase stream, meaning the first render shows "Unknown member" for all names until Firebase responds. The `register()` flow correctly calls `userDao.upsert(user.toEntity())`, but login does not.

**Fix**: After `login`, fetch the current user's profile via `userFirebaseSource.findByEmail(email)` or `observeUser(uid)` and upsert it into Room.

---

### 1.14 🔵 `HwixelDatabase.fallbackToDestructiveMigration(dropAllTables = true)` silently wipes all data on version bump

**File**: [`data/source/local/HwixelDatabase.kt:31`](../app/src/main/java/edu/bluejack252/hwixel/data/source/local/HwixelDatabase.kt)

Any schema version bump destroys all cached data. For a student project in active development this is acceptable, but it should be replaced with proper `Migration` objects before final submission to avoid the reviewer losing data during demo.

---

### 1.15 🔵 `ProjectHubFragment.showCreateProjectDialog()` is dead code

**File**: [`ui/project/hub/ProjectHubFragment.kt:131–163`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/hub/ProjectHubFragment.kt)

The `showCreateProjectDialog()` method is defined and contains full logic, but it is never called. The FAB is explicitly hidden on line 76: `binding.addProjectFab.isVisible = false`. The `createProjectResult` observer is also set up on line 62 (matching the one from `DashboardFragment`) even though project creation cannot be triggered from this screen.

---

### 1.16 🔵 `AnalyticsViewModel` GPT team health is permanently disabled

**File**: [`ui/project/analytics/AnalyticsViewModel.kt:192`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/analytics/AnalyticsViewModel.kt)

```kotlin
private const val GPT_TEAM_HEALTH_ENABLED = false
```

`shouldAnalyzeHealth()` returns `false` whenever this flag is `false`, meaning `refreshTeamHealth()` is a no-op. The Analytics screen still displays the team health card, status chip, summary text, and refresh button — all visible and interactive — but they never show real data. Users can see a "Refresh" button that does nothing.

---

### 1.17 🔵 `CreateEditTaskViewModel.saveTask()` uses a hardcoded English error string

**File**: [`ui/project/taskedit/CreateEditTaskViewModel.kt:78`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/taskedit/CreateEditTaskViewModel.kt)

```kotlin
_uiState.value = CreateEditTaskUiState.Error("Title is required")
```

This string is not from `strings.xml` and will not be translated by the Phase 12 localization. All user-visible strings in ViewModels should use resource IDs or be passed through a resource-aware helper.

---

### 1.18 🔵 `GptApiSource` OkHttpClient has no timeout configuration

**File**: [`data/source/remote/GptApiSource.kt:21`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/GptApiSource.kt)

```kotlin
class GptApiSource(
    private val client: OkHttpClient = OkHttpClient(),
```

`OkHttpClient()` defaults to a 10-second connect/read/write timeout. For an AI API that may take several seconds to respond, this may cause unnecessary timeout errors. The client is also not shared (though the class is effectively a singleton via `ServiceLocator`), so no connection pooling benefit is gained if a new instance were created.

---

### 1.19 🔵 Notification `TYPE_INVITE` is duplicated as a constant in two places

**Files**: [`ui/notifications/NotificationsFragment.kt:61`](../app/src/main/java/edu/bluejack252/hwixel/ui/notifications/NotificationsFragment.kt) and [`data/source/remote/ProjectFirebaseSource.kt:250`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/ProjectFirebaseSource.kt)

Both declare `const val TYPE_INVITE = "invite"` independently in separate companion objects. There is also the literal string `"invite"` written directly in `MembersViewModel.inviteMember()`. If the type value ever changes, it needs to be updated in all three places.

---

## 2. Navigation Flow

### 2.1 🟠 `NotificationsFragment` navigates to `projectHubFragment` using raw bundle, bypassing Safe Args

**File**: [`ui/notifications/NotificationsFragment.kt:49–53`](../app/src/main/java/edu/bluejack252/hwixel/ui/notifications/NotificationsFragment.kt)

```kotlin
findNavController().navigate(
    R.id.projectHubFragment,
    bundleOf("projectId" to notification.referenceId)
)
```

`ProjectHubFragment` reads its argument via `navArgs()` which uses Safe Args generated code. The generated `ProjectHubFragmentArgs` expects the bundle key to be the Safe Args-mangled name. This works only because Safe Args and `bundleOf` use the same underlying key string for simple string args — but this is an implementation detail of Safe Args, not a guarantee. Using `ProjectHubFragmentArgs(projectId = ...).toBundle()` or a generated direction action is the correct approach.

---

### 2.2 🟡 Task board navigates via `requireParentFragment().findNavController()`

**File**: [`ui/project/tasks/TaskBoardFragment.kt:96–103`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/tasks/TaskBoardFragment.kt)

```kotlin
binding.addTaskFab.setOnClickListener {
    requireParentFragment().findNavController().navigate(...)
}
```

`TaskBoardFragment` is a child fragment inside a `ViewPager2` inside `ProjectHubFragment`. `requireParentFragment()` returns the `ProjectHubFragment`. This navigation pattern is coupled to the host structure. If the ViewPager is ever replaced or the fragment re-hosted, this silently breaks.

---

### 2.3 🔵 Navigation graph action for `projectHubFragment → attendanceFragment` is in PR #4 but not yet in main

**File**: [`res/navigation/main_nav_graph.xml`](../app/src/main/res/navigation/main_nav_graph.xml)

The current merged nav graph has no `attendanceFragment`, `peerEvalFragment`, or `fileRepositoryFragment` nodes. PRs #4, #5, #6 add these fragments and actions, but until they merge, any attempt to navigate to them using a direction (e.g. from `ProjectHubFragment`) will crash with `IllegalArgumentException: navigation destination is unknown`.

---

## 3. Firebase Data Model

### 3.1 🟡 `tasks/` node is a flat root — no security rule can scope read access per project

Firebase RTDB's security rules operate on node paths. With `tasks/{projectId}/{taskId}`, a Firebase Security Rule can restrict reads to members of `{projectId}`. However, `DashboardViewModel` reads from `tasks/` root (all projects), which requires a rule that allows reading the entire `tasks` subtree — meaning any authenticated user can read all tasks from all projects. This is a security model mismatch. Without seeing the actual Firebase rules, this is a potential data exposure risk.

---

### 3.2 🔵 `attendanceRecords` not included in `AttendanceSession` domain model (`DomainModels.kt`)

**File**: [`data/model/DomainModels.kt:81–87`](../app/src/main/java/edu/bluejack252/hwixel/data/model/DomainModels.kt)

```kotlin
data class AttendanceSession(
    val id: String = "",
    val projectId: String = "",
    val date: Long = 0L,
    val nextSessionDate: Long = 0L,
    val records: Map<String, Boolean> = emptyMap()
)
```

The `records` map uses `Boolean` values. If a user marked absent (`false`) vs. never marked (missing key), the two states are indistinguishable when reading from Firebase using `getValue(Boolean::class.java)` — missing key returns `null`, which would be treated as `false` by the attendance UI.

---

## 4. Open PRs — Potential Issues in Proposed Changes

### PR #5 (Phase 8 – Peer Evaluation)
- The "Submit an evaluation for yourself" self-eval guard is purely in the UI layer (dropdown excludes self). No backend validation prevents a malicious client from submitting a self-evaluation by posting directly to Firebase.
- Average peer rating is recomputed and stored on the `User` object (`averagePeerRating`). If multiple concurrent submissions arrive, this field may be computed from stale data — a classic read-modify-write race condition without a Firebase Transaction.

### PR #6 (Phase 9 – File Repository)
- Chrome Custom Tabs fallback uses default browser intent. If no browser is installed (rare on Android), this throws `ActivityNotFoundException` which is unhandled.
- Swipe-to-delete shows an "undo-friendly Snackbar" per the PR description, but the delete call is immediate with no actual undo mechanism (no Firebase restore path).

### PR #7 (Phase 10 – Notifications)
- `DeadlineReminderWorker` schedules three `OneTimeWorkRequest` jobs tagged by `taskId`. The tag-based cancellation before re-scheduling is correct, but if a task is deleted, its workers are never cancelled — they will fire a notification pointing to a deleted task.
- FCM token is saved to `users/{uid}/fcmToken` but the `User` domain model and `UserEntity` do not include an `fcmToken` field. This means the token is written to Firebase but read logic does not map it back, making server-side push targeting impossible unless done outside the data layer.

### PR #8 (Phase 11 – Profile)
- Profile screen Phase 11 is described as adding badge awards, dark mode toggle, language preference. Dark mode and language are app-level settings that typically require Activity recreation. If these are toggled mid-session without restarting, stale cached resources may render incorrectly.

### PR #9 (Phase 12 – Localization)
- `AppCompatDelegate.setApplicationLocales()` requires API 33+. The worktree targets API 35 but the `minSdk` should be checked — if it's below 33, a compatibility shim using `AppCompatDelegate.setDefaultNightMode` equivalent from AppCompat compat library is needed.
- `LocalizationTest` and `StringResourceCoverageTest` are declared as JVM unit tests but parse XML with `javax.xml.parsers.DocumentBuilderFactory`. On some CI environments, these may behave differently than on local machines if the XML parser is not available.

---

## Summary Table

| # | Severity | Area | Description |
|---|---|---|---|
| 1.1 | 🔴 | Repository | `observeProjectsForUser` ignores userId, fetches all projects |
| 1.2 | 🔴 | Repository | `createTask` stores blank `id` in Room |
| 1.3 | 🟠 | Firebase | Listener never removed — accumulates per navigation |
| 1.4 | 🟠 | DI | Two separate `ProjectFirebaseSource` instances in ServiceLocator |
| 1.5 | 🟠 | Coroutines | `suspendCoroutine` instead of `suspendCancellableCoroutine` |
| 1.6 | 🟡 | Performance | `observeAllTasks()` downloads entire task DB for Dashboard |
| 1.7 | 🟡 | Performance | 1-second ticker rebuilds full dashboard state every second |
| 1.8 | 🟡 | Logic | `ScoreCalculator` can produce score > 100 |
| 1.9 | 🟡 | Reliability | Null push key falls to empty string causing notification overwrite |
| 1.10 | 🟡 | UX | `"error"` vs `"error:"` mismatch silences error detail in Members |
| 1.11 | 🟡 | UX | `statusUpdateResult` never observed — silent status update failures |
| 1.12 | 🔵 | Code quality | Fragile custom pipe-delimited Room serialization for ProjectMember |
| 1.13 | 🔵 | UX | Login does not cache user profile locally |
| 1.14 | 🔵 | Risk | Destructive migration wipes cache on any schema bump |
| 1.15 | 🔵 | Dead code | `showCreateProjectDialog()` in ProjectHubFragment unreachable |
| 1.16 | 🔵 | UX | Team health UI is permanently non-functional |
| 1.17 | 🔵 | i18n | Hardcoded English error string bypasses localization |
| 1.18 | 🔵 | Config | OkHttpClient has no explicit timeout for GPT calls |
| 1.19 | 🔵 | Maintenance | `TYPE_INVITE` constant duplicated in three places |
| 2.1 | 🟠 | Navigation | Raw bundle navigation bypasses Safe Args contract |
| 2.2 | 🟡 | Navigation | `requireParentFragment()` navigation is fragile to host changes |
| 2.3 | 🔵 | Navigation | Attendance/Eval/Files nav destinations missing from merged nav graph |
| 3.1 | 🟡 | Security | Root `tasks/` listener may bypass per-project Firebase Security Rules |
| 3.2 | 🔵 | Data model | Absent vs. never-marked attendance states are indistinguishable |

---

*End of Backend & Flow Audit*
