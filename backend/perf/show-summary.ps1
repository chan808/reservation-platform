param(
    [string]$SummaryFile = ""
)

$ErrorActionPreference = "Stop"
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$resultsDirectory = Join-Path $scriptDirectory "results"

if (-not $SummaryFile) {
    $latest = Get-ChildItem -Path $resultsDirectory -Filter "*.json" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) {
        throw "No summary JSON files found in $resultsDirectory"
    }
    $SummaryFile = $latest.FullName
}

$summary = Get-Content -Path $SummaryFile -Raw | ConvertFrom-Json

function Read-MetricValue {
    param(
        [object]$Metric,
        [string]$Key
    )

    if ($null -eq $Metric) {
        return "n/a"
    }

    $value = $Metric.$Key
    if ($null -eq $value -or $value -eq "") {
        return "n/a"
    }

    return $value
}

Write-Host "summaryFile: $SummaryFile"
Write-Host "productId: $($summary.meta.productId)"
Write-Host "vus: $($summary.meta.targetVus)"
Write-Host "duration: $($summary.meta.duration)"
Write-Host "paymentType: $($summary.meta.paymentType)"
Write-Host "httpReqsPerSec: $(Read-MetricValue $summary.metrics.http_reqs 'rate')"
Write-Host "httpErrorRate: $(Read-MetricValue $summary.metrics.http_req_failed 'rate')"
Write-Host "completedFlows: $(Read-MetricValue $summary.metrics.completed_flows 'count')"
Write-Host "completedFlowsPerSec: $(Read-MetricValue $summary.metrics.completed_flows 'rate')"
Write-Host "paymentsConfirmed: $(Read-MetricValue $summary.metrics.payments_confirmed 'count')"
Write-Host "orderFlowSuccessRate: $(Read-MetricValue $summary.metrics.order_flow_success_rate 'rate')"
Write-Host "httpP95: $(Read-MetricValue $summary.metrics.http_req_duration 'p(95)')"
Write-Host "httpP99: $(Read-MetricValue $summary.metrics.http_req_duration 'p(99)')"
Write-Host "flowP95: $(Read-MetricValue $summary.metrics.order_flow_duration 'p(95)')"
Write-Host "flowP99: $(Read-MetricValue $summary.metrics.order_flow_duration 'p(99)')"
Write-Host "productLookupFailures: $(Read-MetricValue $summary.metrics.product_lookup_failures 'count')"
Write-Host "orderCreateFailures: $(Read-MetricValue $summary.metrics.order_create_failures 'count')"
Write-Host "paymentConfirmFailures: $(Read-MetricValue $summary.metrics.payment_confirm_failures 'count')"
Write-Host "orderVerifyFailures: $(Read-MetricValue $summary.metrics.order_verify_failures 'count')"
Write-Host "businessErrors: $(Read-MetricValue $summary.metrics.business_errors 'count')"
Write-Host "soldOutErrors: $(Read-MetricValue $summary.metrics.sold_out_errors 'count')"
