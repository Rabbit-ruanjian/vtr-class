@echo off
chcp 65001 >nul
setlocal EnableExtensions DisableDelayedExpansion

rem This file is copied to the current user's Startup folder by
rem install-autostart.cmd. Keep the absolute project path here because the
rem Startup folder is outside the project directory.
call "E:\新的\源代码\start-all.cmd" autostart
endlocal
