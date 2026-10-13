@echo off

setlocal

set "DOUBLE_CLICKED="
echo %cmdcmdline% | find /i "%~nx0" >nul && set "DOUBLE_CLICKED=1"

set "RESULT=1"

pushd "%~dp0.."

if defined CATALINA_HOME (
    set "TOMCAT=%CATALINA_HOME%"
) else (
    set "TOMCAT=..\.."
)

set "SERVLET_API=%TOMCAT%\lib\servlet-api.jar"
if not exist "%SERVLET_API%" (
    echo Could not find %SERVLET_API%
    echo Set CATALINA_HOME to your Tomcat folder and try again.
    goto done
)

set "LIB=Test\lib"
set "CP=Test\out;WEB-INF\classes;%SERVLET_API%;%LIB%\junit-platform-console-standalone-1.10.2.jar;%LIB%\mockito-core-5.11.0.jar;%LIB%\byte-buddy-1.14.12.jar;%LIB%\byte-buddy-agent-1.14.12.jar;%LIB%\objenesis-3.3.jar"

echo Compiling tests...
if exist Test\out rmdir /s /q Test\out
mkdir Test\out
javac -d Test\out -cp "%CP%" Test\Back-End\*.java
if errorlevel 1 (
    echo Compile failed.
    goto done
)

set "SELECT="
if "%~1"=="" (
    for %%F in (Test\Back-End\*.java) do call set "SELECT=%%SELECT%% --select-class %%~nF"
) else (
    set "SELECT=--select-class %~1"
)

java -jar "%LIB%\junit-platform-console-standalone-1.10.2.jar" execute -cp "%CP%" %SELECT% --disable-banner
set "RESULT=%ERRORLEVEL%"

:done
popd
if defined DOUBLE_CLICKED (
    echo.
    pause
)
exit /b %RESULT%
