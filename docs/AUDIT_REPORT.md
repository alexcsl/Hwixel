# Hwixel Full Codebase Audit Report

**Branch audited:** `feature/phase12-localization` (all phases stacked: 7 through 12)
**Date:** 2026-05-18
**Auditor:** Manual static analysis

---

## Executive Summary

The audit covered the entire application codebase including all data, UI, worker, utility, and test layers. The findings are grouped into four severity tiers: Critical, High, Medium, and Low.

Total findings: **14**

| Severity | Count |
|----------|-------|
| Critical | 2 |
| High | 4 |
| Medium | 6 |
| Low | 2 |

---

## Critical

### C-1: PeerEvalViewModel -- eval data pipeline is permanently dead

**File:** `app/src/main/java/edu/bluejack252/hwixel/ui/project/evaluation/PeerEvalViewModel.kt`

`periodsMediator` is a private `MediatorLiveData` that subscribes to `periodsSource` (a Firebase `LiveData`) and writes results into `_periods`. The problem is that `periodsMediator` is never exposed to the Fragment and has no observers. A `MediatorLiveData` only activates its source subscriptions when it has at least one active observer. Because nothing observes `periodsMediator`, `periodsSource` is never subscribed, the Firebase listener is never registered, and `_periods` is never populated.

Every user who opens the peer evaluation screen sees a permanently empty list. The feature is silently broken.

**Fix:** Either expose `periodsMediator` as the public `periods: LiveData<...>` the Fragment observes, or remove the mediator and observe `periodsSource` directly within the ViewModel `init` block using `observeForever` (managed with `onCleared`).

---

### C-2: `UserFirebaseSource.findByEmail` violates RTDB security rules and is O(n)

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/source/remote/UserFirebaseSource.kt`

`findByEmail()` performs an `addListenerForSingleValueEvent` on the root `/users` node and then iterates all children in memory to find a matching email. This has two compounding problems:

1. **Security rule denial at runtime.** The RTDB rules only allow a user to read `/users/{theirOwnUid}`. Reading `/users` (the entire collection) is denied, so the listener receives a `PermissionDenied` error and the lookup always fails for any caller other than an admin.
2. **O(n) scan.** Even if rules were opened, loading the entire users collection for an email lookup is unbounded and would exhaust data quotas on any reasonably sized user base.

This is used during the "add member to project by email" flow. The feature silently fails in production.

**Fix:** Add a `/usersByEmail/{emailHash}/uid` index in the RTDB schema and rules, or use Firebase Auth's `fetchSignInMethodsForEmail` to resolve email to UID without reading the user collection directly.

---

## High

### H-1: `ProjectRepository` -- member and score writes bypass Room cache

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/repository/ProjectRepository.kt`

`addMember()`, `updateMember()`, `updateMemberScore()`, and `updateCompletionPercentage()` delegate exclusively to `firebaseSource`. None of them call a corresponding Room `update` or `upsert`. The Room database (the offline-first source of truth for project data) goes stale after any of these writes. Observers reading from Room will display outdated member lists and scores until the next full sync from Firebase. In offline scenarios, these writes are lost from the local cache entirely.

**Fix:** After each successful Firebase write in those four methods, call the appropriate `projectDao.upsert(...)` to mirror the change locally.

---

### H-2: `ServiceLocator.getNotificationSource()` creates a new instance on every call

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/ServiceLocator.kt` (line 109)

Every other `get*()` method in `ServiceLocator` follows the pattern:

```kotlin
private var fooRef: FooClass? = null
fun getFoo() = fooRef ?: FooClass(...).also { fooRef = it }
```

`getNotificationSource()` is the sole exception -- it constructs and returns a new `NotificationFirebaseSource()` on every invocation with no caching. Callers that call it multiple times (e.g., the ViewModel and the messaging service) each get a different instance, meaning separate Firebase listener registrations and inconsistent in-memory state.

**Fix:** Add a `private var notificationSourceRef: NotificationFirebaseSource? = null` field and follow the same caching pattern as every other getter.

---

### H-3: `UserDao` missing `delete()` method

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/source/local/UserDao.kt`

