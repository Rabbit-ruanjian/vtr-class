param([ValidateSet('start', 'restart', 'autostart')][string]$Mode = 'start')

$ErrorActionPreference = 'Stop'
$appRoot = $PSScriptRoot
$backendDir = Join-Path $appRoot 'virtual-teaching-room\vtr-backend'
$frontendDir = Join-Path $appRoot 'virtual-teaching-room\vtr-frontend\virtual-teaching-frontend'
$logDir = Join-Path $appRoot 'logs'
$jarPath = Join-Path $backendDir 'target\your-project-name-1.0.0.jar'
$configPath = Join-Path $appRoot 'config\ai.env'
$javaPath = 'E:\JDK17\bin\java.exe'
$mavenPath = 'E:\Meaven\apache-maven-3.9.11\bin\mvn.cmd'
$nodePath = 'E:\Node\nodejs\New Folder\node.exe'

function Get-ServiceProcess([int]$Port, [string]$ExpectedCommand) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if (!$listener) { return $null }
    $serviceProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if (!$serviceProcess -or $serviceProcess.CommandLine -notlike "*$ExpectedCommand*") {
        throw "Port $Port belongs to another program; refusing to stop it."
    }
    return Get-Process -Id $listener.OwningProcess
}

function Wait-ForService([string]$Url, [System.Diagnostics.Process]$Process, [bool]$Backend) {
    $deadline = (Get-Date).AddSeconds(120)
    do {
        if ($Process.HasExited) { throw "Service exited with code $($Process.ExitCode). Check $logDir" }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                $content = $response.Content
                if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
                if (!$Backend -or ($content | ConvertFrom-Json).status -eq 'UP') { return }
            }
        } catch { }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Service did not become ready: $Url. Check $logDir"
}

