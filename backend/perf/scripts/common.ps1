function Get-EnvMap {
    param([string]$Path)

    $map = @{}
    Get-Content -Path $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -lt 1) {
            return
        }

        $key = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()
        $map[$key] = $value
    }

    return $map
}

function New-PerfContext {
    param(
        [string]$ScriptDirectory,
        [string]$ComposeFile = "..\\..\\docker-compose.yml",
        [string]$EnvFile = "..\\..\\.env"
    )

    $composePath = [System.IO.Path]::GetFullPath((Join-Path $ScriptDirectory $ComposeFile))
    $envPath = [System.IO.Path]::GetFullPath((Join-Path $ScriptDirectory $EnvFile))

    if (-not (Test-Path $composePath)) {
        throw "Compose file not found: $composePath"
    }
    if (-not (Test-Path $envPath)) {
        throw ".env file not found: $envPath"
    }

    $envMap = Get-EnvMap -Path $envPath
    $mysqlUser = $envMap["MYSQL_USER"]
    $mysqlPassword = $envMap["MYSQL_PASSWORD"]
    $mysqlDatabase = $envMap["MYSQL_DATABASE"]

    if (-not $mysqlUser -or -not $mysqlPassword -or -not $mysqlDatabase) {
        throw "MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE must exist in $envPath"
    }

    return [PSCustomObject]@{
        ComposePath   = $composePath
        EnvPath       = $envPath
        EnvMap        = $envMap
        MysqlUser     = $mysqlUser
        MysqlPassword = $mysqlPassword
        MysqlDatabase = $mysqlDatabase
    }
}

function Invoke-ComposeMySql {
    param(
        [pscustomobject]$Context,
        [string]$Sql,
        [switch]$Batch
    )

    $dockerArgs = @(
        "compose",
        "-f",
        $Context.ComposePath,
        "exec",
        "-T",
        "mysql",
        "mysql"
    )

    if ($Batch) {
        $dockerArgs += @("-N", "-B")
    }

    $dockerArgs += @(
        "-u$($Context.MysqlUser)",
        "-p$($Context.MysqlPassword)",
        "-D",
        $Context.MysqlDatabase
    )

    if ($Batch) {
        $dockerArgs += @("-e", $Sql)
        $result = & docker @dockerArgs
    } else {
        $result = $Sql | & docker @dockerArgs
    }

    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed."
    }

    return $result
}

function Invoke-ComposeRedisCli {
    param(
        [pscustomobject]$Context,
        [string[]]$Arguments
    )

    $redisPassword = $Context.EnvMap["REDIS_PASSWORD"]
    if (-not $redisPassword) {
        throw "REDIS_PASSWORD must exist in $($Context.EnvPath)"
    }

    $dockerArgs = @(
        "compose",
        "-f",
        $Context.ComposePath,
        "exec",
        "-T",
        "redis",
        "redis-cli",
        "--no-auth-warning",
        "-a",
        $redisPassword
    ) + $Arguments

    $result = & docker @dockerArgs

    if ($LASTEXITCODE -ne 0) {
        throw "Redis command failed."
    }

    return $result
}

function Get-PerfProductRow {
    param(
        [pscustomobject]$Context,
        [string]$ProductName = "PERF_TEST_PRODUCT"
    )

    $escapedName = $ProductName.Replace("'", "''")
    $rows = Invoke-ComposeMySql -Context $Context -Batch -Sql "SELECT id, name, stock_quantity, price, status FROM products WHERE name = '$escapedName' ORDER BY id ASC LIMIT 1;"

    if (-not $rows -or $rows.Count -eq 0) {
        return $null
    }

    $firstRow = $rows
    if ($rows -is [System.Array]) {
        $firstRow = $rows[0]
    }

    $parts = $firstRow -split "`t"
    if ($parts.Length -lt 5) {
        return $null
    }

    return [PSCustomObject]@{
        Id            = [int64]$parts[0]
        Name          = $parts[1]
        StockQuantity = [int]$parts[2]
        Price         = [int64]$parts[3]
        Status        = $parts[4]
    }
}

function Ensure-Directory {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Force -Path $Path | Out-Null
    }
}

function Clear-InventoryMirror {
    param(
        [pscustomobject]$Context,
        [int64]$ProductId
    )

    $productKey = "inventory:product:$ProductId"
    [void](Invoke-ComposeRedisCli -Context $Context -Arguments @("DEL", $productKey))
    [void](Invoke-ComposeRedisCli -Context $Context -Arguments @("SREM", "inventory:dirty-products", $ProductId.ToString()))
}
