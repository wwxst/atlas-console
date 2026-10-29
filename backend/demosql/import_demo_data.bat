@echo off
chcp 65001 >nul
setlocal

REM ============================================================
REM  一键导入 Atlas 演示数据
REM  依次执行 demosql\01_sys_user.sql 和 demosql\02_user_account.sql
REM
REM  连接参数默认值与 application.yml 的本地默认值一致，可用环境变量覆盖：
REM    DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD / MYSQL_BIN
REM ============================================================

cd /d "%~dp0"

REM ---------- 连接参数（可用环境变量覆盖） ----------
if not defined DB_HOST     set "DB_HOST=localhost"
if not defined DB_PORT     set "DB_PORT=3306"
if not defined DB_NAME     set "DB_NAME=web_project"
if not defined DB_USERNAME set "DB_USERNAME=root"
if not defined DB_PASSWORD set "DB_PASSWORD=123456"
set "MYSQL_PWD=%DB_PASSWORD%"

set "SQL_FILE_1=01_sys_user.sql"
set "SQL_FILE_2=02_user_account.sql"

REM ---------- 确定 mysql 客户端路径 ----------
set "MYSQL="
if defined MYSQL_BIN if exist "%MYSQL_BIN%" set "MYSQL=%MYSQL_BIN%"
if not defined MYSQL for /f "delims=" %%I in ('where mysql.exe 2^>nul') do if not defined MYSQL set "MYSQL=%%I"

if not defined MYSQL if exist "D:\Develop\sofe\Mysql\bin\mysql.exe" set "MYSQL=D:\Develop\sofe\Mysql\bin\mysql.exe"
if not defined MYSQL if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not defined MYSQL if exist "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL=C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe"

if not defined MYSQL (
    echo [ERROR] 未找到 mysql 客户端。
    echo         请将 mysql.exe 加入 PATH，或设置环境变量 MYSQL_BIN 指向它。
    echo         例如:  set MYSQL_BIN=D:\Develop\sofe\Mysql\bin\mysql.exe
    echo.
    pause
    exit /b 1
)

if not exist "%SQL_FILE_1%" (
    echo [ERROR] 找不到文件: %SQL_FILE_1%
    echo.
    pause
    exit /b 1
)
if not exist "%SQL_FILE_2%" (
    echo [ERROR] 找不到文件: %SQL_FILE_2%
    echo.
    pause
    exit /b 1
)

echo ============================================================
echo  Atlas 演示数据导入
echo ============================================================
echo  mysql   : %MYSQL%
echo  目标库  : %DB_NAME%@%DB_HOST%:%DB_PORT%
echo  账号    : %DB_USERNAME%
echo ------------------------------------------------------------

REM ---------- 先确认能连上目标库 ----------
"%MYSQL%" -h"%DB_HOST%" -P"%DB_PORT%" -u"%DB_USERNAME%" --default-character-set=utf8mb4 -e "USE `%DB_NAME%`;"
if errorlevel 1 (
    echo [ERROR] 无法连接数据库 %DB_NAME%，请检查连接参数或数据库是否已启动。
    echo.
    pause
    exit /b 1
)
echo [OK] 数据库连接正常
echo.

REM ---------- 依次导入 ----------
call :RUN_SQL "%SQL_FILE_1%" "系统用户 sys_user (50 条)"
if errorlevel 1 goto :FAIL

call :RUN_SQL "%SQL_FILE_2%" "普通用户 user_account (100 条)"
if errorlevel 1 goto :FAIL

call :RUN_SQL "03_verify.sql" "导入结果校验"
if errorlevel 1 goto :FAIL

echo ------------------------------------------------------------
echo [DONE] 演示数据导入完成。
echo ------------------------------------------------------------
echo.
pause
exit /b 0

REM ============================================================
REM  子过程：执行单个 SQL 文件
REM  参数1 = 文件路径   参数2 = 描述
REM ============================================================
:RUN_SQL
echo [RUN] %~2
echo        ^<- %~1
"%MYSQL%" -h"%DB_HOST%" -P"%DB_PORT%" -u"%DB_USERNAME%" --default-character-set=utf8mb4 "%DB_NAME%" < "%~1"
if errorlevel 1 (
    echo [ERROR] 导入失败: %~1
    echo         常见原因: 表不存在，或同批数据已存在（唯一键冲突 1062）。
    echo.
    pause
    exit /b 1
)
echo [OK]  %~2 导入成功
echo.
exit /b 0

:FAIL
echo.
echo [FAILED] 导入中断，请根据上面的错误信息排查。
echo.
pause
exit /b 1
