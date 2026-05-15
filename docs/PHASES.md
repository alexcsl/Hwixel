# Hwixel — Implementation Phases

**Project:** Hwixel (Group Project Management App)
**Package:** `edu.bluejack252.hwixel`
**Platform:** Android (Min SDK 35, Kotlin, MVVM)
**Audience:** AI coding agents executing this project end-to-end.

---

## 0. How To Use This Document

This file is the **sequenced execution roadmap**. It does not redefine product or architecture — it points to the three authoritative spec files in this folder:

| Spec | Use For |
|---|---|
| [`Hwixel_PRD.md`](./Hwixel_PRD.md) | What to build (features, constraints, scoring) |
| [`Hwixel_Feature_Docs.md`](./Hwixel_Feature_Docs.md) | Per-screen behavior, weights, relationships, formulas |
| [`Hwixel_Technical_Docs.md`](./Hwixel_Technical_Docs.md) | How to build it (MVVM layout, schema, code patterns) |

### Rules for the Executing Agent (READ BEFORE EVERY PHASE)

1. **Specs win.** If `PHASES.md` and a spec disagree, the spec is correct. File an issue, do not deviate.
2. **One phase at a time.** Finish a phase — including its tests — before starting the next. Update the status legend below.
3. **MVVM is non-negotiable** (Tech §1):
   - No business logic in `Activity` or `Fragment`.
   - `ViewModel` never holds `Context`, `Activity`, or `Fragment`.
   - Only `Repository` touches Firebase or Room.
   - `ViewModel` exposes `private val _x = MutableLiveData<>()` and `val x: LiveData<> = _x`.
4. **No forbidden tech:** no Hilt, no Retrofit, no location APIs, no SMS, no payments (PRD §5, Tech §3).
5. **Secrets never committed:** `local.properties`, `google-services.json`, API keys are git-ignored.
6. **Package layout exactly matches Tech §2.** Do not invent new top-level packages.
7. **All user-visible strings via `strings.xml`** — no hardcoded text, in any phase.

### Phase Status Legend

`[ ]` pending · `[~]` in progress · `[x]` done

| # | Phase | Status |
|---|---|---|
| 0 | Project Foundation & Tooling | `[ ]` |
| 1 | Data Layer Foundation | `[ ]` |
| 2 | Authentication | `[ ]` |
| 3 | Dashboard & Main Navigation | `[ ]` |
| 4 | Project Hub & Project Members | `[ ]` |
| 5 | Task Board, Task Detail, Create/Edit Task | `[ ]` |
| 6 | Contribution Analytics + Groq AI | `[ ]` |
| 7 | Attendance | `[ ]` |
| 8 | Peer Evaluation | `[ ]` |
| 9 | File Repository | `[ ]` |
| 10 | Notifications (FCM + WorkManager) | `[ ]` |
| 11 | Profile & Settings | `[ ]` |
| 12 | Localization (EN + ID) | `[ ]` |
| 13 | Testing Sweep & Code Quality | `[ ]` |
| 14 | Release & Play Store | `[ ]` |

---

## 1. Phase Dependency Graph

```
Phase 0 (Foundation)
   │
   ▼
Phase 1 (Data Layer)
   │
   ▼
Phase 2 (Auth)
   │
   ▼
Phase 3 (Dashboard + Nav Skeleton)
   │
   ▼
Phase 4 (Project Hub + Members)
   │
   ├──────────────┬──────────────┬──────────────┬──────────────┐
   ▼              ▼              ▼              ▼              ▼
Phase 5       Phase 6        Phase 7        Phase 8        Phase 9
(Tasks)       (Analytics)    (Attendance)   (Peer Eval)    (Files)
   │              │              │              │              │
   └──────────────┴──────┬───────┴──────────────┴──────────────┘
                         ▼
                    Phase 10 (Notifications)
                         │
                         ▼
                    Phase 11 (Profile & Settings)
                         │
                         ▼
                    Phase 12 (Localization)
                         │
                         ▼
                    Phase 13 (Testing Sweep)
                         │
                         ▼
                    Phase 14 (Release)
```

Phases 5–9 may be parallelized across agents once Phase 4 lands. Phase 10 depends on data writes from phases 5, 7, 8. Phases 12–14 gate on all features.

---

## 2. Phases

### Phase 0 — Project Foundation & Tooling

**Goal:** A buildable empty app with the correct manifest, dependencies, package skeleton, and BuildConfig wiring.

