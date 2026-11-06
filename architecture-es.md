# Arquitectura --- Triaje Digital y Asistente Clínico VidaCruz

> **Estado:** Propuesta\
> **Estilo arquitectónico:** Microservicios orientados a eventos +
> Arquitectura Hexagonal + CQRS selectivo\
> **Lenguaje principal:** Java\
> **Plataforma objetivo:** Azure Kubernetes Service (AKS)\
> **Alcance:** Servicios backend, APIs y comunicación asíncrona.

## 1. Propósito y alcance

Este documento describe la arquitectura propuesta para la solución de
triaje digital y asistente clínico de VidaCruz.

La solución está compuesta por dos microservicios Java, desplegables de
forma independiente:

1.  **Servicio de Triaje:** registra pacientes, aplica las reglas
    determinísticas de clasificación, administra los estados clínicos,
    supervisa los SLA, escala pacientes que superan el tiempo permitido,
    mantiene la auditoría clínica y genera reportes operativos.
2.  **Servicio de Asistente Clínico:** responde preguntas mediante
    generación aumentada por recuperación (RAG) sobre el corpus oficial
    de protocolos clínicos y expone una vista del estado de urgencias
    construida a partir de los eventos publicados por Triaje.

El diseño prioriza la trazabilidad clínica, las reglas de negocio
determinísticas, la autonomía de los servicios, la integración confiable
mediante eventos, la ejecución reactiva, el uso seguro de IA y la
entrega dentro del plazo de tres días.

## 2. Resumen de decisiones arquitectónicas

  -----------------------------------------------------------------------
  Aspecto                             Decisión
  ----------------------------------- -----------------------------------
  Descomposición                      Dos microservicios: Triaje y
                                      Asistente Clínico

  Arquitectura interna                Arquitectura Hexagonal (Puertos y
                                      Adaptadores) en cada servicio

  Comunicación entre servicios        Arquitectura orientada a eventos
                                      mediante Apache Kafka

  Publicación confiable               Patrón Transactional Outbox en
                                      Triaje

  Modelo de lectura                   CQRS selectivo para la proyección
                                      de urgencias del Asistente

  Programación reactiva               Spring Boot 3, Spring WebFlux, spring security y R2DBC

  Base de datos operacional           PostgreSQL

  Proyección del Asistente            Redis

  Estrategia RAG                      Recuperación híbrida (vectorial +
                                      textual), Reciprocal Rank Fusion
                                      (RRF), reranking opcional y
                                      validación de citas

  Almacenamiento vectorial            PostgreSQL con pgvector

  Integración con LLM                 Spring AI

  Resiliencia                         Circuit Breaker de Resilience4j y
                                      respuesta alternativa HTTP 503

  Observabilidad                      Actuator, Micrometer, logs
                                      estructurados e identificadores de
                                      correlación, OpenTelemetry 

  Contenedores y despliegue           Docker, Docker Compose y Kubernetes
                                      sobre Azure AKS

  Seguridad de API                    Autenticación JWT 
  -----------------------------------------------------------------------

## 3. Contexto del sistema y arquitectura de alto nivel

``` mermaid
flowchart LR
    Client[Personal clínico / cliente API]
    Triage[Servicio de Triaje]
    Assistant[Servicio de Asistente Clínico]
    Kafka[(Apache Kafka<br/>triaje.eventos)]
    TriageDB[(PostgreSQL<br/>Pacientes + auditoría + outbox)]
    Redis[(Redis<br/>Proyección del estado de urgencias)]
    VectorDB[(PostgreSQL + pgvector<br/>Corpus oficial de protocolos)]
    LLM[Proveedor externo de LLM]
    Notify[Endpoint de notificación al equipo médico]

    Client -->|REST| Triage
    Client -->|REST| Assistant
    Triage <-->|R2DBC| TriageDB
    Triage -->|Publicación desde Outbox| Kafka
    Kafka -->|Consumidor reactivo| Assistant
    Assistant <-->|Acceso reactivo| Redis
    Assistant <-->|Recuperación híbrida| VectorDB
    Assistant -->|Solicitud protegida| LLM
    Kafka -->|Evento de escalamiento / consumidor| Notify
```

### 3.1 Responsabilidades y propiedad de los datos

**El Servicio de Triaje es responsable de:** - Datos de registro del
paciente y signos vitales. - Clasificación de triaje y cálculo de SLA. -
Ciclo de vida del paciente y transiciones válidas. - Estado de
escalamiento e idempotencia. - Registros de auditoría clínica. -
Reportes operativos. - Publicación de eventos de dominio.

**El Servicio de Asistente Clínico es responsable de:** - Ingesta de
protocolos, fragmentación, metadatos y embeddings. - Recuperación RAG y
generación de respuestas. - Validación de citas y política de
abstención. - Proyección de lectura de pacientes activos por nivel de
triaje.

El Asistente no debe invocar la API REST de Triaje para construir la
vista de urgencias. Debe reconstruirla a partir de `triaje.eventos`, tal
como exige la prueba.

