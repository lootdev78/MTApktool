@echo off
setlocal
set DIR=%~dp0
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)

for /f "tokens=3" %%v in ('"%JAVA_EXE%" -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VERSION=%%~v
for /f "tokens=1 delims=." %%m in ("%JAVA_VERSION%") do set JAVA_MAJOR=%%m
if not "%JAVA_MAJOR%"=="25" (
  echo MTApktool Java 25 profile requires JDK 25; detected: %JAVA_VERSION% 1>&2
  exit /b 1
)

"%JAVA_EXE%" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew25 -classpath "%DIR%gradle\java25-wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %* -Pmtapktool.javaVersion=25 -Pandroid.builtInKotlin=false -Pandroid.newDsl=false
exit /b %ERRORLEVEL%
