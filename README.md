# Namma Sphere

**SMART CAMPUS INTEGRATED MANAGEMENT SYSTEM USING JAVA AND OBJECT-ORIENTED PROGRAMMING**

*One Campus. One Intelligent Platform.*

A JavaFX desktop campus ERP built with Java, Maven, JDBC and a layered MVC architecture. The included workspace database has a demonstration campus dataset for the Cloud Computing and AIML departments. A fresh database initializes the schema and department catalog; it does not create accounts or passwords.

## Start on Windows

1. Install a JDK version 21 or later and make `java` available on PATH. This workspace was built and tested with the installed JDK 26.
2. Double-click **`run.cmd`** in this folder. It packages the current source (without rerunning tests) before launching `target/campussphere-1.0.0.jar`; keep its `target/lib` directory alongside it.
3. On first launch, create your campus administrator. Use a username of 3–64 letters, numbers, dots, underscores or hyphens and a password of at least 6 characters.

The included `mvnw.cmd` is a small Windows bootstrap launcher: it downloads Apache Maven 3.9.11 into `.tools`, verifies the published SHA-512 checksum, and stores dependencies in `.m2` within this project. The first build needs internet access. A built SQLite installation runs locally without internet; opening web materials still needs connectivity.

```powershell
.\mvnw.cmd test             # Integration tests
.\mvnw.cmd package          # Compile, test and package
.\mvnw.cmd javafx:run       # Run from source
.\run.cmd                  # Run the built application
```

After changing source code, run `mvnw.cmd package` to refresh the packaged application. With a system Maven installation on Linux/macOS, use `mvn test` and `mvn javafx:run`; Maven resolves JavaFX libraries for the build platform. The packaged Windows JavaFX libraries are platform-specific.

## Updated portal navigation

The Student, Teacher and Administrator dashboards use a shared campus layout with a role-specific teal navigation sidebar, welcome banner, live database summary cards and quick links. Sidebar destinations follow each portal's existing access rules. Open modules from the dashboard and use **Home** to return; the header avatar opens **My profile**.

Question papers, room booking, doubt desk/replies, milestones, notifications and leave/on-duty are removed from the portal menus. Teachers also have no Reports or Appointments shortcut. **View project members** combines Members and Projects in one page.

Administrators can use **Examinations** to create exam timetable entries, assign enrolled students to rooms/seats and issue hall tickets. Students have separate **Seating Allotment**, **Exam Timetable** and **Hall Ticket** icons. Under **Finance**, administrators enter student fees and monthly faculty salaries in INR; students see only their fees and teachers only their own salary. No financial amounts are prefilled. Existing campus data and logins are preserved; new tables are created automatically on startup.

CSV export controls are removed from all portals. Select an issued hall ticket to **Download hall ticket**, or select a fee record with a payment / paid salary record to **Download receipt**. Downloads are PDFs. Fee receipts show cumulative payments and the remaining balance; they do not invent individual payment dates or transactions.

Student and Teacher portals have no campus or module search boxes. Search remains available to administrators. Teacher attendance displays the full enrolled student list without a search field.

Teacher attendance opens a named class roster: choose a course and date, check students present (green), leave absent students unchecked (red), and click **Save attendance**. Changes are saved together. Existing late/excused records remain unchanged unless the teacher changes their marks. Only students enrolled in an assigned course can be marked.

## Set up your campus

The interface uses a teal campus header, an academic welcome panel and role-specific dashboard navigation. Student summary cards use enrolled courses, attendance, assignment due dates and recorded fee balances; class schedules, exams and announcements come from the signed-in user's scoped data. Quick Access and the Academics, Examinations, Campus, Community and Requests sections open existing database-backed modules. The header avatar and **My profile** open the signed-in person's profile, with academic or teaching details and a password-change action.

The shared campus-photo component looks for the official image at `src/main/resources/images/campus-building.jpg` and uses a teal gradient fallback if the file is absent. Place the original photo at that path and rebuild to show it on the welcome/login panels. This is a JavaFX desktop app: it does not define public web routes. Authentication has Student, Teacher (Faculty) and Administrator roles; HOD is a faculty designation, not a separate authorization role. Password recovery directs users to an administrator reset; there is no email reset flow.

Everyone uses the same username and password fields. After BCrypt verification, Namma Sphere reads the user's role from the database and opens the existing Student, Teacher, or Administrator dashboard automatically; role is never inferred from a username. Student and teacher accounts are provisioned by an administrator and linked to campus profiles. First-run administrator setup remains available when the database is empty. No default passwords are supplied.

The screenshots are visual references: personal information from them is not imported. The portal has separate seating allotment, exam timetable, hall ticket, fees and faculty salary pages. Administrators enter financial records, allocate seats and issue hall tickets. Students see their own fees and exam documents; teachers see only their own salaries. Fee payments and salary payouts are recorded manually; this app does not process payments.

1. **Departments:** review the included department catalog. The head of department can be assigned after faculty exist.
2. **Faculty and Students:** create campus profiles. Required fields and related records are checked before saving.
3. **User accounts:** an administrator creates student/faculty logins linked to existing profiles. Creating a profile alone does not grant a login. The first administrator can create additional administrator accounts.
4. **Courses:** assign faculty, credits, semester and maximum capacity; use the enrollment action to add students.
5. **Campus resources:** create rooms, laboratories and equipment. Book a dated time interval or schedule recurring classes in Timetable.
6. **Events & activities:** publish an event and register students. Registration observes capacity.
7. Sign in with student and faculty accounts to use their scoped workspaces.