## 4. Arquitectura interna de los microservicios

Ambos servicios utilizan Arquitectura Hexagonal. Las capas de dominio y
aplicación no dependen de Spring, Kafka, PostgreSQL, Redis ni del
proveedor del LLM.

``` mermaid
flowchart TB
    subgraph Inbound[Adaptadores de entrada]
      REST[Controladores REST]
      SCHED[Programador de tareas]
      CONSUMER[Consumidores Kafka]
    end

    subgraph Application[Aplicación]
      USECASE[Casos de uso / servicios de aplicación]
      PORTIN[Puertos de entrada]
      PORTOUT[Puertos de salida]
    end

    subgraph Domain[Dominio]
      MODEL[Modelos de dominio]
      RULES[Reglas y políticas de negocio]
    end

    subgraph Outbound[Adaptadores de salida]
      DB[Persistencia - R2DBC]
      PRODUCER[Productor Kafka / Outbox relay]
      AI[Adaptador RAG y LLM]
      CACHE[Adaptador Redis]
    end

    REST --> PORTIN
    SCHED --> PORTIN
    CONSUMER --> PORTIN
    PORTIN --> USECASE
    USECASE --> MODEL
    USECASE --> RULES
    USECASE --> PORTOUT
    PORTOUT --> DB
    PORTOUT --> PRODUCER
    PORTOUT --> AI
    PORTOUT --> CACHE
```

### 4.1 Estructura de paquetes actual del Servicio de Triaje

``` text
com.vidacruz.triage
├── domain
│   ├── model
│   ├── service
│   └── exception
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
└── infrastructure
    ├── adapter
    │   ├── in
    │   │   └── rest
    │   └── out
    │       ├── persistence
    │       └── kafka
    └── config
```

Los contratos de entrada de `application/port/in` definen los casos de
uso disponibles para los adaptadores. Sus implementaciones en
`application/service` coordinan dominio y puertos de salida
(`application/port/out`), sin depender de Spring ni de los adaptadores.
Los adaptadores de entrada y salida implementan los límites de
infraestructura y se conectan mediante configuración. Esta estructura
describe el Servicio de Triaje implementado; los componentes del
Asistente Clínico y los adaptadores futuros de Triaje permanecen como
parte de la propuesta y no se consideran implementados.

## 5. Servicio de Triaje

### 5.1 Responsabilidades y API

  ------------------------------------------------------------------------------------------
  Endpoint                                               Responsabilidad
  ------------------------------------------------------ -----------------------------------
  `POST /triage/pacientes`                               Validar datos, clasificar al
                                                         paciente, calcular SLA, persistir y
                                                         publicar `paciente_registrado`

  `PATCH /triage/pacientes/{id}/estado`                  Validar transición, persistir el
                                                         cambio, registrar auditoría y
                                                         publicar el evento correspondiente

  `GET /triage/pacientes/{id}`                           Consultar datos, estado, indicador
                                                         de escalamiento y tiempo en el
                                                         estado actual

  `GET /triage/reportes/operacion?desde=...&hasta=...`   Generar agregados horarios, tiempo
                                                         promedio de espera y percentil 95
  ------------------------------------------------------------------------------------------

### 5.2 Reglas de dominio

La clasificación se implementa como lógica determinística en Java,
independiente del LLM y de la infraestructura.

Las reglas se evalúan en el orden definido por el enunciado. Se aplica
el primer nivel cuya condición se cumpla:

1.  `CRITICO`
2.  `URGENTE`
3.  `URGENTE_2`
4.  `NO_URGENTE`
5.  `NO_URGENTE_2`: únicamente cuando el paciente cumple las condiciones
    del nivel 4 y el motivo de consulta pertenece a la lista de motivos
    administrativos.

El SLA se determina según el nivel de triaje:

  Nivel                    SLA
  -------------- -------------
  CRITICO            5 minutos
  URGENTE           10 minutos
  URGENTE_2         30 minutos
  NO_URGENTE        60 minutos
  NO_URGENTE_2     120 minutos

También deben aplicarse los rangos de entrada y la restricción de que la
presión diastólica sea menor que la sistólica, según el enunciado.

### 5.3 Máquina de estados del paciente

``` mermaid
stateDiagram-v2
    [*] --> REGISTRADO
    REGISTRADO --> EN_CONSULTA
    REGISTRADO --> ALTA
    EN_CONSULTA --> ALTA
    ALTA --> [*]
```

Cualquier transición no contemplada en esta máquina debe responder HTTP
`409 Conflict`. Un paciente en estado `EN_CONSULTA` o `ALTA` nunca debe
ser escalado.

### 5.4 Supervisión del SLA y escalamiento idempotente

Un proceso programado y configurable consulta periódicamente los
pacientes que: - Se encuentren en estado `REGISTRADO`. - Hayan superado
el tiempo límite de su SLA. - Todavía no hayan sido escalados.

