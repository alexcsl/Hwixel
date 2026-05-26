# Frontend & UI Audit Report — Hwixel (TPA Mobile)

> **Scope**: All layout XML, adapter code, and fragment rendering logic across all merged and open-PR screens.  
> **Target**: Android 35 (API 35), Android Studio Meerkat (2024.3.1+).  
> **Date**: 2026-05-19  
> **Auditor**: Claude Code (automated deep-read)  
> **Status**: READ-ONLY — no code changes made.

---

## Severity Legend

| Level | Meaning |
|---|---|
| 🔴 CRITICAL | Crash, blank screen, or completely broken flow in normal use |
| 🟠 HIGH | Broken state handling, broken UX path, wrong API for target SDK |
| 🟡 MEDIUM | Visual inconsistency, missing state, layout defect on specific devices |
| 🔵 LOW | Code style, minor redundancy, lint warning |

---

## 1. Loading States

### 1.1 🔴 Dashboard has no loading indicator — blank screen on cold start

**File**: [`fragment_dashboard.xml`](../app/src/main/res/layout/fragment_dashboard.xml), [`DashboardFragment.kt:81–91`](../app/src/main/java/edu/bluejack252/hwixel/ui/dashboard/DashboardFragment.kt)

`DashboardUiState` has no `isLoading` field. On cold start, `projects` and `tasks` lists are empty while Firebase responds asynchronously. The fragment renders immediately with empty lists, showing `emptyProjectsTextView` and `emptyDeadlinesTextView` ("No projects" / "No upcoming deadlines"). The user has no indication that data is being fetched; the app appears broken or empty for 1–3 seconds.

**Missing**: A `ProgressBar` or `ShimmerLayout` shown while data loads, hidden once the first non-null payload arrives.

---

### 1.2 🔴 `TaskDetailFragment` shows a blank white screen while loading

**File**: [`TaskDetailFragment.kt:116–117`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/taskdetail/TaskDetailFragment.kt)

```kotlin
private fun render(state: TaskDetailUiState) {
    val task = state.task ?: return    // ← returns on null — nothing shown
    ...
}
```

`TaskDetailUiState.isLoading` is set to `true` on ViewModel init (`_uiState.value = TaskDetailUiState(isLoading = true)`), but the fragment never reads `state.isLoading`. The early return on `state.task == null` means the entire screen is blank until Firebase returns the task. On slow connections, or if the task ID is invalid, the user sees a blank screen indefinitely.

---

### 1.3 🔴 `ProjectHubFragment.render()` silently returns when project is null

**File**: [`ProjectHubFragment.kt:97–101`](../app/src/main/java/edu/bluejack252/hwixel\ui/project/hub/ProjectHubFragment.kt)

```kotlin
private fun render(state: ProjectHubUiState) {
    val project = state.project ?: return   // ← blank screen on null
    ...
}
```

Same pattern as Task Detail. If `observeProject()` hasn't yet emitted (network latency), or if the project ID is wrong (invalid navigation), the entire hub content area stays blank. The tab layout and AppBarLayout are visible, but all content areas are empty with no loading feedback.

---

### 1.4 🟠 `MembersFragment` renders nothing during initial load

**File**: [`fragment_members.xml`](../app/src/main/res/layout/fragment_members.xml)

`MembersUiState.isLoading` is set to `true` in the ViewModel init, and `emptyMembersTextView` is hidden while loading (`state.members.isEmpty() && !state.isLoading`). However, the layout has no `ProgressBar` — the screen shows a blank `RecyclerView` and hidden empty state during load, giving no visual feedback.

---

### 1.5 🟡 `AnalyticsFragment` has a loading indicator but it's a raw `ProgressBar`

**File**: [`fragment_analytics.xml:14–18`](../app/src/main/res/layout/fragment_analytics.xml)

```xml
<ProgressBar
    android:id="@+id/analyticsLoadingIndicator"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="center_horizontal" />
```

The indicator is a basic spinner (no explicit style), which defaults to the platform spinner. On API 35 with Material 3, this is an `@android:attr/progressBarStyleLarge` spinner instead of `LinearProgressIndicator` or `CircularProgressIndicator` from Material 3. It is inconsistent with the rest of the app's Material components.

The analytics loading indicator also starts as **visible by default** (no `android:visibility="gone"`), meaning it appears briefly even when data is already cached, then disappears. The `render()` function correctly sets `isVisible = state.isLoading`, but the default XML visibility causes a flicker.

