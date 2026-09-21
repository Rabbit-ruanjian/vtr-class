@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "APP_ROOT=%~dp0"
set "JAVA_HOME=E:\JDK17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "AI_ENV_FILE=%APP_ROOT%config\ai.env"
set "BACKEND_DIR=%APP_ROOT%virtual-teaching-room\vtr-backend"
set "BACKEND_JAR=%BACKEND_DIR%\target\your-project-name-1.0.0.jar"

if not exist "%APP_ROOT%logs" mkdir "%APP_ROOT%logs" >nul 2>&1

rem Load the persistent AI configuration for every login. Do not put the key
rem on the command line or in the frontend bundle.
if exist "%AI_ENV_FILE%" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%AI_ENV_FILE%") do (
    if /I "%%A"=="KIMI_API_KEY" set "KIMI_API_KEY=%%B"
    if /I "%%A"=="MOONSHOT_API_KEY" set "MOONSHOT_API_KEY=%%B"
    if /I "%%A"=="KIMI_BASE_URL" set "KIMI_BASE_URL=%%B"
    if /I "%%A"=="KIMI_MODEL" set "KIMI_MODEL=%%B"
    if /I "%%A"=="KIMI_FAST_MODE" set "KIMI_FAST_MODE=%%B"
    if /I "%%A"=="KIMI_FALLBACK_MODELS" set "KIMI_FALLBACK_MODELS=%%B"
    if /I "%%A"=="KIMI_CONNECT_TIMEOUT_MS" set "KIMI_CONNECT_TIMEOUT_MS=%%B"
    if /I "%%A"=="KIMI_READ_TIMEOUT_MS" set "KIMI_READ_TIMEOUT_MS=%%B"
    if /I "%%A"=="KIMI_PROXY_HOST" set "KIMI_PROXY_HOST=%%B"
    if /I "%%A"=="KIMI_PROXY_PORT" set "KIMI_PROXY_PORT=%%B"
    if /I "%%A"=="AI_EMBEDDING_ENABLED" set "AI_EMBEDDING_ENABLED=%%B"
    if /I "%%A"=="AI_EMBEDDING_BASE_URL" set "AI_EMBEDDING_BASE_URL=%%B"
    if /I "%%A"=="AI_EMBEDDING_API_KEY" set "AI_EMBEDDING_API_KEY=%%B"
    if /I "%%A"=="AI_EMBEDDING_MODEL" set "AI_EMBEDDING_MODEL=%%B"
    if /I "%%A"=="AI_REQUESTS_PER_MINUTE" set "AI_REQUESTS_PER_MINUTE=%%B"
    if /I "%%A"=="AI_MAX_CONCURRENT" set "AI_MAX_CONCURRENT=%%B"
  )
)
if not defined KIMI_API_KEY if defined MOONSHOT_API_KEY set "KIMI_API_KEY=%MOONSHOT_API_KEY%"
if not defined KIMI_BASE_URL set "KIMI_BASE_URL=https://api.moonshot.cn/v1"
if not defined KIMI_MODEL set "KIMI_MODEL=kimi-k2.6"
if not defined KIMI_FAST_MODE set "KIMI_FAST_MODE=true"

if not defined KIMI_API_KEY (
  echo [ERROR] KIMI_API_KEY is not configured. Check "%AI_ENV_FILE%".
  endlocal & exit /b 2
)

if not exist "%BACKEND_JAR%" (
  goto :build_and_run
)

netstat -ano -p tcp | findstr /R /C:":8080 .*LISTENING" >nul
if not errorlevel 1 (
  echo vtr-backend is already running on port 8080.
  endlocal & exit /b 0
)

cd /d "%BACKEND_DIR%"
echo Starting verified vtr-backend JAR on port 8080...
echo Runtime logs: "%APP_ROOT%logs\vtr-backend-prod.log"
java -jar "%BACKEND_JAR%" >> "%APP_ROOT%logs\vtr-backend-prod.log" 2>> "%APP_ROOT%logs\vtr-backend-prod.error.log"
set "BACKEND_EXIT_CODE=%ERRORLEVEL%"
if not "%BACKEND_EXIT_CODE%"=="0" echo vtr-backend stopped with exit code %BACKEND_EXIT_CODE%. Check the production logs.
endlocal & exit /b %BACKEND_EXIT_CODE%

:build_and_run
echo [INFO] Verified backend JAR not found. Building it once...
call "%APP_ROOT%start-backend-prod.cmd"
exit /b %ERRORLEVEL%