El escalamiento debe ser atómico e idempotente. Se recomienda una
actualización condicional en base de datos ---o una estrategia
equivalente de bloqueo/optimismo--- para asegurar que solo una ejecución
concurrente pueda modificar el indicador de escalamiento.

SQL ilustrativo:

``` sql
UPDATE triage_patient
SET escalated = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE id = :patientId
  AND status = 'REGISTRADO'
  AND escalated = FALSE
  AND registered_at + (sla_minutes * INTERVAL '1 minute')
      <= CURRENT_TIMESTAMP
RETURNING id;
```

Solo si la actualización devuelve un registro, la aplicación debe
insertar la auditoría del escalamiento y crear el evento
`paciente_escalado` en Outbox, dentro de la misma transacción.

El intervalo del programador y los valores de SLA deben ser
configurables para facilitar las pruebas automatizadas.

### 5.5 Auditoría clínica

Cada registro, transición de estado y escalamiento debe quedar
almacenado en una tabla de auditoría de tipo append-only desde la
perspectiva de la API.

Atributos mínimos: - Fecha y hora. - Identificador del paciente. -
Estado o indicador anterior. - Estado o indicador nuevo. - Tipo de
evento.

La aplicación no expone operaciones para eliminar auditorías. Cuando sea
posible, los permisos de base de datos también deben impedir que el
usuario de aplicación actualice o elimine estos registros.

### 5.6 Reportes operativos

El reporte operativo se calcula en PostgreSQL, sin cargar todos los
pacientes en memoria de la aplicación.

Se utilizarán agregaciones y funciones de ventana SQL ---por ejemplo,
`FILTER`, `ROW_NUMBER`, `PERCENTILE_CONT` o expresiones equivalentes
compatibles--- para calcular por hora: - Total de pacientes
registrados. - Cantidad por nivel de triaje. - Tiempo promedio entre
registro e inicio de consulta. - Percentil 95 (p95) de ese tiempo.

La consulta definitiva debe validarse contra la versión de PostgreSQL
utilizada en el proyecto.

## 6. Integración orientada a eventos

### 6.1 Tópico Kafka y tipos de evento

Tópico: `triaje.eventos`

Eventos iniciales:

  -----------------------------------------------------------------------
  Evento                              Momento de publicación
  ----------------------------------- -----------------------------------
  `paciente_registrado`               El paciente se registra y clasifica
                                      correctamente

  `paciente_en_consulta`              El paciente pasa a consulta

  `paciente_alta`                     El paciente recibe el alta

  `paciente_escalado`                 El paciente supera el SLA y se
                                      escala
  -----------------------------------------------------------------------

Los eventos representan hechos que ya ocurrieron; no son comandos
dirigidos al otro servicio.

### 6.2 Estructura del evento

Se propone JSON para simplificar la implementación inicial. Se incluye
una versión explícita del contrato para permitir su evolución.

``` json
{
  "eventId": "uuid",
  "eventType": "paciente_registrado",
  "eventVersion": 1,
  "occurredAt": "2026-11-05T10:15:30Z",
  "correlationId": "uuid",
  "patientId": "uuid",
  "payload": {
    "triageLevel": "URGENTE",
    "status": "REGISTRADO",
    "registeredAt": "2026-11-05T10:15:30Z",
    "slaMinutes": 10
  }
}
```

No se deben publicar datos personales o clínicos innecesarios. Para
construir los agregados, el Asistente solo necesita el identificador del
paciente, el nivel de triaje, el estado y los metadatos requeridos para
aplicar cada cambio.

### 6.3 Patrón Transactional Outbox

El Servicio de Triaje no debe confirmar una modificación del paciente en
PostgreSQL y después intentar publicar en Kafka como dos operaciones
independientes.

Cada operación de negocio persiste lo siguiente en una única transacción
PostgreSQL: 1. Modificación del paciente. 2. Registro de auditoría,
cuando aplique. 3. Evento en la tabla Outbox.

Un proceso independiente publica los registros pendientes de Outbox en
Kafka y los marca como publicados después de recibir confirmación del
broker.

El relay puede publicar un evento más de una vez si Kafka lo aceptó,
pero el proceso falló antes de registrar la publicación exitosa. Por
tanto: - Cada evento tiene un `eventId` estable. - Los consumidores son
idempotentes. - El Asistente registra los identificadores procesados o
utiliza una estrategia equivalente. - Se configuran reintentos y un
tópico de mensajes fallidos (DLQ) para mensajes que no puedan
procesarse.

Este diseño ofrece entrega al menos una vez (*at-least-once*) con
procesamiento idempotente; no constituye una garantía de ejecución
exactamente una vez de extremo a extremo.

### 6.4 Consumidor y proyección del Asistente

El Asistente Clínico consume el tópico y mantiene en Redis una
proyección de pacientes activos agrupados por nivel de triaje.

Comportamiento de la proyección: - `paciente_registrado`: agrega el
paciente al conteo activo de su nivel. - `paciente_en_consulta`:
mantiene al paciente activo, pues todavía no está en estado `ALTA`. -
`paciente_alta`: retira al paciente del conteo activo. -
`paciente_escalado`: actualiza los datos de escalamiento si es
necesario, sin incrementar nuevamente el conteo de pacientes activos.

