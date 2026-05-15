# Feature Documentation
# Hwixel — Full Feature Specification and Feature Relations

**Version:** 1.0  
**Team:** HW XL**  
**XL:** Login, Register, Dashboard, Project Hub, Task Board, Task Detail, Create/Edit Task  
**HW:** Contribution Analytics, Attendance, Peer Evaluation, Project Members, File Repository, Notifications, Profile & Settings  

---

## 1. Feature Map Overview

All 14 screens are grouped below. Each screen lists its features, the weight of each feature from the scoring template, and which other screens it depends on or connects to.

---

## 2. Authentication

### 2.1 Login Page
**Team:** XL | **Page Weight:** 2.0

| Feature | Weight | Description |
|---|---|---|
| Display login form (email and password fields) | 0.25 | Two TextInputLayouts with email keyboard type and password input type. Show/hide password toggle. |
| Validate email format | 0.50 | Use `Patterns.EMAIL_ADDRESS`. Show inline error below the field if malformed. |
| Validate password is not empty | 0.50 | Show inline error if password field is empty on submit tap. |
| Show error / success message | 0.50 | Snackbar or inline error on failed login. Navigate on success. |
| Redirect to Dashboard on successful login | 0.25 | Pop auth back stack. Navigate to DashboardFragment via NavController. |

**Dependencies:** Firebase Authentication  
**Connects to:** Dashboard (success), Register (via link)

---

### 2.2 Register Page
**Team:** XL | **Page Weight:** 2.0

| Feature | Weight | Description |
|---|---|---|
| Display registration form | 0.25 | Fields: email, password, confirm password, name, Student ID. |
| Validate email format on register | 0.25 | Same regex as login page. |
| Setup profile (Name, Student ID) | 0.50 | After `createUserWithEmailAndPassword`, write name and studentId to RTDB `users/{uid}` and Room UserEntity. |
| Integrate with Firebase Auth | 0.50 | Call `createUserWithEmailAndPassword`. On success, write user profile to RTDB. |
| Show error / success message | 0.25 | Map FirebaseAuthException error codes to human-readable messages. |
| Password validation (min 8 chars, uppercase, special char) | 0.25 | Client-side regex check before calling Firebase. |

**Dependencies:** Firebase Authentication, Firebase RTDB (users node)  
**Connects to:** Dashboard (success), Login (back)

---

## 3. Dashboard

### 3.1 Dashboard (Home Page)
**Team:** XL | **Page Weight:** 2.25

| Feature | Weight | Description |
|---|---|---|
| Display list of active group projects | 0.50 | RecyclerView of ProjectCard items. Query projects where `members/{uid}` exists. Shows project name, role, and completion percentage. |
| Upcoming Deadlines countdown widget | 0.75 | Horizontal scrollable list of the 3-5 nearest task deadlines across all user projects. Shows task name, project name, and a live countdown timer updated every second. |
| Total pending tasks summary across groups | 0.50 | Count of tasks where status is `todo` or `in_progress` and `assignees` contains current uid. Shown as a summary badge. |
| Navigate to Project Hub on project tap | 0.25 | Pass `projectId` as Safe Args to ProjectHubFragment. |
| Display bottom navbar | 0.25 | BottomNavigationView with three destinations: Dashboard, Notifications, Profile. |

**Dependencies:** Firebase RTDB (projects, tasks), Room (local cache)  
**Connects to:** Project Hub (project tap), Notifications (bottom nav), Profile (bottom nav)

---

## 4. Project

### 4.1 Project Hub
**Team:** XL | **Page Weight:** 1.75

| Feature | Weight | Description |
|---|---|---|
| Display project goals and description | 0.25 | Show project name, goals text, and description from RTDB `projects/{projectId}`. |
| Overall completion percentage progress bar | 0.50 | Computed as `(done tasks / total tasks) x 100`. Stored in RTDB and updated whenever a task status changes to `done`. Displayed as `LinearProgressIndicator`. |
| Tabbed navigation (Tasks, Analytics, Members) | 0.25 | TabLayout + ViewPager2 with three tabs: TaskBoardFragment, AnalyticsFragment, MembersFragment. |
| Add new project button | 0.25 | FAB or toolbar menu item. Opens a Create Project dialog. |
| Display project due date | 0.25 | Formatted date string below the project name. Color turns red when past due. |
| Recent Activity feed | 0.25 | Last 10 history entries across all tasks in the project, pulled from `tasks/{projectId}/*/history`, sorted by timestamp descending. |

