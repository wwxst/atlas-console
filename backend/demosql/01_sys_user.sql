-- ============================================================
-- Atlas 演示数据 01/02：系统用户 sys_user（50 条）
--
-- 密码：BCrypt("123456")，与 web_project.sql 中 admin 一致
-- 列  ：username, password, nickname, status, created_at, updated_at
-- 注意：sys_user 表没有 email / phone 列，故不插入
--
-- 演示数据特征：
--   username  —— 字母+数字（u10001 ~ u10050），互不相同，长度 6
--   nickname  —— 随机中文姓名（如 张伟、李娜），互不相同
--   created_at/updated_at —— 最近 60 天内随机错位分布，互不相同且 updated_at >= created_at
--
-- 可重复执行：使用 INSERT IGNORE，username 已存在的行会被跳过，缺失的才插入。
-- 不删除、不修改任何已有数据，不含 DELETE / TRUNCATE。
-- ============================================================

INSERT IGNORE INTO sys_user (username, password, nickname, status, created_at, updated_at)
VALUES
('u10020', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '杨静', 1, '2026-08-07 15:58:00', '2026-08-10 14:16:00'),
('u10012', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '崔茜', 1, '2026-08-08 12:57:00', '2026-08-11 06:34:00'),
('u10016', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '张晓峰', 1, '2026-08-09 19:08:00', '2026-08-11 18:23:00'),
('u10019', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '刘倩', 1, '2026-08-09 21:36:00', '2026-08-12 02:02:00'),
('u10013', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '冯明', 1, '2026-08-10 19:37:00', '2026-08-11 08:50:00'),
('u10045', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '徐刚', 1, '2026-08-14 09:54:00', '2026-08-16 11:16:00'),
('u10008', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '冯俊', 1, '2026-08-14 11:10:00', '2026-08-17 03:38:00'),
('u10004', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '林超', 1, '2026-08-15 20:57:00', '2026-08-18 13:02:00'),
('u10043', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '魏斌', 1, '2026-08-16 12:04:00', '2026-08-19 11:37:00'),
('u10044', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '赵阳', 1, '2026-08-17 01:48:00', '2026-08-18 06:31:00'),
('u10042', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '丁志强', 1, '2026-08-17 07:29:00', '2026-08-19 16:40:00'),
('u10036', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '任科', 1, '2026-08-18 06:37:00', '2026-08-19 09:45:00'),
('u10049', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '蔡敏', 1, '2026-08-18 09:43:00', '2026-08-20 11:23:00'),
('u10035', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '谭丽', 1, '2026-08-19 05:22:00', '2026-08-20 11:49:00'),
('u10029', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '唐昊', 1, '2026-08-20 06:47:00', '2026-08-20 13:01:00'),
('u10010', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '卢斌', 1, '2026-08-20 20:25:00', '2026-08-23 01:49:00'),
('u10046', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '姚敏', 1, '2026-08-22 16:20:00', '2026-08-23 11:49:00'),
('u10003', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '陈涛', 1, '2026-08-23 01:37:00', '2026-08-25 08:42:00'),
('u10021', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '杜宁', 1, '2026-08-26 06:15:00', '2026-08-26 09:41:00'),
('u10028', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '田杰', 1, '2026-08-26 15:27:00', '2026-08-28 10:16:00'),
('u10018', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '李晨', 1, '2026-08-28 10:18:00', '2026-08-29 03:43:00'),
('u10041', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '曾琳', 1, '2026-08-29 13:38:00', '2026-08-31 22:37:00'),
('u10026', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '袁涛', 1, '2026-09-03 07:06:00', '2026-09-05 04:30:00'),
('u10011', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '郭鹏', 1, '2026-09-03 22:30:00', '2026-09-04 15:45:00'),
('u10023', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '宋曼', 1, '2026-09-05 04:06:00', '2026-09-06 09:40:00'),
('u10032', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '谭宁', 1, '2026-09-05 19:28:00', '2026-09-05 23:09:00'),
('u10022', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '梁海燕', 1, '2026-09-06 04:59:00', '2026-09-07 10:47:00'),
('u10034', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '王蕾', 1, '2026-09-06 12:31:00', '2026-09-07 04:25:00'),
('u10009', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '崔伟', 1, '2026-09-06 15:20:00', '2026-09-09 00:44:00'),
('u10038', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '胡思远', 1, '2026-09-08 05:37:00', '2026-09-11 03:09:00'),
('u10033', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '胡强', 1, '2026-09-12 05:42:00', '2026-09-12 19:16:00'),
('u10040', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '崔旭', 1, '2026-09-12 08:15:00', '2026-09-12 19:21:00'),
('u10031', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '叶蕾', 1, '2026-09-12 16:41:00', '2026-09-13 04:34:00'),
('u10015', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '吕鑫', 1, '2026-09-13 17:08:00', '2026-09-14 20:20:00'),
('u10037', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '董俊', 1, '2026-09-13 21:06:00', '2026-09-16 01:12:00'),
('u10024', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '徐霞', 1, '2026-09-14 14:11:00', '2026-09-14 18:07:00'),
('u10048', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '姜琳', 1, '2026-09-15 22:00:00', '2026-09-18 02:34:00'),
('u10047', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '崔昊', 1, '2026-09-17 06:59:00', '2026-09-19 00:53:00'),
('u10002', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '董海燕', 1, '2026-09-19 02:49:00', '2026-09-20 05:21:00'),
('u10001', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '赵娜', 1, '2026-09-19 17:46:00', '2026-09-22 11:45:00'),
('u10006', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '魏科', 1, '2026-09-19 21:00:00', '2026-09-21 05:22:00'),
('u10039', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '徐娜', 1, '2026-09-22 08:59:00', '2026-09-23 23:35:00'),
('u10030', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '董建华', 1, '2026-09-23 07:43:00', '2026-09-25 00:20:00'),
('u10017', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '姚晶', 1, '2026-09-23 11:11:00', '2026-09-24 12:56:00'),
('u10005', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '王洋', 1, '2026-09-24 23:33:00', '2026-09-27 08:51:00'),
('u10014', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '余晶', 1, '2026-09-25 01:20:00', '2026-09-26 20:33:00'),
('u10027', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '余宁', 1, '2026-09-27 17:22:00', '2026-09-30 01:38:00'),
('u10007', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '刘斌', 1, '2026-10-02 20:32:00', '2026-10-04 20:19:00'),
('u10050', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '徐超', 1, '2026-10-02 22:20:00', '2026-10-05 16:51:00'),
('u10025', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '周敏', 1, '2026-10-04 15:36:00', '2026-10-06 16:34:00');