**Tasks:**
- Configure `app/build.gradle.kts`: `compileSdk = 35`, `minSdk = 35`, `targetSdk = 35`, Kotlin, View Binding, Safe Args plugin, KSP for Room.
- Read `groq.api.key` from `local.properties` and expose as `BuildConfig.GROQ_API_KEY` (Tech §10.1).
- Add dependencies (Tech §3): Firebase BoM + Auth + RTDB + Messaging, Room (`runtime`, `ktx`, `compiler` via ksp), Coroutines (`core`, `android`), Lifecycle (`viewmodel-ktx`, `livedata-ktx`), Navigation (`fragment-ktx`, `ui-ktx`), Material 3, MPAndroidChart, Glide, OkHttp, WorkManager, Browser (Chrome Custom Tabs). Test deps: JUnit4, Mockito-Kotlin, `kotlinx-coroutines-test`, AndroidX Test, Espresso.
- `AndroidManifest.xml`: package `edu.bluejack252.hwixel`, `INTERNET` and `POST_NOTIFICATIONS` permissions only. Single `MainActivity`. Do **not** add `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `SEND_SMS`, `RECEIVE_SMS`, or `BILLING` (PRD §5, Tech §15).
- Document `google-services.json` placement in README; ensure `.gitignore` excludes `local.properties`, `google-services.json`, `*.keystore`.
- Create empty package skeleton per Tech §2: `data/{model,repository,source/remote,source/local,mapper}`, `ui/{auth/login,auth/register,dashboard,project/{hub,tasks,taskdetail,taskedit,analytics,attendance,evaluation,members,files},notifications,profile}`, `util/{extensions,validators,constants}`.

**Spec refs:** PRD §5, §6 · Tech §2, §3, §15.

**Acceptance:** `./gradlew assembleDebug` succeeds. App launches an empty `MainActivity` on Android 15 emulator. `BuildConfig.GROQ_API_KEY` resolves.

**Tests:** N/A (no logic yet).

---

### Phase 1 — Data Layer Foundation

**Goal:** All models, Room database + DAOs, Firebase source skeletons, repositories, mappers, validators, and `ServiceLocator` wired up.

**Tasks:**
- **Domain models** in `data/model/`: `User`, `Project`, `ProjectMember`, `Task`, `Subtask`, `Comment`, `Attachment`, `HistoryEntry`, `AttendanceSession`, `EvaluationSubmission`, `FileLink`, `Notification`. Field types exactly match RTDB schema in Tech §5.2.
- **Room** in `data/source/local/`: `HwixelDatabase`, `ProjectEntity`, `TaskEntity`, `UserEntity`, `ProjectDao`, `TaskDao`, `UserDao` (Tech §6). Use `@TypeConverter` for list fields where needed.
- **Mappers** in `data/mapper/`: extension functions `XxxEntity.toDomain()` and `XxxDomain.toEntity()`.
- **Remote sources** in `data/source/remote/`: `AuthFirebaseSource`, `ProjectFirebaseSource`, `TaskFirebaseSource`, `UserFirebaseSource`. Use `FirebaseAuth.getInstance()` and `FirebaseDatabase.getInstance().reference`. Expose suspend functions and `LiveData<List<T>>` for observed nodes.
- **Repositories** in `data/repository/` — interface + `Impl` pair: `AuthRepository`, `UserRepository`, `ProjectRepository`, `TaskRepository`. All suspend ops return `Result<T>` (Tech §7).
- **`ServiceLocator`** object in `data/` providing singletons for every repository (Tech §4).
- **Utilities** in `util/`: `validators/EmailValidator` (uses `Patterns.EMAIL_ADDRESS`), `validators/PasswordValidator` (min 8, ≥1 uppercase, ≥1 special char), `constants/Constants` (status, priority, role string constants).

**Spec refs:** Tech §2, §5.2, §6, §7.

**Acceptance:** Project compiles. ServiceLocator returns the same repository instance across calls. Room migrations defined (start at version 1).

**Tests (`app/src/test/`):**
- `EmailValidatorTest`, `PasswordValidatorTest` covering valid + invalid cases.
- One repository test per repo following Tech §13 pattern (mock source + DAO, verify writes hit both).

---

### Phase 2 — Authentication (Login + Register)

**Team:** XL · **Screens:** Login, Register · **Page Weights:** 2.0 + 2.0

**Tasks:**
- `res/navigation/auth_nav_graph.xml` — start destination `LoginFragment`, action to `RegisterFragment`.
- `ui/auth/login/`: `LoginFragment`, `LoginViewModel`, `LoginViewModelFactory`, `LoginUiState` sealed class (`Idle | Loading | Success | Error(msg)`) per Tech §8.
- `ui/auth/register/`: `RegisterFragment`, `RegisterViewModel`, `RegisterViewModelFactory`, `RegisterUiState`.
- XML layouts (`fragment_login.xml`, `fragment_register.xml`) using `TextInputLayout` Material 3 components. All strings in `strings.xml`.
- `MainActivity` decides graph: if `FirebaseAuth.getInstance().currentUser != null` → main graph, else auth graph.
- Register flow: `createUserWithEmailAndPassword` → on success write `users/{uid}` (name, studentId, email, empty badges/avatar) to RTDB and Room.

**Spec refs:** PRD §7.1 · Feature §2.1, §2.2 · Tech §5.1, §9.

**Acceptance:**
- Email validation uses `Patterns.EMAIL_ADDRESS` and shows inline error.
- Password validation enforces ≥8 chars, ≥1 uppercase, ≥1 special.
- Registration writes to RTDB `users/{uid}` AND Room `UserEntity`.
- Login on success pops auth back stack and navigates to Dashboard.
- All FirebaseAuthException codes map to readable Snackbar messages.

**Tests:**
- `LoginViewModelTest`: valid credentials → `Success`; invalid email → `Error`; empty password → `Error`; Firebase error → mapped message.
- `RegisterViewModelTest`: weak password rejected before Firebase call; successful register writes user profile.

---

### Phase 3 — Dashboard & Main Navigation Skeleton

**Team:** XL · **Screen:** Dashboard · **Page Weight:** 2.25

**Tasks:**
- `res/navigation/main_nav_graph.xml` — start `DashboardFragment`. Include placeholders for `NotificationsFragment` and `ProfileFragment` so the bottom nav graph compiles.
- `MainActivity` `BottomNavigationView` with three destinations: Dashboard, Notifications, Profile.
- `ui/dashboard/DashboardFragment` + `DashboardViewModel`:
  - Project list RecyclerView (project name, role, completion %).
  - Upcoming Deadlines horizontal scrollable RecyclerView of the 3–5 nearest deadlines; live countdown updated every 1 second via `viewModelScope` ticker.
  - Pending tasks summary badge: count of tasks where `status ∈ {todo, in_progress}` and `assignees` contains current uid.
  - Tap project card → `ProjectHubFragment` with `projectId` via Safe Args.
- Stub `NotificationsFragment` (empty layout + placeholder text) and `ProfileFragment` (same) so phases 10 and 11 can fill them.

**Spec refs:** PRD §7.2 · Feature §3.1 · Tech §9.

**Acceptance:** Bottom nav switches destinations. Countdown ticks. Tapping a project navigates with the correct `projectId`.

**Tests:** `DashboardViewModelTest` for deadline sort (ascending by deadline), pending-task aggregation, and countdown formatting.

---

### Phase 4 — Project Hub & Project Members

**Teams:** XL (Hub), HW (Members) · **Screens:** Project Hub, Project Members · **Page Weights:** 1.75 + 1.5

**Tasks:**
- `ui/project/hub/ProjectHubFragment` + `ProjectHubViewModel`:
  - Header: project name, goals, description, due date (red when overdue), `LinearProgressIndicator` for completion %.
  - `TabLayout` + `ViewPager2` with three tabs: Tasks (TaskBoardFragment stub for now), Analytics (stub), Members.
  - Recent Activity feed: last 10 history entries from `tasks/{projectId}/*/history` sorted desc.
  - "Add New Project" — accessible from Dashboard FAB or Hub toolbar; opens `CreateProjectDialog`.
- `ui/project/members/MembersFragment` + `MembersViewModel`:
  - RecyclerView of `MemberCard` (avatar, name, role chip, contribution score badge, status).
  - WhatsApp link (`https://wa.me/{phone}` via `ACTION_VIEW`) and email (`mailto:`).
  - Role dropdown — editable only when current user has role `Team Lead` on this project.
  - Active/inactive toggle — also Team-Lead-gated. Inactive members rendered greyed out.
  - Invite Member dialog: email input → search RTDB `users/` → add to `projects/{id}/members` → trigger in-app notification (Phase 10 will wire FCM; for now write to `notifications/{invitedUserId}`).

**Spec refs:** PRD §7.3, §7.10 · Feature §4.1, §8.1 · Tech §5.2.

**Acceptance:**
- Completion % matches `(done tasks / total tasks) × 100` and stays in sync when tasks change (Phase 5 will wire the recompute on status change; here, just read the persisted value).
- Role/status edits blocked for non-leads.
- Invite by email finds existing users and writes the member node.

**Tests:** completion calc, role-gating predicate, invite-lookup repository.

---

### Phase 5 — Task Board, Task Detail, Create/Edit Task

**Team:** XL · **Screens:** Task Board, Task Detail, Create/Edit Task · **Page Weights:** 3.0 + 2.0 + 2.0

**Tasks:**
- `ui/project/tasks/TaskBoardFragment` + `TaskBoardViewModel`:
  - Toolbar toggle: Kanban (`HorizontalScrollView` with four RecyclerViews filtered by status) vs. List (single RecyclerView with status chips).
  - `FilterBottomSheet`: multi-select assignee chips, priority radio (All/Low/Medium/High), date range picker. Filter applied in-memory on the LiveData list.
  - Task card shows subtask ratio (e.g., 2/5) and `LinearProgressIndicator`.
  - Minimum: tap card to change status via dialog. Drag-and-drop is optional bonus.
  - FAB → `CreateEditTaskFragment` (new task).
- `ui/project/taskdetail/TaskDetailFragment` + `TaskDetailViewModel`:
  - Title, description, assignees, priority, deadline, status as chips.
  - Attachments: horizontal chips, PDFs/links open in browser, images in full-screen viewer.
  - Comments RecyclerView from `tasks/{projectId}/{taskId}/comments` with text input at bottom.
  - Expandable Activity History from `.../history` reverse-chronological.
  - Edit button → `CreateEditTaskFragment` with task pre-filled.
- `ui/project/taskedit/CreateEditTaskFragment` + `CreateEditTaskViewModel`:
  - Title (required), description (optional, multiline).
  - `MaterialDatePicker` for deadline (Unix timestamp).
  - Multi-select assignee dialog from project members.
  - Priority segmented control (`low | medium | high`).
  - Dynamic subtask rows (add/remove). Each subtask has `title`, `isDone`.
- **Status-change hook** in `TaskRepositoryImpl`: every status change writes a `HistoryEntry`. When status transitions to `done`:
  1. Recompute the actor's `contributionScore` using `util/ScoreCalculator` (formula in Feature §5.1).
  2. Recompute the project's `completionPercentage` and write to `projects/{projectId}`.
  3. Emit a "task-done" signal for Phase 11 badge engine to consume.

**Spec refs:** PRD §7.4–7.6 · Feature §4.2, §4.3, §4.4 · Tech §5.2.

**Acceptance:** all four status columns populated correctly; filters compose (assignee AND priority AND date range); subtask progress live-updates; status change persists and writes history.

**Tests:** filter composition, status transition, subtask-progress derivation, history-entry write, completion-% recompute.

---

### Phase 6 — Contribution Analytics + Groq AI

**Team:** HW · **Screen:** Contribution Analytics · **Page Weight:** 4.0 (highest)

**Tasks:**
- `ui/project/analytics/AnalyticsFragment` + `AnalyticsViewModel`:
  - MPAndroidChart `PieChart` of completed tasks per member. Tap slice → highlight member card below.
  - Grouped bar chart (or per-member cards) of `assigned` vs. `completed`.
  - Per-member contribution score list (color-coded badges).
  - Date range filter (start + end pickers) that re-filters the underlying task set.
- `data/source/remote/GroqApiSource` (OkHttp): `POST https://api.groq.com/openai/v1/chat/completions`, model `llama-3.3-70b-versatile`, Bearer `BuildConfig.GROQ_API_KEY`. Use the exact prompt template from Feature §5.1. Parse `choices[0].message.content` with `org.json.JSONObject`.
- `data/model/TeamHealthResult(status, summary, recommendations)` where `status ∈ {Healthy, Mild Imbalance, Severe Imbalance}`.
- Render Team Health as a color-coded card (green/yellow/red).
- `util/ScoreCalculator`:
  ```
  contributionScore = (completedTasks / totalAssignedTasks) * 100 * weightFactor
  weightFactor = 1 + (highPriorityCompleted * 0.2)
  ```

**Spec refs:** PRD §7.7 · Feature §5.1 · Tech §10.

**Acceptance:** Charts render with correct data; Groq call returns parsed result; network failure shows a non-crashing error state.

**Tests:** `ScoreCalculatorTest` for several distributions; `GroqApiSourceTest` parses canned JSON (success + malformed); `AnalyticsViewModelTest` for loading/error/success states.

---

### Phase 7 — Attendance

**Team:** HW · **Screen:** Attendance · **Page Weight:** 2.0

**Tasks:**
- `ui/project/attendance/AttendanceFragment` + `AttendanceViewModel`:
  - Session list sorted by date desc (each row: date + `present/total` summary).
  - Selected-session view: grid/list of members with attendance toggle.
  - Per-member percentage chip: `(attended / totalSessions) × 100`.
  - Export: build a plain text summary and launch share sheet via `ShareCompat.IntentBuilder`.
  - Next-session info card with "Set Reminder" button that schedules a WorkManager `OneTimeWorkRequest` for a local notification at `nextSessionDate`.
- `AttendanceRepository` writes `attendance/{projectId}/{sessionId}/records/{userId}`. RTDB rules already restrict writes to Team Lead (Tech §5.3) — UI must hide write controls for non-leads.

**Spec refs:** PRD §7.8 · Feature §6.1 · Tech §5.2, §5.3.

**Tests:** percentage math, session mapping, reminder-Worker scheduled with correct delay.

---

### Phase 8 — Peer Evaluation

**Team:** HW · **Screen:** Peer Evaluation · **Page Weight:** 2.0

**Tasks:**
- `ui/project/evaluation/PeerEvalFragment` + `PeerEvalViewModel`:
  - Four 1–5 star `RatingBar` widgets: Communication, Quality, Reliability, Effort.
  - Required multiline feedback `TextInputLayout`.
  - Submit writes to `evaluations/{projectId}/{periodId}/submissions/{evaluatorId}/{evaluateeId}`.
  - "Received" tab/section: evaluations where `evaluateeId == currentUid`.
  - Period status banner (open green / closed grey) driven by `evaluations/{projectId}/{periodId}/isOpen`.
- On submit, recompute the evaluatee's `users/{uid}/averagePeerRating` as the mean of all their received averages where `averageScore = (communication + quality + reliability + effort) / 4`.

**Spec refs:** PRD §7.9 · Feature §7.1.

**Tests:** average math, re-submission overwrite, period-gating, averagePeerRating recompute.

---

### Phase 9 — File Repository

**Team:** HW · **Screen:** File Repository · **Page Weight:** 1.75

**Tasks:**
- `ui/project/files/FileRepoFragment` + `FileRepoViewModel`:
  - RecyclerView of `FileCard` items from `files/{projectId}`. Type icons for `drive | github | other`.
  - FAB → `AddLinkBottomSheet` (label, URL, type, optional `versionNotes`).
  - Swipe-to-delete.
  - Version History section: entries whose `versionNotes` is non-empty, in chronological order.
  - Open link via `CustomTabsIntent`; fallback to default browser if Custom Tabs unavailable.

**Spec refs:** PRD §7.11 · Feature §9.1.

**Tests:** type filtering, add/remove repository calls, version-notes derivation.

---

### Phase 10 — Notifications (FCM + WorkManager + In-App)

**Team:** HW · **Screen:** Notifications · **Page Weight:** 2.0

**Tasks:**
- `data/source/remote/HwixelMessagingService : FirebaseMessagingService`:
  - On message receive: write a `Notification` row to `notifications/{currentUid}/{notifId}` and post a system notification via `NotificationManagerCompat`.
  - On new token: write to `users/{uid}/fcmToken`.
- `data/work/DeadlineReminderWorker` (WorkManager):
  - Schedules three `OneTimeWorkRequest` jobs (24h, 1h, 15min before deadline) tagged by `taskId` when a task's deadline is set or updated.
  - Cancels prior jobs for that `taskId` before scheduling new ones.
- `ui/notifications/NotificationsFragment` + `NotificationsViewModel`:
  - List from `notifications/{currentUid}` sorted desc.
  - Tapping marks `isRead = true` and navigates per `type`:
    - `task_assigned` | `mention` | `deadline` → `TaskDetailFragment(referenceId)`
    - `eval_open` | `eval_close` → `PeerEvalFragment(referenceId)`
    - `invite` → `ProjectHubFragment(referenceId)`
  - Toolbar "Mark All Read" → batch update.
  - Unread badge via `NotificationManagerCompat`.
- RTDB listener (registered in `Application` class) on `evaluations/{projectId}/{periodId}/isOpen`: on change, fan out `eval_open` / `eval_close` notifications to all project members.
- Trigger `task_assigned` notifications when `assignees` field is updated (handle in `TaskRepositoryImpl`).
- Trigger `mention` notifications when a new comment contains `@{userId}` substring.

**Notification model fields (Feature §10.1):**
`type | message | timestamp | isRead | referenceId`

**Spec refs:** PRD §7.12 · Feature §10.1 · Tech §12.

**Tests:** Worker scheduling logic (three jobs scheduled, prior jobs cancelled on update), unread-count derivation, mark-as-read state transitions, notification fan-out from `isOpen` change.

---

### Phase 11 — Profile & Settings

**Team:** HW · **Screen:** Profile and Settings · **Page Weight:** 1.75

**Tasks:**
- `ui/profile/ProfileFragment` + `ProfileViewModel`:
  - Portfolio card: `totalProjectsCompleted`, `averagePeerRating`.
  - Badge chips (icons + label) derived from `users/{uid}/badges`.
  - Dark-mode `SwitchMaterial`: persists to `SharedPreferences` and calls `AppCompatDelegate.setDefaultNightMode()`. Re-applied on `Application.onCreate()`.
  - Language selector (English, Bahasa Indonesia): calls `AppCompatDelegate.setApplicationLocales()` and persists to `SharedPreferences`.
  - Per-type notification toggles in `SharedPreferences`.
  - Edit profile dialog: name, Student ID, avatar via `ActivityResultContracts.GetContent`. Writes to both RTDB and Room.
- `util/BadgeEngine`:
  - `Top Contributor`: triggered on "task-done" signal from Phase 5; if the actor's `contributionScore` is strictly greater than every other member's in that project, write `"top_contributor"` to `users/{uid}/badges`.
  - `Deadline Crusher`: triggered on "task-done"; if `task.completedAt < task.deadline` and the user's lifetime count of such tasks `≥ 5`, write `"deadline_crusher"`.

**Spec refs:** PRD §7.13 · Feature §11.1 · Tech §11.

**Tests:** badge rule evaluation (both predicates), SharedPreferences persistence, dark-mode/locale application across activity restart.

---

### Phase 12 — Localization (EN + ID)

**Tasks:**
- Audit every layout XML and every Kotlin source file for hardcoded user-visible strings. Move them to `res/values/strings.xml`.
- Create `res/values-id/strings.xml` with the full Bahasa Indonesia translations.
- Confirm the Profile language selector switches the locale app-wide (next resume).

**Spec refs:** PRD §8 · Tech §11.

**Acceptance:** `grep` finds zero hardcoded user-visible strings in `app/src/main/res/layout/*.xml`; locale switch flips all 14 screens.

---

### Phase 13 — Testing Sweep & Code Quality

**Tasks:**
- Confirm every `ViewModel` and every `Repository` has at least one unit test (Tech §13 patterns).
- Add Espresso instrumented tests for: (a) login flow, (b) create task flow.
- Architectural pass: no business logic in any `Fragment` / `Activity`; no `Context` references in `ViewModel`s; only `Repository` classes import `FirebaseDatabase` or Room DAOs.
- Run a secret scan on the repo — confirm `local.properties`, `google-services.json`, keystores, and `BuildConfig.GROQ_API_KEY` never appear in git history.

**Spec refs:** PRD §8 · Tech §13, §14.

**Acceptance:** `./gradlew test connectedAndroidTest` passes. Coverage report shows ≥ one test per ViewModel/Repository.

---

### Phase 14 — Release & Play Store

**Tasks:**
- Enable R8 minification and resource shrinking for the `release` build type.
- Configure signing config from a keystore (path read from a non-committed `keystore.properties`).
- Build `.aab` via `./gradlew bundleRelease`.
- Manifest audit: confirm no `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `SEND_SMS`, `RECEIVE_SMS`, or `BILLING` permission.
- Deploy Firebase Security Rules from Tech §5.3.
- Create Play Console listing, upload `.aab`, complete data-safety form. PRD §9 awards 36 points for upload.

**Spec refs:** PRD §5, §9 · Tech §15.

**Acceptance:** Internal-testing track shows the release `.aab` live in Play Console.

---

## 3. Cross-Cutting Conventions

Apply these in every phase — they are not repeated per phase.

### Naming

| Concept | Pattern | Example |
|---|---|---|
| Screen | `XxxFragment` | `TaskBoardFragment` |
| ViewModel | `XxxViewModel` | `TaskBoardViewModel` |
| Factory | `XxxViewModelFactory` | `TaskBoardViewModelFactory` |
| UI state | `XxxUiState` sealed class | `LoginUiState` |
| Repository | `XxxRepository` + `XxxRepositoryImpl` | `TaskRepository` |
| Remote source | `XxxFirebaseSource` | `TaskFirebaseSource` |
| Room | `XxxEntity`, `XxxDao` | `TaskEntity`, `TaskDao` |

### MVVM

- `private val _x = MutableLiveData<T>()`
- `val x: LiveData<T> = _x`
- Suspend repository ops return `Result<T>`.
- Only `RepositoryImpl` may call `FirebaseDatabase.getInstance()` or a Room DAO.

### Strings

- Every user-visible string lives in `res/values/strings.xml`.
- Never use `setText("literal")` in Kotlin.

### Git (Tech §14)

```
<type>(<scope>): <short description>

types : feat | fix | refactor | test | docs | chore | style
scopes: auth | dashboard | tasks | analytics | attendance |
        evaluation | members | files | notifications | profile
```

No direct commits to `main` or `develop`. PRs only. Feature branches deleted after merge.

### Forbidden

- Location APIs (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)
- SMS (`SEND_SMS`, `RECEIVE_SMS`)
- Payments (`BILLING`)
- Hilt, Retrofit, Firebase Emulator (not used per Tech §3)

---

## 4. Glossary

| Term | Meaning |
|---|---|
| **XL** | Team XL — owns Login, Register, Dashboard, Project Hub, Task Board, Task Detail, Create/Edit Task. |
| **HW** | Team HW — owns Contribution Analytics, Attendance, Peer Evaluation, Project Members, File Repository, Notifications, Profile & Settings. |
| **MVVM** | View ↔ ViewModel ↔ Repository three-layer pattern (Tech §1). |
| **RTDB** | Firebase Realtime Database. The source of truth for all remote data (Tech §5.2). |
| **Room** | Local SQLite cache, read-immediately, sync-from-RTDB-after (Tech §6). |
| **ServiceLocator** | Manual DI singleton — replaces Hilt (Tech §4). |
| **Groq** | Free AI inference provider running `llama-3.3-70b-versatile` for Team Health (Tech §10). |
| **Team Lead** | Per-project role with write permissions to attendance, member roles, and active/inactive status (Tech §5.3). |

### Screen → Fragment → Phase

| Screen (PRD §7) | Fragment | Phase |
|---|---|---|
| Login | `LoginFragment` | 2 |
| Register | `RegisterFragment` | 2 |
| Dashboard | `DashboardFragment` | 3 |
| Project Hub | `ProjectHubFragment` | 4 |
| Task Board | `TaskBoardFragment` | 5 |
| Task Detail | `TaskDetailFragment` | 5 |
| Create/Edit Task | `CreateEditTaskFragment` | 5 |
| Contribution Analytics | `AnalyticsFragment` | 6 |
| Attendance | `AttendanceFragment` | 7 |
| Peer Evaluation | `PeerEvalFragment` | 8 |
| Project Members | `MembersFragment` | 4 |
| File Repository | `FileRepoFragment` | 9 |
| Notifications | `NotificationsFragment` | 10 |
| Profile and Settings | `ProfileFragment` | 11 |

### Key RTDB Paths (Tech §5.2)

```
users/{uid}
projects/{projectId}
projects/{projectId}/members/{userId}
tasks/{projectId}/{taskId}
tasks/{projectId}/{taskId}/comments/{commentId}
tasks/{projectId}/{taskId}/subtasks/{subtaskId}
tasks/{projectId}/{taskId}/history/{historyId}
attendance/{projectId}/{sessionId}/records/{userId}
evaluations/{projectId}/{periodId}/submissions/{evaluatorId}/{evaluateeId}
files/{projectId}/{fileId}
notifications/{userId}/{notificationId}
```