**Dependencies:** Firebase RTDB (projects, tasks)  
**Connects to:** Task Board (tab), Contribution Analytics (tab), Project Members (tab)

---

### 4.2 Task Board
**Team:** XL | **Page Weight:** 3.0

| Feature | Weight | Description |
|---|---|---|
| Kanban / List view toggle | 0.75 | Toggle button in toolbar. Kanban: HorizontalScrollView with four RecyclerView columns filtered by status. List: single flat RecyclerView with status chips. |
| Task status columns (To-Do, In Progress, Review, Done) | 0.50 | Status values: `todo`, `in_progress`, `review`, `done`. Minimum: tap-to-change-status via a dialog. Drag-and-drop is a bonus. |
| Filter tasks by assignee, priority, deadline | 1.00 | FilterBottomSheet with multi-select chip group for assignees, priority radio group (All/Low/Medium/High), and a date range picker. Applied as in-memory filter on the LiveData list. |
| Progress bars for sub-tasks | 0.50 | Each task card shows subtask ratio (e.g., 2/5) and a `LinearProgressIndicator`. |
| Tap task to open Task Detail page | 0.25 | Navigate to TaskDetailFragment passing `taskId` and `projectId` via Safe Args. |

**Dependencies:** Firebase RTDB (tasks), Room (local cache), Project Hub  
**Connects to:** Task Detail (tap), Create/Edit Task (FAB for new task)

---

### 4.3 Task Detail Page
**Team:** XL | **Page Weight:** 2.0

| Feature | Weight | Description |
|---|---|---|
| Display task title, description | 0.25 | Show full task name and description. Also show assignees, priority, deadline, and status as chips. |
| File attachments (PDFs, Images, Links) | 0.50 | Horizontal scrollable list of attachment chips. Files stored as external URLs. PDFs and links open in WebView or browser. Images open in a full-screen viewer. |
| Comment section for task discussions | 0.50 | Chronological RecyclerView from `tasks/{projectId}/{taskId}/comments`. Each item shows author name, timestamp, and text. New comment input at the bottom. |
| Task activity history (audit trail) | 0.50 | Expandable section showing all history entries from `tasks/{projectId}/{taskId}/history` in reverse-chronological order. |
| Edit task button | 0.25 | Toolbar icon or FAB navigating to CreateEditTaskFragment with current task data pre-filled. |

**Dependencies:** Task Board (parent), Firebase RTDB (tasks/comments/history)  
**Connects to:** Create/Edit Task (edit button)

---

### 4.4 Create / Edit Task Page
**Team:** XL | **Page Weight:** 2.0

| Feature | Weight | Description |
|---|---|---|
| Create task form (title and description) | 0.25 | TextInputLayout for title (required) and description (optional, multiline). |
| Set deadline with date picker | 0.25 | `MaterialDatePicker` dialog. Selected date stored as Unix timestamp. |
| Assign task to member(s) | 0.50 | Multi-select dialog listing all project members. Selected members stored in `assignees` list. |
| Set priority level (Low / Medium / High) | 0.50 | SegmentedButton or RadioGroup. Values: `low`, `medium`, `high`. |
| Add and manage sub-tasks | 0.50 | Dynamic list: each row has a TextInputLayout and remove button. Plus button adds a new row. Each sub-task has `title` and `isDone`. |

**Dependencies:** Project Members (for assignee list), Task Board (navigates back on save)  
**Connects to:** Task Board (on save), Notifications (triggers assignment alert for assignees)

---

## 5. Analytics

### 5.1 Contribution Analytics Page
**Team:** HW | **Page Weight:** 4.0

| Feature | Weight | Description |
|---|---|---|
| Pie chart work distribution per member | 1.00 | MPAndroidChart `PieChart`. Each slice = one member. Value = number of completed tasks. Tapping a slice highlights member details below the chart. |
| Tasks assigned vs. completed breakdown | 1.00 | Grouped bar chart or a list of cards per member, each showing assigned count vs. completed count. |
| AI-driven Team Health status | 1.25 | Calls Groq API (free tier, `llama-3.3-70b-versatile` model) via OkHttp with current member stats. Returns status badge (Healthy / Mild Imbalance / Severe Imbalance), a one-sentence summary, and up to 3 action recommendations. Displayed as a color-coded card. |
| Per-member contribution score display | 0.50 | Read `contributionScore` from `projects/{projectId}/members/{userId}`. Shown as a numbered list with color-coded badges. |
| Date range filter for analytics | 0.25 | Start and end date pickers that filter which completed tasks are counted in the calculations. |

