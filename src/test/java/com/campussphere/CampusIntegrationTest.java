package com.campussphere;

import com.campussphere.config.*;
import com.campussphere.database.Database;
import com.campussphere.exception.*;
import com.campussphere.model.*;
import com.campussphere.repository.*;
import com.campussphere.service.*;
import com.campussphere.util.CsvExporter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CampusIntegrationTest {
    @TempDir Path temp;
    Database db; JdbcRepository repo; Session session; AuthService auth; CampusService campus; WorkflowService workflows;
    User admin; Row department,faculty,student,otherStudent,course,resource;
    @BeforeEach void setup() {
        db=new Database(new DatabaseConfig("jdbc:sqlite:"+temp.resolve("campus.db"),"","")); db.initialize(); repo=new JdbcRepository(db); session=new Session(); auth=new AuthService(repo,session); campus=new CampusService(repo,session); workflows=new WorkflowService(repo,session);
        admin=auth.setupAdmin("Test Administrator","administrator","admin@campus.edu","TestPass!12345");
        department=campus.list("departments").getFirst();
        faculty=campus.save("faculty",null,m("name","Test Faculty","department_id",department.id(),"designation","Professor","email","faculty@campus.edu"));
        student=newStudent("S001","Student One"); otherStudent=newStudent("S002","Student Two");
        course=campus.save("courses",null,m("code","CS301","name","Object Oriented Programming","credits",4,"department_id",department.id(),"semester",3,"faculty_id",faculty.id(),"capacity",1));
        resource=campus.save("resources",null,m("name","AI Lab","type","LABORATORY","building","Academic Block","floor",2,"capacity",30,"status","AVAILABLE"));
    }
    Row newStudent(String usn,String name) { return campus.save("students",null,m("usn",usn,"name",name,"department_id",department.id(),"semester",3,"section","A","email",usn.toLowerCase()+"@campus.edu","admission_year",2025,"status","ACTIVE")); }
    void markFourPresent(long courseId,long studentId) {
        for(int i=4;i>0;i--) workflows.markAttendance(courseId,studentId,LocalDate.now().minusDays(i).toString(),"PRESENT");
    }
    User asStudent(Row profile) { return asRole(profile,Role.STUDENT); }
    User asRole(Row profile,Role role) {
        Row user=repo.insert("users",m("username","user"+role+profile.id(),"email","user"+role+profile.id()+"@campus.edu","password_hash","fixture-not-for-login","name",profile.text("name"),"role",role.name(),"created_at",LocalDateTime.now().toString()));
        repo.update(role==Role.STUDENT?"students":"faculty",profile.id(),Map.of("user_id",user.id())); User u=new User(user.id(),user.text("username"),user.text("name"),role); session.signIn(u); return u;
    }
    static Map<String,Object> m(Object... pairs) { Map<String,Object> map=new LinkedHashMap<>(); for(int i=0;i<pairs.length;i+=2)map.put(pairs[i].toString(),pairs[i+1]);return map; }
    @Test void portalMenusHideRemovedOptionsAndExposeRoleSpecificAdditions() {
        for(Role role:Role.values()) {
            if(role==Role.ADMIN) session.signIn(admin);
            else if(role==Role.STUDENT) asStudent(student);
            else asRole(faculty,Role.FACULTY);
            Set<String> keys=new HashSet<>(campus.modules().stream().map(ModuleSpec::key).toList());
            for(String hidden:List.of("resource_bookings","doubts","doubt_replies","project_milestones","notifications","leave_requests","projects","library_borrowings","library_reservations","faculty_substitutions","timetable_overrides")) assertFalse(keys.contains(hidden),hidden);
            assertTrue(keys.contains("project_members"));
            assertEquals(role!=Role.STUDENT,keys.contains("faculty_salaries"));
            assertEquals(role!=Role.FACULTY,keys.contains("student_fees"));
            if(role==Role.STUDENT) assertTrue(keys.containsAll(List.of("seating_allotments","hall_tickets","exam_schedule")));
            if(role==Role.FACULTY) assertFalse(keys.contains("faculty_appointments"));
        }
        assertFalse(ModuleCatalog.visibleInPortal("reports",Role.FACULTY));
        assertEquals("View project members",ModuleCatalog.get("project_members").title());
    }
    @Test void financialRecordsAreValidatedAndPrivateToTheirOwners() {
        Row fee=campus.save("student_fees",null,m("student_id",student.id(),"title","Tuition","academic_year","2026-27","amount","1000.50","paid_amount","200.25","due_date","2026-12-01"));
        campus.save("student_fees",null,m("student_id",otherStudent.id(),"title","Tuition","academic_year","2026-27","amount",500,"paid_amount",0,"due_date","2026-12-01"));
        Row salary=campus.save("faculty_salaries",null,m("faculty_id",faculty.id(),"salary_month","2026-10","amount","45000.25","status","PENDING"));
        Row otherFaculty=campus.save("faculty",null,m("name","Other Faculty","department_id",department.id(),"designation","Professor","email","other.salary@campus.edu"));
        campus.save("faculty_salaries",null,m("faculty_id",otherFaculty.id(),"salary_month","2026-10","amount",60000,"status","PENDING"));
        assertEquals(2,campus.list("faculty_salaries").size());
        for(Object invalid:List.of("-1","NaN","Infinity","1.001","10000000000")) {
            Map<String,Object> bad=editable("student_fees",fee); bad.put("amount",invalid);
            assertThrows(CampusException.class,()->campus.save("student_fees",fee.id(),bad));
        }
        Map<String,Object> overpaid=editable("student_fees",fee); overpaid.put("paid_amount",2000);
        assertThrows(CampusException.class,()->campus.save("student_fees",fee.id(),overpaid));
        Map<String,Object> badMonth=editable("faculty_salaries",salary); badMonth.put("salary_month","2026-13");
        assertThrows(CampusException.class,()->campus.save("faculty_salaries",salary.id(),badMonth));
        Map<String,Object> paid=editable("faculty_salaries",salary); paid.put("status","PAID");
        assertThrows(CampusException.class,()->campus.save("faculty_salaries",salary.id(),paid));
        paid.put("paid_date","2026-10-31"); campus.save("faculty_salaries",salary.id(),paid);
        asStudent(student);
        assertEquals(List.of(fee.id()),campus.list("student_fees").stream().map(Row::id).toList());
        assertThrows(CampusException.class,()->campus.list("faculty_salaries"));
        assertThrows(CampusException.class,()->campus.save("student_fees",fee.id(),editable("student_fees",fee)));
        session.signIn(admin); asRole(faculty,Role.FACULTY);
        assertEquals(List.of(salary.id()),campus.list("faculty_salaries").stream().map(Row::id).toList());
        assertEquals("PAID",campus.list("faculty_salaries").getFirst().text("status"));
        assertThrows(CampusException.class,()->campus.list("student_fees"));
        assertThrows(CampusException.class,()->campus.delete("faculty_salaries",salary.id()));
    }
    @Test void seatingAndHallTicketsRequireEnrollmentAndRespectStudentPrivacy() {
        Row exam=campus.save("exam_schedule",null,m("course_id",course.id(),"title","Final exam","date","2026-12-10","start_time","09:00","end_time","11:00","resource_id",resource.id()));
        Map<String,Object> seat=m("exam_id",exam.id(),"student_id",student.id(),"resource_id",resource.id(),"seat_number","a1","roll_number",student.text("usn"),"bench_number","B001","seat_side","LEFT","room_number",resource.text("name"));
        Map<String,Object> ticket=m("exam_id",exam.id(),"student_id",student.id(),"ticket_number","HT001","issued_date","2026-12-01","status","ISSUED");
        assertThrows(CampusException.class,()->campus.save("seating_allotments",null,seat));
        assertThrows(CampusException.class,()->campus.save("hall_tickets",null,ticket));
        workflows.enroll(course.id(),student.id());
        assertThrows(CampusException.class,()->campus.save("seating_allotments",null,seat));
        assertThrows(CampusException.class,()->campus.save("hall_tickets",null,ticket));
        markFourPresent(course.id(),student.id());
        Row allotted=campus.save("seating_allotments",null,seat);
        assertEquals("A1",allotted.text("seat_number"));
        Row issued=campus.save("hall_tickets",null,ticket);
        assertThrows(CampusException.class,()->campus.save("seating_allotments",null,seat));
        assertThrows(CampusException.class,()->campus.save("hall_tickets",null,ticket));
        asStudent(otherStudent);
        assertTrue(campus.list("seating_allotments").isEmpty());
        assertTrue(campus.list("hall_tickets").isEmpty());
        session.signIn(admin); asStudent(student);
        assertEquals(List.of(allotted.id()),campus.list("seating_allotments").stream().map(Row::id).toList());
        assertEquals(List.of(issued.id()),campus.list("hall_tickets").stream().map(Row::id).toList());
        assertEquals(List.of(exam.id()),campus.list("exam_schedule").stream().map(Row::id).toList());
        assertThrows(CampusException.class,()->campus.delete("hall_tickets",issued.id()));
    }
    @Test void rosterAttendanceSavesAtomicallyAndChecksTeacherAndEnrollment() {
        repo.update("courses",course.id(),Map.of("capacity",3));
        workflows.enroll(course.id(),student.id());workflows.enroll(course.id(),otherStudent.id());
        asRole(faculty,Role.FACULTY);
        String today=LocalDate.now().toString();
        List<Row> roster=workflows.attendanceRoster(course.id(),today);
        assertEquals(Set.of("Student One","Student Two"),new HashSet<>(roster.stream().map(r->r.text("name")).toList()));
        assertTrue(roster.stream().allMatch(r->r.text("attendance_status").isEmpty()));
        workflows.markAttendanceBatch(course.id(),today,Map.of(student.id(),"PRESENT",otherStudent.id(),"ABSENT"));
        assertEquals("PRESENT",workflows.attendanceRoster(course.id(),today).stream().filter(r->r.id()==student.id()).findFirst().orElseThrow().text("attendance_status"));
        assertThrows(CampusException.class,()->workflows.markAttendanceBatch(course.id(),today,Map.of(student.id(),"ABSENT")));
        assertThrows(CampusException.class,()->workflows.markAttendanceBatch(course.id(),today,Map.of(student.id(),"ABSENT",otherStudent.id(),"INVALID")));
        assertThrows(CampusException.class,()->workflows.markAttendanceBatch(course.id(),LocalDate.now().plusDays(1).toString(),Map.of(student.id(),"ABSENT",otherStudent.id(),"ABSENT")));
        assertEquals("PRESENT",workflows.attendanceRoster(course.id(),today).stream().filter(r->r.id()==student.id()).findFirst().orElseThrow().text("attendance_status"));
        workflows.markAttendanceBatch(course.id(),today,Map.of(student.id(),"ABSENT",otherStudent.id(),"PRESENT"));
        assertEquals(2,campus.list("attendance").size());
        session.signIn(admin);
        Row otherFaculty=campus.save("faculty",null,m("name","Unassigned","department_id",department.id(),"designation","Professor","email","unassigned@campus.edu"));
        asRole(otherFaculty,Role.FACULTY);
        assertThrows(CampusException.class,()->workflows.attendanceRoster(course.id(),today));
        assertThrows(CampusException.class,()->workflows.markAttendanceBatch(course.id(),today,Map.of(student.id(),"PRESENT",otherStudent.id(),"PRESENT")));
        session.signIn(admin);asStudent(student);
        assertThrows(CampusException.class,()->workflows.attendanceRoster(course.id(),today));
        assertThrows(CampusException.class,()->workflows.markAttendanceBatch(course.id(),today,Map.of(student.id(),"PRESENT",otherStudent.id(),"PRESENT")));
    }
    @Test void documentsUseAuthorizedCurrentRecordsAndRequireIssueOrPayment() throws Exception {
        DocumentService documents=new DocumentService(repo,session);
        Row fee=campus.save("student_fees",null,m("student_id",student.id(),"title","Tuition","academic_year","2026-27","amount","1000.50","paid_amount","200.25","due_date","2026-12-01"));
        Row unpaid=campus.save("student_fees",null,m("student_id",otherStudent.id(),"title","Tuition","academic_year","2026-27","amount",500,"paid_amount",0,"due_date","2026-12-01"));
        Row salary=campus.save("faculty_salaries",null,m("faculty_id",faculty.id(),"salary_month","2026-10","amount","45000.25","status","PENDING"));
        assertThrows(CampusException.class,()->documents.prepare("faculty_salaries",salary.id()));
        assertThrows(CampusException.class,()->documents.prepare("student_fees",unpaid.id()));
        Map<String,Object> paid=editable("faculty_salaries",salary);paid.put("status","PAID");paid.put("paid_date","2026-10-31");campus.save("faculty_salaries",salary.id(),paid);
        Row exam=campus.save("exam_schedule",null,m("course_id",course.id(),"title","Final exam","date","2026-12-10","start_time","09:00","end_time","11:00","resource_id",resource.id()));
        workflows.enroll(course.id(),student.id());
        markFourPresent(course.id(),student.id());
        Row ticket=campus.save("hall_tickets",null,m("exam_id",exam.id(),"student_id",student.id(),"ticket_number","HT001","issued_date","2026-12-01","status","WITHHELD"));
        assertThrows(CampusException.class,()->documents.prepare("hall_tickets",ticket.id()));
        Map<String,Object> issued=editable("hall_tickets",ticket);issued.put("status","ISSUED");campus.save("hall_tickets",ticket.id(),issued);
        campus.save("seating_allotments",null,m("exam_id",exam.id(),"student_id",student.id(),"resource_id",resource.id(),"seat_number","A7","roll_number",student.text("usn"),"bench_number","B001","seat_side","LEFT","room_number",resource.text("name")));
        asStudent(student);
        DownloadDocument receipt=documents.prepare("student_fees",fee.id());
        assertTrue(receipt.entries().contains(new DownloadDocument.Entry("Balance (INR)","800.25")));
        DownloadDocument hall=documents.prepare("hall_tickets",ticket.id());
        assertTrue(hall.entries().contains(new DownloadDocument.Entry("Seat","A7")));
        assertTrue(hall.entries().contains(new DownloadDocument.Entry("Student","Student One")));
        DownloadDocument allHalls=documents.prepareAllHallTickets();
        assertTrue(allHalls.title().contains("Course Hall Tickets"));
        assertTrue(allHalls.entries().stream().anyMatch(entry->entry.label().equals("Course ticket 1")));
        assertThrows(CampusException.class,()->documents.prepare("student_fees",unpaid.id()));
        assertThrows(CampusException.class,()->documents.prepare("faculty_salaries",salary.id()));
        assertThrows(CampusException.class,()->documents.prepare("students",student.id()));
        Path pdf=temp.resolve("hall-ticket.pdf");com.campussphere.util.PdfDocument.write(pdf,hall);
        byte[] bytes=Files.readAllBytes(pdf);String source=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(source.startsWith("%PDF-1.4"));assertTrue(source.endsWith("%%EOF\n"));assertTrue(bytes.length>10000);
        int marker=source.lastIndexOf("startxref\n");int offset=Integer.parseInt(source.substring(marker+10,source.indexOf('\n',marker+10)));
        assertTrue(source.substring(offset).startsWith("xref\n"));
        session.signIn(admin);asStudent(otherStudent);
        assertThrows(CampusException.class,()->documents.prepare("hall_tickets",ticket.id()));
        session.signIn(admin);asRole(faculty,Role.FACULTY);
        DownloadDocument salaryReceipt=documents.prepare("faculty_salaries",salary.id());
        assertTrue(salaryReceipt.entries().contains(new DownloadDocument.Entry("Amount paid (INR)","45000.25")));
        assertThrows(CampusException.class,()->documents.prepare("student_fees",fee.id()));
    }
    @Test void passwordsAreHashedAndAuthenticationWorks() {
        assertTrue(repo.findById("users",admin.id()).orElseThrow().text("password_hash").startsWith("$2a$"));
        auth.logout(); assertThrows(CampusException.class,()->auth.login("administrator","wrong"));
        assertEquals(Role.ADMIN,auth.login("ADMIN@CAMPUS.EDU","TestPass!12345").role());
        assertThrows(CampusException.class,()->auth.setupAdmin("Duplicate","newadmin","new@campus.edu","TestPass!12345"));
        assertFalse(campus.list("users").getFirst().values().containsKey("password_hash"));
    }
    @Test void sixCharacterPasswordsWorkAcrossAllPortalsAndShorterPasswordsAreRejected() {
        assertThrows(CampusException.class,()->auth.resetPassword(admin.id(),"12345"));
        auth.resetPassword(admin.id(),"Adm123");
        auth.createAccount("Student One","six.student","six.student@campus.edu","Stu123",Role.STUDENT,student.id());
        auth.createAccount("Test Faculty","six.teacher","six.teacher@campus.edu","Tea123",Role.FACULTY,faculty.id());
        auth.logout();
        assertEquals(Role.ADMIN,auth.login("administrator","Adm123",Role.ADMIN).role());
        auth.logout();
        assertEquals(Role.STUDENT,auth.login("six.student","Stu123",Role.STUDENT).role());
        assertThrows(CampusException.class,()->auth.changePassword("Stu123","12345"));
        auth.changePassword("Stu123","New123");
        auth.logout();
        assertThrows(CampusException.class,()->auth.login("six.student","Stu123",Role.STUDENT));
        assertEquals(Role.STUDENT,auth.login("six.student","New123",Role.STUDENT).role());
        auth.logout();
        assertEquals(Role.FACULTY,auth.login("six.teacher","Tea123",Role.FACULTY).role());
    }
    @Test void accountProvisioningLinksProfileAndCannotLinkTwice() {
        auth.createAccount("Student One","student.one","account@campus.edu","StudentPass!123",Role.STUDENT,student.id());
        assertTrue(repo.findById("students",student.id()).orElseThrow().number("user_id")>0);
        assertThrows(CampusException.class,()->auth.createAccount("Again","again.user","again@campus.edu","StudentPass!123",Role.STUDENT,student.id()));
        auth.login("student.one","StudentPass!123"); assertEquals(1,campus.list("students").size());
        assertThrows(CampusException.class,()->auth.resetPassword(admin.id(),"InjectedPass!123"));
    }
    @Test void separatePortalsRejectWrongRolesBeforeCreatingSessions() {
        String password="PortalPass!123";
        auth.createAccount("Student One","portal.student","portal.student@campus.edu",password,Role.STUDENT,student.id());
        auth.createAccount("Test Faculty","portal.teacher","portal.teacher@campus.edu",password,Role.FACULTY,faculty.id());
        Map<Role,String> usernames=Map.of(Role.ADMIN,"administrator",Role.STUDENT,"portal.student",Role.FACULTY,"portal.teacher");
        for(Role account:Role.values()) for(Role portal:Role.values()) {
            auth.logout();
            String secret=account==Role.ADMIN?"TestPass!12345":password;
            if(account==portal) assertEquals(account,auth.login(usernames.get(account),secret,portal).role());
            else {
                assertThrows(CampusException.class,()->auth.login(usernames.get(account),secret,portal));
                assertTrue(session.user().isEmpty(),"A rejected portal must not create a session");
            }
        }
        auth.logout();
        assertThrows(CampusException.class,()->auth.login("portal.student","wrong",Role.STUDENT));
        assertTrue(session.user().isEmpty());
        assertEquals(Role.FACULTY,auth.login("PORTAL.TEACHER@CAMPUS.EDU",password,Role.FACULTY).role());
    }
    @Test void authenticationUsesStoredRoleAndRejectsInactiveAccounts() {
        auth.createAccount("Test Faculty","stu00099","db.role@campus.edu","FacultyPass!123",Role.FACULTY,faculty.id());
        assertEquals(Role.FACULTY,auth.login("stu00099","FacultyPass!123").role(),"The database role wins even when the username resembles a student USN.");
        Row account=repo.findAll("users").stream().filter(row->row.text("username").equals("stu00099")).findFirst().orElseThrow();
        auth.logout(); repo.update("users",account.id(),Map.of("account_status","INACTIVE"));
        CampusException inactive=assertThrows(CampusException.class,()->auth.login("stu00099","FacultyPass!123"));
        assertTrue(inactive.getMessage().contains("inactive"));
        assertTrue(session.user().isEmpty());
    }
    @Test void jdbcCrudSurvivesDatabaseReopenAndSupportsSearchOverloads() {
        Map<String,Object> values=editable("students",student); values.put("name","Updated Name"); campus.save("students",student.id(),values);
        Database reopened=new Database(new DatabaseConfig("jdbc:sqlite:"+temp.resolve("campus.db"),"","")); reopened.initialize(); StudentRepository students=new StudentRepository(new JdbcRepository(reopened));
        assertEquals("Updated Name",students.searchStudent(student.id()).orElseThrow().getName());
        assertEquals(1,students.searchStudent("updated").size()); assertEquals(2,students.searchStudent(department.id(),3).size());
        campus.delete("students",otherStudent.id()); assertEquals(1,students.findAll().size());
    }
    @Test void studentCannotWriteAdminRecordsOrReadOtherProfiles() {
        asStudent(student); assertEquals(List.of(student.id()),campus.list("students").stream().map(Row::id).toList());
        assertThrows(CampusException.class,()->campus.delete("students",otherStudent.id()));
        assertThrows(CampusException.class,()->campus.save("departments",null,m("name","Injected","code","BAD")));
        assertThrows(CampusException.class,()->campus.list("users"));
        assertThrows(CampusException.class,()->campus.save("resource_bookings",null,Map.of()));
    }
    @Test void facultyCanOnlyChangeAssignedCourses() {
        asRole(faculty,Role.FACULTY);
        campus.save("assignments",null,m("course_id",course.id(),"title","Build a class","description","Implement encapsulation","due_date",LocalDate.now().plusDays(7).toString()));
        session.signIn(admin); Row otherFaculty=campus.save("faculty",null,m("name","Other Faculty","department_id",department.id(),"designation","Lecturer","email","other@campus.edu")); User other=asRole(otherFaculty,Role.FACULTY);
        assertTrue(campus.list("assignments").isEmpty());
        assertThrows(CampusException.class,()->campus.save("assignments",null,m("course_id",course.id(),"title","Bad","description","Bad","due_date",LocalDate.now().toString())));
    }
    @Test void courseCapacityAndEnrollmentOwnershipAreEnforced() {
        workflows.enroll(course.id(),student.id()); assertThrows(CourseCapacityExceededException.class,()->workflows.enroll(course.id(),otherStudent.id()));
        assertThrows(CampusException.class,()->workflows.enroll(course.id(),student.id()));
        asStudent(otherStudent); assertThrows(CampusException.class,()->workflows.removeEnrollment(repo.findAll("course_enrollments").getFirst().id()));
    }
    @Test void simultaneousBookingsCommitExactlyOne() throws Exception {
        List<Boolean> results=race(()->workflows.bookResource(resource.id(),"2030-01-10T10:00","2030-01-10T11:00","Workshop"));
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count()); assertEquals(1,repo.findAll("resource_bookings").size());
    }
    @Test void adjacentBookingAllowedOverlapRejectedAndCancellationFreesSlot() {
        Row first=workflows.bookResource(resource.id(),"2030-01-10T10:00","2030-01-10T11:00","Lecture");
        workflows.bookResource(resource.id(),"2030-01-10T11:00","2030-01-10T12:00","Workshop");
        assertThrows(BookingConflictException.class,()->workflows.bookResource(resource.id(),"2030-01-10T10:30","2030-01-10T11:30","Overlap"));
        workflows.cancelBooking(first.id()); workflows.bookResource(resource.id(),"2030-01-10T10:00","2030-01-10T11:00","Replacement");
    }
    @Test void maintenanceResourceCannotBeBooked() {
        Map<String,Object> values=editable("resources",resource); values.put("status","MAINTENANCE"); campus.save("resources",resource.id(),values);
        assertThrows(CampusException.class,()->workflows.bookResource(resource.id(),"2030-01-10T10:00","2030-01-10T11:00","Workshop"));
    }
    @Test void bookingOwnershipAndNotificationPrivacyAreEnforced() {
        User one=asStudent(student); Row booking=workflows.bookResource(resource.id(),"2030-01-10T10:00","2030-01-10T11:00","Study"); long notification=campus.list("notifications").getFirst().id();
        asStudent(otherStudent); assertTrue(campus.list("resource_bookings").isEmpty()); assertTrue(campus.list("notifications").isEmpty());
        assertThrows(CampusException.class,()->workflows.cancelBooking(booking.id())); assertThrows(CampusException.class,()->workflows.markNotificationRead(notification));
        session.signIn(one); workflows.markNotificationRead(notification); assertEquals(1,campus.list("notifications").getFirst().number("is_read"));
    }
    @Test void attendanceRequiresEnrollmentAndIsAnUpsert() {
        assertThrows(InvalidAttendanceException.class,()->workflows.markAttendance(course.id(),student.id(),LocalDate.now().toString(),"PRESENT"));
        workflows.enroll(course.id(),student.id()); Row record=workflows.markAttendance(course.id(),student.id(),LocalDate.now().toString(),"ABSENT");
        assertEquals(record.id(),workflows.markAttendance(course.id(),student.id(),LocalDate.now().toString(),"PRESENT").id()); assertEquals(1,repo.findAll("attendance").size());
        assertThrows(InvalidAttendanceException.class,()->workflows.markAttendance(course.id(),student.id(),LocalDate.now().plusDays(1).toString(),"PRESENT"));
    }
    @Test void leaveDateRoleAndApprovalStateAreValidated() {
        User user=asStudent(student);
        assertThrows(CampusException.class,()->workflows.requestLeave("FACULTY_LEAVE","2030-01-01","2030-01-02","Wrong role"));
        assertThrows(InvalidLeaveDateException.class,()->workflows.requestLeave("STUDENT_LEAVE","2030-01-02","2030-01-01","Bad dates"));
        Row request=workflows.requestLeave("ON_DUTY","2030-01-01","2030-01-02","University competition");
        assertThrows(CampusException.class,()->workflows.decideLeave(request.id(),true,"Approved"));
        session.signIn(admin); workflows.decideLeave(request.id(),true,"Approved"); assertThrows(CampusException.class,()->workflows.decideLeave(request.id(),false,"Again"));
        assertEquals("APPROVED",repo.findById("leave_requests",request.id()).orElseThrow().text("status"));
        session.signIn(user); assertThrows(CampusException.class,()->workflows.cancelLeave(request.id()));
    }
    @Test void libraryInventoryReturnAndReservationQueuePersist() {
        Row book=campus.save("library_resources",null,m("title","Java Fundamentals","author","Test Author","category","Computing","total_copies",1));
        Row loan=workflows.borrowBook(book.id(),student.id(),LocalDate.now().plusDays(14).toString());
        assertThrows(CampusException.class,()->workflows.borrowBook(book.id(),otherStudent.id(),LocalDate.now().plusDays(14).toString()));
        asStudent(otherStudent); Row reservation=workflows.reserveBook(book.id());
        session.signIn(admin); workflows.returnBook(loan.id());
        assertThrows(CampusException.class,()->workflows.borrowBook(book.id(),student.id(),LocalDate.now().plusDays(14).toString()));
        workflows.borrowBook(book.id(),otherStudent.id(),LocalDate.now().plusDays(14).toString()); assertEquals("FULFILLED",repo.findById("library_reservations",reservation.id()).orElseThrow().text("status"));
    }
    @Test void timetableConflictsWithBookingsAndOtherTeaching() {
        workflows.saveTimetable(null,course.id(),faculty.id(),resource.id(),LocalDate.of(2030,1,10).getDayOfWeek().getValue(),"10:00","11:00");
        assertThrows(BookingConflictException.class,()->workflows.bookResource(resource.id(),"2030-01-10T10:30","2030-01-10T11:30","Conflict"));
        assertThrows(FacultyConflictException.class,()->workflows.saveTimetable(null,course.id(),faculty.id(),resource.id(),LocalDate.of(2030,1,10).getDayOfWeek().getValue(),"10:30","11:30"));
    }
    @Test void substitutionsDetectFacultyConflictsAndApprovedLeave() {
        Row replacement=campus.save("faculty",null,m("name","Substitute","department_id",department.id(),"designation","Lecturer","email","sub@campus.edu"));
        Row slot=workflows.saveTimetable(null,course.id(),faculty.id(),resource.id(),LocalDate.of(2030,1,10).getDayOfWeek().getValue(),"10:00","11:00");
        User substitute=asRole(replacement,Role.FACULTY); Row leave=workflows.requestLeave("FACULTY_LEAVE","2030-01-10","2030-01-10","Conference");
        session.signIn(admin); workflows.decideLeave(leave.id(),true,"Approved"); assertTrue(workflows.availableFaculty(slot.id(),"2030-01-10").isEmpty());
        assertThrows(FacultyConflictException.class,()->workflows.allocateSubstitute(slot.id(),replacement.id(),"2030-01-10"));
    }
    @Test void eventCapacityAndCancellationWork() {
        Row event=campus.save("events",null,m("title","Tech Day","description","Campus talks","location","Hall","start_time","2035-01-10T10:00","end_time","2035-01-10T12:00","capacity",1));
        Row registration=workflows.registerEvent(event.id(),student.id()); assertThrows(CampusException.class,()->workflows.registerEvent(event.id(),otherStudent.id())); workflows.cancelEventRegistration(registration.id()); workflows.registerEvent(event.id(),otherStudent.id());
    }
    @Test void doubtReplyAndHelpDeskAreRealWorkflows() {
        workflows.enroll(course.id(),student.id()); User learner=asStudent(student); Row doubt=campus.save("doubts",null,m("student_id",student.id(),"course_id",course.id(),"subject","Polymorphism","question","How does overriding work?","status","OPEN")); Row ticket=campus.save("helpdesk_tickets",null,m("subject","Library access","description","Please help"));
        asRole(faculty,Role.FACULTY); workflows.replyToDoubt(doubt.id(),"The runtime type chooses the overridden method."); assertEquals("ANSWERED",repo.findById("doubts",doubt.id()).orElseThrow().text("status"));
        session.signIn(admin); workflows.resolveTicket(ticket.id(),"Access restored"); session.signIn(learner); assertEquals("RESOLVED",campus.list("helpdesk_tickets").getFirst().text("status")); assertFalse(campus.list("notifications").isEmpty());
    }
    @Test void invalidForeignKeysAndInputDoNotCreatePartialRecords() {
        long count=repo.findAll("students").size(); Map<String,Object> values=editable("students",student); values.put("usn","NEW"); values.put("email","new@campus.edu"); values.put("department_id",99999);
        assertThrows(CampusException.class,()->campus.save("students",null,values)); assertEquals(count,repo.findAll("students").size()); values.put("department_id",department.id()); values.put("email","invalid"); assertThrows(CampusException.class,()->campus.save("students",null,values));
    }
    @Test void csvEscapesQuotesNewlinesAndSpreadsheetFormulas() throws Exception {
        Path csv=temp.resolve("records.csv"); CsvExporter.export(csv,List.of("id","name"),List.of(new Row(1,Map.of("name"," =HYPERLINK(\"bad\")")),new Row(2,Map.of("name","two\nlines")))); String contents=Files.readString(csv); assertTrue(contents.contains("' =HYPERLINK(\"\"bad\"\")")); assertTrue(contents.contains("\"two\nlines\""));
    }
    @Test void concurrentEnrollmentCannotExceedCapacity() throws Exception {
        java.util.concurrent.atomic.AtomicInteger next=new java.util.concurrent.atomic.AtomicInteger();
        List<Boolean> results=race(()->workflows.enroll(course.id(),next.getAndIncrement()==0?student.id():otherStudent.id()));
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());assertEquals(1,repo.findAll("course_enrollments").size());
    }
    @Test void materialUploadCopiesBytesAndRestrictsCourseOwnership() throws Exception {
        Path source=temp.resolve("oop-notes.txt");Files.writeString(source,"Object oriented programming notes");
        Map<String,Object> values=m("course_id",course.id(),"title","OOP notes","type","NOTES");
        Row uploaded=new MaterialService(campus,session).upload(source,values);Path copied=Path.of(java.net.URI.create(uploaded.text("url")));
        try {assertEquals(Files.readString(source),Files.readString(copied));assertEquals(1,campus.list("study_materials").size());asStudent(student);assertThrows(CampusException.class,()->new MaterialService(campus,session).upload(source,values));}
        finally {Files.deleteIfExists(copied);}
    }
    @Test void deletingTimetableRemovesItsDatedChangesTransactionally() {
        LocalDate day=LocalDate.of(2030,1,10);Row slot=workflows.saveTimetable(null,course.id(),faculty.id(),resource.id(),day.getDayOfWeek().getValue(),"10:00","11:00");
        workflows.saveTimetableOverride(slot.id(),day.toString(),resource.id(),"12:00","13:00","Guest lecture");
        workflows.removeTimetable(slot.id());assertTrue(repo.findAll("timetable").isEmpty());assertTrue(repo.findAll("timetable_overrides").isEmpty());
    }
    @Test void concurrentSubstitutionsCannotDoubleBookReplacementFaculty() throws Exception {
        Row teacher=campus.save("faculty",null,m("name","Other Teacher","department_id",department.id(),"designation","Lecturer","email","other.teacher@campus.edu"));
        Row replacement=campus.save("faculty",null,m("name","Replacement","department_id",department.id(),"designation","Lecturer","email","replacement@campus.edu"));
        Row secondCourse=campus.save("courses",null,m("code","CS401","name","Advanced Java","credits",4,"department_id",department.id(),"semester",4,"faculty_id",teacher.id(),"capacity",20));
        Row room=campus.save("resources",null,m("name","Classroom B","type","CLASSROOM","building","Academic Block","floor",1,"capacity",30,"status","AVAILABLE"));
        LocalDate date=LocalDate.of(2030,1,10);Row first=workflows.saveTimetable(null,course.id(),faculty.id(),resource.id(),date.getDayOfWeek().getValue(),"10:00","11:00"),second=workflows.saveTimetable(null,secondCourse.id(),teacher.id(),room.id(),date.getDayOfWeek().getValue(),"10:00","11:00");
        java.util.concurrent.atomic.AtomicInteger next=new java.util.concurrent.atomic.AtomicInteger();List<Boolean> results=race(()->workflows.allocateSubstitute(next.getAndIncrement()==0?first.id():second.id(),replacement.id(),date.toString()));
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());assertEquals(1,repo.findAll("faculty_substitutions").size());
    }
    private Map<String,Object> editable(String key,Row row) { Map<String,Object> values=new LinkedHashMap<>(); ModuleCatalog.get(key).fields().forEach(f->values.put(f.key(),row.get(f.key())));return values; }
    private List<Boolean> race(Callable<Row> operation) throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try { List<Future<Boolean>> futures=new ArrayList<>(); for(int i=0;i<2;i++) futures.add(executor.submit(()->{start.await();try{operation.call();return true;}catch(CampusException expected){return false;}}));start.countDown();List<Boolean> result=new ArrayList<>();for(Future<Boolean> f:futures)result.add(f.get(20,TimeUnit.SECONDS));return result; } finally { executor.shutdownNow(); }
    }
}
