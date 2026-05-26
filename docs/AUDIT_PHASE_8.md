# Phase 8 Audit Report -- Peer Evaluation

**Branch:** `feature/phase12-localization` (all phases stacked)
**Date:** 2026-05-18
**Spec ref:** PRD §7.9 · Feature §7.1

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 1 |
| High | 1 |
| Medium | 2 |
| Low | 0 |

---

## Critical

### C-1: `periodsMediator` never observed -- entire eval data pipeline is dead

**File:** [`ui/project/evaluation/PeerEvalViewModel.kt:45`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/evaluation/PeerEvalViewModel.kt)

`periodsMediator` is a private `MediatorLiveData<List<String>>`. It adds `periodsSource` (a Firebase LiveData) as a source. When `periodsSource` emits, the handler calls `_periods.value = ids` and then `subscribeToPeriod(latest)` which wires up `_isPeriodOpen`, `_submittedByMe`, and `_receivedEvals`. This entire chain depends on `periodsSource` being activated.

`periodsMediator` is never exposed to the Fragment and has no observers. A `MediatorLiveData` only activates its source subscriptions when it has at least one active observer. Since `periodsMediator` is never observed, `periodsSource` is never subscribed, no Firebase listener is registered, and `_periods` is never populated. Because `subscribeToPeriod` is never called, `_isPeriodOpen`, `_submittedByMe`, and `_receivedEvals` are also never populated.

The Fragment observes `periods`, `isPeriodOpen`, `submittedByMe`, and `receivedEvals` -- all of which remain at their initial empty/false defaults forever. The peer evaluation screen appears permanently empty, and the submit flow cannot proceed because `isPeriodOpen` is always `false`.

**Fix:** Expose `periodsMediator` as the public `periods: LiveData<List<String>>` the Fragment observes, replacing the current `_periods` / `val periods = _periods` pattern. With at least one observer on `periodsMediator`, `periodsSource` will activate and the chain will work.

---

## High

### H-1: `EvalRepositoryImpl.updateAveragePeerRating` writes directly to Firebase -- MVVM violation

**File:** [`data/repository/EvalRepository.kt:27`](../app/src/main/java/edu/bluejack252/hwixel/data/repository/EvalRepository.kt)

```kotlin
private val usersRef = FirebaseDatabase.getInstance().reference.child("users")
```

`EvalRepositoryImpl` instantiates `FirebaseDatabase` directly at construction time and uses it in `updateAveragePeerRating` to write `/users/{uid}/averagePeerRating`. The MVVM contract requires that only `*FirebaseSource` classes call `FirebaseDatabase.getInstance()`. Repositories may only call into source classes.

This also creates an untestable dependency -- unit tests cannot inject a fake Firebase reference because the field is hard-coded in the constructor body.

**Fix:** Add `updateAveragePeerRating(userId: String, average: Float)` to `UserFirebaseSource` (or a new dedicated method on `UserRemoteSource`) and call it from `EvalRepositoryImpl` via constructor injection.

---

## Medium

### M-1: `EvalRepositoryImpl.notifSource` default creates an untracked `NotificationFirebaseSource` instance

**File:** [`data/repository/EvalRepository.kt:24`](../app/src/main/java/edu/bluejack252/hwixel/data/repository/EvalRepository.kt)

```kotlin
class EvalRepositoryImpl(
    private val firebaseSource: EvalFirebaseSource,
    private val notifSource: NotificationFirebaseSource = NotificationFirebaseSource()
)
```

`ServiceLocator.getEvalRepository()` calls `EvalRepositoryImpl(firebaseSource = EvalFirebaseSource())` without passing a `notifSource`. The default parameter therefore creates a new `NotificationFirebaseSource()` instance that is not registered in `ServiceLocator`. `ServiceLocator.getNotificationSource()` already has this bug (it returns a new instance every call), but this adds a third independent instance. Notification state held in-memory by each instance is not shared.

**Fix:** Pass `ServiceLocator.getNotificationSource()` explicitly in the `ServiceLocator.getEvalRepository()` factory call.

---

### M-2: `EvalFirebaseSource.observeReceivedEvals` downloads the entire submissions subtree

**File:** [`data/source/remote/EvalFirebaseSource.kt:43`](../app/src/main/java/edu/bluejack252/hwixel/data/source/remote/EvalFirebaseSource.kt)

```kotlin
return FirebaseValueLiveData(
    db.child("evaluations").child(projectId).child(periodId).child("submissions")
) { snapshot ->
    snapshot.children.forEach { evaluatorSnap -> ... }
}
```

This listener downloads every evaluator's submissions for the entire period. In a project with 10 members, each having evaluated 9 others, that is 90 submission nodes downloaded every time any submission changes -- just to show the current user's received evaluations. As team size and period count grow, this read becomes expensive in both bandwidth and Firebase costs.

**Fix:** Store an additional reverse-index at `evaluations/{projectId}/{periodId}/received/{evaluateeId}/{evaluatorId}` so that a user's received evals can be read without fetching the entire submissions tree.

---

## What Is Working Correctly

- The 4-criteria average formula (lines 151-156) matches the spec: `(communication + quality + reliability + effort) / 4`.
- Self-evaluation is blocked at the ViewModel level (line 115).
- Period-closed submissions are blocked at the ViewModel level (line 111).
- `subscribeToPeriod` properly removes old sources before adding new ones (lines 63-65), preventing duplicate listener registrations.
- `onCleared` removes all sources from `periodsMediator`.
- The Fragment seeds member data from nav args rather than making a Firebase call, keeping the Fragment lean.
