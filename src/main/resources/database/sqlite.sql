CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username VARCHAR(191) NOT NULL UNIQUE,
  email VARCHAR(191) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(255) NOT NULL,
  role VARCHAR(16) NOT NULL CHECK(role IN ('ADMIN','FACULTY','STUDENT')),
  account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK(account_status IN ('ACTIVE','INACTIVE')),
  created_at VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS departments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name VARCHAR(255) NOT NULL UNIQUE,
  code VARCHAR(32) NOT NULL UNIQUE,
  hod_id BIGINT,
  FOREIGN KEY(hod_id) REFERENCES faculty(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS students (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id BIGINT UNIQUE,
  usn VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  department_id BIGINT NOT NULL,
  semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 12),
  section VARCHAR(32) NOT NULL,
  email VARCHAR(191) NOT NULL UNIQUE,
  phone VARCHAR(32),
  dob VARCHAR(10),
  address TEXT,
  admission_year INTEGER NOT NULL CHECK(admission_year BETWEEN 1900 AND 2200),
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','INACTIVE','GRADUATED')),
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL,
  FOREIGN KEY(department_id) REFERENCES departments(id)
);

CREATE TABLE IF NOT EXISTS faculty (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id BIGINT UNIQUE,
  name VARCHAR(255) NOT NULL,
  department_id BIGINT NOT NULL,
  designation VARCHAR(255) NOT NULL,
  email VARCHAR(191) NOT NULL UNIQUE,
  phone VARCHAR(32),
  address TEXT,
  qualification VARCHAR(255),
  experience INTEGER NOT NULL DEFAULT 0 CHECK(experience >= 0),
  specialization VARCHAR(255),
  joining_date VARCHAR(10),
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL,
  FOREIGN KEY(department_id) REFERENCES departments(id)
);

CREATE TABLE IF NOT EXISTS administrators (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS courses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  credits INTEGER NOT NULL CHECK(credits BETWEEN 1 AND 30),
  department_id BIGINT NOT NULL,
  semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 12),
  faculty_id BIGINT NOT NULL,
  capacity INTEGER NOT NULL CHECK(capacity > 0),
  FOREIGN KEY(department_id) REFERENCES departments(id),
  FOREIGN KEY(faculty_id) REFERENCES faculty(id),
  UNIQUE(code, department_id, semester)
);

CREATE TABLE IF NOT EXISTS course_enrollments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  course_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  UNIQUE(course_id,student_id),
  FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE,
  FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS class_teacher_assignments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  faculty_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 12),
  section VARCHAR(32) NOT NULL,
  UNIQUE(department_id,semester,section),
  FOREIGN KEY(faculty_id) REFERENCES faculty(id),
  FOREIGN KEY(department_id) REFERENCES departments(id)
);

CREATE TABLE IF NOT EXISTS resources (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name VARCHAR(191) NOT NULL UNIQUE,
  type VARCHAR(32) NOT NULL CHECK(type IN ('CLASSROOM','LABORATORY','EQUIPMENT','SEMINAR_ROOM')),
  building VARCHAR(255) NOT NULL,
  floor INTEGER NOT NULL,
  capacity INTEGER NOT NULL CHECK(capacity > 0),
  status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE' CHECK(status IN ('AVAILABLE','OCCUPIED','RESERVED','MAINTENANCE'))
);

