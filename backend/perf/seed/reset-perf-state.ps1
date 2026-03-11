param(
    [string]$ComposeFile = "..\\..\\docker-compose.yml",
    [string]$EnvFile = "..\\..\\.env",
    [string]$SqlFile = ".\\reset-perf-state.sql",
    [int64]$ProductId = 0,
    [string]$ProductName = "PERF_TEST_PRODUCT",
    [int]$StockQuantity = 1000000
)

$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDirectory "..\\scripts\\common.ps1")

$sqlPath = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory $SqlFile))
if (-not (Test-Path $sqlPath)) {
    throw "SQL file not found: $sqlPath"
}

$context = New-PerfContext -ScriptDirectory $scriptDirectory -ComposeFile $ComposeFile -EnvFile $EnvFile

if ($ProductId -le 0) {
    $product = Get-PerfProductRow -Context $context -ProductName $ProductName
    if (-not $product) {
        throw "Perf product not found by name: $ProductName"
    }
    $ProductId = $product.Id
}

$sqlTemplate = Get-Content -Path $sqlPath -Raw
$sql = $sqlTemplate.Replace("__PERF_PRODUCT_ID__", $ProductId.ToString())
$sql = $sql.Replace("__PERF_PRODUCT_STOCK__", $StockQuantity.ToString())
$null = Invoke-ComposeMySql -Context $context -Sql $sql

$productRow = Get-PerfProductRow -Context $context -ProductName $ProductName

Write-Host "Perf state reset complete."
Write-Host "productId: $ProductId"
if ($productRow) {
    Write-Host "productStock: $($productRow.StockQuantity)"
    Write-Host "productStatus: $($productRow.Status)"
}
