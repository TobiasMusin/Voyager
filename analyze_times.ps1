$times = Get-Content 'times.txt' | ForEach-Object { [double]$_ }

$stats = $times | Measure-Object -Minimum -Maximum -Average
$avg = [math]::Round($stats.Average, 2)
$min = [math]::Round($stats.Minimum, 2)
$max = [math]::Round($stats.Maximum, 2)

$sorted = $times | Sort-Object
$percentile95Index = [math]::Ceiling($sorted.Count * 0.95) - 1
$p95 = [math]::Round($sorted[$percentile95Index], 2)

$sumOfSquares = ($times | ForEach-Object { ($_ - $avg) * ($_ - $avg) }) | Measure-Object -Sum
$stddev = [math]::Round([math]::Sqrt($sumOfSquares.Sum / $times.Count), 2)

Write-Output "Minimum (ms):   $min"
Write-Output "Average (ms):   $avg"
Write-Output "Max (ms):       $max"
Write-Output "Std Dev (ms):   $stddev"
Write-Output "95%ile (ms):    $p95"

# Optional: Text-Histogram
$bins = 10
$range = $max - $min
$binSize = $range / $bins
$histogram = @(0) * $bins
foreach ($t in $times) {
    $index = [math]::Min([math]::Floor(($t - $min) / $binSize), $bins - 1)
    $histogram[$index]++
}
Write-Output "`nHistogram:"
for ($i = 0; $i -lt $bins; $i++) {
    $low = [math]::Round($min + $i * $binSize, 2)
    $high = [math]::Round($low + $binSize, 2)
    $count = $histogram[$i]
    $bar = '#' * ($count / ($times.Count / 50))
    Write-Output ("$low - $high ms : $bar")
}
