param(
    [string]$ComposeFile = "..\\..\\docker-compose.yml",
    [string]$EnvFile = "..\\..\\.env",
    [string]$SqlFile = ".\\reset-perf-state.sql",
    [int64]$ProductId = 0,
    [string]$ProductName = "PERF_TEST_PRODUCT",
    [int]$StockQuantity = 1000000,
    [int]$MaxAttempts = 5,
    [int]$RetryDelayMs = 500
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
$resetSucceeded = $false

for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    try {
        $null = Invoke-ComposeMySql -Context $context -Sql $sql
        $resetSucceeded = $true
        break
    } catch {
        if ($attempt -eq $MaxAttempts) {
            throw
        }
        Write-Host "Reset retry $attempt failed. Waiting ${RetryDelayMs}ms before retry..."
        Start-Sleep -Milliseconds $RetryDelayMs
    }
}

if (-not $resetSucceeded) {
    throw "Perf state reset failed."
}

Clear-InventoryMirror -Context $context -ProductId $ProductId

$productRow = Get-PerfProductRow -Context $context -ProductName $ProductName

Write-Host "Perf state reset complete."
Write-Host "productId: $ProductId"
if ($productRow) {
    Write-Host "productStock: $($productRow.StockQuantity)"
    Write-Host "productStatus: $($productRow.Status)"
}
