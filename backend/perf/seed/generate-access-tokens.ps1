param(
    [string]$ComposeFile = "..\\..\\docker-compose.yml",
    [string]$EnvFile = "..\\..\\.env",
    [string]$TokensCsvPath = "..\\k6\\tokens.csv",
    [int]$AccessTokenExpirySeconds = 1800,
    [string]$EmailPattern = "perftest%@test.com",
    [string]$JwtSecret = "",
    [string]$ValidateBaseUrl = ""
)

$ErrorActionPreference = "Stop"
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDirectory "..\\scripts\\common.ps1")

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)

    $encoded = [Convert]::ToBase64String($Bytes)
    return $encoded.TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-Hs256Jwt {
    param(
        [string]$Subject,
        [string]$Role,
        [string]$Secret,
        [int]$ExpirySeconds
    )

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = '{"alg":"HS256","typ":"JWT"}'
    $payloadJson = '{"sub":"' + $Subject + '","role":"' + $Role + '","iat":' + $now + ',"exp":' + ($now + $ExpirySeconds) + '}'

    $header = ConvertTo-Base64Url -Bytes ([System.Text.Encoding]::UTF8.GetBytes($headerJson))
    $payload = ConvertTo-Base64Url -Bytes ([System.Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsignedToken = "$header.$payload"

    $hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $signatureBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($unsignedToken))
    } finally {
        $hmac.Dispose()
    }

    $signature = ConvertTo-Base64Url -Bytes $signatureBytes
    return "$unsignedToken.$signature"
}

$tokensCsvFullPath = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory $TokensCsvPath))
$context = New-PerfContext -ScriptDirectory $scriptDirectory -ComposeFile $ComposeFile -EnvFile $EnvFile
$jwtSecret = if ($JwtSecret) { $JwtSecret } else { $context.EnvMap["JWT_SECRET"] }

if (-not $jwtSecret) {
    throw "JWT_SECRET must exist in $($context.EnvPath)"
}

$escapedPattern = $EmailPattern.Replace("'", "''")
$rows = Invoke-ComposeMySql -Context $context -Batch -Sql "SELECT id, email FROM members WHERE email LIKE '$escapedPattern' AND withdrawn_at IS NULL ORDER BY email ASC;"

$lines = @("email,member_id,access_token")
foreach ($row in $rows) {
    if (-not $row) {
        continue
    }

    $parts = $row -split "`t"
    if ($parts.Length -lt 2) {
        continue
    }

    $memberId = $parts[0].Trim()
    $email = $parts[1].Trim()
    $token = New-Hs256Jwt -Subject $memberId -Role "USER" -Secret $jwtSecret -ExpirySeconds $AccessTokenExpirySeconds
    $lines += "$email,$memberId,$token"
}

$directory = Split-Path -Parent $tokensCsvFullPath
if ($directory -and -not (Test-Path $directory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

Set-Content -Path $tokensCsvFullPath -Value $lines -Encoding UTF8

if ($ValidateBaseUrl) {
    $firstTokenRow = $lines | Select-Object -Skip 1 -First 1
    if (-not $firstTokenRow) {
        throw "No token rows generated."
    }

    $token = ($firstTokenRow -split ",", 3)[2]
    $headers = @{ Authorization = "Bearer $token" }

    try {
        $response = Invoke-WebRequest -Uri ($ValidateBaseUrl.TrimEnd("/") + "/api/members/me") -Method Get -Headers $headers -UseBasicParsing
        if ($response.StatusCode -ne 200) {
            throw "Token validation failed. status=$($response.StatusCode)"
        }
    } catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            throw "Generated tokens do not match the running backend JWT configuration. /api/members/me returned status=$statusCode"
        }
        throw
    }
}

Write-Host "Access token CSV generated."
Write-Host "tokens.csv: $tokensCsvFullPath"
Write-Host "rows: $($lines.Count - 1)"
