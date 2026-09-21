$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$configPath = Join-Path $projectRoot 'config\ai.env'
$passwordPath = Join-Path $projectRoot 'config\smtp-password.dat'

function Set-ConfigValue([string]$Path, [string]$Name, [string]$Value) {
    $lines = if (Test-Path -LiteralPath $Path) { @(Get-Content -LiteralPath $Path -Encoding UTF8) } else { @() }
    $replacement = "$Name=$Value"
    $matched = $false
    $updated = foreach ($line in $lines) {
        if ($line -match ('^\s*' + [Regex]::Escape($Name) + '=')) {
            $matched = $true
            $replacement
        } else {
            $line
        }
    }
    if (!$matched) { $updated += $replacement }
    Set-Content -LiteralPath $Path -Value $updated -Encoding UTF8
}

Write-Host 'VTR QQ SMTP secure setup' -ForegroundColor Cyan
Write-Host 'The authorization code is encrypted for the current Windows user and is never displayed.' -ForegroundColor DarkGray
Write-Host 'When prompted, type the QQ SMTP authorization code, then press Enter.' -ForegroundColor Yellow
$securePassword = Read-Host 'Enter QQ SMTP authorization code' -AsSecureString
if ($null -eq $securePassword -or $securePassword.Length -eq 0) {
    throw 'No authorization code was entered.'
}

New-Item -ItemType Directory -Path (Split-Path -Parent $passwordPath) -Force | Out-Null
$securePassword | ConvertFrom-SecureString | Set-Content -LiteralPath $passwordPath -Encoding UTF8
Set-ConfigValue $configPath 'VERIFICATION_PROVIDER' 'smtp'
Set-ConfigValue $configPath 'MAIL_HOST' 'smtp.qq.com'
Set-ConfigValue $configPath 'MAIL_PORT' '465'
Set-ConfigValue $configPath 'MAIL_USERNAME' 'dry20060606@qq.com'
Set-ConfigValue $configPath 'MAIL_SSL' 'true'
Set-ConfigValue $configPath 'MAIL_STARTTLS' 'false'

Write-Host 'SMTP settings saved securely. Restarting the teaching system...' -ForegroundColor Green
& (Join-Path $projectRoot 'start-services.ps1') -Mode restart
exit $LASTEXITCODE
