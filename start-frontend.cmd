@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "APP_ROOT=%~dp0"
set "NODE_HOME=E:\Node\nodejs\New Folder"
set "NPM_CMD=%NODE_HOME%\npm.cmd"
set "PATH=%NODE_HOME%;%PATH%"

if not exist "%APP_ROOT%logs" mkdir "%APP_ROOT%logs" >nul 2>&1

if not exist "%NPM_CMD%" (
  echo [ERROR] npm was not found at "%NPM_CMD%".
  echo [ERROR] Please update NODE_HOME in start-frontend.cmd to your Node.js installation directory.
  endlocal & exit /b 1
)

rem This file may be started directly after a reboot. Never expose a Vite
rem page without its API backend, otherwise the AI assistant looks broken.
netstat -ano -p tcp | findstr /R /C:":8080 .*LISTENING" >nul
if errorlevel 1 (
  echo vtr-backend is not running. Starting it before the frontend...
  start "vtr-backend" /min "%ComSpec%" /d /c call "%APP_ROOT%start-backend-prod.cmd"
)
call :wait_for_backend 180
if errorlevel 1 (
  echo [ERROR] vtr-backend did not become healthy. The frontend will not start alone.
  echo [ERROR] Check "%APP_ROOT%logs\vtr-backend-build.log" and "%APP_ROOT%logs\vtr-backend-prod.error.log".
  endlocal & exit /b 1
)

if /I "%~1"=="restart" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$lines=netstat -ano -p tcp; foreach($line in $lines){ if($line -match '^\s*TCP\s+\S+:3000\s+\S+\s+LISTENING\s+(\d+)\s*$'){ Stop-Process -Id ([int]$matches[1]) -Force -ErrorAction SilentlyContinue } }" >nul 2>&1
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 1" >nul 2>&1
)

netstat -ano -p tcp | findstr /R /C:":3000 .*LISTENING" >nul
if not errorlevel 1 (
  echo vtr-frontend is already running on port 3000.
  endlocal & exit /b 0
)

cd /d "%APP_ROOT%virtual-teaching-room\vtr-frontend\virtual-teaching-frontend"
echo Starting vtr-frontend on http://127.0.0.1:3000/ ...
call "%NPM_CMD%" run dev -- --host 127.0.0.1 --port 3000 >> "%APP_ROOT%logs\vtr-frontend-runtime.log" 2>> "%APP_ROOT%logs\vtr-frontend-runtime.error.log"
set "FRONTEND_EXIT_CODE=%ERRORLEVEL%"
if not "%FRONTEND_EXIT_CODE%"=="0" echo vtr-frontend stopped with exit code %FRONTEND_EXIT_CODE%. Check the frontend error log.
endlocal & exit /b %FRONTEND_EXIT_CODE%

:wait_for_backend
set /a WAIT_SECONDS=%~1
:wait_for_backend_loop
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "try { $r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://127.0.0.1:8080/actuator/health'; if($r.StatusCode -eq 200){ exit 0 } } catch {}; exit 1" >nul 2>&1
if not errorlevel 1 exit /b 0
if %WAIT_SECONDS% LEQ 0 exit /b 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 1" >nul 2>&1
set /a WAIT_SECONDS-=1
goto :wait_for_backend_loop
