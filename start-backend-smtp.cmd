@echo off
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-backend-smtp.ps1"
exit /b %ERRORLEVEL%
