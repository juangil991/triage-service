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

## Monitoreo de SLA (RF-03)

El monitor periódico escala los pacientes que siguen en `REGISTRADO` al vencer el SLA guardado en cada registro. Los SLA iniciales por nivel y el intervalo del monitor se pueden ajustar con `TRIAGE_SLA_*_MINUTOS` y `TRIAGE_ESCALATION_POLL_INTERVAL_MS` en `.env`; los cambios de SLA aplican a registros nuevos. El escalamiento actualiza el flag `escalado`, crea auditoría clínica y encola `paciente_escalado` en el outbox para su publicación a Kafka.

## Reporte de operación (RF-05)

`GET /triage/reportes/operacion?desde=2026-11-06T00:00:00Z&hasta=2026-11-07T00:00:00Z` devuelve una fila por hora UTC con el total de registros, conteos por nivel y promedio/p95 del tiempo entre registro y primera entrada a consulta. El rango usa `desde` inclusivo y `hasta` exclusivo; los tiempos de espera consideran solo pacientes que entraron a consulta y se expresan en minutos. Los conteos, promedio y p95 se calculan en PostgreSQL.
