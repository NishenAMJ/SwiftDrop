# Prerequisites

Install these before you clone and run SwiftDrop. Use the versions listed so we all build the same way.

## Required tools

| Tool | Version | Used for |
|---|---|---|
| Git | 2.45 or newer | Source control |
| Java JDK | **21** (Eclipse Temurin recommended) | Backend services |
| Maven | 3.9.x (optional) | Building services. Each service includes the Maven wrapper (`mvnw`), so a global install is not required |
| Node.js | **24 LTS, 24.15.0 or newer** (team uses 24.22.1) | Angular frontend |
| npm | 11.x (comes with Node) | Frontend packages |
| Angular CLI | **22.x** (team uses 22.2.4) | `frontend/` |
| Docker Desktop | Recent version with Docker Compose v2+ | PostgreSQL, RabbitMQ and running services through `compose.yaml` |

These come in through the build files and need no separate install:

| Dependency | Version |
|---|---|
| Spring Boot | 4.1.2 (set in each service's `pom.xml`) |
| PostgreSQL, RabbitMQ | Run as Docker containers from `compose.yaml` |

> Angular CLI 22 will **not** start on Node versions older than 24.15.0 (or 22.22.3 on the Node 22 line). If `ng version` reports a Node.js error, upgrade Node.

## Install (Windows)

```powershell
winget install Git.Git
winget install EclipseAdoptium.Temurin.21.JDK
winget install OpenJS.NodeJS.LTS
winget install Docker.DockerDesktop
npm install -g @angular/cli@22
```

On macOS or Linux, use the installers from:
- Java 21: https://adoptium.net
- Node.js 24 LTS: https://nodejs.org
- Docker Desktop: https://www.docker.com/products/docker-desktop

### Set `JAVA_HOME`

Point `JAVA_HOME` at your JDK 21 folder (the one that contains `bin\java.exe`), then reopen the terminal:

```powershell
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21..."
```

### Docker Desktop

On Windows, Docker Desktop needs WSL 2. Make sure it is in **Linux containers** mode, and wait for **Engine running** before you use `docker compose`.

## Check your setup

Run these and compare the output with the table above:

```powershell
git --version
java -version
echo $env:JAVA_HOME
node --version
npm --version
ng version
docker --version
docker compose version
docker info
```

To check that a backend service builds, run this from its folder (for example `services/identity-merchant-service`):

```powershell
.\mvnw.cmd -v
```

## Optional

| Tool | Why | Install |
|---|---|---|
| GitHub CLI (`gh`) | Pull requests and issues from the terminal | `winget install GitHub.cli` |
| `psql` / pgAdmin / DBeaver | Inspect the database | `winget install PostgreSQL.PostgreSQL` |
| VS Code extensions | Extension Pack for Java, Spring Boot Extension Pack, Angular Language Service, Docker, YAML | VS Code Extensions panel |
