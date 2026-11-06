@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ========================================
echo   Filiz - Tek Seferde EXE Oluşturma
echo ========================================
echo.
echo Java kontrol ediliyor...
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo HATA: Java bulunamadi!
    echo Lutfen once Java 17+ yukleyin:
    echo https://www.java.com/tr/download/
    echo.
    pause
    exit /b 1
)

echo Java bulundu.
echo.
echo Gradle indiriliyor ve kuruluyor...
.\gradlew.bat --version

if %ERRORLEVEL% neq 0 (
    echo.
    echo HATA: Gradle baslatilamadi!
    pause
    exit /b 1
)

echo.
echo ========================================
echo   Derleniyor...
echo ========================================
call .\gradlew.bat clean build --console=plain

if %ERRORLEVEL% neq 0 (
    echo.
    echo HATA: Derleme basarisiz!
    pause
    exit /b 1
)

echo.
echo ========================================
echo   EXE Olusturuluyor...
echo ========================================
call .\gradlew.bat createDistributable --console=plain

if %ERRORLEVEL% neq 0 (
    echo.
    echo HATA: EXE olusturma basarisiz!
    pause
    exit /b 1
)

echo.
echo ========================================
echo   TAMAMLANDI!
echo ========================================
echo.
echo EXE dosyalari:
dir /b build\compose\binaries\main\exe\*.exe 2>nul
echo.
echo Simdi bu klasordeki EXE dosyasini kullanabilirsiniz!
echo.
pause
