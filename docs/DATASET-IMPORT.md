# GMU CSV dataset

The supplied CSV is stored in `data/imports/dataset oops pbl.csv` and is imported with `scripts/import_gmu_csv.py`. It contains 3,000 student rows, 100 faculty rows and 10 administration rows. The import retains the existing administrator and does not grant administrator access to those 10 rows.

Student usernames and faculty usernames use the source IDs. The generated unique initial passwords are written to the local, git-ignored file `data/import-reports/gmu_portal_credentials.csv`; the existing administrator also receives a new temporary password there. The file contains credentials and should be shared only with the intended account holders. The password-change control is available under My Profile.

The CSV does not supply a semester, section or admission year. Per the selected mapping, B.E. years map to semesters 1, 3, 5 and 7, section is `Unassigned`, and admission year is calculated from 2026 minus the year offset. Source departments map to the existing catalog. Administration rows remain in the source CSV only.

The course columns are retained in the source file, but course offerings and enrollments are not loaded because the source does not identify course terms or unambiguous faculty assignments. The import creates a SQLite backup, runs in one transaction, and checks foreign keys and database integrity. Results are recorded in `data/import-reports/gmu_csv_import_report.json`.