Dates are `yyyy-mm-dd`; times are 24-hour `HH:mm`; booking timestamps are `yyyy-mm-ddTHH:mm`. The desktop computer's local timezone is the campus timezone. Timetable days use Monday=1 through Sunday=7.

## Implemented modules

| Area | Working behavior |
|---|---|
| People | Student/faculty CRUD, required profile fields, search, sorting, filters, details, class teachers, departments and HOD assignment |
| Courses | Faculty assignment, enrollment/removal, capacity checks and scoped course records |
| Resources | Classroom/laboratory/equipment/seminar-room hierarchy, maintenance states, next reservations, transactional overlap detection and cancellation |
| Academics | Weekly timetable, dated changes, faculty substitution with conflict/leave checks, attendance upserts, assignments, study material and question-paper links, exam records |
| Library | Course-linked e-library catalog, searchable web resources, PDF upload, open and download |
| Requests | Student/faculty leave, on-duty and timed permissions, administrator approval/rejection, cancellation, faculty appointments |
| Campus life | Event/activity management, capacity-safe registrations and cancellation |
| Collaboration | Student doubt desk, assigned faculty replies, projects, members and milestones |
| Communication | Audience-scoped announcements, persistent personal notifications and read state, help-desk tickets and administrator responses |
| Reporting | Database-derived dashboards and JavaFX charts, grouped reports and global search |
| Administration | First-run setup, BCrypt password hashing, account provisioning/reset, password changes, sessions, service-level access policies and activity logs |

Every module is backed by JDBC. Specialized tables are read-only to generic CRUD: their action buttons invoke validated workflow services. Students cannot change administration records or other students' requests. Faculty access to teaching records is restricted to assigned courses; only administrators decide leave and permission requests in this edition.

**Remember username** stores only the username, never a password or login token. **Forgot password** explains the administrator reset process; it is the requested local simulation, with no outgoing email. Study materials support HTTP(S) links and actual document uploads (up to 25 MB) into `data/materials`; the Open material action launches the associated viewer. Digital library entries can include web URLs. Uploaded files are local to this installation: shared MySQL users need shared document storage to open files across computers. Assignments support due dates, optional PDF instructions, student uploads or replacements (PDF, Word, text or ZIP up to 25 MB), upload status and time, grades, feedback, and return-for-revision. Only the student and faculty assigned to that course can access a submission. Assignment creation, upcoming deadlines, grades and returned work send in-app notifications.

## Database configuration

SQLite is the zero-setup demonstration default: `data/campussphere.db`. Schema initialization enables foreign keys, WAL and a busy timeout. Back up this file with the application closed.

For the primary MySQL deployment, create a database on a MySQL 8+ server, then launch with environment variables:

```powershell
$env:CAMPUS_DB_URL = 'jdbc:mysql://localhost:3306/campussphere?serverTimezone=Asia/Kolkata'
$env:CAMPUS_DB_USER = 'campus_user'
$env:CAMPUS_DB_PASSWORD = 'your-database-password'
.\run.cmd
```

The database user needs schema-creation and CRUD privileges. Tables are initialized from `src/main/resources/database/mysql.sql`; passwords are not stored in source files. The MySQL adapter and schema are included, but a live MySQL server was not available for verification in this workspace. Integration testing uses a real temporary SQLite database.

The 37 requested domain tables include keys, foreign keys, unique constraints, checks and indexes. An additional `campus_lock` table is an internal transaction mutex, containing no campus records. SQLite transactions acquire `BEGIN IMMEDIATE` before validating conflicts. MySQL uses an InnoDB mutex row plus parent-row locks and `READ_COMMITTED`; operations are serialized deliberately for this academic desktop workload.

## Demonstration semester data

The included `data/campussphere.db` contains demo timetable, attendance, exam seating, hall tickets, course library links and project teams for the two departments. Cloud Computing semester 3 periods were imported from the supplied `GMU_CC2A_timetable.csv`; the other semester schedules were created for the assigned course names, with four weekly periods per course across weekdays. Attendance is synthetic at 80% for each enrollment, and December 2026 exam seats and hall tickets are allocated only above 75%. Demo fee records use INR 80,000 total and INR 60,000 paid; pending monthly salary samples range from INR 45,000 to INR 95,000 by designation. Replace these sample values with approved campus data before using them as real records.

To reproduce or refresh the semester records, run `python scripts/seed_semester_records.py --timetable "C:\\path\\to\\GMU_CC2A_timetable.csv"`. The seed script is idempotent and requires the course/student database to exist first.

## Architecture and academic review

```text
JavaFX views/controllers → services and access policies → repositories → JDBC → database
```

Controllers contain no SQL. Background JavaFX Tasks keep JDBC loading, login, reports and exports off the UI thread. Models demonstrate inheritance, encapsulation, polymorphism and interfaces; typed repositories demonstrate generics and overloaded search. Read [the OOP guide](docs/OOP-GUIDE.md), [test guide](docs/TESTING.md) and [architecture notes](docs/ARCHITECTURE.md).

The source requirements are preserved in `docs/SPECIFICATION.txt`. The supplied attachment stops mid-sentence in section 41. FXML was optional; this implementation uses reusable JavaFX view builders and a central module catalog instead.

## Scope and operation

This is an academic desktop application with local or shared MySQL persistence. It is not a web service, email service or cloud file host. All connected desktop clients must agree on campus timezone. Client-side database credentials and desktop service checks are appropriate to the PBL architecture; an internet-facing production deployment would require a server-side authorization boundary.

Git ignore rules are included. Git was unavailable in the development environment, so no repository history was initialized. No production campus records are seeded; integration and UI probes use separate temporary databases under test directories.
