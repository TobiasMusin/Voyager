@echo off
setlocal enabledelayedexpansion

rem === Run Voyager.exe 1000 times with fixed parameter ===
for /l %%i in (1,1,1000) do (
    start "" ".\Voyager-0.0.1-SNAPSHOT.exe" ".\src\main\resources"
)

rem === Run Voyager.exe 1000 times for each .jt file ===
for %%F in (.\src\main\resources\*.jt) do (
    for /l %%i in (1,1,1000) do (
        start "" ".\Voyager-0.0.1-SNAPSHOT.exe" "%%F"
    )
)
