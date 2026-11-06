"""Idempotently seed the local college-project SQLite dataset with portal demo data."""

from __future__ import annotations

import argparse
import csv
import sqlite3
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path


COURSE_RESOURCES = {
    "PYTHON": ("Python Tutorial", "Python Software Foundation", "https://docs.python.org/3/tutorial/"),
    "WEBDEV": ("Learn Web Development", "MDN Contributors", "https://developer.mozilla.org/en-US/docs/Learn_web_development"),
    "MATH": ("College Algebra 2e", "OpenStax", "https://openstax.org/details/books/college-algebra-2e"),
    "ADIG": ("Lessons in Electric Circuits: Digital", "Tony R. Kuphaldt", "https://www.allaboutcircuits.com/textbook/"),
    "OOPS": ("Think Java: How to Think Like a Computer Scientist (2e)", "Allen B. Downey and Chris Mayfield", "https://greenteapress.com/wp/think-java-2e/"),
    "DSC": ("OpenDSA Data Structures and Algorithms", "Virginia Tech et al.", "https://opendsa.org/"),
    "OS": ("Operating Systems: Three Easy Pieces (OSTEP)", "Remzi H. Arpaci-Dusseau and Andrea C. Arpaci-Dusseau", "https://pages.cs.wisc.edu/~remzi/OSTEP/"),
    "COA": ("Dive into Systems: Computer Organization & Architecture", "Suzanne J. Matthews, Tia Newhall, and Kevin C. Webb", "https://diveintosystems.org/book/"),
    "AML": ("Dive into Deep Learning", "Aston Zhang et al.", "https://d2l.ai/d2l-en.pdf"),
    "CLOUD": ("OpenStack Documentation", "OpenInfra Foundation", "https://docs.openstack.org/2026.2/"),
    "MAD": ("Android Developer Guides", "Google Android Developers", "https://developer.android.com/develop"),
    "CGV": ("LearnOpenGL", "Joey de Vries", "https://learnopengl.com/book/learnopengl_book.pdf"),
    "CNS": ("Guide to a Secure Enterprise Network Landscape", "NIST", "https://csrc.nist.gov/pubs/sp/800/215/final"),
    "EA": ("Environmental Education Resources", "UNEP Ozone Secretariat", "https://ozone.unep.org/reset-earth-education-resources"),
    "MP": ("MIT OpenCourseWare", "Massachusetts Institute of Technology", "https://ocw.mit.edu/courses/1-124j-foundations-of-software-engineering-fall-2000/pages/projects/"),
}

PERIODS = [
    ("08:00", "08:50"), ("08:50", "09:40"), ("10:10", "11:00"), ("11:00", "11:50"),
    ("11:50", "12:40"), ("13:40", "14:30"), ("14:30", "15:20"), ("15:20", "16:10"),
]
EXAM_DATES = {
    (1, "PYTHON"): ("2026-12-14", "09:00"), (1, "WEBDEV"): ("2026-12-15", "09:00"),
    (1, "MATH"): ("2026-12-16", "09:00"), (1, "ADIG"): ("2026-12-17", "09:00"),
    (3, "OOPS"): ("2026-12-18", "09:00"), (3, "DSC"): ("2026-12-19", "09:00"),
    (3, "OS"): ("2026-12-20", "09:00"), (3, "COA"): ("2026-12-21", "09:00"),
    (5, "AML"): ("2026-12-22", "09:00"), (5, "CLOUD"): ("2026-12-23", "09:00"),
    (5, "MAD"): ("2026-12-24", "09:00"), (5, "CGV"): ("2026-12-24", "14:00"),
    (7, "CNS"): ("2026-12-22", "09:00"), (7, "EA"): ("2026-12-23", "09:00"),
    (7, "MP"): ("2026-12-24", "09:00"),
}


def insert_if_missing(db, table, columns, values, where, params):
    if db.execute(f"SELECT 1 FROM {table} WHERE {where} LIMIT 1", params).fetchone():
        return False
    names = ",".join(columns)
    placeholders = ",".join("?" for _ in columns)
    db.execute(f"INSERT INTO {table} ({names}) VALUES ({placeholders})", values)
    return True