---

## 2. Empty States

### 2.1 🟡 `fragment_task_board.xml` has no empty state for the task list

**File**: [`fragment_task_board.xml`](../app/src/main/res/layout/fragment_task_board.xml)

When a project has no tasks, both the list view and Kanban columns show empty `RecyclerView`s with no "No tasks yet" message. There is no `emptyTasksTextView` in the layout. Users of a new project see blank columns with no call to action.

---

### 2.2 🟡 `fragment_create_edit_task.xml` has no empty state when no members are available to assign

**File**: [`CreateEditTaskFragment.kt:197–200`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/taskedit/CreateEditTaskFragment.kt)

```kotlin
if (availableMembers.isEmpty()) {
    Snackbar.make(binding.root, R.string.no_members_available, Snackbar.LENGTH_SHORT).show()
    return
}
```

This is handled via a `Snackbar` which is correct, but the "Assign Members" button has no visual distinction showing that it's conditionally unavailable. A disabled button state or a subtitle "No members yet" would be clearer.

---

### 2.3 🟡 `fragment_profile.xml` is entirely placeholder content

**File**: [`fragment_profile.xml`](../app/src/main/res/layout/fragment_profile.xml), [`ProfileFragment.kt`](../app/src/main/java/edu/bluejack252/hwixel/ui/profile/ProfileFragment.kt)

The Profile screen displays only:
- A title "Profile"
- Static text `@string/profile_placeholder` ("Profile feature coming soon" or similar)
- A Logout button

This is a pre-Phase-11 placeholder. PR #8 (Phase 11) adds the real profile screen. Until that PR is merged, the Profile tab provides no useful information. No user name, avatar, student ID, badges, or settings are visible. For demo purposes this is a significant gap.

---

### 2.4 🔵 `TaskDetailFragment` shows section labels even when sections are empty

**File**: [`fragment_task_detail.xml`](../app/src/main/res/layout/fragment_task_detail.xml)

Section header labels ("Attachments", "Subtasks", "Comments", "Activity History") are always visible even when the corresponding section is empty. The empty-state `TextView`s (`emptyAttachmentsTextView`, etc.) show the "none" message, but the section headers above them remain. This results in:

```
Attachments
[No attachments]

Subtasks
[No subtasks]
```

Showing section headers for empty sections adds visual noise. Ideally the headers are hidden when their section is empty.

---

## 3. Error States

### 3.1 🟠 Login/Register errors shown via `Snackbar` without retry mechanism

**File**: [`LoginFragment.kt`](../app/src/main/java/edu/bluejack252/hwixel/ui/auth/login/LoginFragment.kt)

Auth errors (wrong password, network timeout) are displayed in a `Snackbar` at the bottom of the screen. The Snackbar has no retry action. For transient network errors, the user must manually re-tap the login button. The industry standard is to either offer a "Retry" action in the `Snackbar` or re-enable the button immediately with the fields still filled.

---

### 3.2 🟠 Status update errors (task board) are silently dropped

**File**: [`TaskBoardFragment.kt`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/tasks/TaskBoardFragment.kt)

As noted in the backend audit (1.11), `statusUpdateResult` is never observed in `TaskBoardFragment`. If changing a task's status fails (network error, Firebase rule rejection), the user gets **no feedback**. The task card may appear to have moved status momentarily due to optimistic UI, then snap back when Firebase emits the unchanged state — with no error shown.

---

### 3.3 🟡 Comment submission failure shows success Snackbar regardless of error

**File**: [`TaskDetailFragment.kt:79–85`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/taskdetail/TaskDetailFragment.kt)

```kotlin
viewModel.commentResult.observe(viewLifecycleOwner) { result ->
    result ?: return@observe
    if (result.isSuccess) {
        Snackbar.make(binding.root, R.string.comment_added, Snackbar.LENGTH_SHORT).show()
    }
    viewModel.consumeCommentResult()   // ← always consumed, no else branch for failure
}
```

If `addComment()` fails (e.g. Firebase offline), `result.isFailure` is `true`. The observer fires, shows nothing (no error Snackbar for the failure case), and consumes the result. The user's comment vanishes with no explanation.

---

### 3.4 🟡 Invite member failure uses a generic message when error detail is blank

**File**: [`MembersFragment.kt:68–70`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/members/MembersFragment.kt)

