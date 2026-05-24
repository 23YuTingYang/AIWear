# AIWear

AIWear is a full-stack image editing platform for outfit-related workflows. The repository contains a Vue frontend, a Spring Boot backend, a Python image service, and deployment examples for local or team setup.

## Project Structure

```text
fronted/                     Vue 3 + Vite frontend
java_code/AIWear/            Spring Boot backend
python_code/AI_Wear_python/  Flask-based Python image service
deploy/                      Example deployment configs for MySQL, Redis, and Nginx
```

## Tech Stack

- Frontend: Vue 3, Vite, Vue Router, Pinia, Element Plus, Axios
- Backend: Spring Boot 3, MyBatis-Plus, Redis, JWT, Java Mail, Aliyun OSS SDK
- Python service: Flask, DashScope, Redis, Torch, Transformers, Pillow
- Deployment: Docker Compose, Nginx, MySQL, Redis

## Runtime Dependencies

- Node.js 18+
- Java 17
- Python 3.10+
- MySQL 8
- Redis 7

## Local Startup Order

1. Start MySQL and Redis first.
2. Configure backend environment values and start the Spring Boot service on `8081`.
3. Configure Python service environment values and start the Flask service on `6789`.
4. Start the frontend with `npm install` and `npm run dev`.

## Setup Notes

### Frontend

```powershell
cd fronted
npm install
npm run dev
```

The frontend dev proxy reads `fronted/.env.development`. The default target in this repository points to `http://127.0.0.1:8081`.

### Java Backend

Update the example values in `java_code/AIWear/src/main/resources/application.yml` before local startup. This repository keeps only sanitized sample values.

Typical startup:

```powershell
cd java_code\AIWear
mvn spring-boot:run
```

### Python Service

Create a local `.env` from the example file and fill in your private values:

```powershell
cd python_code\AI_Wear_python
Copy-Item .env.example .env
```

Then install dependencies and run the service:

```powershell
pip install -r requirements.txt
python server.py
```

## Desensitization Notice

This repository is a sanitized collaboration copy prepared for GitHub upload:

- Real passwords, tokens, mail authorization codes, OSS credentials, and private endpoints were removed.
- Local dependency directories and build outputs were excluded from version control.
- Private runtime configuration should be stored in local `.env` files or private environment variables, not committed to Git.
