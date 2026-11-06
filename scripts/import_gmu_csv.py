"""Import the supplied GMU CSV as linked student/faculty profiles and accounts.

The source does not include semester, section, or password fields. This import
maps B.E. years to semesters 1/3/5/7, uses section "Unassigned", infers each
admission year from the current calendar year, and writes unique initial
passwords to a local credentials CSV. Administration rows are retained in the
source CSV but do not receive administrator access.
"""
import argparse
import collections
import csv
import datetime as dt
import hashlib
import json
import os
import re
import secrets
import shutil
import sqlite3
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "data" / "imports" / "dataset oops pbl.csv"
DEFAULT_DATABASE = ROOT / "data" / "campussphere.db"
DEFAULT_CREDENTIALS = ROOT / "data" / "import-reports" / "gmu_portal_credentials.csv"
DEPARTMENT_CODES = {
    "CSE": "SCST", "ISE": "SISE", "ECE": "SECE", "EEE": "SEEE",
    "Mechanical": "SME", "Civil": "SCE", "AI & ML": "SAIDS",
    "Data Science": "SAIDS",
}
COURSE_FIELDS = [f"Course_{i}_{part}" for i in range(1, 6) for part in ("Code", "Name", "Credits")]
REQUIRED_FIELDS = ["ID", "First_Name", "Last_Name", "Full_Name", "Role", "Department",
                   "Email", "Contact_Number", "Course_or_Designation", *COURSE_FIELDS]


def read_source(path):
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != REQUIRED_FIELDS:
            raise ValueError("CSV columns do not match the supplied GMU dataset format.")
        rows = list(reader)
    if len(rows) != 3110:
        raise ValueError(f"Expected 3110 source rows; found {len(rows)}.")
    if any(None in row for row in rows):
        raise ValueError("A CSV row has an unexpected number of fields.")
    ids = [row["ID"].strip() for row in rows]
    emails = [row["Email"].strip().casefold() for row in rows]
    if any(not value for value in ids + emails) or len(set(ids)) != len(ids) or len(set(emails)) != len(emails):
        raise ValueError("Every record needs a unique, non-empty ID and email.")
    roles = collections.Counter(row["Role"].strip() for row in rows)
    if roles != {"Student": 3000, "Faculty": 100, "Administration": 10}:
        raise ValueError(f"Unexpected role totals: {dict(roles)}")
    for row in rows:
        role = row["Role"].strip()
        if role not in {"Student", "Faculty", "Administration"}:
            raise ValueError("Unsupported role value in source CSV.")
        for field in ("First_Name", "Last_Name", "Full_Name", "Department", "Email", "Contact_Number", "Course_or_Designation"):
            if not row[field].strip():
                raise ValueError(f"A {role} record has a blank required field: {field}.")
        if role in {"Student", "Faculty"} and row["Department"].strip() not in DEPARTMENT_CODES:
            raise ValueError("A student/faculty department does not map to the campus catalog.")
        if role == "Student" and not re.fullmatch(r"B\.E\.\s+[1-4](?:st|nd|rd|th)\s+Year", row["Course_or_Designation"].strip()):
            raise ValueError("Student year must be B.E. 1st, 2nd, 3rd, or 4th Year.")
    return rows, roles