```kotlin
result.startsWith("error:") -> {
    val detail = result.removePrefix("error:").ifBlank { getString(R.string.error_generic) }
    getString(R.string.invite_failed_format, detail)
}
```

The detail is taken from `exception.localizedMessage` which, for Firebase `DatabaseException`s, often contains internal Firebase error codes (e.g. `"Permission denied"` in English, not localized). For an Indonesian-language user, the error detail is always in English.

---

### 3.5 🔵 `AnalyticsFragment` team health error text overlaps summary text

**File**: [`fragment_analytics.xml:131–136`](../app/src/main/res/layout/fragment_analytics.xml)

The `teamHealthErrorTextView` and `teamHealthSummaryTextView` are both inside the same `LinearLayout` with no `visibility="gone"` guard on `teamHealthSummaryTextView`. When `state.teamHealthError != null`, the error text is shown, but the summary text still shows the last known (or default) value, potentially displaying both simultaneously.

---

## 4. Android 35 / Meerkat Compatibility

### 4.1 🔴 `android:orientation` on `RecyclerView` is a no-op — attachments show as vertical list

**File**: [`fragment_task_detail.xml:99–107`](../app/src/main/res/layout/fragment_task_detail.xml)

```xml
<androidx.recyclerview.widget.RecyclerView
    android:id="@+id/attachmentsRecyclerView"
    android:orientation="horizontal"
    app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />
```

`android:orientation` on a `RecyclerView` is **not respected** by `LinearLayoutManager` when the layout manager is specified via `app:layoutManager` XML attribute. The default `LinearLayoutManager` constructed by the attribute uses `VERTICAL`. The horizontal orientation string has no effect. Attachments will render as a vertical list. To get horizontal layout, the code must call `binding.attachmentsRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)` at runtime, or use `app:layoutManager` + `android:orientation` with the `@attr/layoutManager` approach which does not exist for the standard attribute.

---

### 4.2 🟠 Deprecated `TextAppearance.MaterialComponents.*` styles used throughout layouts

**Files**: `fragment_dashboard.xml`, `fragment_project_hub.xml`, `fragment_task_detail.xml`, `fragment_task_board.xml`, `item_task_card.xml`, and others

Examples:
```xml
android:textAppearance="@style/TextAppearance.MaterialComponents.Headline4"
android:textAppearance="@style/TextAppearance.MaterialComponents.Subtitle2"
android:textAppearance="@style/TextAppearance.MaterialComponents.Body2"
```

`TextAppearance.MaterialComponents.*` styles are from Material 2. On an API 35 project targeting Material 3, these are deprecated. The correct M3 equivalents use `?attr/textAppearanceHeadlineLarge`, `?attr/textAppearanceTitleSmall`, `?attr/textAppearanceBodyMedium`, etc. Mixing Material 2 style references with Material 3 themes (`analytics.xml` uses `?attr/textAppearanceTitleLarge` while nearby files use `TextAppearance.MaterialComponents.Headline6`) produces inconsistent typography scaling, especially on foldables and large-screen devices.

---

### 4.3 🟠 `Widget.MaterialComponents.Chip.Entry` used for non-interactive status/priority chips

**Files**: `fragment_task_detail.xml`, `item_task_card.xml`, `item_member_card.xml`

```xml
<com.google.android.material.chip.Chip
    android:clickable="false"
    style="@style/Widget.MaterialComponents.Chip.Entry" />
```

`Widget.MaterialComponents.Chip.Entry` (Entry chip style) is designed for chips that represent a user input entry and includes a close/remove button affordance. Using it for read-only status display (where `android:clickable="false"`) is semantically incorrect. The correct style for read-only chips is `Widget.Material3.Chip.Assist` or `Widget.MaterialComponents.Chip.Choice`. On API 35 with the M3 theme, this may also render with unexpected padding or icon slots reserved for the close button.

---

### 4.4 🟠 `android.R.color.holo_red_light` / `holo_red_dark` used instead of semantic theme colors

**Files**: [`ProjectHubFragment.kt:118`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/hub/ProjectHubFragment.kt), [`TaskDetailFragment.kt:143`](../app/src/main/java/edu\bluejack252/hwixel/ui/project/taskdetail/TaskDetailFragment.kt), `fragment_analytics.xml:136`

```kotlin
binding.projectDueDateTextView.setTextColor(
    requireContext().getColor(android.R.color.holo_red_light)
)
```