def timetable_csv_rows(path: Path, course_codes: set[str]):
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.reader(source)
        headers = next(reader)
        for row in reader:
            day, *cells = row
            weekday = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].index(day) + 1
            for header, label in zip(headers[1:], cells):
                normalized = label.strip().upper()
                code = next((code for code in course_codes if normalized.startswith(code)), None)
                if code is None:
                    continue
                start, end = header.split("-")
                yield code, weekday, to_24_hour(start), to_24_hour(end)


def to_24_hour(value: str) -> str:
    hour, minute = map(int, value.split(":"))
    if hour < 8:
        hour += 12
    elif hour < 8 or hour > 12:
        hour += 12
    return f"{hour:02d}:{minute:02d}"


def seed(db_path: Path, csv_path: Path) -> dict[str, int]:
    db = sqlite3.connect(db_path)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys=ON")
    counts = defaultdict(int)
    try:
        # Also apply additive columns here so this import works on an older local DB file.
        for table, column, declaration in (
            ("library_resources", "course_id", "BIGINT REFERENCES courses(id) ON DELETE SET NULL"),
            ("seating_allotments", "roll_number", "VARCHAR(64)"),
            ("seating_allotments", "bench_number", "VARCHAR(32)"),
            ("seating_allotments", "seat_side", "VARCHAR(8)"),
            ("seating_allotments", "room_number", "VARCHAR(64)"),
        ):
            columns = {column_row[1] for column_row in db.execute(f"PRAGMA table_info({table})")}
            if column not in columns:
                db.execute(f"ALTER TABLE {table} ADD COLUMN {column} {declaration}")
        db.execute("BEGIN IMMEDIATE")
        courses = db.execute("""SELECT c.*,d.code AS branch_code FROM courses c
            JOIN departments d ON d.id=c.department_id ORDER BY d.code,c.semester,c.id""").fetchall()
        if not courses:
            raise RuntimeError("The course dataset is empty; import the college dataset first.")
        resource_by_course = {}
        for course in courses:
            capacity = db.execute("SELECT COUNT(*) FROM course_enrollments WHERE course_id=?", (course["id"],)).fetchone()[0]
            capacity = max(capacity, 1)
            room_name = f"{course['branch_code']}-S{course['semester']}-{course['code']}-CLASSROOM"
            insert_if_missing(db, "resources", ("name", "type", "building", "floor", "capacity", "status"),
                              (room_name, "CLASSROOM", "GMU Academic Block", (course["semester"] + 1) // 2, capacity, "AVAILABLE"),
                              "name=?", (room_name,))
            resource = db.execute("SELECT id FROM resources WHERE name=?", (room_name,)).fetchone()[0]
            resource_by_course[course["id"]] = resource
            insert_if_missing(db, "classrooms", ("resource_id", "has_projector"), (resource, 1), "resource_id=?", (resource,))
            counts["course_rooms"] += 1

            title, author, url = COURSE_RESOURCES[course["code"]]
            book_title = f"{title} - {course['branch_code']} S{course['semester']}"
            matching_books = db.execute("SELECT id FROM library_resources WHERE course_id=? AND author=? AND url=? ORDER BY id",
                                        (course["id"], author, url)).fetchall()
            if matching_books:
                book_ids = [book[0] for book in matching_books]
                db.execute("UPDATE library_resources SET title=?,category=?,total_copies=5 WHERE id=?",
                           (book_title, f"{course['name']} course resource", book_ids[0]))
                for duplicate_id in book_ids[1:]:
                    if not db.execute("SELECT 1 FROM library_borrowings WHERE library_resource_id=? LIMIT 1", (duplicate_id,)).fetchone() \
                            and not db.execute("SELECT 1 FROM library_reservations WHERE library_resource_id=? LIMIT 1", (duplicate_id,)).fetchone():
                        db.execute("DELETE FROM library_resources WHERE id=?", (duplicate_id,))
                counts["library_books"] += 1
            else:
                db.execute("INSERT INTO library_resources(course_id,title,author,category,url,total_copies) VALUES(?,?,?,?,?,5)",
                           (course["id"], book_title, author, f"{course['name']} course resource", url))
                counts["library_books"] += 1

            exam_date, exam_start = EXAM_DATES[(course["semester"], course["code"])]
            exam_title = f"Final Examination - {course['name']} - {course['branch_code']} S{course['semester']}"
            old_exam = db.execute("SELECT id FROM exam_schedule WHERE course_id=? AND title LIKE 'Final Examination%' ORDER BY id LIMIT 1",
                                  (course["id"],)).fetchone()
            exam_values = (exam_title, exam_date, exam_start, "12:00" if exam_start == "09:00" else "17:00", resource_by_course[course["id"]])
            if old_exam:
                db.execute("UPDATE exam_schedule SET title=?,date=?,start_time=?,end_time=?,resource_id=? WHERE id=?",
                           (*exam_values, old_exam[0]))
            else:
                db.execute("INSERT INTO exam_schedule(course_id,title,date,start_time,end_time,resource_id) VALUES(?,?,?,?,?,?)",
                           (course["id"], *exam_values))
                counts["exams"] += 1

        # Preserve the supplied Cloud Computing semester 3 timetable as the source schedule.
        cloud_s3 = [c for c in courses if c["branch_code"] == "CLOUD" and c["semester"] == 3]
        cloud_codes = {c["code"] for c in cloud_s3}
        if csv_path.exists():
            course_by_code = {c["code"]: c for c in cloud_s3}
            rows = list(timetable_csv_rows(csv_path, cloud_codes))
            for code, weekday, start, end in rows:
                course = course_by_code[code]
                counts["timetable"] += insert_if_missing(
                    db, "timetable", ("course_id", "faculty_id", "resource_id", "day_of_week", "start_time", "end_time"),
                    (course["id"], course["faculty_id"], resource_by_course[course["id"]], weekday, start, end),
                    "course_id=? AND day_of_week=? AND start_time=? AND end_time=?",
                    (course["id"], weekday, start, end))
        else:
            raise FileNotFoundError(f"Timetable CSV was not found: {csv_path}")

        # Replace generated records with a clear weekly plan while preserving the supplied Cloud S3 CSV.
        generated_course_ids = [course["id"] for course in courses
                                if not (course["branch_code"] == "CLOUD" and course["semester"] == 3)]
        for course_id in generated_course_ids:
            old_ids = [row[0] for row in db.execute("SELECT id FROM timetable WHERE course_id=?", (course_id,))]
            if any(db.execute("SELECT 1 FROM timetable_overrides WHERE timetable_id=? LIMIT 1", (row_id,)).fetchone()
                   or db.execute("SELECT 1 FROM faculty_substitutions WHERE timetable_id=? LIMIT 1", (row_id,)).fetchone()
                   for row_id in old_ids):
                raise RuntimeError("A generated timetable entry has an override/substitution. Resolve it before rebuilding schedules.")
        if generated_course_ids:
            marks = ",".join("?" for _ in generated_course_ids)
            db.execute(f"DELETE FROM timetable WHERE course_id IN ({marks})", generated_course_ids)

        # Each course meets four times per week on distinct weekdays, with class and faculty overlaps avoided.
        occupied_faculty = set()
        occupied_group = set()
        for row in db.execute("SELECT t.faculty_id,t.course_id,t.day_of_week,t.start_time,c.department_id,c.semester FROM timetable t JOIN courses c ON c.id=t.course_id"):
            occupied_faculty.add((row["faculty_id"], row["day_of_week"], row["start_time"]))
            occupied_group.add((row["department_id"], row["semester"], row["day_of_week"], row["start_time"]))
        groups = defaultdict(list)
        for course in courses:
            if course["branch_code"] != "CLOUD" or course["semester"] != 3:
                groups[(course["department_id"], course["semester"], course["branch_code"])].append(course)
        for group_index, ((department_id, semester, branch_code), group_courses) in enumerate(sorted(groups.items())):
            group_courses.sort(key=lambda course: (course["id"], course["code"]))
            for course_index, course in enumerate(group_courses):
                preferred_days = [((course_index + step + group_index) % 5) + 1 for step in range(4)]
                preferred_start_slot = (course_index * 2 + semester // 2 + group_index) % len(PERIODS)
                for occurrence, preferred_day in enumerate(preferred_days):
                    placed = False
                    day_order = [preferred_day] + [((preferred_day - 1 + offset) % 5) + 1 for offset in range(1, 5)]
                    for day in day_order:
                        if day in {row[0] for row in db.execute(
                                "SELECT day_of_week FROM timetable WHERE course_id=?", (course["id"],))}:
                            continue
                        for period_offset in range(len(PERIODS)):
                            period_index = (preferred_start_slot + occurrence + period_offset) % len(PERIODS)
                            start, end = PERIODS[period_index]
                            faculty_slot = (course["faculty_id"], day, start)
                            group_slot = (department_id, semester, day, start)
                            if faculty_slot in occupied_faculty or group_slot in occupied_group:
                                continue
                            db.execute("INSERT INTO timetable(course_id,faculty_id,resource_id,day_of_week,start_time,end_time) VALUES(?,?,?,?,?,?)",
                                       (course["id"], course["faculty_id"], resource_by_course[course["id"]], day, start, end))
                            occupied_faculty.add(faculty_slot)
                            occupied_group.add(group_slot)
                            counts["timetable"] += 1
                            placed = True
                            break
                        if placed:
                            break
                    if not placed:
                        raise RuntimeError(f"Could not schedule weekly meeting for course {course['code']} ({branch_code} S{semester}).")

        # Insert 20 weekday records per enrolled student/course. Demo attendance is 80%.
        days = []
        day = date(2026, 9, 1)
        while len(days) < 20:
            if day.weekday() < 5:
                days.append(day.isoformat())
            day += timedelta(days=1)
        enrollments = db.execute("SELECT course_id,student_id FROM course_enrollments ORDER BY course_id,student_id").fetchall()
        for course_id, student_id in enrollments:
            absent_start = (student_id * 7 + course_id * 3) % len(days)
            absent = {(absent_start + i * 5) % len(days) for i in range(4)}
            rows = [(course_id, student_id, attended_day, "ABSENT" if index in absent else "PRESENT")
                    for index, attended_day in enumerate(days)]
            before = db.total_changes
            db.executemany("INSERT OR IGNORE INTO attendance(course_id,student_id,date,status) VALUES(?,?,?,?)", rows)
            counts["attendance"] += db.total_changes - before

        # Mark generated demo money records explicitly as samples, not official values.
        for student in db.execute("SELECT id FROM students"):
            fee = db.execute("SELECT id FROM student_fees WHERE student_id=? AND title LIKE 'DEMO %' AND academic_year='2026-27' ORDER BY id LIMIT 1",
                             (student["id"],)).fetchone()
            fee_values = ("DEMO tuition fee - sample amount", "2026-27", 80000.00, 60000.00, "2026-11-30")
            if fee:
                db.execute("UPDATE student_fees SET title=?,academic_year=?,amount=?,paid_amount=?,due_date=? WHERE id=?",
                           (*fee_values, fee[0]))
            else:
                db.execute("INSERT INTO student_fees(student_id,title,academic_year,amount,paid_amount,due_date) VALUES(?,?,?,?,?,?)",
                           (student["id"], *fee_values))
                counts["student_fees"] += 1
        sample_salary = {"Professor": 95000.00, "Associate Professor": 80000.00,
                         "Assistant Professor": 65000.00, "Lecturer": 45000.00}
        for faculty in db.execute("SELECT id,designation FROM faculty"):
            salary = sample_salary.get(faculty["designation"], 60000.00)
            existing_salary = db.execute("SELECT id FROM faculty_salaries WHERE faculty_id=? AND salary_month='2026-10' ORDER BY id LIMIT 1",
                                         (faculty["id"],)).fetchone()
            if existing_salary:
                db.execute("UPDATE faculty_salaries SET amount=?,status='PENDING',paid_date=NULL WHERE id=?",
                           (salary, existing_salary[0]))
            else:
                db.execute("INSERT INTO faculty_salaries(faculty_id,salary_month,amount,status,paid_date) VALUES(?,'2026-10',?,'PENDING',NULL)",
                           (faculty["id"], salary))
                counts["faculty_salaries"] += 1

        # Assign teams of five within the same branch and semester; guides are branch faculty.
        for group in db.execute("SELECT department_id,semester,code FROM departments d JOIN students s ON s.department_id=d.id GROUP BY department_id,semester ORDER BY d.code,semester"):
            department_id, semester, branch_code = group[0], group[1], group[2]
            students = [r[0] for r in db.execute("SELECT id FROM students WHERE department_id=? AND semester=? ORDER BY id", (department_id, semester))]
            guides = [r[0] for r in db.execute("SELECT id FROM faculty WHERE department_id=? ORDER BY id", (department_id,))]
            if not guides:
                continue
            for start in range(0, len(students), 5):
                team_no = start // 5 + 1
                title = f"DEMO-GMU-Team-{branch_code}-S{semester}-{team_no:03d}"
                mentor = guides[(team_no - 1) % len(guides)]
                row = db.execute("SELECT id FROM projects WHERE title=?", (title,)).fetchone()
                if row:
                    project_id = row[0]
                else:
                    cur = db.execute("INSERT INTO projects(title,description,mentor_id,status) VALUES(?,?,?,'ACTIVE')",
                                     (title, "Demo student project team. Replace title and description with the approved project proposal.", mentor))
                    project_id = cur.lastrowid
                    counts["projects"] += 1
                before = db.total_changes
                db.executemany("INSERT OR IGNORE INTO project_members(project_id,student_id) VALUES(?,?)",
                               [(project_id, student_id) for student_id in students[start:start+5]])
                counts["project_members"] += db.total_changes - before

        # Assign every eligible enrolled student one bench seat and a hall ticket for each exam.
        exam_rows = db.execute("SELECT e.*,c.code FROM exam_schedule e JOIN courses c ON c.id=e.course_id WHERE e.title LIKE 'Final Examination - %' ORDER BY e.id").fetchall()
        for exam in exam_rows:
            eligible = db.execute("""SELECT ce.student_id,s.usn,COUNT(a.id) AS sessions,
                SUM(CASE WHEN a.status IN ('PRESENT','LATE') THEN 1 ELSE 0 END) AS attended
                FROM course_enrollments ce JOIN students s ON s.id=ce.student_id
                LEFT JOIN attendance a ON a.course_id=ce.course_id AND a.student_id=ce.student_id
                WHERE ce.course_id=? GROUP BY ce.student_id HAVING sessions>0 AND attended*100.0/sessions>75
                ORDER BY s.usn""", (exam["course_id"],)).fetchall()
            for index, student in enumerate(eligible):
                side = "LEFT" if index % 2 == 0 else "RIGHT"
                bench = index // 2 + 1
                seat = f"B{bench:03d}-{'L' if side == 'LEFT' else 'R'}"
                inserted_seat = insert_if_missing(db, "seating_allotments",
                                  ("exam_id", "student_id", "resource_id", "seat_number", "roll_number", "bench_number", "seat_side", "room_number"),
                                  (exam["id"], student["student_id"], exam["resource_id"], seat, student["usn"], f"B{bench:03d}", side,
                                   db.execute("SELECT name FROM resources WHERE id=?", (exam["resource_id"],)).fetchone()[0]),
                                  "exam_id=? AND student_id=?", (exam["id"], student["student_id"]))
                counts["seats"] += inserted_seat
                ticket = f"GMU-{exam['code']}-{exam['date'].replace('-', '')}-{student['usn']}"
                counts["hall_tickets"] += insert_if_missing(
                    db, "hall_tickets", ("exam_id", "student_id", "ticket_number", "issued_date", "status"),
                    (exam["id"], student["student_id"], ticket, date.today().isoformat(), "ISSUED"),
                    "exam_id=? AND student_id=?", (exam["id"], student["student_id"]))
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()
    return dict(counts)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, default=Path("data/campussphere.db"))
    parser.add_argument("--timetable", type=Path, required=True, help="Path to GMU semester 3 Cloud Computing timetable CSV")
    args = parser.parse_args()
    print(seed(args.database, args.timetable))