$startupMutex = New-Object System.Threading.Mutex($false, 'Local\VirtualTeachingRoomStartup')
$locked = $false
try {
    $locked = $startupMutex.WaitOne(0)
    if (!$locked) { throw 'Another startup is already running. Please wait for it to finish.' }
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    if (!(Test-Path -LiteralPath $configPath)) { throw 'Configure config\ai.env before starting.' }
    foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
        # Only load recognized application settings, never arbitrary shell commands.
        if ($line -match '^\s*((?:KIMI|MOONSHOT|AI)_[A-Z0-9_]+|VERIFICATION_PROVIDER|MAIL_(?:HOST|PORT|USERNAME|PASSWORD|SSL|STARTTLS|CONNECTION_TIMEOUT|TIMEOUT|WRITE_TIMEOUT))=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim(), 'Process')
        }
    }
    $smtpPasswordPath = Join-Path $appRoot 'config\smtp-password.dat'
    if (!$env:MAIL_PASSWORD -and (Test-Path -LiteralPath $smtpPasswordPath)) {
        try {
            $secureSmtpPassword = ConvertTo-SecureString (Get-Content -LiteralPath $smtpPasswordPath -Raw -Encoding UTF8).Trim()
            $smtpPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSmtpPassword)
            try {
                $env:MAIL_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($smtpPasswordPointer)
            } finally {
                [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($smtpPasswordPointer)
            }
        } catch {
            throw 'The encrypted SMTP authorization code could not be read by the current Windows user. Run start-backend-smtp.cmd again.'
        }
    }
    $env:JAVA_HOME = 'E:\JDK17'
    $env:PATH = "E:\JDK17\bin;E:\Node\nodejs\New Folder;$env:PATH"
    $redisServerPath = if ($env:REDIS_SERVER) { $env:REDIS_SERVER } else { 'E:\Matlab\bin\win64\redis-server.exe' }
    $redisCliPath = if ($env:REDIS_CLI) { $env:REDIS_CLI } else { 'E:\Matlab\bin\win64\redis-cli.exe' }
    if (!(Test-Path -LiteralPath $redisServerPath) -or !(Test-Path -LiteralPath $redisCliPath)) {
        throw 'Redis executable was not found. Configure REDIS_SERVER and REDIS_CLI before starting.'
    }
    & $redisCliPath -h 127.0.0.1 -p 6379 ping 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Output '[INFO] Starting local Redis for verification codes...'
        Start-Process -FilePath $redisServerPath -ArgumentList @('--bind', '127.0.0.1', '--port', '6379') -WindowStyle Hidden | Out-Null
        $redisDeadline = (Get-Date).AddSeconds(10)
        do {
            Start-Sleep -Milliseconds 300
            & $redisCliPath -h 127.0.0.1 -p 6379 ping 2>$null | Out-Null
        } while ($LASTEXITCODE -ne 0 -and (Get-Date) -lt $redisDeadline)
        if ($LASTEXITCODE -ne 0) { throw 'Redis did not become ready on 127.0.0.1:6379.' }
    }
    Write-Output '[INFO] Checking authenticated Kimi connectivity using Java...'
    $checkAttempts = if ($Mode -eq 'autostart') { 6 } else { 3 }
    for ($attempt = 1; $attempt -le $checkAttempts; $attempt++) {
        # Windows PowerShell treats native stderr as an ErrorRecord; retain the Java exit code for retries.
        $ErrorActionPreference = 'Continue'
        & $javaPath (Join-Path $appRoot 'tools\KimiConnectionCheck.java') $configPath > (Join-Path $logDir 'ai-check.log') 2>&1
        $checkExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($checkExit -eq 0) { break }
        if ($attempt -lt $checkAttempts) {
            Write-Output "[INFO] Waiting for AI/network readiness ($attempt/$checkAttempts)..."
            Start-Sleep -Seconds 5
        }
    }
    if ($checkExit -ne 0) { throw 'AI connection check failed. See logs\ai-check.log; no services were restarted.' }

    $backend = Get-ServiceProcess 8080 'your-project-name-1.0.0.jar'
    $frontend = Get-ServiceProcess 3000 'vite'
    $inputs = @(Get-ChildItem -LiteralPath (Join-Path $backendDir 'src\main') -Recurse -File)
    $inputs += Get-Item -LiteralPath (Join-Path $backendDir 'pom.xml')
    $needsBuild = !(Test-Path -LiteralPath $jarPath)
    if (!$needsBuild) {
        $jarTime = (Get-Item -LiteralPath $jarPath).LastWriteTimeUtc
        $needsBuild = @($inputs | Where-Object { $_.LastWriteTimeUtc -gt $jarTime }).Count -gt 0
    }
    if ($needsBuild -or $Mode -eq 'restart') {
        Write-Output '[INFO] Packaging current backend source...'
        # Windows locks the running JAR. Build separately, then replace it after the old JVM exits.
        $stageDirectory = Join-Path $backendDir 'target\startup-stage'
        $stagedJar = Join-Path $stageDirectory 'your-project-name-1.0.0.jar'
        Push-Location $backendDir
        try {
            & $mavenPath -q -DskipTests "-Dvtr.build.directory=$stageDirectory" package > (Join-Path $logDir 'vtr-backend-build.log') 2>&1
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed; existing services were left running. See logs\vtr-backend-build.log.' }
        } finally { Pop-Location }
    }

    $reloadBackend = $Mode -eq 'restart' -or $needsBuild
    if ($backend) {
        $reloadBackend = $reloadBackend -or (Get-Item $jarPath).LastWriteTime -gt $backend.StartTime `
            -or (Get-Item $configPath).LastWriteTime -gt $backend.StartTime
    }
    # Stop the old process before polling health, so it cannot impersonate the replacement.
    if ($backend -and $reloadBackend) {
        Stop-Process -Id $backend.Id
        $backend.WaitForExit(10000) | Out-Null
        $backend = $null
    }
    if ($stagedJar) { Copy-Item -LiteralPath $stagedJar -Destination $jarPath -Force }
    if (!$backend) {
        $backend = Start-Process -FilePath $javaPath -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', ('"' + $jarPath + '"')) `
            -WorkingDirectory $backendDir -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $logDir 'vtr-backend-prod.log') `
            -RedirectStandardError (Join-Path $logDir 'vtr-backend-prod.error.log')
    }
    Wait-ForService 'http://127.0.0.1:8080/actuator/health' $backend $true
    if ($frontend -and $Mode -eq 'restart') {
        Stop-Process -Id $frontend.Id
        $frontend.WaitForExit(10000) | Out-Null
        $frontend = $null
    }
    if (!$frontend) {
        $vitePath = Join-Path $frontendDir 'node_modules\vite\bin\vite.js'
        $frontend = Start-Process -FilePath $nodePath `
            -ArgumentList @(('"' + $vitePath + '"'), '--host', '127.0.0.1', '--port', '3000', '--strictPort') `
            -WorkingDirectory $frontendDir -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $logDir 'vtr-frontend-runtime.log') `
            -RedirectStandardError (Join-Path $logDir 'vtr-frontend-runtime.error.log')
    }
    Wait-ForService 'http://127.0.0.1:3000/' $frontend $false
    Write-Output "[OK] Backend PID=$($backend.Id); frontend PID=$($frontend.Id); AI authentication verified."
    Write-Output '[OK] Open http://127.0.0.1:3000/ . Services run independently of this terminal.'
} catch {
    Write-Output "[ERROR] $($_.Exception.Message)"
    exit 1
} finally {
    if ($locked) { $startupMutex.ReleaseMutex() }
    $startupMutex.Dispose()
}