`ProjectDao` and `TaskDao` both expose a `@Delete` method. `UserDao` does not. This makes it impossible to evict a stale user record from Room (e.g., on sign-out or when a user is removed from a project). Cache entries accumulate and can surface deleted users.

**Fix:** Add `@Delete suspend fun delete(user: UserEntity)` to `UserDao`.

---

### H-4: `ProjectDao` missing `getById()` method

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/source/local/ProjectDao.kt`

`TaskDao` exposes `getById(id: String): TaskEntity?` for single-record lookups. `ProjectDao` only exposes `getAll()`. Repositories that need a single project by ID must load the entire table and filter in memory, which is wasteful and inconsistent.

**Fix:** Add `@Query("SELECT * FROM projects WHERE id = :id LIMIT 1") suspend fun getById(id: String): ProjectEntity?` to `ProjectDao`.

---

## Medium

### M-1: `EvalRepositoryImpl` instantiates Firebase directly -- MVVM violation

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/repository/EvalRepository.kt` (approx. line 27)

`EvalRepositoryImpl` constructs `FirebaseDatabase.getInstance().reference.child("users")` directly in its constructor, bypassing the `RemoteSource` abstraction layer that every other repository uses. It also declares `notifSource: NotificationFirebaseSource = NotificationFirebaseSource()` as a default parameter, creating a fresh instance rather than using `ServiceLocator.getNotificationSource()`.

This makes the class un-testable (cannot inject a fake Firebase instance), breaks the architecture contract (only `*FirebaseSource` classes should touch Firebase), and creates yet another untracked `NotificationFirebaseSource` instance.

**Fix:** Introduce an `EvalFirebaseSource` class (or extend `UserFirebaseSource`) to encapsulate the Firebase access. Wire through `ServiceLocator`. Use `ServiceLocator.getNotificationSource()` for notifications.

---

### M-2: `HwixelMessagingService` leaks a `CoroutineScope`

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/source/remote/HwixelMessagingService.kt` (lines 25, 39)

The service launches coroutines with `CoroutineScope(Dispatchers.IO).launch { ... }`. This scope is anonymous -- it has no lifecycle binding. If the service is destroyed while coroutines are still running, they continue running on the leaked scope. This also means exceptions in those coroutines are silently swallowed rather than reported via a structured hierarchy.

**Fix:** Override `onCreate` / `onDestroy` in the service and maintain a `private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` that is cancelled in `onDestroy`. Or use `lifecycleScope` if the service extends `LifecycleService`.

---

### M-3: `AttendanceReminderWorker` notification body is a hardcoded format string

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/work/AttendanceReminderWorker.kt`

The notification body is constructed as `"$projectName - $sessionDate"` -- a hardcoded English format not represented in `strings.xml`. This string is not translated and will always appear in English regardless of the user's chosen locale.

**Fix:** Add a `notification_attendance_reminder_body` string resource (in both `values/strings.xml` and `values-in/strings.xml`) with a format placeholder, and use `applicationContext.getString(R.string.notification_attendance_reminder_body, projectName, sessionDate)`.

---

### M-4: `TaskBoardViewModel.applyFilter()` ignores the date range field

**File:** `app/src/main/java/edu/bluejack252/hwixel/ui/project/tasks/TaskBoardViewModel.kt`

`applyFilter()` handles assignee and priority filtering but the `TaskFilter` data class has a date range field (`dueBefore` / `dueAfter` or equivalent) that is never read in the filter logic. The PHASES spec lists date range filtering as a required task board feature. Tasks outside the requested date window are not excluded.

**Fix:** Add a date range check inside `applyFilter()` comparing `task.dueDate` against the filter's date bounds.

---

