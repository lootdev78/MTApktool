@echo off
setlocal
if defined JAVA_HOME (set JAVA_EXE=%JAVA_HOME%\bin\java.exe) else (set JAVA_EXE=java.exe)
for /f "tokens=3" %%v in ('"%JAVA_EXE%" -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VERSION_TOKEN=%%v
set JAVA_VERSION_TOKEN=%JAVA_VERSION_TOKEN:"=%
for /f "tokens=1 delims=." %%m in ("%JAVA_VERSION_TOKEN%") do set JAVA_MAJOR=%%m
if not "%JAVA_MAJOR%"=="17" (
  echo MTApktool Java 17 profile requires JDK 17; detected: %JAVA_MAJOR%
  exit /b 1
)
call "%~dp0gradlew.bat" %* -Pmtapktool.javaVersion=17
endlocal
