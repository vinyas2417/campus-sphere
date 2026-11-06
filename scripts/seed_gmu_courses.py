"""Create GMU course offerings for the imported student and faculty dataset.

Offers the requested third-semester set and five course offerings in semesters
1, 5, and 7, assigning each offering to a faculty member in its department.
Where the CSV names a faculty member for that course, that match is preferred.
"""
import argparse
import csv
import datetime as dt
import shutil
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data" / "imports" / "dataset oops pbl.csv"
DATABASE = ROOT / "data" / "campussphere.db"
DEPARTMENTS = {
    "CSE": "SCST", "ISE": "SISE", "ECE": "SECE", "EEE": "SEEE",
    "Mechanical": "SME", "Civil": "SCE", "AI & ML": "SAIDS",
    "Data Science": "SAIDS",
}
COURSES = {
    "OOPS": ("Object-Oriented Programming", 3, "OOPS"),
    "OS": ("Operating Systems", 3, "OS"),
    "DSC": ("Data Structures and Complexity", 4, "DSC"),
    "COA": ("Computer Organization and Architecture", 3, "COA"),
    "SPORTS": ("Sports", 1, "Sports"),
    "TC": ("Technical Competency", 2, "Technical Competency"),
    "IOT": ("Internet of Things", 3, "IOT"),
    "PBL": ("Project-Based Learning", 2, "PBL"),
    "PYTHON": ("Python Programming", 3, "Python Programming"),
    "DBMS": ("Database Management Systems", 3, "DBMS"),
    "YOGA": ("Yoga", 1, "Yoga"),
}
SEMESTERS = {
    1: ("PYTHON", "DBMS", "YOGA", "SPORTS", "TC"),
    3: ("OOPS", "OS", "DSC", "COA", "SPORTS", "TC", "IOT", "PBL"),
    5: ("PYTHON", "DBMS", "YOGA", "IOT", "COA"),
    7: ("PYTHON", "DBMS", "YOGA", "PBL", "DSC"),
}


def read_source(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != 3110:
        raise ValueError(f"Expected the imported 3110-row GMU CSV; found {len(rows)} rows.")
    return rows


def migrate_course_uniqueness(conn):
    sql = conn.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name='courses'").fetchone()[0]
    if "unique(code,department_id,semester)" in "".join(sql.lower().split()):
        return False
    if conn.execute("SELECT COUNT(*) FROM courses").fetchone()[0]:
        raise ValueError("Cannot safely change course-code uniqueness while course rows already exist.")
    conn.execute("PRAGMA foreign_keys=OFF")
    conn.execute("BEGIN IMMEDIATE")
    try:
        conn.execute("DROP TABLE courses")
        conn.execute("""CREATE TABLE courses (
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
        )""")
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.execute("PRAGMA foreign_keys=ON")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--database", type=Path, default=DATABASE)
    args = parser.parse_args()
    source_rows = read_source(args.source)
    source_faculty = [row for row in source_rows if row.get("Role", "").strip() == "Faculty"]
    if len(source_faculty) != 100:
        raise ValueError("The source must contain the 100 imported faculty rows.")

    args.database.parent.mkdir(parents=True, exist_ok=True)
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = args.database.with_name(f"{args.database.stem}-before-courses-{stamp}{args.database.suffix}")
    shutil.copy2(args.database, backup)
    conn = sqlite3.connect(args.database)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys=ON")
    try:
        migrated = migrate_course_uniqueness(conn)
        departments = {r["code"]: r["id"] for r in conn.execute("SELECT id,code FROM departments")}
        faculty_by_user = {r["username"]: r for r in conn.execute(
            "SELECT f.id AS faculty_id, f.department_id, u.username FROM faculty f JOIN users u ON u.id=f.user_id")}
        faculty_by_dept = {}
        for row in source_faculty:
            username = row["ID"].strip().lower()
            profile = faculty_by_user.get(username)
            dept_code = DEPARTMENTS.get(row["Department"].strip())
            if profile and dept_code in departments and profile["department_id"] == departments[dept_code]:
                faculty_by_dept.setdefault(dept_code, []).append((row, profile["faculty_id"]))
        for dept in set(DEPARTMENTS.values()):
            if dept not in departments or not faculty_by_dept.get(dept):
                raise ValueError(f"No imported faculty profile found for department {dept}.")

        students_by_dept_sem = {(r["department_id"], r["semester"]): r["n"] for r in conn.execute(
            "SELECT department_id,semester,COUNT(*) AS n FROM students GROUP BY department_id,semester")}
        values = []
        for semester, codes in SEMESTERS.items():
            for dept_code in sorted(set(DEPARTMENTS.values())):
                dept_id = departments[dept_code]
                cohort_size = students_by_dept_sem.get((dept_id, semester), 0)
                capacity = max(60, cohort_size)
                candidates = faculty_by_dept[dept_code]
                for code in codes:
                    name, credits, source_course = COURSES[code]
                    matching = [item for item in candidates if any(
                        (item[0].get(f"Course_{i}_Code") or "").strip().casefold() == source_course.casefold()
                        for i in range(1, 6))]
                    faculty_id = (matching or candidates)[0][1]
                    values.append((code, name, credits, dept_id, semester, faculty_id, capacity))

        conn.execute("BEGIN IMMEDIATE")
        conn.executemany("""INSERT OR IGNORE INTO courses
            (code,name,credits,department_id,semester,faculty_id,capacity) VALUES(?,?,?,?,?,?,?)""", values)
        if list(conn.execute("PRAGMA foreign_key_check")):
            raise ValueError("Foreign-key validation failed; no course offerings were committed.")
        conn.commit()
        summary = conn.execute("SELECT semester,COUNT(*) FROM courses GROUP BY semester ORDER BY semester").fetchall()
        total = conn.execute("SELECT COUNT(*) FROM courses").fetchone()[0]
        print({"status": "seeded", "added_or_existing_offerings": len(values), "total_offerings": total,
               "by_semester": {row[0]: row[1] for row in summary}, "faculty_assignment": "department-matched",
               "course_schema_migrated": migrated, "backup": str(backup)})
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
