# Phase 9 Audit Report -- File Repository

**Branch:** `feature/phase12-localization` (all phases stacked)
**Date:** 2026-05-18
**Spec ref:** PRD §7.11 · Feature §9.1

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0 |
| High | 0 |
| Medium | 1 |
| Low | 1 |

---

## Medium

### M-1: `versionHistory` sorts by Firebase push key -- fragile ordering

**File:** [`ui/project/files/FileRepoViewModel.kt:23`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/files/FileRepoViewModel.kt)

```kotlin
val versionHistory: LiveData<List<FileLink>> = files.map { list ->
    list.filter { it.versionNotes.isNotBlank() }.sortedBy { it.id }
}
```

The version history list is sorted by `it.id`, which is the Firebase `push()` key. Firebase push keys are designed to sort chronologically, so this produces the right order in practice. However, `FileLink.id` is not documented as a sortable timestamp -- it is an opaque string from the domain perspective. If `id` is ever sourced from a non-push path, the sort silently breaks. The spec says "chronological order" and expects a reliable sort guarantee.

**Fix:** Add a `createdAt: Long = 0L` field to `FileLink` (and write `ServerValue.TIMESTAMP` to it in `FileFirebaseSource.addFile`), then sort by `it.createdAt`. This makes the intent explicit and is not sensitive to key format.

---

## Low

### L-1: Swipe-to-delete provides no visual rollback if the Firebase delete fails

**File:** [`ui/project/files/FileRepoFragment.kt:88`](../app/src/main/java/edu/bluejack252/hwixel/ui/project/files/FileRepoFragment.kt)

```kotlin
override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    val file = filesAdapter.currentList[position]
    viewModel.deleteFile(file.id)
}
```

`ItemTouchHelper` removes the swiped item from the RecyclerView immediately on `onSwiped`. `viewModel.deleteFile` fires the Firebase delete asynchronously. If the delete fails (network error, permission denied), the item disappears from the UI and `FileRepoUiState.DeleteSuccess` never fires. The only recovery is for the Firebase listener to re-emit the list (which will re-add the item), but there is no error message, no rollback animation, and no user-visible indication that the delete failed.

**Fix:** On delete failure, call `filesAdapter.notifyItemChanged(position)` (or notify the adapter to re-bind from the current list) and show a Snackbar error. Alternatively, defer the adapter removal until the ViewModel emits `DeleteSuccess`.

---

## What Is Working Correctly

Phase 9 is the cleanest phase in the codebase. Specific strengths:

- `FileFirebaseSource` correctly uses `onActive()` / `onInactive()` to attach and detach Firebase listeners with the LiveData lifecycle (no listener leaks).
- URL scheme validation in the ViewModel (`parsedUrl.scheme !in setOf("http", "https")`) prevents non-HTTP links from being saved.
- Custom Tabs launch with a proper fallback to `Intent.ACTION_VIEW` when Custom Tabs are unavailable.
- `FileRepositoryImpl` is a thin, clean pass-through with no extraneous logic.
- Swipe-to-delete is implemented (spec requirement met), with a custom red background drawn via Canvas.
- The FAB → `AddLinkBottomSheet` flow is clean and the callback lambda is wired correctly.
