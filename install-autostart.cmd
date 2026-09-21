@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "APP_ROOT=%~dp0"
set "TASK_NAME=VirtualTeachingRoom-AutoStart"
set "TASK_RUN=%ComSpec% /d /c call %APP_ROOT%start-all.cmd autostart"
set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "STARTUP_FILE=%STARTUP_DIR%\VirtualTeachingRoom-AutoStart.cmd"

echo Installing automatic startup for Virtual Teaching Room...
schtasks /Create /TN "%TASK_NAME%" /SC ONLOGON /TR "%TASK_RUN%" /RL LIMITED /F >nul
if not errorlevel 1 (
  echo [OK] Automatic startup installed as a Windows task: %TASK_NAME%
) else (
  rem Some managed Windows accounts cannot create scheduled tasks. Fall back
  rem to the current user's Startup folder, which needs no administrator
  rem permission and is sufficient for a user-facing local deployment.
  if not exist "%STARTUP_DIR%" mkdir "%STARTUP_DIR%" >nul 2>&1
  copy /Y "%APP_ROOT%VirtualTeachingRoom-AutoStart.cmd" "%STARTUP_FILE%" >nul 2>&1
  if errorlevel 1 (
    echo [ERROR] Automatic startup installation failed in both Windows Task Scheduler and Startup folder.
    echo [ERROR] Please run this installer once with administrator permission.
    endlocal & exit /b 1
  )
  echo [OK] Automatic startup installed in the current user's Startup folder.
)

echo [OK] It will run after the current Windows user logs in.
echo [OK] It will load the persistent AI configuration from:
echo      %APP_ROOT%config\ai.env
endlocal & exit /b 0
