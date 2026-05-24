# AIWear

AIWear 是一个面向服饰相关场景的全栈图片编辑平台。仓库中包含 Vue 前端、Spring Boot 后端、Python 图像服务，以及用于本地或团队部署的示例配置。

## 项目结构

```text
fronted/                     Vue 3 + Vite 前端
java_code/AIWear/            Spring Boot 后端
python_code/AI_Wear_python/  基于 Flask 的 Python 图像服务
deploy/                      MySQL、Redis、Nginx 的示例部署配置
```

## 技术栈

- 前端：Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios
- 后端：Spring Boot 3、MyBatis-Plus、Redis、JWT、Java Mail、阿里云 OSS SDK
- Python 服务：Flask、DashScope、Redis、Torch、Transformers、Pillow
- 部署：Docker Compose、Nginx、MySQL、Redis

## 运行依赖

- Node.js 18+
- Java 17
- Python 3.10+
- MySQL 8
- Redis 7

## 本地启动顺序

1. 先启动 MySQL 和 Redis。
2. 配置后端环境变量后，启动 Spring Boot 服务，默认端口 `8081`。
3. 配置 Python 服务环境变量后，启动 Flask 服务，默认端口 `6789`。
4. 在前端目录执行 `npm install` 和 `npm run dev`。

## 使用说明

### 前端

```powershell
cd fronted
npm install
npm run dev
```

前端开发代理读取 `fronted/.env.development`，当前仓库默认代理目标为 `http://127.0.0.1:8081`。

### Java 后端

启动前请先根据本地环境修改 `java_code/AIWear/src/main/resources/application.yml` 中的示例配置。当前仓库仅保留脱敏后的示例值。

常用启动方式：

```powershell
cd java_code\AIWear
mvn spring-boot:run
```

### Python 服务

先根据示例文件创建本地 `.env`，再填写你自己的私有配置：

```powershell
cd python_code\AI_Wear_python
Copy-Item .env.example .env
```

然后安装依赖并启动服务：

```powershell
pip install -r requirements.txt
python server.py
```

## 脱敏说明

当前仓库是为 GitHub 上传准备的脱敏协作副本：

- 已移除真实密码、token、邮箱授权码、OSS 凭据和私有服务地址。
- 已排除本地依赖目录和构建产物，不纳入版本控制。
- 私有运行配置应放在本地 `.env` 文件或私有环境变量中，不应提交到 Git。
