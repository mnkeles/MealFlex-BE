[CmdletBinding()]
param(
    [string]$Path = '.env.staging'
)

$ErrorActionPreference = 'Stop'
$taskErrors = [System.Collections.Generic.List[string]]::new()
$taskValues = @{}

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    Write-Error "$Path bulunamadı. .env.staging.example dosyasını kopyalayıp düzenleyin."
    exit 1
}

foreach ($taskLine in Get-Content -LiteralPath $Path) {
    $taskTrimmed = $taskLine.Trim()
    if (-not $taskTrimmed -or $taskTrimmed.StartsWith('#')) { continue }
    $taskParts = $taskTrimmed.Split('=', 2)
    if ($taskParts.Count -ne 2 -or [string]::IsNullOrWhiteSpace($taskParts[0])) {
        $taskErrors.Add("Geçersiz satır: $taskTrimmed")
        continue
    }
    $taskValues[$taskParts[0].Trim()] = $taskParts[1].Trim()
}

$taskRequired = @(
    'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD', 'APP_VERSION', 'PUBLIC_HOST',
    'PUBLIC_BASE_URL', 'TLS_EMAIL', 'JWT_SECRET', 'PAYMENT_WEBHOOK_SECRET'
)
foreach ($taskName in $taskRequired) {
    if (-not $taskValues.ContainsKey($taskName) -or [string]::IsNullOrWhiteSpace($taskValues[$taskName])) {
        $taskErrors.Add("$taskName eksik.")
    }
}

foreach ($taskSecretName in @('DB_PASSWORD', 'JWT_SECRET', 'PAYMENT_WEBHOOK_SECRET')) {
    $taskSecret = $taskValues[$taskSecretName]
    if ($taskSecret -and ($taskSecret.Length -lt 32 -or $taskSecret -match 'replace|example|password')) {
        $taskErrors.Add("$taskSecretName en az 32 karakterlik gerçek bir rastgele değer olmalı.")
    }
}
if ($taskValues['JWT_SECRET'] -and $taskValues['JWT_SECRET'] -eq $taskValues['PAYMENT_WEBHOOK_SECRET']) {
    $taskErrors.Add('JWT_SECRET ve PAYMENT_WEBHOOK_SECRET birbirinden farklı olmalı.')
}

if ($taskValues['PUBLIC_BASE_URL']) {
    try {
        $taskUri = [Uri]$taskValues['PUBLIC_BASE_URL']
        if (($taskUri.Scheme -ne 'https') -or
            (-not $taskUri.Host) -or
            ($taskUri.Host -in @('localhost', '127.0.0.1', '::1')) -or
            ($taskUri.Port -ne 443) -or
            ($taskUri.AbsolutePath -ne '/')) {
            throw 'external HTTPS required'
        }
    } catch {
        $taskErrors.Add('PUBLIC_BASE_URL internetten erişilebilir kök HTTPS adresi olmalı.')
    }
}

$taskHost = $taskValues['PUBLIC_HOST']
if ($taskHost -and (
    $taskHost -match '[:/\\]' -or
    $taskHost -notmatch '^[A-Za-z0-9.-]+$' -or
    [Uri]::CheckHostName($taskHost) -ne [UriHostNameType]::Dns -or
    -not $taskHost.Contains('.'))) {
    $taskErrors.Add('PUBLIC_HOST yalnızca alan adı olmalı; protokol, port veya yol içermemeli.')
}

if ($taskHost -and $taskUri -and $taskUri.Host -ne $taskHost) {
    $taskErrors.Add('PUBLIC_BASE_URL ile PUBLIC_HOST aynı alan adını kullanmalı.')
}
if ($taskHost -and $taskValues['PUBLIC_BASE_URL'] -ne "https://$taskHost") {
    $taskErrors.Add('PUBLIC_BASE_URL tam olarak https://PUBLIC_HOST biçiminde olmalı; sonunda / bulunmamalı.')
}

if ($taskValues['TLS_EMAIL'] -and $taskValues['TLS_EMAIL'] -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') {
    $taskErrors.Add('TLS_EMAIL geçerli bir e-posta adresi olmalı.')
}

if ($taskValues['APP_VERSION'] -and $taskValues['APP_VERSION'] -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$') {
    $taskErrors.Add('APP_VERSION 1-64 karakter olmalı ve yalnız harf, rakam, nokta, tire veya alt çizgi içermeli.')
}

foreach ($taskThreshold in @(
    @{ Name = 'BACKUP_MAX_AGE_HOURS'; Minimum = 1; Maximum = 168 },
    @{ Name = 'DISK_USAGE_WARN_PERCENT'; Minimum = 1; Maximum = 99 }
)) {
    if ($taskValues.ContainsKey($taskThreshold.Name)) {
        $taskParsed = 0
        if ((-not [int]::TryParse($taskValues[$taskThreshold.Name], [ref]$taskParsed)) -or
            $taskParsed -lt $taskThreshold.Minimum -or
            $taskParsed -gt $taskThreshold.Maximum) {
            $taskErrors.Add("$($taskThreshold.Name) $($taskThreshold.Minimum)-$($taskThreshold.Maximum) aralığında olmalı.")
        }
    }
}

if ($taskErrors.Count -gt 0) {
    Write-Error "Staging yapılandırması geçersiz:`n - $($taskErrors -join "`n - ")"
    exit 1
}

Write-Output 'Staging yapılandırması geçerli; hiçbir secret değeri ekrana yazdırılmadı.'
