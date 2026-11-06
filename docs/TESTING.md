# Verification

Run `mvnw.cmd test`. JUnit 5 integration tests use actual SQLite files in a temporary directory, with test-only records. They do not change `data/campussphere.db`.

The integration suite covers:

- BCrypt authentication, invalid login, single administrator setup and password-hash redaction.
- Account/profile linking, duplicate linking and unauthorized password resets.
- Database role detection, inactive-account rejection, single-login behavior, student/faculty/admin dashboard routing, and wrong-role API requests.
- JDBC create/read/update/delete, database reopen, search overloading and generics.
- Student read scoping and denial of administrative writes.
- Faculty ownership of assignments and teaching records.
- Enrollment capacity, duplicate enrollment and removal ownership.
- Concurrent bookings: two threads compete for the same interval and exactly one succeeds.
- Concurrent course enrollments and substitute assignments cannot exceed capacity or double-book replacement faculty.
- Overlap rejection, adjacent intervals, cancellation and maintenance states.
- Booking ownership, notification privacy and read persistence.
- Attendance enrollment checks, date checks and correction via upsert.
- Leave date/type validation, authorized approvals and terminal-state protection.
- Library inventory, returns and ordered reservations.
- Teaching/resource conflicts and substitute availability during approved leave.
- Event capacity/cancellation, doubt replies and help-desk responses.
- Foreign-key failures with rollback, invalid email and CSV escaping/formula neutralization.
- Actual document-upload byte persistence, upload permissions and transactional removal of timetable changes.

## Desktop smoke probe

After `mvnw.cmd package`, run:

```powershell
java --enable-native-access=ALL-UNNAMED -cp "target\test-classes;target\classes;target\lib\*" com.campussphere.UiSmokeProbe
```

This opens a temporary JavaFX window, fills the administrator setup form, signs in, creates a department and generates a live grouped report. It then creates isolated student/teacher fixtures, signs into each account through the same login form, verifies database-role routing, opens attendance through dashboard icons, checks each person's profile and a compact 1080 × 720 window, then signs out and verifies administrator access. It captures real UI snapshots under `target/ui-smoke`, then closes automatically. Both its database and remembered-username file are isolated in that directory. Success prints `UI_SMOKE_OK`.

The JavaFX smoke probe is explicit rather than part of ordinary unit tests because it requires a desktop display. The application was also visually inspected through its generated setup, dashboard and dialog snapshots. The smoke probe does not establish complete UI coverage of all workflow dialogs.

## MySQL validation

A live MySQL server was unavailable in this environment, so MySQL runtime behavior has not been verified. To test a MySQL deployment, configure the environment variables documented in README, start against an empty database, and repeat the academic demonstration including two simultaneous clients booking the same resource interval.

## Manual acceptance checks

1. Restart the application and confirm saved records and credentials persist.
2. Verify table search, reference-name filters, sorting, detail viewing and filtered CSV export.
3. Sign in as faculty and student; confirm forbidden management actions cannot be invoked.
4. Exercise leave/permission rejection remarks, event registration, attendance correction, study links, library return and cancellation.
5. Review the activity log and recipient-only notifications after changes.
6. Generate reports after adding records and confirm their counts match the filtered source tables.

On JDK 26, Maven/JavaFX may print compatibility warnings about deprecated JVM internals; these are distinct from build or application errors. Java 21 is the source baseline.