`android.R.color.holo_red_*` colors are from the Holo design language (Android 4.x). On devices with API 35 and custom OEM themes, these may render differently. The correct approach is to use the theme-aware error color `?attr/colorError` from the Material theme, which automatically adapts to light/dark mode and the liquid glass design system introduced in PR #4.

---

### 4.5 🟡 `MainActivity` applies system bar insets to the root view padding

**File**: [`MainActivity.kt:22–26`](../app/src/main/java/edu/bluejack252/hwixel/MainActivity.kt)

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.setPadding(0, systemBars.top, 0, systemBars.bottom)
    insets
}
```

This correctly handles edge-to-edge on API 35. However, fragments that use `CoordinatorLayout` + `AppBarLayout` with `app:layout_behavior="@string/appbar_scrolling_view_behavior"` may double-count the bottom inset if the FAB or scrollable content inside them also applies inset padding. On devices using gesture navigation (no on-screen buttons), the bottom system bar inset is larger, which can cause the FAB to appear partially hidden or the bottom of a `ScrollView` to be inaccessible.

---

### 4.6 🔵 `@style/Widget.MaterialComponents.Button.OutlinedButton` mixed with `@style/Widget.Material3.Button.OutlinedButton`

**Files**: `fragment_analytics.xml` vs. `fragment_task_board.xml`, `fragment_create_edit_task.xml`, etc.

```xml
<!-- In fragment_analytics.xml: -->
style="@style/Widget.Material3.Button.OutlinedButton"

<!-- In fragment_task_board.xml: -->
style="@style/Widget.MaterialComponents.Button.OutlinedButton"
```

Both M2 and M3 button styles coexist in the same app. This creates visual inconsistency: M3 buttons have different default padding, corner radius, and ripple behavior than M2. On API 35 + Material 3 theme, M2 button styles may be resolved via the theme overlay but produce slightly different renders than their M3 counterparts.

---

## 5. Responsiveness & Device Compatibility

### 5.1 🟠 Kanban columns have fixed `250dp` width — clips on narrow devices (SW360 and below)

**File**: [`fragment_task_board.xml:82–106`](../app/src/main/res/layout/fragment_task_board.xml)

```xml
<include
    android:id="@+id/columnTodo"
    layout="@layout/layout_kanban_column"
    android:layout_width="250dp"
    android:layout_height="match_parent" />
```

On a `HorizontalScrollView`, 250dp per column is correct for horizontal scrolling. However, on a 4" device (SW320 — roughly a Nexus 4 equivalent), 250dp > 320dp screen width / 2, meaning a single column spans more than half the screen. Kanban view should work but the columns may feel oversized. On foldable devices in half-open mode, the experience may also be suboptimal.

---

### 5.2 🟡 `activityRecyclerView` in `fragment_project_hub.xml` has a fixed `120dp` height

**File**: [`fragment_project_hub.xml:107–113`](../app/src/main/res/layout/fragment_project_hub.xml)

```xml
<androidx.recyclerview.widget.RecyclerView
    android:id="@+id/activityRecyclerView"
    android:layout_height="120dp"
    android:nestedScrollingEnabled="false"
    ... />
```

120dp is approximately 2 list items tall (assuming ~56dp per item). If the activity feed has more than 2 entries, they are clipped. If it has 0 entries, 120dp of dead space exists (although `emptyActivityTextView` appears instead). On tablets, 120dp feels cramped. On foldable inner screens, this is proportionally smaller.

---

### 5.3 🟡 Dashboard's `Space` bottom padding is fixed at `88dp` — may double-pad with gesture nav

**File**: [`fragment_dashboard.xml:85–87`](../app/src/main/res/layout/fragment_dashboard.xml)

```xml
<Space
    android:layout_width="match_parent"
    android:layout_height="88dp" />
