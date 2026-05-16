# Product Requirements Document (PRD)
# Hwixel — Group Project Management App

**Version:** 1.0  
**Team:** HW XL  
**Package:** `edu.bluejack252.hwixel`  
**Platform:** Android (Google Play Store)  
**Min SDK:** 35 (Android 15)  
**Language:** Kotlin  
**Architecture:** MVVM  

---

## 1. Overview

Hwixel is a mobile application built for Android that helps university students manage group projects. It centralizes task tracking, attendance logging, peer evaluations, contribution analytics, and team communication in one place. The app reduces friction during collaborative academic work and makes individual contributions visible and fair.

---

## 2. Problem Statement

Group project management in academic settings is fragmented. Students use separate tools for task assignment, meeting attendance, and evaluations, which causes miscommunication, unequal workload distribution, and lack of accountability. There is no single mobile-first tool tailored to the student project workflow.

---

## 3. Goals

- Provide a centralized platform for all group project activities.
- Make individual contribution visible and measurable.
- Enable structured, criteria-based peer evaluation.
- Automate deadline reminders and notifications.
- Surface workload imbalances through AI-driven analytics.

---

## 4. Target Users

**Primary:** University and college students (undergraduate) participating in group projects.  
**Secondary:** Academic team leads or project coordinators within a student group.

---

## 5. Platform and Technical Constraints

| Constraint | Detail |
|---|---|
| Platform | Android only |
| Min SDK | 35 (Android 15) |
| Language | Kotlin |
| Architecture | MVVM |
| Deployment | Google Play Store |
| No location | Location APIs are prohibited |
| No payments | No in-app purchases or payment flows |
| No SMS | No SMS sending or OTP via SMS |
| Package name | `edu.bluejack252.hwixel` |

---

## 6. Tech Stack Summary

| Layer | Technology |
|---|---|
| Authentication | Firebase Authentication (email/password) |
| Remote Database | Firebase Realtime Database |
| Local Storage | Room Database (SQLite) |
| Async | Kotlin Coroutines |
| Reactive UI | LiveData |
| Navigation | Jetpack Navigation Component with Safe Args |
| UI | XML Layouts + Material Design 3 |
| Charts | MPAndroidChart (pie and bar charts) |
| Image Loading | Glide |
| AI Feature | Jatevo GPT-5.5 (OpenAI Responses API at `https://lb.jatevo.ai/v1`, model `gpt-5.5`) |
| HTTP Client | OkHttp (for GPT-5.5 API call) |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Scheduled Notifications | WorkManager |
| Localization | Android `strings.xml` with locale support (EN + ID) |
| Testing | JUnit4 + Mockito-Kotlin + Espresso |
| Version Control | Git with defined branching strategy |

---

## 7. Feature Requirements

### 7.1 Authentication

**Login Page**

- Display login form with email and password fields.
- Validate that the email field follows a valid email format.
- Validate that the password field is not empty.
- Show appropriate error messages for invalid inputs and failed login attempts.
- Redirect the user to the Dashboard on successful login.
- Provide a link to the Register screen.

**Register Page**

- Display registration form with email, password, name, and Student ID fields.
- Validate email format.
- Enforce password rules: minimum 8 characters, at least one uppercase letter, at least one special character.
- Collect and save name and Student ID as part of the profile setup.
- Integrate registration with Firebase Authentication.
- Show error and success messages.

---

### 7.2 Dashboard (Home Page)

- Display a list of all active group projects the user is a member of.
- Show an "Upcoming Deadlines" countdown widget listing the nearest task deadlines across all projects.
- Display the total count of pending tasks assigned to the user across all projects.
- Tapping a project card navigates to that project's Project Hub.
- Display a bottom navigation bar for global navigation.

---

### 7.3 Project Hub

- Display the project name, goals, and description.
- Show the overall project completion percentage as a progress bar.
- Provide tabbed navigation to Tasks (Task Board), Analytics (Contribution Analytics), and Members (Project Members).
- Display the project due date.
- Show a Recent Activity feed of the latest actions taken in the project.
- Provide an "Add New Project" button accessible from the dashboard flow.

---

### 7.4 Task Board

- Support both Kanban board view and list view with a toggle between the two.
- Organize tasks into four status columns: To-Do, In Progress, Review, Done.
- Allow filtering tasks by assignee, priority level, and deadline.
- Show progress bars for sub-tasks within a parent task.
- Tapping a task navigates to the Task Detail page.

---

