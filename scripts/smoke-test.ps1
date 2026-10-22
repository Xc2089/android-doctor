$ErrorActionPreference = 'Stop'
$baseUrl = if ($env:ANDROID_DOCTOR_URL) { $env:ANDROID_DOCTOR_URL.TrimEnd('/') } else { 'http://localhost:9090' }
$samplePath = Join-Path $PSScriptRoot '..\samples\combined-android.log'
$log = Get-Content -Raw -LiteralPath $samplePath
$body = @{ log = $log; sourceName = 'combined-android.log'; useAi = $false } | ConvertTo-Json
$result = Invoke-RestMethod -Uri "$baseUrl/api/diagnoses" -Method Post -ContentType 'application/json' -Body $body

if (-not $result.id) { throw 'Missing diagnosis id' }
if ($result.findings.code -notcontains 'NATIVE_LIB_NOT_FOUND') { throw 'Native library rule did not match' }
if ($result.findings.code -notcontains 'ABI_MISMATCH') { throw 'ABI rule did not match' }
if ($result.redactionCount -lt 1) { throw 'Expected the sample email to be redacted' }

Write-Host "Smoke test passed: $($result.id)" -ForegroundColor Green
Write-Host "Matched rules: $($result.findings.code -join ', ')"