La proyección debe ser idempotente. Un evento duplicado no puede
contabilizar dos veces al mismo paciente. Se recomienda almacenar el
estado por paciente en Redis y derivar los conteos de ese estado, o
realizar actualizaciones atómicas junto con deduplicación de eventos.

Como la proyección se actualiza de forma asíncrona, el endpoint ofrece
consistencia eventual. Deben documentarse este comportamiento y las
métricas de frescura/retraso de la proyección.

### 6.5 Alertas de escalamiento médico (RF-06)

Decisión propuesta: implementar un adaptador de notificaciones dentro
del despliegue de Triaje, activado por el evento `paciente_escalado`
mediante un grupo de consumidores Kafka independiente. El adaptador
invoca un webhook HTTP configurable del sistema de notificaciones del
equipo médico.

Justificación: - No requiere incorporar un tercer microservicio. -
Separa el envío de alertas de la transacción crítica de escalamiento. -
Permite reintentos y un tópico de mensajes fallidos ante errores
persistentes. - Una indisponibilidad del sistema de notificaciones no
revierte el escalamiento ni bloquea las API de Triaje.

La URL del webhook y sus credenciales deben inyectarse mediante
variables de entorno o un gestor de secretos. El destino real del
webhook es una suposición de integración que debe documentarse, ya que
el enunciado no especifica un proveedor externo.

## 7. Servicio de Asistente Clínico y RAG

### 7.1 Responsabilidades y API

  -----------------------------------------------------------------------
  Endpoint                            Responsabilidad
  ----------------------------------- -----------------------------------
  `POST /asistente/consulta`          Recuperar evidencia de protocolos,
                                      generar una respuesta fundamentada
                                      y devolver fuentes validadas

  `GET /asistente/estado-urgencias`   Consultar el conteo de pacientes
                                      activos por nivel de triaje desde
                                      la proyección local
  -----------------------------------------------------------------------

El Asistente no es propietario del flujo clínico del paciente y no debe
invocar la API de Triaje para construir el tablero de urgencias.

### 7.2 Estrategia RAG: recuperación híbrida

Se propone un flujo de recuperación híbrida que combina:

-   **Búsqueda vectorial:** similitud semántica sobre embeddings
    almacenados en pgvector.
-   **Búsqueda por palabras clave:** PostgreSQL Full Text Search para
    términos clínicos exactos, identificadores de protocolos,
    abreviaturas y códigos.
-   **Reciprocal Rank Fusion (RRF):** combinación de los rankings de
    ambas búsquedas.
-   **Reranking:** reordenamiento opcional de los mejores candidatos
    mediante un modelo especializado, si se dispone de uno adecuado.
-   **Política de relevancia:** determina si la evidencia recuperada es
    suficiente antes de invocar el LLM.
-   **Validación de citas:** comprueba todas las referencias generadas
    contra los registros reales del corpus recuperado.

Parámetros iniciales para experimentación (no son umbrales clínicos y
deben validarse con un conjunto de preguntas de prueba): - Recuperación
vectorial: top 20. - Recuperación textual: top 20. - Candidatos después
de RRF: top 10. - Contexto después del reranking: 3--5 fragmentos. -
Tamaño de fragmento: aproximadamente 400--700 tokens, con solapamiento
de 50--100 tokens únicamente cuando sea necesario dividir una sección.

### 7.3 Ingesta de protocolos

El proceso de ingesta debe: 1. Cargar únicamente documentos oficiales y
aprobados. 2. Extraer el texto conservando título, identificador del
protocolo, sección, página y versión. 3. Dividir el contenido por
secciones semánticas, evitando cortes arbitrarios. 4. Generar
embeddings. 5. Almacenar fragmentos, vectores y metadatos. 6. Controlar
el estado y la vigencia de los documentos; los protocolos inactivos o
reemplazados no deben recuperarse como evidencia vigente.

Metadatos sugeridos para cada fragmento:

``` json
{
  "chunkId": "uuid",
  "protocolId": "PROTO-001",
  "title": "Protocolo de dolor torácico",
  "section": "Criterios de alarma",
  "version": "2.1",
  "status": "ACTIVE",
  "page": 12,
  "source": "Corpus oficial de protocolos"
}
```

Los valores de versión y página son ilustrativos; los valores reales
deben extraerse de los documentos fuente.

### 7.4 Flujo de recuperación y generación

