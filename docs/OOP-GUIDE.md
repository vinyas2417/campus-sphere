# Academic OOP guide

Official title: **SMART CAMPUS INTEGRATED MANAGEMENT SYSTEM USING JAVA AND OBJECT-ORIENTED PROGRAMMING**.

| Requirement | Implementation and demonstration |
|---|---|
| Classes, objects and constructors | `AppContext` composes constructed services and repositories; each record represents a database entity |
| Packages | `app`, `config`, `controller`, `model`, `service`, `repository`, `database`, `exception`, `validation`, `util`, `task`, `ui` |
| Encapsulation | `Person` has private profile state and controlled getters/setters; `Row` defensively copies and exposes an unmodifiable values map |
| Abstraction | Abstract `Person`, `Resource`, `LeaveRequest`, and reusable `TypedJdbcRepository<T>` |
| Inheritance | `Student`, `Faculty`, `Administrator` extend Person; `Classroom`, `Laboratory`, `Equipment`, `SeminarRoom` extend Resource; StudentLeave, FacultyLeave, OnDutyRequest extend LeaveRequest |
| Polymorphism and overriding | Profile details call overridden `displayDetails()` through `Person`; booking invokes `ensureBookable()` through `Resource`; request approval uses a polymorphic LeaveRequest |
| Interfaces | `Bookable` is implemented by Resource; `Approvable` by LeaveRequest; `Repository<T>` by typed JDBC adapters |
| Generics | `Repository<T>`, `TypedJdbcRepository<T>`, `EntityValidator<T>`, generic JavaFX Task helper, typed observable lists |
| Method overloading | StudentRepository has `searchStudent(long)`, `searchStudent(String)` and `searchStudent(long departmentId, int semester)`; FacultyRepository and CourseRepository also provide typed searches |
| Collections | ArrayList record results, HashMap lookups/access-policy cache, HashSet field allowlist, Queue/ArrayDeque library reservation ordering |
| Stream API | Search/filter/sort, course ownership graphs, dashboard grouping/counting, report aggregation, available replacement faculty filtering |
| Lambdas | JavaFX actions, Task success/failure handlers, transaction callbacks, stream predicates and validators |
| Custom functional interface | `EntityValidator<T>` is injected into typed repository adapters and runs before writes |
| Exception handling | CampusException plus BookingConflictException, FacultyConflictException, CourseCapacityExceededException, InvalidAttendanceException, InvalidLeaveDateException, ResourceNotAvailableException and other domain exceptions |
| Multithreading | `Background` uses JavaFX Task and a daemon ExecutorService; callbacks update UI on the JavaFX Application Thread |
| Synchronization | Database transactions acquire an SQLite write reservation or MySQL lock before rechecking booking/capacity conditions; rollback keeps failures atomic |
| JDBC CRUD | JdbcRepository and WorkflowRepository use prepared statements and try-with-resources; services enforce policies and validation |

## Suggested demonstration

1. Start with the empty database and create an administrator; inspect the BCrypt hash in a test database.
2. Add a department, faculty member and two students. Create a course with capacity one.
3. Enroll the first student; show the readable capacity exception when trying the second.
4. Create an AI Lab and book 10:00–11:00. Attempt 10:30–11:30 to demonstrate conflict checking; show an adjacent 11:00–12:00 booking is valid.
5. Run `CampusIntegrationTest.simultaneousBookingsCommitExactlyOne` to show competing transactions produce one booking.
6. Show `Person.fromRow` and the three overridden role/detail methods. Open student details in the application.
7. Provision a student login, submit on-duty leave, and sign in as administrator to approve it. Return to the student notifications.
8. Generate a grouped report and export CSV. Explain why Task keeps the UI responsive and why UI visibility alone is insufficient access control.

Typed repositories are persistence components, not public authorization APIs. User operations enter service methods. The generic repository is used by the administrator's student search as well as persistence integration tests. Database transaction locking, rather than a Java-only `synchronized` block, protects multiple desktop instances too.
