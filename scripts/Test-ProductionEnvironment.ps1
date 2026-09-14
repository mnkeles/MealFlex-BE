[CmdletBinding()]
param(
    [switch]$AllowMockPayment,
    [switch]$AllowMockPayout
)

$ErrorActionPreference = 'Stop'
$taskErrors = [System.Collections.Generic.List[string]]::new()

function Get-RequiredEnvironmentValue {
    param([Parameter(Mandatory = $true)][string]$Name)

    $taskValue = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($taskValue)) {
        $taskErrors.Add("$Name is missing.")
        return ''
    }
    Write-Host "PASS $Name is configured"
    return $taskValue.Trim()
}

function Test-ExternalUrl {
    param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][string]$Value)

    try {
        $taskUri = [Uri]$Value
        if ($taskUri.Scheme -ne 'https' -or $taskUri.Host -in @('localhost', '127.0.0.1', '::1')) {
            throw 'must use HTTPS and must not target a local host'
        }
        Write-Host "PASS $Name is an external HTTPS URL"
    } catch {
        $taskErrors.Add("$Name must be an external HTTPS URL.")
    }
}

$taskProfiles = Get-RequiredEnvironmentValue -Name 'SPRING_PROFILES_ACTIVE'
if ($taskProfiles -and ($taskProfiles -split ',' | ForEach-Object Trim) -notcontains 'prod') {
    $taskErrors.Add('SPRING_PROFILES_ACTIVE must include prod.')
}

$taskDbUrl = Get-RequiredEnvironmentValue -Name 'DB_URL'
if ($taskDbUrl -and -not $taskDbUrl.StartsWith('jdbc:postgresql://', [StringComparison]::OrdinalIgnoreCase)) {
    $taskErrors.Add('DB_URL must use a PostgreSQL JDBC URL.')
}
Get-RequiredEnvironmentValue -Name 'DB_USERNAME' | Out-Null
Get-RequiredEnvironmentValue -Name 'DB_PASSWORD' | Out-Null

$taskFrontendUrl = Get-RequiredEnvironmentValue -Name 'FRONTEND_BASE_URL'
if ($taskFrontendUrl) { Test-ExternalUrl -Name 'FRONTEND_BASE_URL' -Value $taskFrontendUrl }

$taskCorsOrigins = Get-RequiredEnvironmentValue -Name 'CORS_ALLOWED_ORIGINS'
if ($taskCorsOrigins) {
    foreach ($taskOrigin in $taskCorsOrigins -split ',') {
        Test-ExternalUrl -Name 'CORS_ALLOWED_ORIGINS entry' -Value $taskOrigin.Trim()
    }
}

$taskJwtSecret = Get-RequiredEnvironmentValue -Name 'JWT_SECRET'
if ($taskJwtSecret -and $taskJwtSecret.Length -lt 32) {
    $taskErrors.Add('JWT_SECRET must contain at least 32 characters.')
}

$taskPaymentProvider = Get-RequiredEnvironmentValue -Name 'PAYMENT_PROVIDER'
$taskPaymentProvider = $taskPaymentProvider.ToUpperInvariant()
if ($taskPaymentProvider -eq 'MOCK' -and -not $AllowMockPayment) {
    $taskErrors.Add('PAYMENT_PROVIDER must not be MOCK for production. Use -AllowMockPayment only in a non-production rehearsal.')
} elseif ($taskPaymentProvider -eq 'IYZICO') {
    Get-RequiredEnvironmentValue -Name 'IYZICO_API_KEY' | Out-Null
    Get-RequiredEnvironmentValue -Name 'IYZICO_SECRET_KEY' | Out-Null
    $taskIyzicoBaseUrl = Get-RequiredEnvironmentValue -Name 'IYZICO_BASE_URL'
    if ($taskIyzicoBaseUrl) {
        Test-ExternalUrl -Name 'IYZICO_BASE_URL' -Value $taskIyzicoBaseUrl
        try {
            $taskIyzicoUri = [Uri]$taskIyzicoBaseUrl
            if ($taskIyzicoUri.Host -notin @('api.iyzipay.com', 'sandbox-api.iyzipay.com')) {
                $taskErrors.Add('IYZICO_BASE_URL must use an official iyzipay API host.')
            }
        } catch {
            # Test-ExternalUrl already records the malformed URL.
        }
    }
    $taskIyzicoCallbackUrl = Get-RequiredEnvironmentValue -Name 'IYZICO_CALLBACK_URL'
    if ($taskIyzicoCallbackUrl) { Test-ExternalUrl -Name 'IYZICO_CALLBACK_URL' -Value $taskIyzicoCallbackUrl }
    $taskIyzicoCardCallbackUrl = Get-RequiredEnvironmentValue -Name 'IYZICO_CARD_MANAGEMENT_CALLBACK_URL'
    if ($taskIyzicoCardCallbackUrl) { Test-ExternalUrl -Name 'IYZICO_CARD_MANAGEMENT_CALLBACK_URL' -Value $taskIyzicoCardCallbackUrl }
} elseif ($taskPaymentProvider -ne 'MOCK') {
    $taskErrors.Add("PAYMENT_PROVIDER '$taskPaymentProvider' is not supported. Use MOCK or IYZICO.")
}
if ($taskPaymentProvider -eq 'MOCK') {
    $taskWebhookSecret = Get-RequiredEnvironmentValue -Name 'PAYMENT_WEBHOOK_SECRET'
    if ($taskWebhookSecret -and $taskWebhookSecret.Length -lt 32) {
        $taskErrors.Add('PAYMENT_WEBHOOK_SECRET must contain at least 32 characters.')
    }
}

