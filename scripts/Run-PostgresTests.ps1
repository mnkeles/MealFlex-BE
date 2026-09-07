$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$taskDbValues = @{}
if (Test-Path -LiteralPath '.env') {
    foreach ($taskDbLine in Get-Content -LiteralPath '.env') {
        if ($taskDbLine -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$') { $taskDbValues[$Matches[1]] = $Matches[2] }
    }
}
$taskDbUrl = if ($env:DB_URL) { $env:DB_URL } else { $taskDbValues['DB_URL'] }
if ($taskDbUrl -and $taskDbUrl -notmatch '^jdbc:postgresql://(localhost|127\.0\.0\.1):5432/') { throw 'Only local PostgreSQL on port 5432 is allowed.' }
$taskDbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } elseif ($taskDbValues['DB_USERNAME']) { $taskDbValues['DB_USERNAME'] } else { 'postgres' }
$taskDbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { $taskDbValues['DB_PASSWORD'] }
$taskPsql = 'C:\Program Files\PostgreSQL\15\bin\psql.exe'
if (!(Test-Path -LiteralPath $taskPsql)) { $taskPsql = (Get-Command psql).Source }
$taskDatabase = 'mealflex_qa_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
$taskPreviousPgPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $taskDbPassword
    & $taskPsql -X -w -h localhost -p 5432 -U $taskDbUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $taskDatabase"
    if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated QA database.' }
    $env:MEALFLEX_QA_DB_URL = "jdbc:postgresql://localhost:5432/$taskDatabase"
    $env:MEALFLEX_QA_DB_USER = $taskDbUser
    $env:MEALFLEX_QA_DB_PASSWORD = $taskDbPassword
    & mvn '-Dtest=Postgres*Test' test
    $taskTestExitCode = $LASTEXITCODE
    Write-Output "Isolated QA database retained for inspection: $taskDatabase"
    exit $taskTestExitCode
} finally {
    $env:PGPASSWORD = $taskPreviousPgPassword
    Remove-Item Env:MEALFLEX_QA_DB_URL, Env:MEALFLEX_QA_DB_USER, Env:MEALFLEX_QA_DB_PASSWORD -ErrorAction SilentlyContinue
}
