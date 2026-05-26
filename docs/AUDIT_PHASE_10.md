# Phase 10 Audit Report -- Notifications (FCM + WorkManager + In-App)

**Branch:** `feature/phase12-localization` (all phases stacked)
**Date:** 2026-05-18
**Spec ref:** PRD §7.12 · Feature §10.1 · Tech §12

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0 |
| High | 2 |
| Medium | 3 |
| Low | 1 |

---

## High

### H-1: `HwixelMessagingService` uses an anonymous `CoroutineScope` that leaks on service destruction

**File:** [`data/source/remote/HwixelMessagingService.kt:25`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/HwixelMessagingService.kt)

Both `onNewToken` and `onMessageReceived` launch coroutines via `CoroutineScope(Dispatchers.IO).launch { ... }`. This anonymous scope has no lifecycle binding. If the service is destroyed while the coroutine is still running (e.g., writing the FCM token or the notification record to Firebase), the coroutine continues on an orphaned scope. Exceptions are silently swallowed rather than propagated through a structured hierarchy.

**Fix:** Override `onCreate` and `onDestroy` in the service to manage a `private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`, cancelling it in `onDestroy`. All coroutines launched from `onNewToken` and `onMessageReceived` should use `serviceScope.launch`.

---

### H-2: `handleNotifTap` routes `eval_open` and `eval_close` to `ProjectHubFragment` -- spec violation

**File:** [`ui/notifications/NotificationsFragment.kt:100`](../app/src/main/java/edu/bluejack252/hwixel/ui/notifications/NotificationsFragment.kt)

```kotlin
"eval_open", "eval_close", "invite" -> {
    findNavController().navigate(
        actionNotificationsFragmentToProjectHubFragment(projectId = notif.referenceId)
    )
}
```

The spec (Feature §10.1) requires:
- `eval_open` | `eval_close` → `PeerEvalFragment(referenceId)`
- `invite` → `ProjectHubFragment(referenceId)`

All three types are grouped together and routed to `ProjectHubFragment`. Tapping an eval notification navigates to the project hub instead of the peer evaluation screen, losing the direct deep-link benefit.

**Fix:** Separate the two cases. `eval_open` and `eval_close` should navigate to `PeerEvalFragment` (using the appropriate nav action with `projectId = notif.referenceId`). `invite` can remain on `ProjectHubFragment`.

---

## Medium

### M-1: Application-level `isOpen` listener for eval fan-out is not implemented

**Spec:** Phase 10 tasks: "RTDB listener (registered in `Application` class) on `evaluations/{projectId}/{periodId}/isOpen`: on change, fan out `eval_open` / `eval_close` notifications to all project members."

**File:** [`HwixelApplication.kt`](../app/src/main/java/edu/bluejack252/hwixel/HwixelApplication.kt)

`HwixelApplication.onCreate` only calls `applyAppearance()`. There is no RTDB listener for `isOpen`. The fan-out only fires when a Team Lead explicitly calls `EvalRepositoryImpl.setPeriodOpen()`. If the period state is changed by an admin directly in the Firebase console, no notifications are sent. The spec's intent is to make the fan-out reactive to the RTDB state itself, not just to UI actions.

**Fix:** Register a `ChildEventListener` on `evaluations` in `HwixelApplication` that fires when any `isOpen` node changes, and calls `NotificationRepositoryImpl.writeToProjectMembers` for the affected project.

---

### M-2: `HwixelMessagingService.onMessageReceived` creates `NotificationFirebaseSource()` directly

**File:** [`data/source/remote/HwixelMessagingService.kt:41`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/HwixelMessagingService.kt)

```kotlin
NotificationFirebaseSource().writeNotification(uid, type, message, referenceId)
```

This creates a fresh `NotificationFirebaseSource` instance outside of `ServiceLocator`, same as the `ServiceLocator.getNotificationSource()` caching bug (documented in AUDIT_REPORT.md H-2). With each FCM message received, a new instance is spun up and immediately discarded after one write.

**Fix:** Use `ServiceLocator.getNotificationSource().writeNotification(...)` and fix the `getNotificationSource()` caching issue at the same time.

---

### M-3: `writeNotification` uses `System.currentTimeMillis()` for timestamp -- device clock skew risk

**File:** [`data/source/remote/NotificationFirebaseSource.kt:65`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/NotificationFirebaseSource.kt)

```kotlin
"timestamp" to System.currentTimeMillis(),
```

Notifications are displayed sorted by `timestamp` descending. A device with a wrong system clock will write an incorrect timestamp to Firebase. The notification will sort to the wrong position and may appear buried or at the top of the list regardless of when it arrived. Other Firebase writes in the codebase use `ServerValue.TIMESTAMP` (e.g., `EvalFirebaseSource.createPeriod`).

**Fix:** Use `ServerValue.TIMESTAMP` for the `timestamp` field. This requires changing the write to `updateChildren` and the local `timestamp` field to hold `Any` (or reading the assigned server timestamp via a callback).

---

## Low

### L-1: `cancelAllWorkByTag` before re-schedule is asynchronous -- duplicate notification race condition

**File:** [`util/DeadlineScheduler.kt:15`](../app/src/main/java/edu/bluejack252/hwixel/util/DeadlineScheduler.kt)

```kotlin
wm.cancelAllWorkByTag("deadline_$taskId")
// ... immediately enqueues 3 new requests
```

`WorkManager.cancelAllWorkByTag` is asynchronous. There is no guarantee the cancellation finishes before the new work is enqueued. In rare cases (task deadline updated rapidly), both the old and new reminder sets may be active simultaneously, producing duplicate notifications. This is an edge case but violates the spec's requirement to cancel prior jobs before scheduling new ones.

**Fix:** Use `ExistingWorkPolicy.REPLACE` for each of the three unique work names (`deadline_{taskId}_24h`, `deadline_{taskId}_1h`, `deadline_{taskId}_15m`) instead of a cancel-then-enqueue pattern. `REPLACE` atomically cancels and re-schedules in one operation.

---

## What Is Working Correctly

- `NotificationsFragment` correctly marks a notification as read on tap before navigating.
- "Mark All Read" batch update uses a Firebase `updateChildren` multi-path write, which is atomic.
- `NotificationsViewModel` derives `unreadCount` via a `LiveData.map` transform rather than a separate query.
- `NotificationFirebaseSource` uses the `onActive` / `onInactive` lifecycle pattern correctly -- no listener leaks.
- `DeadlineReminderWorker` correctly uses string resources for all three time-window body texts (24h, 1h, 15m).
- `DeadlineScheduler.cancel` is correctly called elsewhere to clean up workers when tasks are deleted.
- `DeadlineReminderWorker` checks `NotificationManagerCompat.areNotificationsEnabled()` before posting, correctly handling the Android 13+ `POST_NOTIFICATIONS` permission.