```

This 88dp spacer is intended to clear the bottom navigation bar. However, `MainActivity` already applies `systemBars.bottom` as padding to the root view. On a gesture-navigation device (API 35), the system bottom bar is larger (typically 44–72dp gesture area). The combination of the root view's system bar padding + the fixed 88dp Space may result in excessive dead space at the bottom of the Dashboard scroll. On an on-screen-nav-button device, this over-pads; on gesture nav, it may under-pad if the system bar is taller than 88dp.

---

### 5.4 🟡 `fragment_analytics.xml` `dateRangeButton` text size hardcoded at `18sp`

**File**: [`fragment_analytics.xml:47,55,145`](../app/src/main/res/layout/fragment_analytics.xml)

```xml
android:textSize="18sp"
```

Applied to multiple buttons in the analytics screen. `18sp` is larger than the default button text (`14sp`). On small screens (SW360), this can cause the button label to be truncated or wrap awkwardly. On large screens, it looks acceptable. Text sizes should reference the theme's type scale (`?attr/textAppearanceLabelLarge`, etc.) rather than hardcoded values.

---

### 5.5 🔵 `fragment_dashboard.xml` `deadlinesRecyclerView` uses `app:layoutManager` (vertical) but code overrides to horizontal

**File**: [`fragment_dashboard.xml:46–53`](../app/src/main/res/layout/fragment_dashboard.xml), [`DashboardFragment.kt:56–61`](../app/src/main/java/edu/bluejack252/hwixel/ui/dashboard/DashboardFragment.kt)

```xml
<!-- XML declares vertical LinearLayoutManager -->
app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
```

```kotlin
// Code sets horizontal:
binding.deadlinesRecyclerView.layoutManager = LinearLayoutManager(
    requireContext(), LinearLayoutManager.HORIZONTAL, false
)
```

The XML declaration is overridden in code. This is not a bug (code wins), but it is confusing to anyone maintaining the layout and may cause brief layout flicker if the XML manager renders one frame before the code override.

---

## 6. UI Flow & Navigation Disconnects

### 6.1 🔴 Attendance, Peer Eval, and File tabs are not in the merged nav graph

**File**: [`main_nav_graph.xml`](../app/src/main/res/navigation/main_nav_graph.xml)

The current merged `main_nav_graph.xml` contains only:
- `dashboardFragment`
- `notificationsFragment`
- `profileFragment`
- `projectHubFragment`
- `taskDetailFragment`
- `createEditTaskFragment`

PRs #4, #5, #6 add `attendanceFragment`, `peerEvalFragment`, and `fileRepositoryFragment` with corresponding tab buttons in `ProjectHubFragment`. Until those PRs are merged, any user flow that would tap these buttons (if they exist in the hub) would crash with navigation destination unknown.

---

### 6.2 🟠 `ProfileFragment` logout resets the nav graph but does not clear the back stack

**File**: [`ProfileFragment.kt:42`](../app/src/main/java/edu/bluejack252/hwixel/ui/profile/ProfileFragment.kt)

```kotlin
private fun logout() {
    ServiceLocator.getAuthRepository(requireContext()).logout()
    findNavController().setGraph(R.navigation.auth_nav_graph)
}
```

`setGraph()` replaces the current navigation graph and pops the back stack to the new start destination. However, `ServiceLocator` singletons (`authRepository`, `userRepository`, etc.) retain their previous state (cached `currentUserId`, `hasLoaded` flags). On the next login, `DashboardViewModel.hasLoaded` being `true` would prevent re-loading (`if (hasLoaded && currentUserId == userId) return`), but since a different user may have logged in, `currentUserId` changes which correctly triggers a reload. However, `NotificationsViewModel.hasLoaded` also guards its load — if User A logs out and User B logs in, the notifications may briefly show User A's data if the ViewModel is reused by the system.

**Fix**: `ServiceLocator` should expose a `reset()` method that nullifies all repository singletons on logout, forcing fresh construction for the next session.

---

### 6.3 🟡 `TaskBoardFragment` FAB uses `requireParentFragment().findNavController()` — fragile host coupling

**File**: [`TaskBoardFragment.kt:95–103`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/tasks/TaskBoardFragment.kt)

The FAB in the Task Board (a child of `ProjectHubFragment`'s ViewPager2) navigates to `createEditTaskFragment` via the parent's `NavController`. If ViewPager2 is ever replaced with a different host or the TaskBoardFragment is used outside of ProjectHub, `requireParentFragment()` will return the wrong fragment and the navigation will fail or navigate to the wrong destination.

---

### 6.4 🟡 Notification deep-link navigation uses incorrect argument delivery

**File**: [`NotificationsFragment.kt:47–53`](../app/src/main/java/edu/bluejack252/hwixel/ui/notifications/NotificationsFragment.kt)

PR #7 (Phase 10) describes that task notifications use a compound `projectId|taskId` in `referenceId`. If the notification type is `task_assigned`, the fragment would need to split this compound string and navigate to `TaskDetailFragment` with separate `projectId` and `taskId` arguments. The current `NotificationsFragment.onNotificationClicked` only handles `TYPE_INVITE` and navigates to `projectHubFragment` for all other types silently ignoring the navigation. Once PR #7 merges, this needs a routing table to map notification types to their correct deep-link destinations.

---

### 6.5 🔵 `ProjectHubFragment` `showCreateProjectDialog()` method is unreachable dead code

**File**: [`ProjectHubFragment.kt:131`](../app/src/main/java/edu/bluejack252\hwixel/ui/project/hub/ProjectHubFragment.kt)

Full create-project dialog code (including date picker, observers) exists in `ProjectHubFragment` but the FAB is hidden (`isVisible = false`) and no other UI element calls `showCreateProjectDialog()`. This dead method also observes `createProjectResult` LiveData, meaning a coroutine and LiveData source are running for a feature that cannot be triggered.

---

## 7. Visual & Typography Issues

### 7.1 🟡 Analytics screen "Team Health" section title is permanently hidden

**File**: [`fragment_analytics.xml:95–100`](../app/src/main/res/layout/fragment_analytics.xml)

```xml
<TextView
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:text="@string/analytics_team_health"
    android:visibility="gone" />    <!-- ← hardcoded gone, never shown -->