$taskPayoutProvider = Get-RequiredEnvironmentValue -Name 'PAYOUT_PROVIDER'
$taskPayoutProvider = $taskPayoutProvider.ToUpperInvariant()
if ($taskPayoutProvider -eq 'MOCK') {
    if (-not $AllowMockPayout) {
        $taskErrors.Add('PAYOUT_PROVIDER must not be MOCK for production. A real bank/payout adapter is required. Use -AllowMockPayout only in a non-production rehearsal.')
    } else {
        Write-Output 'PASS MOCK payout is explicitly allowed for this rehearsal'
    }
} elseif ($taskPayoutProvider) {
    $taskErrors.Add("PAYOUT_PROVIDER '$taskPayoutProvider' has no implemented adapter. Configure a supported real payout adapter before production.")
}

$taskNotificationsEnabled = [Environment]::GetEnvironmentVariable('NOTIFICATION_EXTERNAL_ENABLED')
if ([string]::IsNullOrWhiteSpace($taskNotificationsEnabled)) {
    $taskNotificationsEnabled = 'false'
}
if ($taskNotificationsEnabled -notin @('true', 'false')) {
    $taskErrors.Add('NOTIFICATION_EXTERNAL_ENABLED must be true or false.')
} elseif ($taskNotificationsEnabled -eq 'true') {
    foreach ($taskChannel in @('EMAIL', 'SMS', 'PUSH')) {
        $taskEndpointName = "NOTIFICATION_${taskChannel}_ENDPOINT"
        $taskEndpoint = Get-RequiredEnvironmentValue -Name $taskEndpointName
        if ($taskEndpoint) { Test-ExternalUrl -Name $taskEndpointName -Value $taskEndpoint }
        Get-RequiredEnvironmentValue -Name "NOTIFICATION_${taskChannel}_API_KEY" | Out-Null
    }
} else {
    Write-Output 'PASS External notifications are explicitly disabled'
}

$taskDistanceEngine = [Environment]::GetEnvironmentVariable('LOCATION_DISTANCE_ENGINE')
if ([string]::IsNullOrWhiteSpace($taskDistanceEngine)) { $taskDistanceEngine = 'HAVERSINE' }
$taskDistanceEngine = $taskDistanceEngine.ToUpperInvariant()
if ($taskDistanceEngine -notin @('HAVERSINE', 'SHADOW', 'POSTGIS')) {
    $taskErrors.Add('LOCATION_DISTANCE_ENGINE must be HAVERSINE, SHADOW, or POSTGIS.')
} elseif ($taskDistanceEngine -in @('SHADOW', 'POSTGIS')) {
    $taskFlywayLocations = Get-RequiredEnvironmentValue -Name 'FLYWAY_LOCATIONS'
    if ($taskFlywayLocations -and -not $taskFlywayLocations.Contains('classpath:db/spatial-migration')) {
        $taskErrors.Add('FLYWAY_LOCATIONS must include classpath:db/spatial-migration for SHADOW or POSTGIS mode.')
    }
} else {
    Write-Output 'PASS Distance engine is HAVERSINE'
}

if ($taskErrors.Count -gt 0) {
    Write-Error "Production preflight failed:`n - $($taskErrors -join "`n - ")"
    exit 1
}

Write-Output 'Production environment preflight completed successfully.'
