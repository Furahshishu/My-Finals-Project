@echo off
REM Compiles and runs Night Shift. Needs the JDK (javac) and the MySQL connector .jar inside the "lib" folder.
cd /d "%~dp0"
if not exist out mkdir out
javac -cp "lib\*" -d out NightShift.java
if errorlevel 1 (
  echo.
  echo Compile failed. Make sure the JDK is installed.
  pause
  exit /b 1
)
java -cp "out;lib\*" NightShift
pause
