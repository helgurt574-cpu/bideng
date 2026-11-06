@echo off
cd /d "%~dp0"
echo ========================================
echo   Filiz Desktop - EXE Paketi Olusturma
echo ========================================
echo.

echo EXE paketi olusturuluyor...
call gradlew.bat clean createDistributable

if %ERRORLEVEL% neq 0 (
    echo.
    echo HATA: Paket olusturma basarisiz!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo   Paket olusturuldu!
echo ========================================
echo.
echo EXE dosyalari:
dir /b build\compose\binaries\main\exe\*.exe 2>nul
echo.
echo Bu klasordeki Filiz.exe dosyasini calistirarak uygulamayi kullanabilirsiniz.
echo.
pause
