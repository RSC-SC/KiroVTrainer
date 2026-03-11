@echo off
REM V-Trainer Setup Verification Script (Windows)
REM This script checks if all required files and configurations are in place

echo.
echo V-Trainer Setup Verification
echo ================================
echo.

set PASSED=0
set FAILED=0
set WARNINGS=0

REM Function to check file existence
:check_file
if exist "%~1" (
    echo [OK] %~1
    set /a PASSED+=1
) else (
    echo [MISSING] %~1
    set /a FAILED+=1
)
goto :eof

echo Checking Firebase Configuration...
call :check_file "firebase.json"
call :check_file ".firebaserc"
call :check_file "firestore.rules"
call :check_file "firestore.indexes.json"
call :check_file "storage.rules"
echo.

echo Checking Android Mobile App...
call :check_file "app\build.gradle.kts"
call :check_file "app\src\main\AndroidManifest.xml"
call :check_file "app\google-services.json"
echo.

echo Checking Wear OS App...
call :check_file "wear\build.gradle.kts"
call :check_file "wear\src\main\AndroidManifest.xml"
call :check_file "wear\google-services.json"
echo.

echo Checking Cloud Functions...
call :check_file "functions\package.json"
call :check_file "functions\tsconfig.json"
call :check_file "functions\src\index.ts"
call :check_file "functions\src\syncWorkout.ts"
call :check_file "functions\src\calculateProgress.ts"
call :check_file "functions\src\sendWorkoutReminder.ts"
echo.

echo Checking Gradle Configuration...
call :check_file "build.gradle.kts"
call :check_file "settings.gradle.kts"
call :check_file "gradle.properties"
echo.

echo Checking Documentation...
call :check_file "README.md"
call :check_file "SETUP.md"
call :check_file "PROJECT_STRUCTURE.md"
echo.

echo ================================
echo Summary:
echo Passed: %PASSED%
echo Failed: %FAILED%
echo.

if %FAILED% EQU 0 (
    echo All checks passed! Your setup is complete.
) else (
    echo Setup is incomplete. Please check the missing files above.
)

pause