def make_hashes(passwords):
    tools = ROOT / "target" / "import-tools"
    tools.mkdir(parents=True, exist_ok=True)
    source = tools / "GmuPasswordHasher.java"
    source.write_text('''import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.mindrot.jbcrypt.BCrypt;
public class GmuPasswordHasher {
  public static void main(String[] args) throws Exception {
    List<String> passwords = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
    int workers = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    ExecutorService pool = Executors.newFixedThreadPool(workers);
    try {
      List<Future<String>> hashes = new ArrayList<>();
      for (String password : passwords) hashes.add(pool.submit(() -> BCrypt.hashpw(password, BCrypt.gensalt(12))));
      for (Future<String> hash : hashes) System.out.println(hash.get());
    } finally { pool.shutdownNow(); }
  }
}''', encoding="utf-8")
    jar = ROOT / "target" / "lib" / "jbcrypt-0.4.jar"
    classes = tools / "classes"
    classes.mkdir(exist_ok=True)
    subprocess.run(["javac", "--class-path", str(jar), "-d", str(classes), str(source)], check=True, timeout=60)
    password_file = tools / ("passwords-" + uuid.uuid4().hex + ".txt")
    password_file.write_text("\n".join(passwords) + "\n", encoding="utf-8")
    try:
        result = subprocess.run(["java", "--class-path", str(classes) + os.pathsep + str(jar),
                                 "GmuPasswordHasher", str(password_file)],
                                capture_output=True, text=True, check=True, timeout=1800)
        hashes = result.stdout.splitlines()
        if len(hashes) != len(passwords) or any(not value.startswith("$2a$12$") for value in hashes):
            raise ValueError("Password hashing returned an invalid result count.")
        return hashes
    finally:
        password_file.unlink(missing_ok=True)


