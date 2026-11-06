@echo off
setlocal
cd /d "%~dp0"
if not exist ".tools\apache-maven-3.9.11\bin\mvn.cmd" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\bootstrap.ps1"
  if errorlevel 1 exit /b 1
)
call "%~dp0.tools\apache-maven-3.9.11\bin\mvn.cmd" "-Dmaven.repo.local=%~dp0.m2\repository" %*
exit /b %errorlevel%
