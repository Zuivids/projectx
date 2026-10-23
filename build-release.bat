@echo off
rem Builds and tests everything a deployment ships, into release\:
rem   release\projectx.jar   backend (run with: java -jar projectx.jar)
rem   release\frontend\      static files of the React app
rem Deploying them to a server waits for the hosting decision (BACKLOG D-05, D-09).
setlocal EnableExtensions
cd /d "%~dp0"

docker info >nul 2>nul || (echo ERROR: Docker must be running ^(the tests use it^). & exit /b 1)

echo Building and testing the backend...
call .\mvnw.cmd -B -q clean verify || (echo ERROR: Backend build or tests failed. & exit /b 1)

echo Building and testing the frontend...
pushd frontend
call npm ci --no-fund || (popd & exit /b 1)
call npm run lint || (popd & exit /b 1)
call npm test || (popd & exit /b 1)
call npm run build || (popd & exit /b 1)
popd

if exist release rmdir /s /q release
mkdir release
for %%f in (target\projectx-*.jar) do copy /y "%%f" release\projectx.jar >nul
xcopy /e /i /q frontend\dist release\frontend >nul
git rev-parse --short HEAD > release\VERSION 2>nul

echo Release ready in release\
