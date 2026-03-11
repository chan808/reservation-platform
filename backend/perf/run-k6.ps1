param(
    [ValidateRange(1, 10000)]
    [int]$Vus = 100,
    [string]$Duration = "60s",
    [int64]$ProductId = 0,
    [string]$ProductName = "PERF_TEST_PRODUCT",
    [ValidateSet("PAYPAL", "KAKAO", "TOSS")]
    [string]$PaymentType = "PAYPAL",
    [ValidateSet("constant", "burst")]
    [string]$ScenarioMode = "constant",
    [int]$OrderQuantity = 1,
    [int]$StockQuantity = 1000000,
    [int]$UserCount = 5000,
    [string]$BaseUrl = "http://host.docker.internal:8080",
    [string]$RequestTimeout = "30s",
    [string]$BurstRampUp = "5s",
    [string]$BurstHold = "30s",
    [string]$BurstRampDown = "5s",
    [switch]$UseTokens,
    [switch]$PrepareSeed,
    [switch]$PrepareTokens,
    [switch]$ResetState,
    [string]$JwtSecret = "",
    [string]$SummaryPrefix = "order-flow"
)

$ErrorActionPreference = "Stop"
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDirectory "scripts\\common.ps1")

$context = New-PerfContext -ScriptDirectory $scriptDirectory -ComposeFile "..\\docker-compose.yml" -EnvFile "..\\.env"

function Resolve-PerfProductId {
    param([switch]$AllowSeed)

    if ($ProductId -gt 0) {
        return $ProductId
    }

    $product = Get-PerfProductRow -Context $context -ProductName $ProductName
    if ($product) {
        return $product.Id
    }

    if ($AllowSeed) {
        Write-Host "Perf product not found. Running apply-perf-seed.ps1..."
        & powershell -ExecutionPolicy Bypass -File (Join-Path $scriptDirectory "seed\\apply-perf-seed.ps1") -ProductName $ProductName -StockQuantity $StockQuantity -UserCount $UserCount
        if ($LASTEXITCODE -ne 0) {
            throw "apply-perf-seed.ps1 failed."
        }

        $product = Get-PerfProductRow -Context $context -ProductName $ProductName
        if ($product) {
            return $product.Id
        }
    }

    throw "Perf product not found by name: $ProductName."
}

function Test-PreGeneratedToken {
    param(
        [string]$TokensCsvPath,
        [string]$BaseUrl
    )

    $tokenRow = Import-Csv $TokensCsvPath | Select-Object -First 1
    if (-not $tokenRow) {
        throw "tokens.csv is empty: $TokensCsvPath"
    }

    $headers = @{ Authorization = "Bearer $($tokenRow.access_token)" }
    $uri = $BaseUrl.TrimEnd("/") + "/api/members/me"

    try {
        $response = Invoke-WebRequest -Uri $uri -Method Get -Headers $headers -UseBasicParsing
        if ($response.StatusCode -ne 200) {
            throw "Token preflight failed. status=$($response.StatusCode)"
        }
    } catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            throw "Pre-generated tokens are invalid for the running backend. $uri returned status=$statusCode. Restart the backend with the correct JWT_SECRET or regenerate tokens with -JwtSecret."
        }
        throw
    }
}

function Get-CsvDataRowCount {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return 0
    }

    $rows = Import-Csv $Path
    if (-not $rows) {
        return 0
    }

    return @($rows).Count
}

$shouldPrepareSeed = $PrepareSeed -or $ResetState -or ($ProductId -le 0)
$ProductId = Resolve-PerfProductId -AllowSeed:$shouldPrepareSeed

if ($ResetState) {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $scriptDirectory "seed\\reset-perf-state.ps1") -ProductId $ProductId -StockQuantity $StockQuantity
    if ($LASTEXITCODE -ne 0) {
        throw "reset-perf-state.ps1 failed."
    }
}

