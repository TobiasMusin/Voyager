@echo off
REM Extracts LWJGL native DLLs from Maven dependencies into target/ alongside the .exe
setlocal

set TARGET_DIR=target
set REPO=%USERPROFILE%\.m2\repository\org\lwjgl

REM Find the LWJGL version from pom.xml (default 3.3.4)
set LWJGL_VER=3.3.4

echo Extracting LWJGL native DLLs (v%LWJGL_VER%) to %TARGET_DIR% ...

for %%M in (lwjgl lwjgl-glfw lwjgl-opengl) do (
    set JAR=%REPO%\%%M\%LWJGL_VER%\%%M-%LWJGL_VER%-natives-windows.jar
    echo   %%M ...
    call powershell -NoProfile -Command ^
        "$jar = '%REPO%\%%M\%LWJGL_VER%\%%M-%LWJGL_VER%-natives-windows.jar'; " ^
        "Add-Type -AssemblyName System.IO.Compression.FileSystem; " ^
        "$zip = [IO.Compression.ZipFile]::OpenRead($jar); " ^
        "foreach ($e in $zip.Entries) { " ^
        "  if ($e.FullName -match '\.dll$') { " ^
        "    $dest = '%TARGET_DIR%\' + $e.Name; " ^
        "    [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true); " ^
        "    Write-Host ('    ' + $e.Name) " ^
        "  } " ^
        "}; $zip.Dispose()"
)

echo Done. DLLs are in %TARGET_DIR%\
endlocal