### M-5: Firebase POJO deserialization for `Task` nested maps may silently fail

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/model/DomainModels.kt`

`Task` uses `Map<String, Comment>`, `Map<String, Subtask>`, and `Map<String, HistoryEntry>` as field types. Firebase's `getValue(Task::class.java)` POJO deserializer can deserialize `Map<String, Any>` (raw maps) but does not automatically convert nested maps to typed domain objects when the container is a generic `Map<String, DomainType>`. In practice this means `task.comments`, `task.subtasks`, and `task.history` may come back as `Map<String, Map<String, Any>>` (raw nested maps) at runtime, causing `ClassCastException` wherever they are iterated as typed objects.

**Fix:** Either use a manual `DataSnapshot.children` iteration with explicit `getValue(Comment::class.java)` calls in the remote source, or mark the nested types with `@IgnoreExtraProperties` and switch to manual snapshot parsing.

---

### M-6: `fallbackToDestructiveMigration` will silently wipe user data on schema change

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/source/local/HwixelDatabase.kt`

The Room database is built with `fallbackToDestructiveMigration(dropAllTables = true)`. This is convenient during development but means that any accidental version bump (or a missing `Migration` object) will drop every cached table on the user's device on upgrade. The offline-first design relies on the Room cache being intact between sessions.

**Fix:** Before shipping, remove `fallbackToDestructiveMigration` and write explicit `Migration` objects for every version pair. If the data is fully re-syncable from Firebase on first launch, document this assumption explicitly and add a post-migration sync trigger.

---

## Low

### L-1: `User` domain model has a `phone` field absent from the RTDB schema

**File:** `app/src/main/java/edu/bluejack252/hwixel/data/model/DomainModels.kt` (line 8)

`User` declares a `phone: String` field, but the RTDB schema documented in `Hwixel_Technical_Docs.md` has no `/users/{uid}/phone` node. The field will always deserialize as an empty string, will never be written to Firebase, and any UI that surfaces it will show blank data. This is a dead field that adds noise.

**Fix:** Either add `phone` to the RTDB schema and update the security rules / write paths accordingly, or remove the field from the domain model.

---

### L-2: `BadgeEngine.shouldAwardTopContributor` is scoped to a single project

**File:** `app/src/main/java/edu/bluejack252/hwixel/util/BadgeEngine.kt`

`shouldAwardTopContributor` determines the top contributor by comparing scores within the member map of a single project. If a user participates in multiple projects and achieves the highest score in only one of them, they receive the badge. The evaluation does not account for cross-project ranking and does not match the "top contributor across all projects" intent implied by the feature docs.

**Fix:** If the badge is meant to be per-project, document this clearly. If it is meant to be global, accumulate scores across all projects the user belongs to before comparing.

---

## Summary Table

| ID  | Severity | File | Description |
|-----|----------|------|-------------|
| C-1 | Critical | `PeerEvalViewModel.kt` | `periodsMediator` never observed; eval pipeline is dead |
| C-2 | Critical | `UserFirebaseSource.kt` | `findByEmail` denied by RTDB rules; O(n) full-table scan |
| H-1 | High | `ProjectRepository.kt` | Member/score writes skip Room cache |
| H-2 | High | `ServiceLocator.kt` | `getNotificationSource()` creates new instance every call |
| H-3 | High | `UserDao.kt` | Missing `delete()` method |
| H-4 | High | `ProjectDao.kt` | Missing `getById()` method |
| M-1 | Medium | `EvalRepository.kt` | Direct Firebase instantiation; MVVM violation |
| M-2 | Medium | `HwixelMessagingService.kt` | Anonymous `CoroutineScope` leaks on service destroy |
| M-3 | Medium | `AttendanceReminderWorker.kt` | Notification body is hardcoded; not translated |
| M-4 | Medium | `TaskBoardViewModel.kt` | Date range filter field never applied |
| M-5 | Medium | `DomainModels.kt` | `Task` nested map types may not deserialize via Firebase POJO |
| M-6 | Medium | `HwixelDatabase.kt` | `fallbackToDestructiveMigration` risks silent data wipe on upgrade |
| L-1 | Low | `DomainModels.kt` | `User.phone` field not present in RTDB schema |
| L-2 | Low | `BadgeEngine.kt` | Top-contributor check is per-project, not cross-project |