**Contribution Score Formula:**
```
contributionScore = (completedTasks / totalAssignedTasks) x 100 x weightFactor
weightFactor = 1 + (highPriorityCompleted x 0.2)
```

**AI Prompt sent to Groq:**
```
Analyze this student group project workload and return ONLY a JSON object with:
status (Healthy, Mild Imbalance, or Severe Imbalance),
summary (one sentence),
recommendations (array of up to 3 short strings).
Data:
- Alice: assigned=5, completed=4, overdue=0
- Bob: assigned=2, completed=1, overdue=1
...
```

**Dependencies:** Task Board (task data), Project Members (member list), Groq API  
**Connects to:** Project Hub (via tab), Project Members (member tap)

---

## 6. Attendance

### 6.1 Attendance Page
**Team:** HW | **Page Weight:** 2.0

| Feature | Weight | Description |
|---|---|---|
| Display member attendance list / grid | 0.25 | RecyclerView or GridLayout of all project members for the selected session. Shows name, avatar, and attendance toggle. |
| Mark / unmark attendance per session | 0.50 | Toggle per member writes `present: true/false` to `attendance/{projectId}/{sessionId}/records/{userId}`. |
| Log and view historical attendance records | 0.50 | Session list sorted by date. Each row shows date and summary (e.g., 5/6 present). Tapping a past session shows individual records. |
| Display attendance summary percentage | 0.25 | Per-member: `(sessions attended / total sessions) x 100`. Shown as a percentage chip next to the member name. |
| Export attendance report | 0.25 | Generates a plain text summary of all sessions and records. Shared via Android `ShareCompat` intent (share sheet). |
| Next session info / set reminder | 0.25 | Reads `nextSessionDate` from the attendance node. Button schedules a WorkManager `OneTimeWorkRequest` for a local notification at that date. |

**Dependencies:** Project Members, WorkManager, Firebase RTDB (attendance node)  
**Connects to:** Notifications (reminder scheduling)

---

## 7. Peer Evaluation

### 7.1 Peer Evaluation Page
**Team:** HW | **Page Weight:** 2.0

| Feature | Weight | Description |
|---|---|---|
| Multi-criteria rating (Communication, Quality, Reliability, Effort) | 0.50 | Four 1-5 star `RatingBar` widgets, one per criterion. Stored in `evaluations/{projectId}/{periodId}/submissions/{evaluatorId}/{evaluateeId}`. |
| Qualitative feedback text input section | 0.50 | Multiline `TextInputLayout`. Required before submission. |
| Submit and save peer evaluation | 0.50 | Submit button writes to RTDB. Re-submission overwrites. Triggers average rating recalculation on the user profile. |
| View received evaluations from teammates | 0.25 | A "Received" tab or section showing evaluations where `evaluateeId` = current uid. Shows scores and feedback text. |
| Show evaluation period open / closed status | 0.25 | Reads `evaluations/{projectId}/{periodId}/isOpen`. Shows a banner: open (green) or closed (grey). |

**Evaluation Average:**
```
averageScore = (communication + quality + reliability + effort) / 4
```
Contributes to `users/{uid}/averagePeerRating`.

**Dependencies:** Project Members (evaluatee list), Firebase RTDB (evaluations), Notifications  
**Connects to:** Profile (updates average rating), Notifications

---

## 8. Project Members

### 8.1 Project Members Page
**Team:** HW | **Page Weight:** 1.5

| Feature | Weight | Description |
|---|---|---|
| Display member list with contact info | 0.25 | RecyclerView of MemberCard items. Shows avatar, name, and role. |
| Show WhatsApp / Email contact links | 0.25 | WhatsApp: opens `https://wa.me/{phone}` via `ACTION_VIEW` intent if phone stored. Email: opens `mailto:` intent. |
| Role definition | 0.25 | Role dropdown per member. Options: Team Lead, Editor, Researcher, Designer, Developer, Other. Only Team Lead can edit roles. Stored in `projects/{projectId}/members/{userId}/role`. |
| Display individual Contribution Score | 0.25 | Read `contributionScore` from the members node. Color-coded badge: green (high), yellow (mid), red (low). |
| Display member status (active / inactive) | 0.25 | Toggle only available to Team Lead. Inactive members shown greyed out. |
| Invite member button | 0.25 | Dialog with email input. Searches RTDB `users/` for matching email. If found, adds user to `projects/{projectId}/members` and sends an in-app notification to the invited user. |