```

The "Team Health" label `TextView` is permanently hidden (`visibility="gone"`). The section title never appears, making the health card's purpose ambiguous — only the status chip and summary text are visible without a section heading.

---

### 7.2 🟡 `item_task_card.xml` `LinearProgressIndicator` shows 0% for tasks with no subtasks

**File**: [`item_task_card.xml:65–72`](../app/src/main/res/layout/item_task_card.xml)

The subtask `LinearProgressIndicator` has no visibility control in the adapter. When a task has no subtasks, the progress bar renders at 0% (full grey track) which looks like a bug or an in-progress operation. It should be hidden (`GONE`) when `subtasks.isEmpty()`.

---

### 7.3 🔵 `deadlineTextView` in `TaskDetailFragment` does not reset color on non-overdue tasks

**File**: [`TaskDetailFragment.kt:139–143`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/taskdetail/TaskDetailFragment.kt)

```kotlin
if (task.deadline < System.currentTimeMillis() && task.status != Constants.STATUS_DONE) {
    binding.deadlineTextView.setTextColor(
        requireContext().getColor(android.R.color.holo_red_light)
    )
}
// No else to reset the color
```

If a task is rendered while overdue (red text), and then its status changes to `DONE` via the observer, the color is never reset to the default text color. The text stays red. A `else` branch resetting the color to `?attr/colorOnSurface` is needed.

---

### 7.4 🔵 `fragment_project_hub.xml` uses `LinearLayout` with `layout_weight` inside an already `match_parent` parent

**File**: [`fragment_project_hub.xml:86–97`](../app/src/main/res/layout/fragment_project_hub.xml)

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    app:layout_behavior="@string/appbar_scrolling_view_behavior">

    <androidx.viewpager2.widget.ViewPager2
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
```

Using `layout_weight` inside a `LinearLayout` with `match_parent` height is correct but requires the parent `LinearLayout` to have a defined height. Because `app:layout_behavior` positions it within a `CoordinatorLayout`, the height calculation depends on the `CoordinatorLayout` correctly sizing the child. This works in the current layout but is fragile — adding a bottom view with fixed height (like the activity feed section) correctly reduces the ViewPager's space, but this layout approach has caused measurement-pass bugs historically in `CoordinatorLayout`.

---

## 8. Accessibility

### 8.1 🟠 Interactive FABs lack descriptive content descriptions on several screens

**Files**: `fragment_dashboard.xml`, `fragment_task_board.xml`, `fragment_members.xml`

`fragment_dashboard.xml`:
```xml
android:contentDescription="@string/hub_add_project"
```
This says "Add project" — acceptable but `hub_add_project` is a project hub string used in both Dashboard and Hub, which is semantically correct but reused.

`fragment_task_board.xml`:
```xml
android:contentDescription="@string/task_add_new"
```
Acceptable.

`fragment_members.xml` — the `ExtendedFloatingActionButton` has no `android:contentDescription`. Screen readers will read the button text "Invite" which is acceptable, but an explicit `contentDescription` is better practice.

