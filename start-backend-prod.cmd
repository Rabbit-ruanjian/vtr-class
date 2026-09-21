@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "APP_ROOT=%~dp0"
set "JAVA_HOME=E:\JDK17"
set "MAVEN_HOME=E:\Meaven\apache-maven-3.9.11"
set "PATH=E:\JDK17\bin;%MAVEN_HOME%\bin;%PATH%"
set "AI_ENV_FILE=%APP_ROOT%config\ai.env"
if not exist "%APP_ROOT%logs" mkdir "%APP_ROOT%logs" >nul 2>&1

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
if /I "%KIMI_MODEL%"=="moonshot-v1-128k-vision-preview" set "KIMI_MODEL=kimi-k2.6"

if not defined KIMI_API_KEY (
  echo [ERROR] KIMI_API_KEY is not configured. Create "%AI_ENV_FILE%" from config\ai.env.example first.
  endlocal & exit /b 2
)

if defined KIMI_PROXY_HOST if not defined KIMI_PROXY_PORT echo [WARNING] KIMI_PROXY_HOST is set but KIMI_PROXY_PORT is empty. Proxy will not be used.

rem 只做连通性预检并给出警告，不因外网暂时不可用而阻止平台启动。
rem 预检不携带 API Key，也不会输出任何敏感配置。
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$u=[Uri]([Environment]::GetEnvironmentVariable('KIMI_BASE_URL','Process')); $ph=[Environment]::GetEnvironmentVariable('KIMI_PROXY_HOST','Process'); $pp=[int]([Environment]::GetEnvironmentVariable('KIMI_PROXY_PORT','Process')); if($null -eq $u -or [string]::IsNullOrWhiteSpace($u.DnsSafeHost)){ exit 2 }; if(-not [string]::IsNullOrWhiteSpace($ph) -and $pp -gt 0){ $hostName=$ph; $port=$pp } else { $hostName=$u.DnsSafeHost; $port=$u.Port }; if(Test-NetConnection -ComputerName $hostName -Port $port -InformationLevel Quiet){ exit 0 } else { exit 1 }" >nul 2>&1
if errorlevel 1 echo [WARNING] Kimi host is not reachable from this machine right now. Check firewall, proxy, DNS, or network policy.

if /I "%~1"=="restart" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$lines=netstat -ano -p tcp; foreach($line in $lines){ if($line -match '^\s*TCP\s+\S+:8080\s+\S+\s+LISTENING\s+(\d+)\s*$'){ Stop-Process -Id ([int]$matches[1]) -Force -ErrorAction SilentlyContinue } }" >nul 2>&1
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 2" >nul 2>&1
)

netstat -ano -p tcp | findstr /R /C:":8080 .*LISTENING" >nul
if not errorlevel 1 (
  echo vtr-backend is already running on port 8080. Use start-backend-prod.cmd restart to reload configuration.
  endlocal & exit /b 0
)

cd /d "%APP_ROOT%virtual-teaching-room\vtr-backend"
echo Building production JAR. This may take a moment...
call "%MAVEN_HOME%\bin\mvn.cmd" -DskipTests package >> "%APP_ROOT%logs\vtr-backend-build.log" 2>&1
if errorlevel 1 (
  echo [ERROR] Production build failed. Check "%APP_ROOT%logs\vtr-backend-build.log".
  endlocal & exit /b 1
)

echo Starting packaged vtr-backend with Java 17...
echo Runtime logs: "%APP_ROOT%logs\vtr-backend-prod.log"
java -jar "target\your-project-name-1.0.0.jar" >> "%APP_ROOT%logs\vtr-backend-prod.log" 2>> "%APP_ROOT%logs\vtr-backend-prod.error.log"
set "BACKEND_EXIT_CODE=%ERRORLEVEL%"
if not "%BACKEND_EXIT_CODE%"=="0" echo vtr-backend stopped with exit code %BACKEND_EXIT_CODE%. Check the production logs.
endlocal & exit /b %BACKEND_EXIT_CODE%
