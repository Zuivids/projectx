@echo off
rem Builds and tests everything a deployment ships, into release\:
rem   release\projectx.jar   backend (run with: java -jar projectx.jar)
rem   release\frontend\      static files of the React app
rem Deploying them to a server waits for the hosting decision (BACKLOG D-05, D-09).
setlocal EnableExtensions
cd /d "%~dp0"

rem Started by double-click? Then keep the window open at the end, so messages can be read.
set "KEEP_OPEN="
echo %cmdcmdline% | find /i "%~nx0" >nul && set "KEEP_OPEN=1"

docker info >nul 2>nul || (set "MSG=Docker must be running (the tests use it)." & goto :fail)

echo Building and testing the backend...
call .\mvnw.cmd -B -q clean verify || (set "MSG=Backend build or tests failed. See the output above." & goto :fail)

echo Building and testing the frontend...
pushd frontend
call npm ci --no-fund || (popd & set "MSG=npm ci failed." & goto :fail)
call npm run lint || (popd & set "MSG=Frontend lint failed." & goto :fail)
call npm test || (popd & set "MSG=Frontend tests failed." & goto :fail)
call npm run build || (popd & set "MSG=Frontend build failed." & goto :fail)
popd

if exist release rmdir /s /q release
mkdir release
for %%f in (target\projectx-*.jar) do copy /y "%%f" release\projectx.jar >nul
xcopy /e /i /q frontend\dist release\frontend >nul
git rev-parse --short HEAD > release\VERSION 2>nul

echo Release ready in release\
if defined KEEP_OPEN pause
exit /b 0

:fail
echo.
echo ERROR: %MSG%
if defined KEEP_OPEN pause
exit /b 1
