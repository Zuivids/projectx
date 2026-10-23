@echo off
rem Deletes the LOCAL database with all its data. The next dev-start.bat creates a fresh one,
rem including the local super admin.
setlocal EnableExtensions
cd /d "%~dp0"

set /p "answer=Delete ALL local data (users, courses)? Type YES to continue: "
if not "%answer%"=="YES" (
  echo Cancelled.
  pause
  exit /b 0
)

rem Run inside this window; dev-stop.bat only pauses when double-clicked itself
call "%~dp0dev-stop.bat"
docker compose down -v
echo Local database deleted. Run dev-start.bat to start with a fresh one.
pause
