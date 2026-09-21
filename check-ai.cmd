@echo off
setlocal EnableExtensions DisableDelayedExpansion
"E:\JDK17\bin\java.exe" "%~dp0tools\KimiConnectionCheck.java" "%~dp0config\ai.env" %*
exit /b %ERRORLEVEL%