``` mermaid
sequenceDiagram
    participant U as Cliente API
    participant A as API del Asistente
    participant R as Adaptador de recuperación
    participant P as PostgreSQL / pgvector
    participant L as Proveedor LLM
    participant V as Validador de citas

    U->>A: POST /asistente/consulta
    A->>R: recuperar(pregunta)
    par Búsqueda vectorial
        R->>P: similitud semántica
    and Búsqueda textual
        R->>P: búsqueda de texto completo
    end
    P-->>R: fragmentos candidatos
    R->>R: RRF + reranking opcional
    R-->>A: evidencia ordenada
    alt Sin evidencia relevante
        A-->>U: NO_PROTOCOL_FOUND (sin generación clínica)
    else Evidencia relevante
        A->>L: pregunta + evidencia + restricciones
        L-->>A: respuesta + citas propuestas
        A->>V: validar citas contra el corpus
        alt Citas válidas
            V-->>A: válidas
            A-->>U: respuesta + fuentes
        else Citas inválidas
            V-->>A: inválidas
            A-->>U: abstención segura / error controlado
        end
    end
```

### 7.5 Política para evitar alucinaciones

La aplicación impone tres controles:

1.  **Antes de generar:** si la recuperación no supera la política de
    relevancia configurada, no se invoca el LLM. Se devuelve el mensaje
    explícito de ausencia de protocolo requerido por la prueba.
2.  **Durante la generación:** se instruye al modelo para utilizar
    exclusivamente la evidencia suministrada y citar identificadores y
    secciones de los protocolos. El prompt es un control, no una
    garantía de seguridad.
3.  **Después de generar:** se valida que cada cita exista en la
    evidencia recuperada y corresponda a un documento oficial y vigente.
    Si las citas faltan o son inválidas, no se devuelve contenido
    clínico sin respaldo.

Ejemplo de respuesta sin coincidencias:

``` json
{
  "respuesta": "No existe un protocolo disponible en el corpus oficial para responder esta pregunta.",
  "fuentes": []
}
```

Ejemplo de respuesta fundamentada:

``` json
{
  "respuesta": "Según el protocolo recuperado, ...",
  "fuentes": [
    {
      "protocolo": "PROTO-001",
      "seccion": "Criterios de alarma"
    }
  ]
}
```

No se debe asumir que el diseño del prompt, por sí solo, evita las
alucinaciones. La validación de citas y el control previo a la
generación son responsabilidades obligatorias de la aplicación.

### 7.6 Almacén vectorial y ejecución reactiva

El almacén vectorial propuesto es PostgreSQL con pgvector. Redis se
reserva para la proyección del estado de urgencias.

La implementación debe respetar el requisito de programación reactiva: -
Utilizar R2DBC para el acceso a bases de datos desde la aplicación. -
Implementar la recuperación vectorial mediante un adaptador compatible
con el modelo reactivo (por ejemplo, SQL parametrizado con R2DBC y
operadores de pgvector). - Si una integración seleccionada de Spring AI
expone únicamente una API bloqueante, aislarla en un scheduler acotado o
reemplazarla por un adaptador reactivo. - No ejecutar JDBC bloqueante ni
operaciones de red síncronas en los hilos del event loop de WebFlux. -
Verificar el comportamiento real de las integraciones de Spring AI
elegidas; utilizar WebFlux no convierte automáticamente todas las
llamadas externas en operaciones no bloqueantes.

### 7.7 Resiliencia del LLM

Se aplicará un Circuit Breaker de Resilience4j a las llamadas al
proveedor LLM, con: - Umbrales configurables de tasa de fallos y ventana
deslizante. - Tiempo de espera en estado abierto y cantidad de llamadas
permitidas en estado semiabierto. - Tiempos límite y reintentos acotados
únicamente para fallos transitorios; se deben evitar tormentas de
reintentos. - Respuesta alternativa explícita HTTP
`503 Service Unavailable` cuando el proveedor no esté disponible. -
Métricas de latencia, fallos, estado del circuito y cantidad de
respuestas alternativas.

El Servicio de Triaje no tiene dependencia de ejecución respecto al
Asistente ni al LLM.

## 8. Arquitectura de datos

### 8.1 PostgreSQL --- Triaje

Tablas lógicas sugeridas: - `triage_patient`: datos de registro, signos
vitales, nivel de triaje, SLA, estado, indicador de escalamiento y
marcas de tiempo. - `clinical_audit`: registros append-only de
auditoría. - `outbox_event`: identificador, tipo, versión, payload,
fecha de creación, estado de publicación, intentos y último error.

Se recomiendan identificadores UUID y restricciones de base de datos
cuando sean pertinentes. El modelo de persistencia debe mantenerse
separado del modelo de dominio.

### 8.2 PostgreSQL + pgvector --- Corpus de protocolos

Tablas lógicas sugeridas: - `protocol_document`: identificador, título,
versión, estado, fuente y fechas de vigencia. - `protocol_chunk`: texto
del fragmento, sección, página, referencia al documento y vector
embedding.

Se deben crear índices apropiados para las búsquedas vectoriales y
textuales. Los filtros por metadatos deben excluir documentos inactivos
o reemplazados.

### 8.3 Redis --- Proyección del Asistente

Claves y estructuras sugeridas: - Proyección por paciente:
identificador, nivel de triaje, estado actual, indicador de escalamiento
y metadatos del último evento procesado. - Deduplicación de eventos:
`eventId` procesados, con una política de retención apropiada. - Los
contadores agregados pueden almacenarse en caché, pero deben poder
reconstruirse desde la proyección por paciente o desde el historial de
eventos reproducible.

