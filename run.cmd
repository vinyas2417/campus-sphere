@echo off
setlocal
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "JAVAFX_CACHE=%~dp0.tools\openjfx-cache"
call mvnw.cmd -DskipTests package
if errorlevel 1 (pause & exit /b 1)
java --enable-native-access=ALL-UNNAMED -jar target\campussphere-1.0.0.jar
if errorlevel 1 pause
