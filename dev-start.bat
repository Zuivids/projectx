@echo off
rem Starts the local system: database (Docker), backend (Spring Boot, "local" profile) and
rem frontend (Vite). Creates .env with random passwords on the first run.
rem Logs: logs\backend.log, logs\frontend.log. Stop everything with dev-stop.bat
setlocal EnableExtensions
cd /d "%~dp0"

rem Started by double-click? Then keep the window open at the end, so messages can be read.
set "KEEP_OPEN="
echo %cmdcmdline% | find /i "%~nx0" >nul && set "KEEP_OPEN=1"

where docker >nul 2>nul || (set "MSG=Docker is not installed." & goto :fail)
where npm >nul 2>nul || (set "MSG=Node.js (npm) is not installed." & goto :fail)
where java >nul 2>nul || (set "MSG=Java 21 is not installed." & goto :fail)

rem Docker Desktop takes a while to start after login; wait up to 90 seconds
docker info >nul 2>nul && goto :docker_ready
echo Waiting for Docker Desktop to start...
for /l %%i in (1,1,45) do (
  docker info >nul 2>nul && goto :docker_ready
  ping -n 3 127.0.0.1 >nul
)
set "MSG=Docker is not running. Start Docker Desktop, wait until it says it is running, and try again."
goto :fail
:docker_ready

if not exist .env (
  echo Creating .env with random local passwords...
  powershell -NoProfile -Command ^
    "$r = { [guid]::NewGuid().ToString('N').Substring(0,24) };" ^
    "(Get-Content .env.example) -replace '^DB_PASSWORD=.*', ('DB_PASSWORD=' + (& $r))" ^
    " -replace '^DB_ROOT_PASSWORD=.*', ('DB_ROOT_PASSWORD=' + (& $r))" ^
    " -replace '^LOCAL_ADMIN_PASSWORD=.*', ('LOCAL_ADMIN_PASSWORD=' + (& $r))" ^
    " | Set-Content -Encoding ascii .env"
  if errorlevel 1 (set "MSG=Could not create .env" & goto :fail)
)

rem Load .env into this window's environment; the started processes inherit it
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"

if not exist logs mkdir logs

echo Starting database (container projectx-mariadb)...
docker compose up -d --wait --wait-timeout 180
if errorlevel 1 (
  echo.
  echo Last database log lines:
  docker compose logs --tail 15 mariadb
  set "MSG=The database did not start. See the lines above."
  goto :fail
)

call :port_in_use 8080
if not errorlevel 1 (
  echo Backend is already running on port 8080.
) else (
  echo Starting backend ^(first start compiles the code, about a minute^)...
  start "ProjectX backend" /min cmd /c ".\mvnw.cmd -q spring-boot:run -Dspring-boot.run.profiles=local > logs\backend.log 2>&1"
)

call :port_in_use 5173
if not errorlevel 1 (
  echo Frontend is already running on port 5173.
) else (
  if not exist frontend\node_modules (
    echo Installing frontend packages...
    pushd frontend
    call npm ci --no-fund
    popd
  )
  echo Starting frontend...
  start "ProjectX frontend" /min cmd /c "cd frontend && npm run dev > ..\logs\frontend.log 2>&1"
)

echo Waiting for the backend...
powershell -NoProfile -Command ^
  "for ($i = 0; $i -lt 180; $i++) { try { if ((Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://localhost:8080/actuator/health).StatusCode -eq 200) { exit 0 } } catch {}; Start-Sleep 1 }; exit 1"
if errorlevel 1 (set "MSG=Backend did not start within 3 minutes. See logs\backend.log" & goto :fail)

echo.
echo ProjectX is running
echo   App:          http://localhost:5173
echo   Super admin:  %LOCAL_ADMIN_EMAIL% / %LOCAL_ADMIN_PASSWORD%
echo                 (set up MFA on the "Drosiba" page to open the admin panel)
echo   Database:     localhost:3306, database lms, user %DB_USERNAME%, password %DB_PASSWORD%
echo   Logs:         logs\backend.log, logs\frontend.log
echo   Stop:         dev-stop.bat
if defined KEEP_OPEN (
  echo.
  echo The system keeps running in the background after you close this window.
  pause
)
exit /b 0

:fail
echo.
echo ERROR: %MSG%
if defined KEEP_OPEN pause
exit /b 1

rem Exit code 0 when something already answers on the port
:port_in_use
powershell -NoProfile -Command ^
  "try { (New-Object Net.Sockets.TcpClient).Connect('localhost', %1); exit 0 } catch { exit 1 }"
exit /b %errorlevel%
