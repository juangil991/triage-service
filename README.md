# VidaCruz Triage

Servicio reactivo para registrar pacientes, clasificarlos con reglas determinísticas y publicar eventos clínicos a Kafka mediante Outbox.

## Requisitos

- JDK 17 o superior.
- Para pruebas de integración: Docker o Podman con una API compatible con Docker.

## Probar

Desde PowerShell, en la raíz del repositorio:

```powershell
.\gradlew.bat test
```

Las pruebas unitarias no requieren contenedores. El test de integración está en `src/test/java/com/vidacruz/triage/integration` y se ejecuta por separado con PostgreSQL y Kafka temporales:

```powershell
.\gradlew.bat integrationTest
```

Con Podman en Windows, ejecuta las pruebas dentro de Gradle/JDK 17 y monta el socket de la máquina:

```powershell
podman machine start
podman run --rm `
  -v "${PWD}:/workspace:Z" `
  -v /run/user/1000/podman/podman.sock:/var/run/docker.sock `
  -w /workspace `
  -e DOCKER_HOST=unix:///var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.containers.internal `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  docker.io/library/gradle:8.10.2-jdk17 bash ./gradlew --no-daemon integrationTest
```

La tarea `integrationTest` usa Testcontainers con PostgreSQL y Kafka reales. No usa H2: el esquema y las consultas dependen de características específicas de PostgreSQL.

## Probar manualmente con Compose

Copia `.env.example` a `.env` si todavía no existe y levanta el servicio con sus dependencias:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
docker compose up --build -d
```

El API queda disponible en `http://localhost:8080`; la readiness se consulta en `/actuator/health/readiness`.