### 7.5 Task Detail Page

- Display the task title and description.
- Support file attachments: PDFs, images, and external links.
- Include a comment section for task-specific discussions.
- Show a task activity history (audit trail) recording all changes.
- Provide an "Edit Task" button navigating to the Create/Edit Task page.

---

### 7.6 Create / Edit Task Page

- Provide a form with fields for task title and description.
- Include a date picker for setting the task deadline.
- Allow assigning the task to one or more project members.
- Allow setting a priority level: Low, Medium, or High.
- Support adding and managing sub-tasks.

---

### 7.7 Contribution Analytics Page

- Display a pie chart showing work distribution per member.
- Show a breakdown of tasks assigned versus tasks completed per member.
- Display an AI-driven "Team Health" status using the Jatevo GPT-5.5 API (model `gpt-5.5`, OpenAI Responses API). Sends member task stats and receives a health verdict (Healthy / Mild Imbalance / Severe Imbalance), a summary sentence, and up to 3 recommendations.
- Display the per-member contribution score.
- Provide a date range filter to narrow the analytics view.

---

### 7.8 Attendance Page

- Display a list or grid of project members for a given session.
- Allow marking and unmarking attendance per member per session.
- Store and display historical attendance records.
- Show each member's attendance summary as a percentage.
- Support exporting an attendance report via the Android share sheet.
- Show next session information and allow setting a local reminder.

---

### 7.9 Peer Evaluation Page

- Provide a multi-criteria rating form with criteria: Communication, Quality of Work, Reliability, and Effort.
- Include a qualitative feedback text input section.
- Allow submitting and saving the completed peer evaluation.
- Show received evaluations from teammates.
- Display whether the current evaluation period is open or closed.

---

### 7.10 Project Members Page

- Display the full list of project members with name, avatar, and role.
- Show WhatsApp and email contact links.
- Show each member's defined role (Team Lead, Editor, Researcher, etc.).
- Display each member's individual Contribution Score.
- Display member status: active or inactive.
- Provide an "Invite Member" button to add new members by email.

---

### 7.11 File Repository Page

- Display links to shared external folders and repositories.
- Support links to Google Drive and GitHub.
- Allow adding and removing shared folder links.
- Support version history notes for major project milestones.
- Allow opening a link in the device browser via Chrome Custom Tabs.

---

### 7.12 Notifications Page

- Show real-time alerts for new task assignments and mentions.
- Send deadline reminders at 24 hours, 1 hour, and 15 minutes before a deadline.
- Send alerts when a peer evaluation period opens or closes.
- Allow marking notifications as read.
- Show a notification badge count on the app icon.

---

### 7.13 Profile and Settings Page

- Display the user's student portfolio: total projects completed and average peer rating.
- Show achievement badges: "Top Contributor" and "Deadline Crusher".
- Provide a dark mode toggle.
- Provide language and notification preference settings.
- Allow editing profile details: name, Student ID, and avatar.

---

## 8. Non-Functional Requirements

| Category | Requirement |
|---|---|
| Performance | App must launch and reach the Dashboard within 3 seconds on a mid-range device. |
| Offline Support | Core data must be readable offline via Room local cache. |
| Security | Firebase rules restrict data access to authenticated members only. |
| Localization | At least two languages: English and Bahasa Indonesia. |
| Testing | All ViewModel and Repository classes must have unit test coverage. |
| Accessibility | All interactive elements must have content descriptions. |
| Code Quality | Strict MVVM: no business logic in Activity or Fragment. |

---

## 9. Scoring Alignment

| Category | Weight |
|---|---|
| Firebase (Auth) | 1 |
| Firebase Realtime DB | 2 |
| Localization | 1 |
| Save Data to Local Device | 2 |
| UI (14 pages) | 5 |
| Mobile Programming Knowledge | 3 |
| MVVM Architecture | 4 |
| Git Best Practices | 5 |
| Unit Tests | 5 |
| Program (Feature Score) | 36 |
| Upload to Play Store | 36 |
| **Total** | **100** |

The 14 UI pages scored are: Login, Register, Dashboard, Project Hub, Task Board, Task Detail, Create/Edit Task, Contribution Analytics, Attendance, Peer Evaluation, Project Members, File Repository, Notifications, Profile and Settings.

---

## 10. Out of Scope

- iOS version
- Web version
- SMS-based authentication or OTP
- In-app payments
- GPS or location features
- Video calling or screen sharing
- External grade submission integration
