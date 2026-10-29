-- 导入后校验：统计演示数据条数，并检查各唯一键是否重复
SELECT 'sys_user(系统用户)'   AS 表, COUNT(*) AS 行数 FROM sys_user WHERE username LIKE 'u%'
UNION ALL SELECT 'sys_user(含 admin)', COUNT(*) FROM sys_user
UNION ALL SELECT 'user_account(普通用户)', COUNT(*) FROM user_account;
