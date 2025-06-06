@echo off
setlocal ENABLEDELAYEDEXPANSION

:: Number of runs
set RUNS=100

:: Temp file
set TIME_FILE=times.txt
if exist %TIME_FILE% del %TIME_FILE%

echo Measuring execution time of Voyager.exe for %RUNS% runs...
echo Please wait...

:: Use en-US culture to enforce decimal point
for /L %%i in (1,1,%RUNS%) do (
    powershell -NoProfile -Command "$OutputCulture = 'en-US'; [System.Threading.Thread]::CurrentThread.CurrentCulture = [System.Globalization.CultureInfo]::GetCultureInfo($OutputCulture); (Measure-Command { .\target\Voyager.exe .\src\main\resources\example_block_jt10.3.jt }).TotalMilliseconds" >> %TIME_FILE%
)

:: Analyze the data using PowerShell script
powershell -ExecutionPolicy Bypass -File analyze_times.ps1

echo Done.
endlocal