Debe definirse una estrategia de recuperación/reconstrucción ante
pérdida de Redis. La proyección es información derivada, no la fuente
autoritativa del estado del paciente.

## 9. Estándares de programación reactiva

El proyecto debe demostrar ejecución realmente no bloqueante, no
solamente controladores con firmas reactivas.

Prácticas requeridas: - Usar `Mono` para resultados asíncronos de cero o
un elemento y `Flux` para flujos de elementos. - Utilizar Spring WebFlux
para las API HTTP. - Utilizar R2DBC para PostgreSQL. - Usar una
integración reactiva de Kafka (por ejemplo, Reactor Kafka) o un
adaptador claramente aislado que no bloquee los hilos del event loop. -
Utilizar acceso reactivo a Redis. - Preferir clientes HTTP no
bloqueantes como `WebClient`. - Propagar cancelación, errores y
backpressure de forma deliberada. - Limitar la concurrencia de `flatMap`
y de llamadas externas. - Configurar tiempos límite y políticas de
reintento controladas.

Evitar: - `block()` y `blockFirst()` en flujos de solicitudes o event
loop. - Llamadas JDBC/JPA en hilos del event loop. - `Thread.sleep()`. -
Clientes HTTP síncronos en rutas reactivas. - Concurrencia no acotada. -
Ocultar errores con publishers vacíos cuando ello oculte fallos de
negocio.

Si un SDK de terceros es bloqueante y no puede sustituirse, debe
aislarse en un scheduler bounded elastic y documentarse esa limitación.

## 10. Seguridad

-   Proteger las API con autenticación JWT Bearer; una API Key es una
    alternativa más sencilla si se justifica.
-   Validar los cuerpos de las solicitudes y aplicar los rangos
    especificados.
-   Mantener las claves del LLM, credenciales de base de datos, Kafka y
    webhook fuera del control de versiones.
-   Utilizar variables de entorno en local y Kubernetes Secrets o
    integración con Azure Key Vault en despliegue.
-   Aplicar mínimo privilegio a usuarios de base de datos y ACL de Kafka
    cuando estén disponibles.
-   Evitar publicar nombres, datos de identificación u otra información
    clínica innecesaria en Kafka.
-   Sanitizar los logs y nunca registrar secretos ni payloads sensibles
    completos.
-   Utilizar TLS para comunicación externa y en producción.

## 11. Observabilidad y operación

### 11.1 Salud y disponibilidad

Exponer endpoints de salud de Spring Boot Actuator y configurar sondas
de liveness y readiness en Kubernetes.

La readiness debe reflejar si el servicio puede cumplir sus
responsabilidades. La disponibilidad de Triaje no debe depender del LLM
ni del Asistente. Se debe definir cuidadosamente si una indisponibilidad
temporal de Kafka afecta la readiness de Triaje, considerando que Outbox
permite continuar registrando transacciones mientras se reintenta la
publicación.

### 11.2 Métricas

Como mínimo: - Registros y clasificaciones de pacientes por nivel de
triaje. - Cantidad de escalamientos y fallos al procesarlos. - Duración
de evaluación de SLA y cantidad de pacientes vencidos. - Cantidad de
eventos pendientes en Outbox, antigüedad del más antiguo y fallos de
publicación. - Retraso del consumidor Kafka y fallos de procesamiento. -
Frescura/retraso de la proyección del Asistente. - Latencia del LLM,
tasa de errores, estado del Circuit Breaker y cantidad de respuestas
alternativas. - Cantidad de resultados recuperados por RAG, tasa de
ausencia de protocolo y fallos de validación de citas.

### 11.3 Logs y trazabilidad distribuida

-   Emitir logs estructurados en JSON.
-   Propagar `correlationId` a través de solicitudes HTTP, registros
    Outbox y eventos Kafka.
-   Incluir identificadores de evento y paciente solo cuando sea
    apropiado y seguro.
-   Incorporar trazabilidad distribuida con OpenTelemetry como mejora,
    no como requisito previo para la funcionalidad principal.

## 12. Arquitectura de despliegue

### 12.1 Desarrollo local

Docker Compose debe iniciar el entorno local completo con un único
comando: - Servicio de Triaje. - Servicio de Asistente Clínico. -
PostgreSQL (esquemas de Triaje y de protocolos/pgvector, ya sea en bases
separadas o esquemas aislados). - Kafka y sus dependencias locales. -
Redis.

Configurar health checks y verificación de disponibilidad de
dependencias; no depender únicamente del orden de inicio de
contenedores.

### 12.2 Despliegue en Azure

Plataforma objetivo: Azure Kubernetes Service (AKS).