**Dependencies:** Firebase RTDB (projects/members, users), Contribution Analytics (score source)  
**Connects to:** Profile (member tap), Notifications (invite alert)

---

## 9. File Repository

### 9.1 File Repository Page
**Team:** HW | **Page Weight:** 1.75

| Feature | Weight | Description |
|---|---|---|
| Display centralized shared folder links | 0.25 | RecyclerView of FileCard items from `files/{projectId}`. Shows label, type icon, and URL preview. |
| Link to Google Drive, GitHub | 0.25 | Dedicated icons for Drive and GitHub. Tapping opens the URL via `ACTION_VIEW`. |
| Add / remove shared folder links | 0.50 | FAB opens AddLinkBottomSheet with label, URL, and type fields. Swipe-to-dismiss for deletion. Writes/deletes to `files/{projectId}/{fileId}`. |
| Version history notes for major milestones | 0.50 | Each file entry can have `versionNotes`. A Version History section shows all entries with non-empty notes in chronological order. |
| Open / preview link in browser | 0.25 | Uses `CustomTabsIntent` for in-app browser. Falls back to default browser if Chrome Custom Tabs unavailable. |

**Dependencies:** Firebase RTDB (files node)  
**Connects to:** Project Hub (accessible from project context)

---

## 10. Notifications

### 10.1 Notifications Page
**Team:** HW | **Page Weight:** 2.0

| Feature | Weight | Description |
|---|---|---|
| Real-time alerts for task assignments and mentions | 0.50 | FCM push notifications triggered when `assignees` changes or a comment contains `@userId`. Written to `notifications/{userId}/{notificationId}` on receipt. |
| Deadline reminders (24h / 1h / 15 min before) | 0.50 | WorkManager `OneTimeWorkRequest` jobs scheduled when a task deadline is set or updated. Three separate jobs per task. |
| Peer evaluation period open / close alerts | 0.50 | RTDB listener on `evaluations/{projectId}/{periodId}/isOpen`. When value changes, notifications are created for all project members. |
| Mark notifications as read | 0.25 | Tapping sets `isRead: true`. Toolbar "Mark All as Read" button sets all to read in one batch write. |
| Notification badge count on app icon | 0.25 | Count of `isRead: false` entries. Updated via standard `NotificationManagerCompat` badge API. |

**Notification data model:**
```
type: "task_assigned" | "mention" | "deadline" | "eval_open" | "eval_close" | "invite"
message: String
timestamp: Long
isRead: Boolean
referenceId: String   ← taskId or projectId depending on type
```

**Dependencies:** Firebase RTDB (notifications), WorkManager, FCM  
**Connects to:** Task Detail (task notifications), Peer Evaluation (eval notifications), Project Members (invite notifications)

---

## 11. Profile and Settings

### 11.1 Profile and Settings Page
**Team:** HW | **Page Weight:** 1.75

| Feature | Weight | Description |
|---|---|---|
| Display student portfolio (projects, average rating) | 0.25 | Read `users/{uid}/totalProjectsCompleted` and `averagePeerRating`. Displayed in a summary card. |
| Achievement badges (Top Contributor, Deadline Crusher) | 0.50 | Read `users/{uid}/badges`. Top Contributor: user has the highest contribution score in any project. Deadline Crusher: user completes 5+ tasks before their deadline. Shown as icon chips. |
| Dark mode toggle | 0.50 | Switch calls `AppCompatDelegate.setDefaultNightMode()`. Saved in SharedPreferences, applied on `Application.onCreate()`. |
| Language and notification preferences | 0.25 | Language selector (English, Bahasa Indonesia). Notification toggles per type, stored in SharedPreferences. |
| Edit profile (name, Student ID, avatar) | 0.25 | Pre-filled form. Avatar picked via `ActivityResultContracts.GetContent`. Saves to both RTDB and Room. |

