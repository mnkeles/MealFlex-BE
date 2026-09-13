[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl,

    [string]$AccessToken,

    [string]$ProtectedPath = '/v1/subscriptions',

    [int[]]$SwaggerExpectedStatus = @(200),

    [int[]]$ApiDocsExpectedStatus = @(200),

    [switch]$AllowInsecureHttp
)

$ErrorActionPreference = 'Stop'

$taskBaseUrl = $BaseUrl.TrimEnd('/')
$taskBaseUri = [Uri]$taskBaseUrl
if ($taskBaseUri.Scheme -notin @('http', 'https')) {
    throw 'BaseUrl must use http or https.'
}

$taskIsLocal = $taskBaseUri.Host -in @('localhost', '127.0.0.1', '::1')
if ($taskBaseUri.Scheme -ne 'https' -and -not $taskIsLocal -and -not $AllowInsecureHttp) {
    throw 'HTTPS is required for a non-local environment. Use -AllowInsecureHttp only for an explicitly approved internal test environment.'
}

function Get-HttpResult {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,

        [hashtable]$Headers = @{}
    )

    try {
        $taskResponse = Invoke-WebRequest -Uri $Uri -Headers $Headers -UseBasicParsing -TimeoutSec 20 -ErrorAction Stop
        $taskContent = if ($taskResponse.Content -is [byte[]]) {
            [System.Text.Encoding]::UTF8.GetString($taskResponse.Content)
        } else {
            [string]$taskResponse.Content
        }
        return [PSCustomObject]@{
            StatusCode = [int]$taskResponse.StatusCode
            Content    = $taskContent
        }
    } catch {
        if ($_.Exception.Response) {
            $taskErrorResponse = $_.Exception.Response
            return [PSCustomObject]@{
                StatusCode = [int]$taskErrorResponse.StatusCode
                Content    = ''
            }
        }
        throw
    }
}

function Assert-StatusCode {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [int]$Actual,

        [Parameter(Mandatory = $true)]
        [int[]]$Expected
    )

    if ($Actual -notin $Expected) {
        throw "$Name returned HTTP $Actual; expected $($Expected -join ' or ')."
    }
    Write-Output "PASS $Name (HTTP $Actual)"
}

$taskHealth = Get-HttpResult -Uri "$taskBaseUrl/actuator/health"
Assert-StatusCode -Name 'Health endpoint' -Actual $taskHealth.StatusCode -Expected @(200)
try {
    $taskHealthBody = $taskHealth.Content | ConvertFrom-Json
} catch {
    throw 'Health endpoint did not return a JSON response.'
}
if ($taskHealthBody.status -ne 'UP') {
    throw "Health endpoint reported '$($taskHealthBody.status)' instead of 'UP'."
}
Write-Output 'PASS Health status is UP'

$taskSwagger = Get-HttpResult -Uri "$taskBaseUrl/swagger-ui.html"
Assert-StatusCode -Name 'Swagger UI' -Actual $taskSwagger.StatusCode -Expected $SwaggerExpectedStatus

$taskApiDocs = Get-HttpResult -Uri "$taskBaseUrl/api-docs"
Assert-StatusCode -Name 'OpenAPI document' -Actual $taskApiDocs.StatusCode -Expected $ApiDocsExpectedStatus

$taskProtectedUrl = "$taskBaseUrl/$($ProtectedPath.TrimStart('/'))"
$taskAnonymous = Get-HttpResult -Uri $taskProtectedUrl
Assert-StatusCode -Name 'Anonymous access is rejected' -Actual $taskAnonymous.StatusCode -Expected @(401)

if ($AccessToken) {
    $taskAuthenticated = Get-HttpResult -Uri $taskProtectedUrl -Headers @{ Authorization = "Bearer $AccessToken" }
    if ($taskAuthenticated.StatusCode -lt 200 -or $taskAuthenticated.StatusCode -ge 300) {
        throw "Authenticated read endpoint returned HTTP $($taskAuthenticated.StatusCode); expected a 2xx response."
    }
    Write-Output "PASS Authenticated read endpoint (HTTP $($taskAuthenticated.StatusCode))"
} else {
    Write-Output 'SKIP Authenticated read endpoint (no AccessToken provided)'
}

Write-Output 'API smoke test completed successfully.'
