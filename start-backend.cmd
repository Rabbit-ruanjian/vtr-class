@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "APP_ROOT=%~dp0"

if /I "%~1"=="prod" (
  call "%APP_ROOT%start-backend-prod.cmd" %2
  if errorlevel 1 (endlocal & exit /b 1) else (endlocal & exit /b 0)
)

set "JAVA_HOME=E:\JDK17"
set "MAVEN_HOME=E:\Meaven\apache-maven-3.9.11"
set "PATH=E:\JDK17\bin;%MAVEN_HOME%\bin;%PATH%"

if not exist "%APP_ROOT%logs" mkdir "%APP_ROOT%logs" >nul 2>&1

rem 优先读取项目外置的本机配置文件；没有该文件时仍兼容当前终端环境变量。
rem 文件中只允许加载 AI 相关配置，避免把任意内容注入启动环境。
set "AI_ENV_FILE=%APP_ROOT%config\ai.env"
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
    if /I "%%A"=="VERIFICATION_PROVIDER" set "VERIFICATION_PROVIDER=%%B"
    if /I "%%A"=="MAIL_HOST" set "MAIL_HOST=%%B"
    if /I "%%A"=="MAIL_PORT" set "MAIL_PORT=%%B"
    if /I "%%A"=="MAIL_USERNAME" set "MAIL_USERNAME=%%B"
    if /I "%%A"=="MAIL_PASSWORD" set "MAIL_PASSWORD=%%B"
    if /I "%%A"=="MAIL_SSL" set "MAIL_SSL=%%B"
    if /I "%%A"=="MAIL_STARTTLS" set "MAIL_STARTTLS=%%B"
  )
)

if not defined KIMI_API_KEY if defined MOONSHOT_API_KEY set "KIMI_API_KEY=%MOONSHOT_API_KEY%"
if not defined KIMI_BASE_URL set "KIMI_BASE_URL=https://api.moonshot.cn/v1"
if not defined KIMI_MODEL set "KIMI_MODEL=kimi-k2.6"
if not defined KIMI_FAST_MODE set "KIMI_FAST_MODE=true"
if /I "%KIMI_MODEL%"=="moonshot-v1-128k-vision-preview" set "KIMI_MODEL=kimi-k2.6"

if not defined KIMI_API_KEY (
  echo [WARNING] KIMI_API_KEY is not configured for this Windows process.
  echo [WARNING] Put it in "%AI_ENV_FILE%" based on config\ai.env.example, or set a persistent User environment variable.
  echo [WARNING] The platform can start, but the AI assistant will remain unavailable until the key is configured.
) else (
  echo Kimi API key detected. The key value is intentionally not displayed.
)

if defined KIMI_PROXY_HOST if not defined KIMI_PROXY_PORT (
  echo [WARNING] KIMI_PROXY_HOST is set but KIMI_PROXY_PORT is empty. Proxy will not be used.
)

rem 只做连通性预检并给出警告，不因外网暂时不可用而阻止平台启动。
rem 预检不携带 API Key，也不会输出任何敏感配置。
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$u=[Uri]([Environment]::GetEnvironmentVariable('KIMI_BASE_URL','Process')); $ph=[Environment]::GetEnvironmentVariable('KIMI_PROXY_HOST','Process'); $pp=[int]([Environment]::GetEnvironmentVariable('KIMI_PROXY_PORT','Process')); if($null -eq $u -or [string]::IsNullOrWhiteSpace($u.DnsSafeHost)){ exit 2 }; if(-not [string]::IsNullOrWhiteSpace($ph) -and $pp -gt 0){ $hostName=$ph; $port=$pp } else { $hostName=$u.DnsSafeHost; $port=$u.Port }; if(Test-NetConnection -ComputerName $hostName -Port $port -InformationLevel Quiet){ exit 0 } else { exit 1 }" >nul 2>&1
if errorlevel 1 echo [WARNING] Kimi host is not reachable from this machine right now. Check firewall, proxy, DNS, or network policy.

if not defined REDIS_SERVER set "REDIS_SERVER=E:\Matlab\bin\win64\redis-server.exe"
if not defined REDIS_CLI set "REDIS_CLI=E:\Matlab\bin\win64\redis-cli.exe"
if exist "%REDIS_CLI%" (
  "%REDIS_CLI%" -p 6379 ping >nul 2>&1
  if errorlevel 1 if exist "%REDIS_SERVER%" start "vtr-redis" /min "%REDIS_SERVER%" --bind 127.0.0.1 --port 6379
)

if /I "%~1"=="restart" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$lines=netstat -ano -p tcp; foreach($line in $lines){ if($line -match '^\s*TCP\s+\S+:8080\s+\S+\s+LISTENING\s+(\d+)\s*$'){ Stop-Process -Id ([int]$matches[1]) -Force -ErrorAction SilentlyContinue } }" >nul 2>&1
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 2" >nul 2>&1
)

netstat -ano -p tcp | findstr /R /C:":8080 .*LISTENING" >nul
if not errorlevel 1 (
  echo vtr-backend is already running on port 8080. Use start-backend.cmd restart to reload configuration.
  endlocal & exit /b 0
)

if /I "%VERIFICATION_PROVIDER%"=="smtp" (
  if not defined MAIL_HOST (
    echo VERIFICATION_PROVIDER=smtp but MAIL_HOST is not set.
    endlocal & exit /b 1
  )
  if not defined MAIL_USERNAME (
    echo VERIFICATION_PROVIDER=smtp but MAIL_USERNAME is not set.
    endlocal & exit /b 1
  )
  if not defined MAIL_PASSWORD (
    echo VERIFICATION_PROVIDER=smtp but MAIL_PASSWORD is not set.
    endlocal & exit /b 1
  )
)

cd /d "%~dp0virtual-teaching-room\vtr-backend"
echo Starting vtr-backend with VERIFICATION_PROVIDER=%VERIFICATION_PROVIDER% ...
echo Backend logs: %~dp0logs\vtr-backend-runtime.log
call "%MAVEN_HOME%\bin\mvn.cmd" spring-boot:run >> "%~dp0logs\vtr-backend-runtime.log" 2>> "%~dp0logs\vtr-backend-runtime.error.log"
set "BACKEND_EXIT_CODE=%ERRORLEVEL%"
if not "%BACKEND_EXIT_CODE%"=="0" echo vtr-backend stopped with exit code %BACKEND_EXIT_CODE%. Check the error log.

endlocal & exit /b %BACKEND_EXIT_CODE%