Recursos Kubernetes: - Deployment y Service para Triaje. - Deployment y
Service para Asistente Clínico. - ConfigMaps para configuración no
sensible. - Secrets (o integración con Azure Key Vault) para
credenciales. - Sondas liveness y readiness. - Solicitudes y límites de
recursos. - Horizontal Pod Autoscaler únicamente cuando la carga
observada lo justifique. - Servicios administrados o persistentes para
PostgreSQL, Kafka y Redis, según el entorno y presupuesto disponibles.

Las imágenes deben construirse de forma reproducible y etiquetarse con
identificadores inmutables de commit/versión. CI debe ejecutar
compilación, pruebas unitarias, pruebas de integración y construcción de
imágenes antes del despliegue.

## 13. Estrategia de pruebas

### Pruebas unitarias

-   Clasificación de triaje para E1--E6.
-   Validaciones de entrada e invariantes de dominio.
-   Transiciones válidas e inválidas de estado.
-   Cálculo de fechas límite de SLA.
-   Política de elegibilidad e idempotencia del escalamiento.
-   Política de relevancia/abstención de RAG.
-   Validación de citas.
-   Transiciones de estado de la proyección a partir de eventos.

### Pruebas de integración

-   Persistencia R2DBC y comportamiento transaccional.
-   Creación de eventos Outbox y funcionamiento del relay.
-   Publicación y consumo de eventos Kafka.
-   Manejo de eventos duplicados e idempotencia de la proyección.
-   Proyección Redis y recuperación/reconstrucción.
-   Recuperación RAG contra el corpus de protocolos de prueba.
-   Adaptador LLM con proveedor simulado.
-   Comportamiento alternativo de Resilience4j.
-   Reporte SQL operacional, incluyendo promedio y p95.

Utilizar Testcontainers para PostgreSQL, Kafka y Redis cuando sea
viable. El LLM debe poder simularse; las pruebas automatizadas no deben
depender de un proveedor externo real.

## 14. Registros de decisiones arquitectónicas (ADR)

Se requieren al menos dos ADR. Se recomiendan los siguientes:

  -----------------------------------------------------------------------
  ADR                                 Decisión
  ----------------------------------- -----------------------------------
  ADR-001                             Dos microservicios, cada uno con
                                      Arquitectura Hexagonal, integrados
                                      mediante eventos Kafka

  ADR-002                             Transactional Outbox para publicar
                                      eventos de Triaje de forma
                                      confiable

  ADR-003                             RAG híbrido con pgvector,
                                      PostgreSQL Full Text Search, RRF y
                                      validación de citas

  ADR-004                             Proyección Redis para pacientes
                                      activos en urgencias (CQRS
                                      selectivo)

  ADR-005                             Adaptador de notificaciones webhook
                                      para alertas de escalamiento

  ADR-006                             Autenticación JWT y política de
                                      fallback con Resilience4j
  -----------------------------------------------------------------------

Cada ADR debe incluir contexto, decisión, alternativas consideradas,
consecuencias y compromisos.

## 15. Alternativas consideradas y no seleccionadas

-   **Event Sourcing completo:** no se selecciona. La prueba requiere
    auditoría y publicación de eventos, pero no exige reconstruir el
    estado autoritativo del paciente desde un almacén de eventos. Una
    tabla de auditoría más Outbox es más sencilla.
-   **CQRS completo:** no se selecciona. Solo la vista de urgencias del
    Asistente se beneficia de una proyección de lectura separada. El
    modelo de escritura y reportes de Triaje puede permanecer en
    PostgreSQL.
-   **Saga:** no se selecciona para el flujo principal del paciente
    porque no existe una transacción de negocio distribuida entre
    servicios que requiera acciones compensatorias.
-   **Microservicios adicionales:** no se seleccionan debido al plazo de
    tres días y a los contextos delimitados identificados. Las
    notificaciones se aíslan mediante un adaptador y un grupo de
    consumidores, sin incorporar un tercer servicio.
-   **RAG agéntico, orquestación multiagente o GraphRAG:** no se
    seleccionan. El corpus y las consultas esperadas se resuelven mejor
    con un flujo de recuperación controlado, abstención explícita y
    citas verificables.

## 16. Compromisos y riesgos arquitectónicos

  -----------------------------------------------------------------------
  Beneficio               Costo o riesgo          Mitigación
  ----------------------- ----------------------- -----------------------
  Autonomía mediante      Consistencia eventual   Exponer frescura y
  Kafka                   en la proyección del    retraso; permitir
                          Asistente               reconstrucción

  Confiabilidad de Outbox Complejidad del relay y Identificadores
                          entregas duplicadas     estables e idempotencia

  Stack reactivo          Mayor complejidad de    Adaptadores no
                          depuración e            bloqueantes y pruebas
                          integración con         de carga
                          terceros                

  RAG híbrido             Más componentes de      Conjunto fijo de
                          recuperación que        evaluación y medición
                          ajustar                 de calidad

  Asistencia mediante LLM Indisponibilidad del    Circuit Breaker,
                          proveedor y riesgo de   control de
                          generación sin respaldo recuperación,
                                                  validación de citas y
                                                  fallback 503

  Proyección Redis        Pérdida potencial de    Reconstrucción desde
                          datos derivados         eventos reproducibles o
                                                  historial autoritativo
  -----------------------------------------------------------------------

