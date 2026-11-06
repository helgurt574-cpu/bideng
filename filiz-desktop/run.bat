@echo off
cd /d "%~dp0"
echo ========================================
echo   Filiz Desktop - Build ve Run
echo ========================================
echo.

echo Derleniyor...
call gradlew.bat clean build

if %ERRORLEVEL% neq 0 (
    echo.
    echo HATA: Derleme başarısız!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Derleme tamamlandi! Baslatiliyor...
call gradlew.bat run

pause