def run(args):
    source = args.source.resolve()
    rows, role_counts = read_source(source)
    database = args.database.resolve()
    credentials_path = args.credentials.resolve()
    if not database.is_file():
        raise ValueError("SQLite database not found: " + str(database))

    conn = sqlite3.connect(database, timeout=30)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA busy_timeout=30000")
    try:
        admin_rows = conn.execute("SELECT id, username, email FROM users WHERE role='ADMIN'").fetchall()
        if len(admin_rows) != 1:
            raise ValueError("Expected exactly one existing administrator account.")
        if conn.execute("SELECT COUNT(*) FROM students").fetchone()[0] or conn.execute("SELECT COUNT(*) FROM faculty").fetchone()[0]:
            raise ValueError("Student/faculty records already exist; refusing to merge or overwrite them.")
        if conn.execute("SELECT COUNT(*) FROM users WHERE role<>'ADMIN'").fetchone()[0]:
            raise ValueError("Non-administrator accounts already exist; refusing to overwrite them.")

        departments = {r["code"]: r["id"] for r in conn.execute("SELECT id,code FROM departments")}
        for code in DEPARTMENT_CODES.values():
            if code not in departments:
                raise ValueError("Required department catalog code is missing: " + code)
        admin = admin_rows[0]
        import_rows = [r for r in rows if r["Role"].strip() in {"Student", "Faculty"}]
        accounts = [{"role": "ADMINISTRATOR", "username": admin["username"], "source_id": ""}]
        for row in import_rows:
            accounts.append({"role": "STUDENT" if row["Role"].strip() == "Student" else "TEACHER",
                             "username": row["ID"].strip().lower(), "source_id": row["ID"].strip()})
        credentials = [{**account, "password": secrets.token_urlsafe(18)} for account in accounts]
        hashes = make_hashes([row["password"] for row in credentials])
        for item, password_hash in zip(credentials, hashes):
            item["password_hash"] = password_hash

        timestamp = dt.datetime.now().isoformat(timespec="seconds")
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        backup_dir = args.backup_dir.resolve()
        backup_dir.mkdir(parents=True, exist_ok=True)
        backup = backup_dir / ("campussphere-before-gmu-import-" + dt.datetime.now().strftime("%Y%m%d-%H%M%S") + ".db")
        with sqlite3.connect(backup) as backup_conn:
            conn.backup(backup_conn)

        credentials_path.parent.mkdir(parents=True, exist_ok=True)
        temp_credentials = credentials_path.with_suffix(credentials_path.suffix + ".tmp")
        with temp_credentials.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["role", "username", "temporary_password"])
            for item in credentials:
                writer.writerow([item["role"], item["username"], item["password"]])

        report = {
            "source": str(source), "source_sha256": digest, "imported_at": timestamp,
            "backup": str(backup), "credentials_csv": str(credentials_path),
            "source_rows": dict(role_counts), "imported_accounts": {"administrator_retained": 1, "students": 3000, "faculty": 100},
            "administration_rows_not_granted_access": 10,
            "mapping": {"student_year_to_semester": {"1st": 1, "2nd": 3, "3rd": 5, "4th": 7},
                        "section": "Unassigned", "admission_year": "2026 minus (B.E. year minus 1)",
                        "departments": DEPARTMENT_CODES},
            "not_imported": ["course offerings and enrollments: the source has no unambiguous course-term and instructor assignment mapping"],
        }
        report_path = args.report.resolve()
        report_path.parent.mkdir(parents=True, exist_ok=True)
        temp_report = report_path.with_suffix(report_path.suffix + ".tmp")
        temp_report.write_text(json.dumps(report, indent=2), encoding="utf-8")

        try:
            conn.execute("BEGIN IMMEDIATE")
            conn.execute("UPDATE users SET password_hash=? WHERE id=?", (credentials[0]["password_hash"], admin["id"]))
            for source_row, account in zip(import_rows, credentials[1:]):
                role = source_row["Role"].strip()
                department_id = departments[DEPARTMENT_CODES[source_row["Department"].strip()]]
                name = source_row["Full_Name"].strip() or (source_row["First_Name"].strip() + " " + source_row["Last_Name"].strip())
                email = source_row["Email"].strip().lower()
                user_cursor = conn.execute(
                    "INSERT INTO users(username,email,password_hash,name,role,created_at) VALUES(?,?,?,?,?,?)",
                    (account["username"], email, account["password_hash"], name,
                     "STUDENT" if role == "Student" else "FACULTY", timestamp))
                user_id = user_cursor.lastrowid
                if role == "Student":
                    year = int(re.search(r"([1-4])(?:st|nd|rd|th) Year", source_row["Course_or_Designation"].strip()).group(1))
                    conn.execute("""INSERT INTO students(user_id,usn,name,department_id,semester,section,email,phone,admission_year,status)
                                    VALUES(?,?,?,?,?,?,?,?,?,?)""",
                                 (user_id, source_row["ID"].strip(), name, department_id, 2 * year - 1,
                                  "Unassigned", email, source_row["Contact_Number"].strip(), 2026 - year + 1, "ACTIVE"))
                else:
                    conn.execute("""INSERT INTO faculty(user_id,name,department_id,designation,email,phone,experience)
                                    VALUES(?,?,?,?,?,?,0)""",
                                 (user_id, name, department_id, source_row["Course_or_Designation"].strip(),
                                  email, source_row["Contact_Number"].strip()))
            conn.execute("CREATE TABLE IF NOT EXISTS dataset_imports (source_sha256 TEXT PRIMARY KEY, imported_at TEXT NOT NULL, report_json TEXT NOT NULL)")
            conn.execute("INSERT INTO dataset_imports(source_sha256,imported_at,report_json) VALUES(?,?,?)",
                         (digest, timestamp, json.dumps(report, separators=(",", ":"))))
            if list(conn.execute("PRAGMA foreign_key_check")):
                raise ValueError("Foreign-key validation failed; the database import was rolled back.")
            if conn.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                raise ValueError("Database integrity validation failed; the import was rolled back.")
            conn.commit()
            os.replace(temp_credentials, credentials_path)
            os.replace(temp_report, report_path)
        except Exception:
            conn.rollback()
            temp_credentials.unlink(missing_ok=True)
            temp_report.unlink(missing_ok=True)
            raise
        print(json.dumps({"status": "imported", "students": 3000, "faculty": 100,
                          "administrators_retained": 1, "administration_rows_not_promoted": 10,
                          "credentials_csv": str(credentials_path), "report": str(report_path),
                          "backup": str(backup)}))
    finally:
        conn.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--database", type=Path, default=DEFAULT_DATABASE)
    parser.add_argument("--credentials", type=Path, default=DEFAULT_CREDENTIALS)
    parser.add_argument("--report", type=Path, default=ROOT / "data" / "import-reports" / "gmu_csv_import_report.json")
    parser.add_argument("--backup-dir", type=Path, default=ROOT / "data" / "backups")
    run(parser.parse_args())
