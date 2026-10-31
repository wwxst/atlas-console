@echo off
chcp 65001 >nul
setlocal

REM Import demo rows into an existing PostgreSQL database.
cd /d "%~dp0"
if not defined DB_HOST set "DB_HOST=localhost"
if not defined DB_PORT set "DB_PORT=5432"
if not defined DB_NAME set "DB_NAME=web_project"
if not defined DB_USERNAME set "DB_USERNAME=atlas"
if not defined DB_PASSWORD set "DB_PASSWORD=123456"
set "PGPASSWORD=%DB_PASSWORD%"
set "PGOPTIONS=-c TimeZone=UTC"

set "PSQL="
if defined PSQL_BIN if exist "%PSQL_BIN%" set "PSQL=%PSQL_BIN%"
if not defined PSQL for /f "delims=" %%I in ('where psql.exe 2^>nul') do if not defined PSQL set "PSQL=%%I"
if not defined PSQL if exist "C:\Program Files\PostgreSQL\17\bin\psql.exe" set "PSQL=C:\Program Files\PostgreSQL\17\bin\psql.exe"
if not defined PSQL if exist "C:\Program Files\PostgreSQL\16\bin\psql.exe" set "PSQL=C:\Program Files\PostgreSQL\16\bin\psql.exe"
if not defined PSQL (
  echo [ERROR] 未找到 PostgreSQL psql 客户端，请设置 PSQL_BIN 或将 psql.exe 加入 PATH。
  pause
  exit /b 1
)

for %%F in (01_sys_user.sql 02_user_account.sql 03_verify.sql) do if not exist "%%F" (
  echo [ERROR] 找不到文件 %%F
  pause
  exit /b 1
)

echo Atlas PostgreSQL 演示数据导入
echo Target: %DB_NAME%@%DB_HOST%:%DB_PORT%
"%PSQL%" -X -v ON_ERROR_STOP=1 -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USERNAME%" -d "%DB_NAME%" -c "SELECT 1;" >nul
if errorlevel 1 (
  echo [ERROR] 无法连接目标 PostgreSQL 数据库，请检查连接参数。
  pause
  exit /b 1
)

call :RUN_SQL "01_sys_user.sql" "系统用户"
if errorlevel 1 goto :FAIL
call :RUN_SQL "02_user_account.sql" "普通用户"
if errorlevel 1 goto :FAIL
call :RUN_SQL "03_verify.sql" "导入校验"
if errorlevel 1 goto :FAIL

echo [DONE] 演示数据导入完成。
pause
exit /b 0

:RUN_SQL
echo [RUN] %~2
"%PSQL%" -X -v ON_ERROR_STOP=1 -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USERNAME%" -d "%DB_NAME%" -f "%~1"
if errorlevel 1 (
  echo [ERROR] 导入失败：%~1
  exit /b 1
)
echo [OK] %~2
exit /b 0

:FAIL
echo [FAILED] 演示数据导入中断。
pause
exit /b 1