## 17. Secuencia de implementación

### Día 1 --- Base del Servicio de Triaje

-   Crear la estructura de servicios y límites hexagonales.
-   Implementar validación, clasificación, SLA y máquina de estados.
-   Implementar persistencia R2DBC, auditoría y Outbox.
-   Agregar pruebas unitarias para E1--E6 y transiciones de estado.

### Día 2 --- Eventos y Asistente

-   Implementar relay Outbox y contratos Kafka.
-   Implementar programador de SLA y escalamiento idempotente.
-   Implementar consumidor de eventos y proyección Redis del Asistente.
-   Implementar ingesta de protocolos, recuperación híbrida y política
    de citas/abstención.
-   Agregar Circuit Breaker del LLM y pruebas con proveedor simulado.
-   Implementar la integración de alertas webhook seleccionada.

### Día 3 --- Calidad y entrega

-   Implementar el reporte SQL operacional avanzado.
-   Completar pruebas de integración y Docker Compose.
-   Agregar Actuator, métricas, logs estructurados y seguridad.
-   Agregar manifiestos Kubernetes para AKS.
-   Escribir README y ADR; validar el sistema completo desde un entorno
    limpio.

## 18. Suposiciones y detalles pendientes de implementación

1.  El corpus oficial de protocolos suministrado con la prueba es la
    única fuente clínica autorizada para la primera entrega.
2.  La respuesta de ausencia de protocolo no debe incluir orientación
    clínica adicional.
3.  La proyección del Asistente tiene consistencia eventual porque se
    actualiza asíncronamente desde Kafka.
4.  Los eventos JSON con identificadores y versiones explícitas son
    suficientes para la primera entrega; se puede evaluar un registro de
    esquemas o Avro si el contrato crece.
5.  El webhook de notificaciones es un límite de integración. Su
    destino, autenticación y payload deben configurarse para el entorno
    objetivo.
6.  El proveedor concreto de LLM y embeddings puede seleccionarse
    mediante configuración; las credenciales se inyectan durante la
    ejecución.
7.  Se debe verificar si las integraciones Spring AI seleccionadas son
    bloqueantes. Cualquier operación bloqueante debe aislarse de los
    hilos del event loop de WebFlux.
8.  Los umbrales y reglas clínicas deben implementarse exactamente como
    los especifica el enunciado y nunca deben inferirse ni modificarse
    mediante el LLM.

## 19. Trazabilidad de requisitos

  -----------------------------------------------------------------------
  Requisito de la prueba              Elemento arquitectónico
  ----------------------------------- -----------------------------------
  RF-01 Registro y clasificación      Adaptador REST de Triaje, política
                                      de clasificación de dominio,
                                      PostgreSQL y Outbox

  RF-02 Transiciones de estado        Máquina de estados, tabla de
                                      auditoría y eventos de dominio

  RF-03 Escalamiento SLA              Programador reactivo, actualización
                                      condicional atómica, auditoría y
                                      Outbox

  RF-04A RAG clínico                  Flujo de recuperación del
                                      Asistente, pgvector, búsqueda
                                      textual, Spring AI y validación de
                                      citas

  RF-04B Estado de urgencias          Consumidor Kafka y proyección
                                      Redis; sin llamada a la API de
                                      Triaje

  RF-05 Reporte operacional           SQL avanzado y funciones de ventana
                                      en PostgreSQL

  RF-06 Alertas de escalamiento       Grupo de consumidores Kafka
                                      dedicado y adaptador de
                                      notificaciones webhook

  RN-01 a RN-05                       Políticas de dominio, SLA,
                                      idempotencia, auditoría append-only
                                      y controles anti-alucinación

  Stack reactivo                      WebFlux, Reactor, R2DBC y
                                      adaptadores no bloqueantes

  Resiliencia                         Circuit Breaker Resilience4j y
                                      fallback explícito 503

  Observabilidad                      Actuator, Micrometer, logs
                                      estructurados y correlación

  Seguridad                           JWT/API Key, validación y secretos
                                      externalizados

  Ejecución local                     Docker Compose

  Despliegue cloud                    Manifiestos Kubernetes orientados a
                                      AKS

  ADR y documentación                 Conjunto de ADR y este documento de
                                      arquitectura
  -----------------------------------------------------------------------

------------------------------------------------------------------------

**Resumen arquitectónico:** dos microservicios Java con Arquitectura
Hexagonal se comunican mediante eventos Kafka. Triaje es propietario del
flujo autoritativo de pacientes y publica eventos de forma confiable
mediante Transactional Outbox. El Asistente consume esos eventos para
mantener una proyección Redis y utiliza RAG híbrido sobre protocolos
oficiales, con control de relevancia, validación de citas y abstención
segura. Spring WebFlux, Reactor y R2DBC conforman la base reactiva;
Resilience4j aísla los fallos del LLM; Docker y Kubernetes sobre Azure
permiten el despliegue.
