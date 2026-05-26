# Phase 11 Audit Report -- Profile & Settings

**Branch:** `feature/phase12-localization` (all phases stacked)
**Date:** 2026-05-18
**Spec ref:** PRD §7.13 · Feature §11.1 · Tech §11

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0 |
| High | 1 |
| Medium | 1 |
| Low | 1 |

---

## High

### H-1: `BadgeEngine.shouldAwardDeadlineCrusher` counts only current-project tasks -- spec says "lifetime count"

**File:** [`util/BadgeEngine.kt:21`](../app/src/main/java/edu/bluejack252/hwixel/util/BadgeEngine.kt) and [`data/repository/TaskRepository.kt:148`](../app/src/main/java/edu/bluejack252/hwixel/data/repository/TaskRepository.kt)

The spec (Phase 11 tasks) states: "Deadline Crusher: triggered on task-done; if `task.completedAt < task.deadline` and the user's **lifetime count** of such tasks ≥ 5."

In `TaskRepositoryImpl.awardBadges`, `BadgeEngine.shouldAwardDeadlineCrusher` is called with `allTasks = firebaseSource.fetchTasksOnce(projectId)` -- which fetches only the tasks of the **current project**. A user who completes 3 tasks on-time in project A and 3 in project B will never accumulate the threshold of 5 because each check sees only the tasks from one project.

**Fix:** Fetch tasks across all projects the user is a member of before calling `shouldAwardDeadlineCrusher`. One approach: add a `fetchTasksByUser(userId)` method to `TaskRemoteSource` that queries all projects the user belongs to and aggregates their on-time tasks. Alternatively, maintain a denormalized counter at `users/{uid}/onTimeTaskCount` that is incremented on each on-time completion.

---

## Medium

### M-1: `HwixelApplication` creates a separate `SharedPrefsProfileSettingsRepository` instance not from `ServiceLocator`

**File:** [`HwixelApplication.kt:10`](../app/src/main/java/edu/bluejack252/hwixel/HwixelApplication.kt)

```kotlin
override fun onCreate() {
    super.onCreate()
    SharedPrefsProfileSettingsRepository(this).applyAppearance()
}
```

This creates a new, uncached `SharedPrefsProfileSettingsRepository` instance. `ServiceLocator.getProfileSettingsRepository(context)` creates and caches a separate instance. The two instances both read and write the same `SharedPreferences` file, so data is consistent, but there are now two instances of the same class in memory. If either instance ever holds in-memory state beyond SharedPreferences (e.g., a cached locale value), they will diverge.

**Fix:** Use `ServiceLocator.getProfileSettingsRepository(this).applyAppearance()` in `HwixelApplication.onCreate` so that there is a single shared instance from startup.

---

## Low

### L-1: `BadgeEngine.shouldAwardTopContributor` is per-project -- intent is ambiguous

**File:** [`util/BadgeEngine.kt:12`](../app/src/main/java/edu/bluejack252/hwixel/util/BadgeEngine.kt)

```kotlin
fun shouldAwardTopContributor(actorId: String, members: Map<String, ProjectMember>): Boolean
```

The function compares contribution scores within the `members` map of a single project. The spec does not explicitly say whether "Top Contributor" is per-project or global. The badge label ("Top Contributor") and its per-project trigger (called in `awardBadges` inside `TaskRepositoryImpl` with one project's member map) suggest it is intentionally per-project, but if a user is the top contributor in one project they receive the badge permanently, even if they subsequently score lowest in another project.

This is less a bug and more an undocumented design decision. However, the badge is never revoked once awarded (because `BadgeEngine.withBadge` only adds, never removes), which means the badge is effectively permanent after the first qualifying trigger regardless of future performance.

**Fix:** Clarify the intended scope in the spec. If per-project, document it explicitly. If the badge should be re-evaluated periodically, add a `shouldRevoke` path in the badge engine.

---

## What Is Working Correctly

- `ProfileViewModel` uses a `MediatorLiveData<ProfileUiState>` that wraps the User LiveData source correctly -- the source is removed in the `observeUser` method when refreshed.
- `ProfileFragment` sets `isRendering = true` before applying LiveData state to UI controls, preventing listener re-entry (setting a Switch triggers `setOnCheckedChangeListener`, which would call the ViewModel again without this guard).
- `SharedPrefsProfileSettingsRepository.setLanguageTag` normalises unsupported tags to the default before persisting and calling `setApplicationLocales`, preventing invalid locale states.
- `SharedPrefsProfileSettingsRepository.applyAppearance` is called in `HwixelApplication.onCreate`, ensuring dark mode and locale are restored on every app launch before the first Activity renders.
- View Binding is correctly nulled in `ProfileFragment.onDestroyView` (`_binding = null`, `_editBinding = null`), preventing view reference leaks.
- `BadgeEngine.withBadge` is idempotent -- duplicate calls for an already-held badge are no-ops.
- The edit profile dialog uses `MaterialAlertDialogBuilder` with a positive button override (`setOnShowListener`) that validates before dismissing, preventing empty-field saves.
