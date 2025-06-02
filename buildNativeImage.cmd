@echo off
setlocal

set APP_NAME=voyager
set PROFILE_DIR=profiling
set PROFILE_FILE=%PROFILE_DIR%\pgo.iprof

echo === Step 1: Build instrumented native image ===
call mvn clean package -Pnative-pgo-instrument || exit /b

if not exist %PROFILE_DIR% mkdir %PROFILE_DIR%
if exist %PROFILE_FILE% del /q %PROFILE_FILE%

echo === Step 2: Run instrumented binary on each file 1000 times ===
for %%f in (%INPUT_DIR%\*.jt) do (
    echo Processing %%f
    for /L %%i in (1,1,1000) do (
        target\%APP_NAME%.exe "%%f" >nul
    )
)

echo === Step 2b: Run instrumented binary on folder itself 1000 times ===
for /L %%i in (1,1,1000) do (
    target\%APP_NAME%.exe "%INPUT_DIR%" >nul
)

echo === Step 3: Build optimized native image ===
call mvn clean package -Pnative-pgo-optimized || exit /b

echo.
echo ✅ Done! Final binary is in target\%APP_NAME%.exe

endlocal