**Badge Logic:**
```
Top Contributor:
  Triggered: task moved to "done" → contribution score recalculated
  Condition: user's contributionScore > all other members in the same project
  Action: write "top_contributor" to users/{uid}/badges

Deadline Crusher:
  Triggered: task moved to "done"
  Condition: task.completedAt < task.deadline AND lifetime count of such tasks >= 5
  Action: write "deadline_crusher" to users/{uid}/badges
```

**Dependencies:** Firebase RTDB (users), Peer Evaluation (average rating), Contribution Analytics (score), Room  
**Connects to:** Dashboard (bottom nav), Notifications (preferences)

---

## 12. Feature Relationships

### 12.1 Navigation Relationships

| Source Screen | Destination Screen | Trigger |
|---|---|---|
| Login | Dashboard | Successful login |
| Register | Dashboard | Successful registration |
| Dashboard | Project Hub | Tap project card |
| Dashboard | Notifications | Bottom nav tap |
| Dashboard | Profile | Bottom nav tap |
| Project Hub | Task Board | Tab selection |
| Project Hub | Contribution Analytics | Tab selection |
| Project Hub | Project Members | Tab selection |
| Task Board | Task Detail | Tap task card |
| Task Board | Create/Edit Task | FAB tap (new task) |
| Task Detail | Create/Edit Task | Edit button tap |
| Notifications | Task Detail | Tap task notification |
| Notifications | Peer Evaluation | Tap eval notification |
| Notifications | Project Hub | Tap invite notification |

### 12.2 Data Trigger Relationships

| Action | Triggers |
|---|---|
| Task created | Notification sent to all assignees |
| Task moved to Done | Contribution score recalculated; badge check run; completion % updated |
| Task deadline set | WorkManager jobs scheduled for 24h, 1h, and 15min reminders |
| Peer eval period opened | Notification sent to all project members |
| Peer eval submitted | Average peer rating recalculated on user profile |
| Member invited | In-app notification sent to invited user |
| User earns badge | Badge written to `users/{uid}/badges`; displayed on Profile page |

---

## 13. Scoring Template Alignment

### 13.1 Program Marking Weights

| Page | Team | Weight |
|---|---|---|
| Login Page | XL | 2.0 |
| Register Page | XL | 2.0 |
| Dashboard (Home Page) | XL | 2.25 |
| Project Hub | XL | 1.75 |
| Task Board | XL | 3.0 |
| Task Detail Page | XL | 2.0 |
| Create / Edit Task Page | XL | 2.0 |
| Contribution Analytics Page | HW | 4.0 |
| Attendance Page | HW | 2.0 |
| Peer Evaluation Page | HW | 2.0 |
| Project Members Page | HW | 1.5 |
| File Repository Page | HW | 1.75 |
| Notifications Page | HW | 2.0 |
| Profile and Settings Page | HW | 1.75 |
| **XL Subtotal** | XL | **15.0** |
| **HW Subtotal** | HW | **15.0** |

### 13.2 UI Scoring Criteria

All 14 pages are scored on four criteria: Design, Usability, Consistency, and Theme.

| Criterion | Focus |
|---|---|
| Design | Visual hierarchy, spacing, component choice, alignment |
| Usability | Ease of use, touch target sizes, error handling, flow clarity |
| Consistency | Uniform typography, colors, icon style across all pages |
| Theme | Material Design 3, dark mode support, brand identity |

### 13.3 General Scoring

| Item | Weight | Target |
|---|---|---|
| Firebase Auth | 1 | Email/password login and registration |
| Firebase Realtime DB | 2 | All remote data via RTDB |
| Localization | 1 | English + Bahasa Indonesia via strings.xml |
| Save Data to Local Device | 2 | Room Database for offline cache |
| UI (14 pages) | 5 | All 14 pages on 4 criteria each |
| Mobile Programming Knowledge | 3 | Kotlin idioms, Jetpack, lifecycle management |
| MVVM Architecture | 4 | Strict MVVM across all 14 screens |
| Git Best Practices | 5 | Branching, commit conventions, PRs |
| Unit Tests | 5 | ViewModel and Repository tests for all domains |
| Program Feature Score | 36 | All sub-features in Program Marking sheet |
| Upload to Play Store | 36 | App published on Google Play Store |
| **Total** | **100** | |
