# VidaCruz Triage

Servicio reactivo para registrar pacientes, clasificarlos con reglas determinísticas y publicar eventos clínicos a Kafka mediante Outbox.

## Requisitos

- JDK 21 o superior.
- Para pruebas de integración: Docker o Podman con una API compatible con Docker.

## Probar

Desde PowerShell, en `vidacruz-triage`:

```powershell
.\gradlew.bat test
```

Las pruebas unitarias no requieren contenedores. El test de integración está en `src/test/java/com/vidacruz/triage/integration` y se ejecuta por separado con PostgreSQL y Kafka temporales:

```powershell
.\gradlew.bat integrationTest
```

Con Podman en Windows, ejecuta las pruebas dentro de Gradle/JDK 21 y monta el socket de la máquina:

```powershell
podman machine start
podman run --rm `
  -v "${PWD}:/workspace:Z" `
  -v /run/user/1000/podman/podman.sock:/var/run/docker.sock `
  -w /workspace `
  -e DOCKER_HOST=unix:///var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.containers.internal `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  docker.io/library/gradle:8.10.2-jdk21 bash ./gradlew --no-daemon integrationTest
```

La tarea `integrationTest` usa Testcontainers con PostgreSQL y Kafka reales. No usa H2: el esquema y las consultas dependen de características específicas de PostgreSQL.

## Probar manualmente con Compose

Desde la raíz `VidaCruz`, copia `.env.example` a `.env` si todavía no existe y levanta ambos servicios con sus dependencias:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
docker compose up --build -d
```

Triaje queda disponible en `http://localhost:8080` y el Asistente en `http://localhost:8081`. La readiness de ambos se consulta en `/actuator/health/readiness`; la vista de urgencias está en `GET http://localhost:8081/asistente/estado-urgencias`.

## Monitoreo local

El Compose de la raíz también levanta Prometheus y Grafana. Prometheus consulta las métricas de Triage y Agent cada 15 segundos mediante `Authorization: Bearer` con las API keys ya configuradas, sin publicar su puerto en el host. Esa forma de autenticación se acepta solo en `/actuator/prometheus`; las rutas de negocio siguen requiriendo `X-API-Key`. Grafana queda disponible en `http://localhost:${GRAFANA_PORT:-3000}`; inicia sesión como `admin` usando `GRAFANA_ADMIN_PASSWORD` del `.env`. El dashboard `VidaCruz - Operaciones` se provisiona automáticamente e incluye disponibilidad, solicitudes HTTP, clasificaciones, escalamientos, latencia del LLM y memoria heap.

Configura un valor no trivial para `GRAFANA_ADMIN_PASSWORD` en `.env` antes de levantar el stack. Las API keys no se guardan en la configuración de monitoreo versionada.

## Autenticación de API

Las rutas de negocio de Triage requieren la cabecera `X-API-Key` con el valor de `TRIAGE_API_KEY`. Configura claves aleatorias distintas en el `.env` usado por Compose: el `.env` de la raíz para levantar ambos servicios o `vidacruz-triage/.env` para su Compose independiente. El servicio falla al arrancar si su clave no está configurada. Las rutas `/actuator/health`, `/actuator/health/**` (incluidas readiness y liveness) permanecen públicas para los healthchecks; las demás rutas requieren autenticación.

Ejemplo:

```powershell
curl.exe -H "X-API-Key: $env:TRIAGE_API_KEY" http://localhost:8080/triage/pacientes
```

## Monitoreo de SLA (RF-03)

El monitor periódico escala los pacientes que siguen en `REGISTRADO` al vencer el SLA guardado en cada registro. Los SLA iniciales por nivel y el intervalo del monitor se pueden ajustar con `TRIAGE_SLA_*_MINUTOS` y `TRIAGE_ESCALATION_POLL_INTERVAL_MS` en `.env`; los cambios de SLA aplican a registros nuevos. El escalamiento actualiza el flag `escalado`, crea auditoría clínica y encola `paciente_escalado` en el outbox para su publicación a Kafka.

## Alertas de escalamiento (RF-06)

El equipo médico recibe alertas mediante un webhook HTTP(S) configurable. El consumidor del servicio Triage usa un grupo Kafka independiente (`vidacruz-triage-alerts`) y procesa `paciente_escalado` desde `triaje.eventos`; los demás eventos se confirman sin enviar alertas. Esta integración mantiene la notificación fuera de la transacción clínica y permite conectar un receptor HTTP existente sin acoplar Triage a un proveedor de correo o mensajería.

Para activarlo, configura en el `.env` de la raíz:

```dotenv
TRIAGE_ALERT_WEBHOOK_ENABLED=true
TRIAGE_ALERT_WEBHOOK_URL=https://receptor-medico.example/alerts
```

La URL puede contener credenciales del receptor; mantenla en variables de entorno y no la agregues al repositorio. Cuando el flag está activo, la aplicación requiere una URL HTTP(S) válida; por defecto está desactivado para permitir el desarrollo local sin un receptor configurado.

El servicio envía un `POST` con `Content-Type: application/json` y la cabecera `Idempotency-Key` igual al `eventId`. El cuerpo contiene `eventId`, `eventType`, `patientId`, `triageLevel`, `escalatedAt`, `slaMinutes` y `correlationId`. Por ejemplo:

```json
{
  "eventId": "c0a80123-4567-489a-9abc-123456789abc",
  "eventType": "paciente_escalado",
  "patientId": "a0a80123-4567-489a-9abc-123456789abc",
  "triageLevel": "CRITICO",
  "escalatedAt": "2026-11-06T14:00:00Z",
  "slaMinutes": 5,
  "correlationId": "req-123"
}
```

Solo una respuesta HTTP `2xx` confirma la entrega; cada petición tiene un timeout de 10 segundos. El offset Kafka se confirma después de la respuesta exitosa; los errores provocan reintentos con backoff y, durante una interrupción del receptor, pueden retrasar otros eventos del mismo topic. La entrega es al menos una vez: un fallo después de que el receptor procese la petición pero antes de confirmar el offset puede repetirla, por lo que el receptor debe deduplicar usando `Idempotency-Key`. La política `earliest` permite recuperar eventos del topic retenidos al iniciar un grupo nuevo.

## Reporte de operación (RF-05)

`GET /triage/reportes/operacion?desde=2026-11-06T00:00:00Z&hasta=2026-11-07T00:00:00Z` devuelve una fila por hora UTC con el total de registros, conteos por nivel y promedio/p95 del tiempo entre registro y primera entrada a consulta. El rango usa `desde` inclusivo y `hasta` exclusivo; los tiempos de espera consideran solo pacientes que entraron a consulta y se expresan en minutos. Los conteos, promedio y p95 se calculan en PostgreSQL.