CREATE TABLE IF NOT EXISTS classrooms (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  resource_id BIGINT NOT NULL UNIQUE,
  has_projector INTEGER NOT NULL DEFAULT 0 CHECK(has_projector IN (0,1)),
  FOREIGN KEY(resource_id) REFERENCES resources(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS laboratories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  resource_id BIGINT NOT NULL UNIQUE,
  lab_type VARCHAR(255) NOT NULL DEFAULT 'GENERAL',
  FOREIGN KEY(resource_id) REFERENCES resources(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS equipment (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  resource_id BIGINT NOT NULL UNIQUE,
  serial_number VARCHAR(191) UNIQUE,
  FOREIGN KEY(resource_id) REFERENCES resources(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS resource_bookings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  resource_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  start_time VARCHAR(32) NOT NULL,
  end_time VARCHAR(32) NOT NULL,
  purpose TEXT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  CHECK(start_time < end_time),
  FOREIGN KEY(resource_id) REFERENCES resources(id),
  FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS timetable (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  course_id BIGINT NOT NULL,
  faculty_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  day_of_week INTEGER NOT NULL CHECK(day_of_week BETWEEN 1 AND 7),
  start_time VARCHAR(8) NOT NULL,
  end_time VARCHAR(8) NOT NULL,
  CHECK(start_time < end_time),
  FOREIGN KEY(course_id) REFERENCES courses(id),
  FOREIGN KEY(faculty_id) REFERENCES faculty(id),
  FOREIGN KEY(resource_id) REFERENCES resources(id)
);

CREATE TABLE IF NOT EXISTS timetable_overrides (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  timetable_id BIGINT NOT NULL,
  date VARCHAR(10) NOT NULL,
  resource_id BIGINT NOT NULL,
  start_time VARCHAR(8) NOT NULL,
  end_time VARCHAR(8) NOT NULL,
  reason TEXT NOT NULL,
  UNIQUE(timetable_id,date),
  CHECK(start_time < end_time),
  FOREIGN KEY(timetable_id) REFERENCES timetable(id) ON DELETE CASCADE,
  FOREIGN KEY(resource_id) REFERENCES resources(id)
);

CREATE TABLE IF NOT EXISTS attendance (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  course_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  date VARCHAR(10) NOT NULL,
  status VARCHAR(24) NOT NULL CHECK(status IN ('PRESENT','ABSENT','LATE','EXCUSED')),
  UNIQUE(course_id,student_id,date),
  FOREIGN KEY(course_id,student_id) REFERENCES course_enrollments(course_id,student_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS assignments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  course_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  due_date VARCHAR(10) NOT NULL,
  attachment_url TEXT,
  FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS assignment_submissions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  assignment_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  file_url TEXT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  uploaded_at VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED' CHECK(status IN ('SUBMITTED','GRADED','RETURNED')),
  grade VARCHAR(32),
  feedback TEXT,
  reviewed_by BIGINT,
  reviewed_at VARCHAR(32),
  FOREIGN KEY(assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
  FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE,
  FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE,
  FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE(assignment_id,student_id)
);
CREATE INDEX IF NOT EXISTS idx_assignment_submissions_course ON assignment_submissions(course_id,student_id);

CREATE TABLE IF NOT EXISTS study_materials (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  course_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  type VARCHAR(32) NOT NULL,
  url TEXT NOT NULL,
  FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS library_resources (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  course_id BIGINT,
  title VARCHAR(255) NOT NULL,
  author VARCHAR(255) NOT NULL,
  category VARCHAR(255) NOT NULL,
  url TEXT,
  total_copies INTEGER NOT NULL CHECK(total_copies >= 0),
  FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS library_borrowings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  library_resource_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  borrowed_date VARCHAR(10) NOT NULL,
  due_date VARCHAR(10) NOT NULL,
  returned_date VARCHAR(10),
  CHECK(due_date >= borrowed_date),
  FOREIGN KEY(library_resource_id) REFERENCES library_resources(id),
  FOREIGN KEY(student_id) REFERENCES students(id)
);

CREATE TABLE IF NOT EXISTS library_reservations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  library_resource_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  reserved_date VARCHAR(10) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','FULFILLED','CANCELLED')),
  FOREIGN KEY(library_resource_id) REFERENCES library_resources(id),
  FOREIGN KEY(student_id) REFERENCES students(id)
);

CREATE TABLE IF NOT EXISTS exam_schedule (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  course_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  date VARCHAR(10) NOT NULL,
  start_time VARCHAR(8) NOT NULL,
  end_time VARCHAR(8) NOT NULL,
  resource_id BIGINT NOT NULL,
  CHECK(start_time < end_time),
  FOREIGN KEY(course_id) REFERENCES courses(id),
  FOREIGN KEY(resource_id) REFERENCES resources(id)
);

CREATE TABLE IF NOT EXISTS events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  location VARCHAR(255) NOT NULL,
  start_time VARCHAR(32) NOT NULL,
  end_time VARCHAR(32) NOT NULL,
  capacity INTEGER NOT NULL CHECK(capacity > 0),
  coordinator_id BIGINT,
  FOREIGN KEY(coordinator_id) REFERENCES faculty(id) ON DELETE SET NULL,
  CHECK(start_time < end_time)
);

CREATE TABLE IF NOT EXISTS event_registrations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  event_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  UNIQUE(event_id,student_id),
  FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE,
  FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS leave_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  requester_id BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL,
  request_date VARCHAR(10) NOT NULL,
  from_date VARCHAR(10) NOT NULL,
  to_date VARCHAR(10) NOT NULL,
  reason TEXT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  approval_remarks TEXT,
  CHECK(to_date >= from_date),
  FOREIGN KEY(requester_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS permission_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  requester_id BIGINT NOT NULL,
  date VARCHAR(10) NOT NULL,
  from_time VARCHAR(8) NOT NULL,
  to_time VARCHAR(8) NOT NULL,
  reason TEXT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  approval_remarks TEXT,
  CHECK(from_time < to_time),
  FOREIGN KEY(requester_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS faculty_substitutions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  timetable_id BIGINT NOT NULL,
  faculty_id BIGINT NOT NULL,
  date VARCHAR(10) NOT NULL,
  start_time VARCHAR(8) NOT NULL,
  end_time VARCHAR(8) NOT NULL,
  UNIQUE(timetable_id,date),
  CHECK(start_time < end_time),
  FOREIGN KEY(timetable_id) REFERENCES timetable(id),
  FOREIGN KEY(faculty_id) REFERENCES faculty(id)
);

CREATE TABLE IF NOT EXISTS faculty_appointments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  student_id BIGINT NOT NULL,
  faculty_id BIGINT NOT NULL,
  start_time VARCHAR(32) NOT NULL,
  end_time VARCHAR(32) NOT NULL,
  purpose TEXT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED')),
  CHECK(start_time < end_time),
  FOREIGN KEY(student_id) REFERENCES students(id),
  FOREIGN KEY(faculty_id) REFERENCES faculty(id)
);

CREATE TABLE IF NOT EXISTS doubts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  subject VARCHAR(255) NOT NULL,
  question TEXT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','ANSWERED','CLOSED')),
  FOREIGN KEY(student_id) REFERENCES students(id),
  FOREIGN KEY(course_id) REFERENCES courses(id)
);

CREATE TABLE IF NOT EXISTS doubt_replies (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  doubt_id BIGINT NOT NULL,
  faculty_id BIGINT NOT NULL,
  body TEXT NOT NULL,
  created_at VARCHAR(32) NOT NULL,
  FOREIGN KEY(doubt_id) REFERENCES doubts(id) ON DELETE CASCADE,
  FOREIGN KEY(faculty_id) REFERENCES faculty(id)
);

CREATE TABLE IF NOT EXISTS projects (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  mentor_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PLANNED' CHECK(status IN ('PLANNED','ACTIVE','COMPLETED','ON_HOLD')),
  FOREIGN KEY(mentor_id) REFERENCES faculty(id)
);

CREATE TABLE IF NOT EXISTS project_members (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  UNIQUE(project_id,student_id),
  FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE,
  FOREIGN KEY(student_id) REFERENCES students(id)
);

CREATE TABLE IF NOT EXISTS project_milestones (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  due_date VARCHAR(10) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','IN_PROGRESS','COMPLETED')),
  FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS announcements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  audience VARCHAR(16) NOT NULL CHECK(audience IN ('ALL','ADMIN','FACULTY','STUDENT')),
  created_at VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS notifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  created_at VARCHAR(32) NOT NULL,
  is_read INTEGER NOT NULL DEFAULT 0 CHECK(is_read IN (0,1)),
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS helpdesk_tickets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id BIGINT NOT NULL,
  subject VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
  response TEXT,
  FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS activity_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id BIGINT,
  action VARCHAR(100) NOT NULL,
  entity VARCHAR(100) NOT NULL,
  entity_id BIGINT,
  created_at VARCHAR(32) NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_bookings_time ON resource_bookings(resource_id,start_time,end_time,status);
CREATE INDEX IF NOT EXISTS idx_enrollment_student ON course_enrollments(student_id,course_id);
CREATE INDEX IF NOT EXISTS idx_timetable_faculty ON timetable(faculty_id,day_of_week,start_time,end_time);
CREATE INDEX IF NOT EXISTS idx_timetable_room ON timetable(resource_id,day_of_week,start_time,end_time);
CREATE INDEX IF NOT EXISTS idx_attendance_student ON attendance(student_id,date);
CREATE INDEX IF NOT EXISTS idx_loans_active ON library_borrowings(library_resource_id,returned_date);
CREATE INDEX IF NOT EXISTS idx_leave_requester ON leave_requests(requester_id,status);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id,is_read);
CREATE INDEX IF NOT EXISTS idx_substitution_faculty ON faculty_substitutions(faculty_id,date,start_time,end_time);

INSERT OR IGNORE INTO departments(name,code) VALUES
  ('Cloud Computing','CLOUD'),
  ('AIML','AIML');


CREATE TABLE IF NOT EXISTS seating_allotments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  exam_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  seat_number VARCHAR(64) NOT NULL,
  roll_number VARCHAR(64),
  bench_number VARCHAR(32),
  seat_side VARCHAR(8),
  room_number VARCHAR(64),
  UNIQUE(exam_id, student_id),
  UNIQUE(exam_id, resource_id, seat_number),
  FOREIGN KEY(exam_id) REFERENCES exam_schedule(id) ON DELETE CASCADE,
  FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE,
  FOREIGN KEY(resource_id) REFERENCES resources(id)
);
CREATE TABLE IF NOT EXISTS hall_tickets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  exam_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  ticket_number VARCHAR(191) NOT NULL UNIQUE,
  issued_date VARCHAR(10) NOT NULL,
  status VARCHAR(16) NOT NULL CHECK(status IN ('ISSUED','WITHHELD')),
  UNIQUE(exam_id, student_id),
  FOREIGN KEY(exam_id) REFERENCES exam_schedule(id) ON DELETE CASCADE,
  FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS student_fees (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  student_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  academic_year VARCHAR(32) NOT NULL,
  amount DECIMAL(12,2) NOT NULL CHECK(amount >= 0),
  paid_amount DECIMAL(12,2) NOT NULL CHECK(paid_amount >= 0 AND paid_amount <= amount),
  due_date VARCHAR(10) NOT NULL,
  FOREIGN KEY(student_id) REFERENCES students(id)
);
CREATE TABLE IF NOT EXISTS faculty_salaries (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  faculty_id BIGINT NOT NULL,
  salary_month VARCHAR(7) NOT NULL,
  amount DECIMAL(12,2) NOT NULL CHECK(amount >= 0),
  status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','PAID')),
  paid_date VARCHAR(10),
  UNIQUE(faculty_id, salary_month),
  FOREIGN KEY(faculty_id) REFERENCES faculty(id)
);
