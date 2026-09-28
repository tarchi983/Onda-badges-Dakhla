@echo off

:: orientation directe l fin kayn lfolder dyal had l file 
cd /d "%~dp0"

:: ndefini fin kayna l path dyal lfolder d java 
SET JAVA_EXE="%~dp0jdk-17.0.18.8-hotspot\bin\javaw.exe"
SET JAR_NAME=onda-badges-desktop-1.0.0-jar-with-dependencies.jar
SET JAR_PATH=%~dp0%JAR_NAME%

:: nverifiw wach kayn lfile dyal jar 
IF NOT EXIST "%JAR_PATH%" (
    echo [ERROR] JAR file not found at %JAR_PATH%
    pause
    EXIT /B 1
)

:: running the app in the backround with java 
start "ONDA Badges App" %JAVA_EXE% -jar "%JAR_PATH%"
exit