@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo.
echo Bideng Windows kurulum paketi olusturuluyor...
echo.

if not exist gradlew.bat (
  echo HATA: gradlew.bat bulunamadi. Bu dosyayi filiz-desktop klasorunden calistirin.
  exit /b 1
)

call gradlew.bat --no-daemon --console=plain clean createRuntimeImage packageMsi packageExe
if errorlevel 1 (
  echo.
  echo HATA: Installer uretimi basarisiz oldu.
  echo.
  echo Not: "single-use Daemon" satiri hata degildir. Hata, yukarida FAILURE veya FAILED yazan satirdadir.
  exit /b 1
)

set OUT=..\hazir\Bideng-Windows-installer
if not exist "%OUT%" mkdir "%OUT%"

for %%F in (build\compose\binaries\main\msi\*.msi) do (
  copy /Y "%%F" "%OUT%\"
)

for %%F in (build\compose\binaries\main\exe\*.exe) do (
  copy /Y "%%F" "%OUT%\"
)

echo.
echo Tamamlandi.
echo Cikti klasoru: %OUT%
echo.
dir "%OUT%"

endlocal
