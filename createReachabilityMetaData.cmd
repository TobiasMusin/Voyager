@echo off
setlocal

:: ==== CONFIG ====
set JAR_PATH=target\Voyager-0.0.1-SNAPSHOT.jar
set INPUT_DIR=src\main\resources
set CONFIG_DIR=generated-config
set TARGET_CONFIG_DIR=src\main\resources\META-INF\native-image\io.github.tomusin\voyager
set "JAVA_HOME=C:\path\to\graalvm-jdk-25"
set "PATH=%JAVA_HOME%\bin;%PATH%"


:: ==== STEP 1: Run the application with tracing agent ====
echo Running application with native-image-agent to generate config...
java -agentlib:native-image-agent=config-output-dir=%CONFIG_DIR% -jar %JAR_PATH% %INPUT_DIR%

if errorlevel 1 (
    echo Error: Application run failed.
    exit /b 1
)

:: ==== STEP 2: Copy generated config files ====
echo Copying config files to native-image metadata folder...

if not exist %TARGET_CONFIG_DIR% (
    mkdir %TARGET_CONFIG_DIR%
)

copy /Y %CONFIG_DIR%\*.json %TARGET_CONFIG_DIR% > nul

if errorlevel 1 (
    echo Warning: No config files were copied or some copy errors occurred.
) else (
    echo Config files copied successfully to %TARGET_CONFIG_DIR%
)

endlocal
