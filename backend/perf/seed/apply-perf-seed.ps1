param(
    [string]$ComposeFile = "..\\..\\docker-compose.yml",
    [string]$EnvFile = "..\\..\\.env",
    [string]$SqlFile = ".\\seed-perf-data.sql",
    [string]$UsersCsvPath = "..\\k6\\users.csv",
    [string]$ProductName = "PERF_TEST_PRODUCT",
    [int]$StockQuantity = 1000000,
    [int]$UserCount = 5000
)

$ErrorActionPreference = "Stop"
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDirectory "..\\scripts\\common.ps1")

function Write-UsersCsv {
    param(
        [string]$Path,
        [int]$Count
    )

    $directory = Split-Path -Parent $Path
    if ($directory -and -not (Test-Path $directory)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }

    $rows = @("email,password")
    for ($index = 1; $index -le $Count; $index++) {
        $email = "perftest{0}@test.com" -f $index.ToString("000")
        $rows += "$email,PerfTest1234!"
    }

    Set-Content -Path $Path -Value $rows -Encoding UTF8
}

function New-MemberValuesSql {
    param([int]$Count)

    $passwordHash = '$2a$10$JXbEYUITSrW2XZkWm7Ccg.GbhRxumcMWFng.jm8FonQwq3wSohs4m'
    $values = for ($index = 1; $index -le $Count; $index++) {
        $email = "perftest{0}@test.com" -f $index.ToString("000")
        "('$email', '$passwordHash', 'USER', 1, NULL, NULL, NULL, NOW(6), NOW(6))"
    }

    return ($values -join ",`n")
}

function Escape-SqlString {
    param([string]$Value)

    return $Value.Replace("'", "''")
}

$sqlPath = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory $SqlFile))
$usersCsvFullPath = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory $UsersCsvPath))

if (-not (Test-Path $sqlPath)) {
    throw "SQL file not found: $sqlPath"
}

$context = New-PerfContext -ScriptDirectory $scriptDirectory -ComposeFile $ComposeFile -EnvFile $EnvFile

Write-UsersCsv -Path $usersCsvFullPath -Count $UserCount

$sqlTemplate = Get-Content -Path $sqlPath -Raw
$sql = $sqlTemplate.Replace("__PERF_MEMBER_VALUES__", (New-MemberValuesSql -Count $UserCount))
$sql = $sql.Replace("__PERF_PRODUCT_NAME__", (Escape-SqlString -Value $ProductName))
$sql = $sql.Replace("__PERF_PRODUCT_STOCK__", $StockQuantity.ToString())
$null = Invoke-ComposeMySql -Context $context -Sql $sql
$product = Get-PerfProductRow -Context $context -ProductName $ProductName
if ($product) {
    Clear-InventoryMirror -Context $context -ProductId $product.Id
}

Write-Host "Perf seed applied."
Write-Host "users.csv: $usersCsvFullPath"
Write-Host "userCount: $UserCount"
Write-Host "SQL source: $sqlPath"
if ($product) {
    Write-Host "productId: $($product.Id)"
    Write-Host "productName: $($product.Name)"
    Write-Host "productStock: $($product.StockQuantity)"
    Write-Host "productStatus: $($product.Status)"
}
