package com.campussphere.config;

import com.campussphere.model.*;
import java.util.*;
import static com.campussphere.model.Role.*;

/** Central form and navigation definitions. Permissions are rechecked in services. */
public final class ModuleCatalog {
    private ModuleCatalog() {}
    private static final Set<Role> ALL = Set.of(ADMIN,FACULTY,STUDENT), STAFF = Set.of(ADMIN,FACULTY), A = Set.of(ADMIN), NONE = Set.of();
    private static final List<ModuleSpec> MODULES = build();
    public static List<ModuleSpec> all() { return MODULES; }
    public static boolean visibleInPortal(String key, Role role) {
        if (role == STUDENT && key.equals("faculty_appointments")) return false;
        if (role == FACULTY && key.equals("event_registrations")) return true;
        if (Set.of("resource_bookings", "doubts", "doubt_replies", "project_milestones", "notifications", "leave_requests", "projects", "library_borrowings", "library_reservations", "faculty_substitutions", "timetable_overrides").contains(key)) return false;
        return role != FACULTY || !Set.of("reports", "faculty_appointments").contains(key);
    }
    public static ModuleSpec get(String key) { return MODULES.stream().filter(m -> m.key().equals(key)).findFirst().orElseThrow(() -> new com.campussphere.exception.CampusException("Unknown module.")); }
    private static List<ModuleSpec> build() {
        List<ModuleSpec> m = new ArrayList<>();
        add(m,"students","Students","Student","People",ALL,A,
            "usn|USN|text!","name|Full name|text!","department_id|Department|@departments!","semester|Semester|integer!","section|Section|text!","email|Email|email!","phone|Phone|text","dob|Date of birth|date","address|Address|textarea","admission_year|Admission year|integer!","status|Status|=ACTIVE,INACTIVE,GRADUATED!");
        add(m,"faculty","Faculty","Faculty member","People",ALL,A,
            "name|Full name|text!","department_id|Department|@departments!","designation|Designation|=Professor,Associate Professor,Assistant Professor,HOD,Dean,Lecturer,Lab Instructor,Coordinator!","email|Email|email!","phone|Phone|text","address|Address|textarea","qualification|Qualification|text","experience|Experience (years)|integer","specialization|Specialization|text","joining_date|Joining date|date");
        add(m,"departments","Departments","Department","Academics",ALL,A,
            "name|Department name|=School of Computer Science and Technology,School of Electronics and Communication Engineering,School of Mechanical Engineering,School of Civil Engineering,School of Electrical and Electronics Engineering,School of Information Science and Engineering,School of Artificial Intelligence and Data Science,School of Biotechnology,School of Physics,School of Mathematics,School of Management Studies,School of Commerce,Department of English,Department of Chemistry!",
            "code|Code|text!","hod_id|Head of department|@faculty");
        add(m,"courses","Courses","Course","Academics",ALL,A,
            "code|Course code|text!","name|Course name|text!","credits|Credits|integer!","department_id|Department|@departments!","semester|Semester|=1,2,3,4,5,6,7,8!","faculty_id|Assigned faculty|@faculty!","capacity|Maximum capacity|integer!");
        add(m,"class_teacher_assignments","Class teachers","Class teacher assignment","Academics",STAFF,A,
            "faculty_id|Faculty|@faculty!","department_id|Department|@departments!","semester|Semester|integer!","section|Section|text!");
        add(m,"course_enrollments","Enrollments","Enrollment","Academics",ALL,NONE,
            "course_id|Course|@courses!","student_id|Student|@students!");
        add(m,"resources","Campus resources","Resource","Campus",ALL,A,
            "name|Resource name|text!","type|Type|=CLASSROOM,LABORATORY,EQUIPMENT,SEMINAR_ROOM!","building|Building|text!","floor|Floor|integer!","capacity|Capacity|integer!","status|Status|=AVAILABLE,OCCUPIED,RESERVED,MAINTENANCE!");
        add(m,"resource_bookings","Resource bookings","Booking","Campus",ALL,NONE,
            "resource_id|Resource|@resources!","user_id|Requested by|@users!","start_time|Start|datetime!","end_time|End|datetime!","purpose|Purpose|textarea!","status|Status|=PENDING,APPROVED,REJECTED,CANCELLED!");
        add(m,"timetable","Timetable","Timetable entry","Academics",ALL,NONE,
            "course_id|Course|@courses!","faculty_id|Faculty|@faculty!","resource_id|Room / lab|@resources!","day_of_week|Day|=1 (Monday),2 (Tuesday),3 (Wednesday),4 (Thursday),5 (Friday),6 (Saturday),7 (Sunday)!","start_time|Start time (HH:mm)|text!","end_time|End time (HH:mm)|text!");
        add(m,"timetable_overrides","Timetable changes","Timetable change","Academics",ALL,NONE,
            "timetable_id|Original class|@timetable!","date|Date|date!","resource_id|Resource|@resources!","start_time|Start time|text!","end_time|End time|text!","reason|Reason|textarea!");
        add(m,"attendance","Attendance","Attendance record","Academics",ALL,NONE,
            "course_id|Course|@courses!","student_id|Student|@students!","date|Date|date!","status|Status|=PRESENT,ABSENT,LATE,EXCUSED!");
        add(m,"assignments","Assignments","Assignment","Academics",ALL,STAFF,
            "course_id|Course|@courses!","title|Title|text!","description|Instructions|textarea!","due_date|Due date|date!","attachment_url|Assignment document|text");
        add(m,"study_materials","Study materials","Study material","Academics",ALL,STAFF,
            "course_id|Course|@courses!","title|Title|text!","type|Type|=NOTES,PRESENTATION,REFERENCE,VIDEO!","url|Resource URL|text!");
        add(m,"exam_schedule","Exam timetable","Exam","Examinations",ALL,STAFF,
            "course_id|Course|@courses!","title|Exam title|text!","date|Date|date!","start_time|Start (HH:mm)|text!","end_time|End (HH:mm)|text!","resource_id|Room|@resources!");
        add(m,"seating_allotments","Seating allotment","Seat allotment","Examinations",Set.of(ADMIN,STUDENT),A,
            "exam_id|Exam|@exam_schedule!","student_id|Student|@students!","resource_id|Room|@resources!","seat_number|Seat number|text!","roll_number|Roll number|text!","bench_number|Bench number|text!","seat_side|Side of bench|=LEFT,RIGHT!","room_number|Room number|text!");
        add(m,"hall_tickets","Hall ticket","Hall ticket","Examinations",Set.of(ADMIN,STUDENT),A,
            "exam_id|Exam|@exam_schedule!","student_id|Student|@students!","ticket_number|Ticket number|text!","issued_date|Issue date|date!","status|Status|=ISSUED,WITHHELD!");
        add(m,"student_fees","Fees","Fee record","Finance",Set.of(ADMIN,STUDENT),A,
            "student_id|Student|@students!","title|Fee description|text!","academic_year|Academic year|text!","amount|Amount (INR)|decimal!","paid_amount|Amount paid (INR)|decimal!","due_date|Due date|date!");
        add(m,"faculty_salaries","Faculty salary","Salary record","Finance",STAFF,A,
            "faculty_id|Faculty|@faculty!","salary_month|Salary month (YYYY-MM)|text!","amount|Salary (INR)|decimal!","status|Status|=PENDING,PAID!","paid_date|Payment date|date");
        add(m,"library_resources","E-Library","Library resource","Campus",ALL,STAFF,
            "course_id|Related course|@courses","title|Title|text!","author|Author|text!","category|Category|text!","url|Digital resource URL|text","total_copies|Copies|integer!");
        add(m,"library_borrowings","Library loans","Library loan","Campus",ALL,NONE,
            "library_resource_id|Book|@library_resources!","student_id|Student|@students!","borrowed_date|Borrowed date|date!","due_date|Due date|date!","returned_date|Returned date|date");
        add(m,"library_reservations","Library reservations","Reservation","Campus",ALL,NONE,
            "library_resource_id|Book|@library_resources!","student_id|Student|@students!","reserved_date|Reserved date|date!","status|Status|=PENDING,FULFILLED,CANCELLED!");
        add(m,"events","Events & activities","Event","Campus",ALL,A,
            "title|Event title|text!","description|Description|textarea!","location|Location|text!","start_time|Start|datetime!","end_time|End|datetime!","capacity|Capacity|integer!","coordinator_id|Event coordinator|@faculty");
        add(m,"event_registrations","Event registrations","Registration","Campus",ALL,NONE,
            "event_id|Event|@events!","student_id|Student|@students!");
        add(m,"leave_requests","Leave & on-duty","Leave request","Requests",ALL,NONE,
            "requester_id|Requester|@users!","type|Type|=STUDENT_LEAVE,FACULTY_LEAVE,ON_DUTY!","request_date|Requested|date!","from_date|From|date!","to_date|To|date!","reason|Reason|textarea!","status|Status|=PENDING,APPROVED,REJECTED,CANCELLED!","approval_remarks|Decision remarks|textarea");
        add(m,"permission_requests","Permissions","Permission request","Requests",ALL,NONE,
            "requester_id|Requester|@users!","date|Date|date!","from_time|From time|text!","to_time|To time|text!","reason|Reason|textarea!","status|Status|=PENDING,APPROVED,REJECTED,CANCELLED!","approval_remarks|Decision remarks|textarea");
        add(m,"faculty_substitutions","Faculty reallocation","Substitute assignment","Requests",STAFF,NONE,
            "timetable_id|Class|@timetable!","faculty_id|Replacement faculty|@faculty!","date|Date|date!","start_time|Start|text!","end_time|End|text!");
        add(m,"faculty_appointments","Faculty appointments","Appointment","Requests",ALL,ALL,
            "student_id|Student|@students!","faculty_id|Faculty|@faculty!","start_time|Start|datetime!","end_time|End|datetime!","purpose|Purpose|textarea!","status|Status|=PENDING,CONFIRMED,CANCELLED,COMPLETED!");
        add(m,"doubts","Doubt desk","Question","Collaboration",ALL,Set.of(ADMIN,STUDENT),
            "student_id|Student|@students!","course_id|Course|@courses!","subject|Subject|text!","question|Question|textarea!","status|Status|=OPEN,ANSWERED,CLOSED!");
        add(m,"doubt_replies","Doubt replies","Reply","Collaboration",ALL,NONE,
            "doubt_id|Question|@doubts!","faculty_id|Faculty|@faculty!","body|Reply|textarea!","created_at|Created|datetime!");
            add(m,"projects","Projects","Project","Collaboration",ALL,STAFF,
                "title|Title|text!","description|Description|textarea!","mentor_id|Guide|@faculty!","status|Status|=PLANNED,ACTIVE,COMPLETED,ON_HOLD!");
        add(m,"project_members","View project members","Project member","Collaboration",ALL,STAFF,
            "project_id|Project|@projects!","student_id|Student|@students!");
        add(m,"project_milestones","Project milestones","Milestone","Collaboration",ALL,STAFF,
            "project_id|Project|@projects!","title|Title|text!","due_date|Due date|date!","status|Status|=PENDING,IN_PROGRESS,COMPLETED!");
        add(m,"announcements","Announcements","Announcement","Communication",ALL,STAFF,
            "title|Title|text!","body|Message|textarea!","audience|Audience|=ALL,STUDENT,FACULTY,ADMIN!");
        add(m,"notifications","Notifications","Notification","Communication",ALL,NONE,
            "user_id|Recipient|@users!","title|Title|text!","body|Message|textarea!","created_at|Created|datetime!","is_read|Read|integer!");
        add(m,"helpdesk_tickets","Help desk","Help desk ticket","Communication",ALL,ALL,
            "subject|Subject|text!","description|Describe the issue|textarea!");
        add(m,"users","User accounts","User account","Administration",A,NONE,
            "username|Username|text!","name|Full name|text!","email|Email|email!","role|Role|=ADMIN,FACULTY,STUDENT!","created_at|Created|datetime!");
        add(m,"activity_logs","Activity logs","Activity","Administration",A,NONE,
            "user_id|User|@users","action|Action|text!","entity|Entity|text!","entity_id|Record ID|integer","created_at|Time|datetime!");
        return List.copyOf(m);
    }
    private static void add(List<ModuleSpec> list,String key,String title,String singular,String group,Set<Role> read,Set<Role> write,String... fields) {
        List<FieldSpec> specs = new ArrayList<>();
        for (String descriptor : fields) {
            String[] p = descriptor.split("\\|", -1);
            String type = p[2]; boolean required = type.endsWith("!");
            if (required) type = type.substring(0,type.length()-1);
            String reference = ""; List<String> choices = List.of();
            if (type.startsWith("@")) { reference = type.substring(1); type = "reference"; }
            else if (type.startsWith("=")) { choices = List.of(type.substring(1).split(",")); type = "choice"; }
            specs.add(new FieldSpec(p[0],p[1],type,required,reference,choices));
        }
        list.add(new ModuleSpec(key,title,singular,group,specs,read,write));
    }
}
