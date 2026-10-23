@echo off
rem Stops the local system. The database keeps its data (reset it with dev-reset-db.bat).
setlocal EnableExtensions
cd /d "%~dp0"

call :stop_port 5173 frontend
call :stop_port 8080 backend
rem The minimized windows opened by dev-start.bat, if still there
taskkill /FI "WINDOWTITLE eq ProjectX backend*" /T /F >nul 2>nul
taskkill /FI "WINDOWTITLE eq ProjectX frontend*" /T /F >nul 2>nul

echo Stopping database...
docker compose stop
echo Stopped. Data is kept; start again with dev-start.bat
exit /b 0

rem Stops whatever listens on the port: the backend JVM (8080) or the Vite server (5173)
:stop_port
set "found="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":%1 .*LISTENING"') do (
  taskkill /PID %%p /T /F >nul 2>nul
  set "found=1"
)
if defined found (echo Stopped %2.) else (echo %2 was not running.)
exit /b 0
