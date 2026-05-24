# 1、初始化数据库：创建业务数据库 yang_wear
# 2、创建示例用户：yang / change_me
# 3、授予该用户业务数据库权限

CREATE DATABASE IF NOT EXISTS `yang_wear` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'yang'@'%' IDENTIFIED BY 'change_me';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'yang'@'%';

GRANT ALL PRIVILEGES ON yang_wear.* TO 'yang'@'%';

FLUSH PRIVILEGES;