---

### 8.2 🟡 Chips used as non-interactive read-only elements with `android:clickable="false"` have no `importantForAccessibility` set

**Files**: `item_task_card.xml`, `fragment_task_detail.xml`

```xml
<com.google.android.material.chip.Chip
    android:clickable="false"
    style="@style/Widget.MaterialComponents.Chip.Entry" />
```

Entry-style chips are focusable by default. With `clickable="false"` they cannot be activated, but TalkBack may still read them as interactive if `focusable` is not explicitly set to `false` or `importantForAccessibility="yes"` is not set with a proper description. The chip label ("high", "todo") is read by TalkBack but without context ("Priority: High", "Status: Todo").

---

## 9. Summary Table

| # | Severity | Screen | Description |
|---|---|---|---|
| 1.1 | 🔴 | Dashboard | No loading state — empty screen on cold start |
| 1.2 | 🔴 | Task Detail | Blank screen while task loads — no indicator |
| 1.3 | 🔴 | Project Hub | Blank screen when project is null — no indicator |
| 1.4 | 🟠 | Members | No loading indicator during member fetch |
| 1.5 | 🟡 | Analytics | Raw `ProgressBar` visible by default, flickers on load |
| 2.1 | 🟡 | Task Board | No empty state for tasks list/Kanban |
| 2.2 | 🟡 | Create Task | "Assign Members" button gives no hint when members unavailable |
| 2.3 | 🟡 | Profile | Entire screen is placeholder — no user data shown |
| 2.4 | 🔵 | Task Detail | Section headers shown even for empty sections |
| 3.1 | 🟠 | Auth | Login errors have no retry affordance |
| 3.2 | 🟠 | Task Board | Status update errors silently dropped |
| 3.3 | 🟡 | Task Detail | Comment submission failure shows nothing |
| 3.4 | 🟡 | Members | Firebase error detail always in English, not localized |
| 3.5 | 🔵 | Analytics | Error text and summary text overlap when both populated |
| 4.1 | 🔴 | Task Detail | `android:orientation` on RecyclerView is a no-op — attachments render vertical |
| 4.2 | 🟠 | All screens | Deprecated `TextAppearance.MaterialComponents.*` on API 35 |
| 4.3 | 🟠 | Multiple | `Widget.MaterialComponents.Chip.Entry` for non-interactive read-only chips |
| 4.4 | 🟠 | Hub/Detail | `android.R.color.holo_red_*` ignores theme — doesn't adapt to dark mode |
| 4.5 | 🟡 | Global | Edge-to-edge inset may double-pad in some fragment layouts |
| 4.6 | 🔵 | Multiple | Mixed M2/M3 button styles in the same app |
| 5.1 | 🟠 | Task Board | Kanban 250dp columns: OK for phones but oversized on some screen sizes |
| 5.2 | 🟡 | Project Hub | Activity feed fixed at 120dp clips long lists |
| 5.3 | 🟡 | Dashboard | Fixed 88dp Space spacer may over/under-pad on gesture nav |
| 5.4 | 🟡 | Analytics | Hardcoded `18sp` text size — truncates on small screens |
| 5.5 | 🔵 | Dashboard | XML `layoutManager` (vertical) overridden in code to horizontal |
| 6.1 | 🔴 | Navigation | Attendance/Eval/Files fragments absent from nav graph |
| 6.2 | 🟠 | Auth | Logout doesn't reset ServiceLocator singletons |
| 6.3 | 🟡 | Task Board | FAB navigation via `requireParentFragment()` is fragile |
| 6.4 | 🟡 | Notifications | Deep-link routing incomplete for task/eval notification types |
| 6.5 | 🔵 | Project Hub | Dead dialog code and LiveData observer for hidden FAB |
| 7.1 | 🟡 | Analytics | Team Health section title permanently hidden |
| 7.2 | 🟡 | Task Card | Progress bar shows 0% for tasks with no subtasks |
| 7.3 | 🔵 | Task Detail | Overdue red text color never resets when task is marked done |
| 7.4 | 🔵 | Project Hub | `layout_weight` in `CoordinatorLayout` child — fragile measurement |
| 8.1 | 🟠 | Members | `ExtendedFloatingActionButton` missing `contentDescription` |
| 8.2 | 🟡 | Multiple | Read-only chips missing accessibility context labels |

---

*End of Frontend & UI Audit*
