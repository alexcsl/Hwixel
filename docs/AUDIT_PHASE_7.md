# Phase 7 Audit Report -- Attendance

**Branch:** `feature/phase12-localization` (all phases stacked)
**Date:** 2026-05-18
**Spec ref:** PRD §7.8 · Feature §6.1 · Tech §5.2, §5.3

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 1 |
| High | 2 |
| Medium | 1 |
| Low | 0 |

---

## Critical

### C-1: `sessionsMediator` never observed -- session list is permanently empty

**File:** [`ui/project/attendance/AttendanceViewModel.kt:41`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/attendance/AttendanceViewModel.kt)

`sessionsMediator` is a `MediatorLiveData<List<AttendanceSession>>` that adds `sessionSource` (a Firebase LiveData) as a source and forwards emissions to `_sessions`. The problem is that `sessionsMediator` is private and never exposed to any observer. A `MediatorLiveData` only activates its source subscriptions when it has at least one observer. Since nothing observes `sessionsMediator`, `sessionSource` is never subscribed, the Firebase `ValueEventListener` is never registered, and `_sessions.value` is never set.

The Fragment observes `sessions` (which is `_sessions`), but that LiveData never receives a value. Every user who opens the attendance screen sees a permanently empty list. Sessions created in Firebase do not appear.

**Fix:** Either expose `sessionsMediator` as the public `sessions: LiveData<...>` the Fragment observes, or remove the mediator and observe `sessionSource` directly in the ViewModel `init` block via `observeForever` managed in `onCleared`.

---

## High

### H-1: `AttendanceFirebaseSource.observeSessions` leaks Firebase listener on every call

**File:** [`data/source/remote/AttendanceFirebaseSource.kt:16`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/AttendanceFirebaseSource.kt)

`observeSessions` creates a `MutableLiveData`, registers an `addValueEventListener` on the Firebase node immediately (not lazily), and returns the LiveData without ever removing the listener. Contrast this with the correct pattern used in `FileFirebaseSource` and `NotificationFirebaseSource`, which override `onActive()` / `onInactive()` to attach and detach the listener with the lifecycle. The `AttendanceFirebaseSource` pattern means:

1. The listener is registered even when the LiveData has no observers.
2. Every time the ViewModel is created (every navigation to the attendance screen), a new listener is added on top of all previous ones, multiplying data delivery and Firebase read counts.

**Fix:** Rewrite `observeSessions` using the `onActive` / `onInactive` pattern (anonymous `LiveData` subclass as used in `FileFirebaseSource` and `NotificationFirebaseSource`).

---

### H-2: `scheduleReminder` accepts `Context` as a parameter -- MVVM violation

**File:** [`ui/project/attendance/AttendanceViewModel.kt:88`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/attendance/AttendanceViewModel.kt)

```kotlin
fun scheduleReminder(context: Context, projectName: String, nextSessionDate: Long)
```

The MVVM contract (Tech §1, PHASES §3) explicitly prohibits ViewModels from holding or receiving `Context` or `Activity` references. Passing `Context` as a function argument is the same violation -- the ViewModel is using the Context to call `WorkManager.getInstance(context)`. On configuration change the Fragment hands in a stale Context; if the Fragment is destroyed before the call completes, the Context could be invalid.

**Fix:** Inject `WorkManager` via the factory at construction time (using `WorkManager.getInstance(application)` from an `AndroidViewModel` subclass), or expose a `scheduleReminder(projectName, nextSessionDate)` API that returns a WorkRequest the Fragment submits directly.

---

## Medium

### M-1: `buildExportText` uses hardcoded "Present" / "Absent" strings

**File:** [`ui/project/attendance/AttendanceViewModel.kt:141`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/attendance/AttendanceViewModel.kt)

```kotlin
sb.appendLine("  - $name: ${if (present) "Present" else "Absent"}")
```

This string literal is not in `strings.xml` and will not be translated when the user has selected Bahasa Indonesia. Every exported attendance report will always display "Present" / "Absent" in English regardless of locale.

**Fix:** Move the strings to `strings.xml` and `values-in/strings.xml`. The ViewModel cannot call `context.getString()`, so the simplest fix is to pass them in as parameters from the Fragment, or return a structured data class that the Fragment formats.

---

## What Is Working Correctly

- `toggleAttendance` correctly gates writes to Team Leads only (line 77).
- `createSession` and `markAttendance` delegate to the repository and handle success/failure through `AttendanceUiState`.
- `onCleared` removes `sessionSource` from the mediator.
- WorkManager `ExistingWorkPolicy.REPLACE` correctly cancels a previous reminder when a new one is set.
- The Fragment uses `ShareCompat.IntentBuilder` as required by the spec.