$tokensCsvPath = Join-Path $scriptDirectory "k6\\tokens.csv"
if ($PrepareTokens -or ($UseTokens -and -not (Test-Path $tokensCsvPath))) {
    $tokenArgs = @(
        "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $scriptDirectory "seed\\generate-access-tokens.ps1")
    )
    if ($JwtSecret) {
        $tokenArgs += @("-JwtSecret", $JwtSecret)
    }
    & powershell @tokenArgs
    if ($LASTEXITCODE -ne 0) {
        throw "generate-access-tokens.ps1 failed."
    }
}

if ($UseTokens) {
    $tokenRowCount = Get-CsvDataRowCount -Path $tokensCsvPath
    if ($tokenRowCount -lt $Vus) {
        throw "tokens.csv has only $tokenRowCount rows but Vus is $Vus. Re-run .\perf\seed\apply-perf-seed.ps1 -UserCount $UserCount and .\perf\seed\generate-access-tokens.ps1."
    }
    Test-PreGeneratedToken -TokensCsvPath $tokensCsvPath -BaseUrl "http://localhost:8080"
}

$summaryDirectory = Join-Path $scriptDirectory "results"
Ensure-Directory -Path $summaryDirectory
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryName = "$SummaryPrefix-$Vus" + "vu-$timestamp.json"
$summaryFileHost = Join-Path $summaryDirectory $summaryName
$summaryFileContainer = "/scripts/results/$summaryName"

$dockerArgs = @(
    "compose",
    "--profile",
    "loadtest",
    "run",
    "--rm",
    "-e", "BASE_URL=$BaseUrl",
    "-e", "PRODUCT_ID=$ProductId",
    "-e", "PAYMENT_TYPE=$PaymentType",
    "-e", "SCENARIO_MODE=$ScenarioMode",
    "-e", "ORDER_QUANTITY=$OrderQuantity",
    "-e", "TARGET_VUS=$Vus",
    "-e", "DURATION=$Duration",
    "-e", "BURST_RAMP_UP=$BurstRampUp",
    "-e", "BURST_HOLD=$BurstHold",
    "-e", "BURST_RAMP_DOWN=$BurstRampDown",
    "-e", "REQUEST_TIMEOUT=$RequestTimeout",
    "-e", "SUMMARY_FILE=$summaryFileContainer",
    "k6"
)

$usersCsvPath = Join-Path $scriptDirectory "k6\\users.csv"
if (Test-Path $usersCsvPath) {
    $dockerArgs += @("-e", "USERS_CSV=/scripts/k6/users.csv")
}

if ($UseTokens) {
    if (-not (Test-Path $tokensCsvPath)) {
        throw "tokens.csv not found: $tokensCsvPath"
    }
    $dockerArgs += @("-e", "TOKENS_CSV=/scripts/k6/tokens.csv")
    $dockerArgs += @("-e", "REQUIRE_TOKENS=true")
}

$dockerArgs += @("run", "/scripts/k6/order-flow.js")

Write-Host "Running k6 scenario..."
Write-Host "productId: $ProductId"
Write-Host "vus: $Vus"
Write-Host "duration: $Duration"
Write-Host "paymentType: $PaymentType"
Write-Host "scenarioMode: $ScenarioMode"
if ($ScenarioMode -eq "burst") {
    Write-Host "burstRampUp: $BurstRampUp"
    Write-Host "burstHold: $BurstHold"
    Write-Host "burstRampDown: $BurstRampDown"
}
Write-Host "stockQuantity: $StockQuantity"
Write-Host "userCount: $UserCount"
Write-Host "summary: $summaryFileHost"

& docker @dockerArgs

$dockerExitCode = $LASTEXITCODE

if ($dockerExitCode -ne 0) {
    if (Test-Path $summaryFileHost) {
        throw "k6 execution failed with exit code $dockerExitCode after summary generation. This usually means a threshold failure. summary=$summaryFileHost"
    }
    throw "k6 execution failed with exit code $dockerExitCode before summary generation. Check the k6 output above for the actual setup or runtime error."
}

Write-Host "k6 execution completed."
Write-Host "summary: $summaryFileHost"
