# MiniNet — Sistema Distribuido de Comunicación en Red

**Proyecto IB · Introducción a Redes · Carrera de Software · UTA 3B**

---

## ¿Qué es MiniNet?

MiniNet es una aplicación distribuida real implementada en Java que demuestra los principios
fundamentales de comunicación en red: arquitectura cliente-servidor, protocolos de aplicación,
modalidades de comunicación y la relación de todo esto con los modelos OSI y TCP/IP.

**No simula capas de red.** Usa la infraestructura real del sistema operativo y la pila TCP/IP
para comunicar procesos independientes sobre una red.

---

## Arquitectura del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                        RED TCP/IP                           │
│                                                             │
│   ┌──────────┐     ┌──────────────────┐     ┌──────────┐   │
│   │ Cliente A│────▶│                  │◀────│ Cliente B│   │
│   └──────────┘     │   SERVIDOR       │     └──────────┘   │
│                    │  (Server.java)   │                     │
│   ┌──────────┐────▶│  Puerto 9090     │◀────┌──────────┐   │
│   │ Cliente C│     │                  │     │ Cliente D│   │
│   └──────────┘     └──────────────────┘     └──────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### El Servidor (`Server.java`)
- Proceso independiente; escucha conexiones TCP en el puerto **9090**
- Un **hilo (Thread)** por cliente conectado (`ClientHandler`)
- Mantiene dos estructuras en memoria:
  - `clients`: mapa `clientId → PrintWriter` (clientes activos)
  - `groups`: mapa `groupId → Set<clientId>` (grupos definidos)
- **Funciones:** registrar clientes, reenviar mensajes, gestionar grupos, detectar desconexiones, escribir log

### Los Clientes (`Client.java`)
- Cada cliente = proceso independiente que conecta al servidor vía socket TCP
- Dos hilos internos: uno lee mensajes del servidor, el otro lee la consola del usuario
- **No se comunican directamente entre sí:** toda comunicación pasa por el servidor (arquitectura estrella)
- Dirección del servidor configurable por parámetro o entrada del usuario

### ¿Qué pasa cuando un cliente se desconecta?
1. El socket lanza `IOException` en el hilo del servidor
2. `ClientHandler.disconnect()` elimina el cliente del mapa `clients`
3. El servidor envía `NOTIF|SALIDA|<id>` a todos los demás clientes
4. Los mensajes dirigidos a ese cliente retornan `ERROR|MENSAJE|DESTINO_NO_DISPONIBLE`

---

## Cómo Iniciar el Sistema

### Paso 1 — Compilar (solo la primera vez)
```
cd mininet
compilar.bat
```
O manualmente:
```
javac Server.java Client.java
```

### Paso 2 — Iniciar el Servidor
Abrir una **terminal exclusiva** para el servidor:
```
servidor.bat
```
O manualmente:
```
java Server 9090
```
El servidor queda escuchando. **No cerrar esta ventana.**

### Paso 3 — Conectar Clientes
Abrir una **terminal por cada cliente** (en el mismo o distinto equipo):
```
cliente.bat
```
O manualmente:
```
java Client localhost 9090 alice
java Client 192.168.1.10 9090 bob      ← desde otro equipo
```
Argumentos: `java Client <host> <puerto> <id>`

### Cómo Detener el Sistema
- **Un cliente:** escribir `Q` o `SALIR` en su consola
- **El servidor:** `Ctrl+C` en la terminal del servidor
- Al detener el servidor, todos los clientes pierden conexión automáticamente

---

## Protocolo de Aplicación MiniNet (MNP)

El protocolo opera sobre **TCP**, usa texto plano y separa campos con `|`.
Cada mensaje termina con `\n` (línea nueva).

### Sintaxis de Mensajes

| Dirección | Mensaje | Formato |
|-----------|---------|---------|
| Cliente → Servidor | Registro | `REGISTRO\|<id>` |
| Cliente → Servidor | Listar activos | `LISTAR` |
| Cliente → Servidor | Unicast | `MENSAJE\|<destino>\|<texto>` |
| Cliente → Servidor | Difusión | `BROADCAST\|<texto>` |
| Cliente → Servidor | Crear grupo | `GRUPO_CREAR\|<grupoId>\|<m1,m2,...>` |
| Cliente → Servidor | Mensaje grupo | `GRUPO\|<grupoId>\|<texto>` |
| Cliente → Servidor | Ping | `PING\|<destino>\|<timestamp_ms>` |
| Cliente → Servidor | Salir | `SALIR\|<id>` |
| Servidor → Cliente | Confirmación OK | `OK\|<operacion>\|...` |
| Servidor → Cliente | Error | `ERROR\|<operacion>\|<razon>` |
| Servidor → Cliente | Mensaje recibido | `MSG\|<origen>\|<texto>` |
| Servidor → Cliente | Broadcast recibido | `BCAST\|<origen>\|<texto>` |
| Servidor → Cliente | Mensaje de grupo | `GMSG\|<grupoId>\|<origen>\|<texto>` |
| Servidor → Cliente | Pong | `PONG\|<dest>\|DISPONIBLE\|<ts>` |
| Servidor → Cliente | Notificación | `NOTIF\|INGRESO\|<id>` / `NOTIF\|SALIDA\|<id>` |

### Secuencias de Intercambio

**Registro (obligatorio al conectar):**
```
Cliente ──── REGISTRO|alice ────────▶ Servidor
Cliente ◀─── OK|REGISTRO|alice ───── Servidor
```

**Unicast (cliente A → cliente B):**
```
Cliente A ── MENSAJE|bob|Hola ──────▶ Servidor
Servidor ─── MSG|alice|Hola ────────▶ Cliente B
Cliente A ◀─ ACK|MENSAJE|bob|2ms ─── Servidor
```

**Si el destino no existe:**
```
Cliente A ── MENSAJE|noexiste|Hola ─▶ Servidor
Cliente A ◀─ ERROR|MENSAJE|DESTINO_NO_DISPONIBLE|noexiste
```

**Broadcast:**
```
Cliente A ── BROADCAST|Hola todos ──▶ Servidor
Servidor ─── BCAST|alice|Hola todos ▶ Cliente B, C, D ...
Cliente A ◀─ ACK|BROADCAST|3
```

**Ping (consulta de disponibilidad):**
```
Cliente A ── PING|bob|1727480060000 ▶ Servidor
Cliente A ◀─ PONG|bob|DISPONIBLE|... Servidor   (latencia calculada en cliente)
```

---

## Modalidades de Comunicación

### 1. Unicast (comunicación individual)
Un cliente envía a **un destinatario específico**.
- A nivel de aplicación: `MENSAJE|destino|texto`
- A nivel de red: sigue siendo un flujo TCP unicast entre cliente y servidor
- El servidor actúa como intermediario y reenvía al destino

### 2. Broadcast (difusión)
Un cliente envía a **todos los clientes activos**.
- A nivel de aplicación: `BROADCAST|texto`
- Implementado como replicación en el servidor: el servidor itera el mapa `clients` y envía a cada uno
- **No usa broadcast IP** (nivel de red); es difusión a nivel de aplicación (capa 7)
- El tráfico real en la red son N conexiones TCP unicast individuales

### 3. Multicast lógico (por grupo)
Un cliente envía solo a **miembros de un grupo predefinido**.
- Se crea el grupo: `GRUPO_CREAR|laboratorio|alice,bob`
- Se envía: `GRUPO|laboratorio|Clase cancelada`
- El servidor filtra y entrega solo a los miembros del grupo
- **No usa multicast IP** (no requiere IGMP ni routers con soporte multicast)
- Es multicast simulado a nivel de aplicación

---

## Relación con el Modelo OSI

Cuando `alice` envía `MENSAJE|bob|Hola`, ocurre lo siguiente en cada capa:

```
┌──────────────────────────────────────────────────────────────────────┐
│  CAPA 7 — APLICACIÓN                                                 │
│  MiniNet genera: "MENSAJE|bob|Hola\n"                                │
│  Este es el PDU de la capa de aplicación (datos del protocolo MNP)  │
├──────────────────────────────────────────────────────────────────────┤
│  CAPA 6 — PRESENTACIÓN                                               │
│  Java convierte el String a bytes UTF-8                              │
│  (en la JVM, no es una capa explícita pero ocurre la codificación)  │
├──────────────────────────────────────────────────────────────────────┤
│  CAPA 5 — SESIÓN                                                     │
│  La conexión TCP persistente actúa como sesión                       │
│  El socket Java mantiene esta sesión abierta durante toda la vida   │
│  del cliente                                                         │
├──────────────────────────────────────────────────────────────────────┤
│  CAPA 4 — TRANSPORTE                                                 │
│  TCP (java.net.Socket usa TCP por defecto)                           │
│  Puerto destino: 9090 (servidor)                                     │
│  Puerto origen: asignado dinámicamente por el SO (ej. 52341)        │
│  TCP garantiza entrega ordenada y sin errores                        │
├──────────────────────────────────────────────────────────────────────┤
│  CAPA 3 — RED                                                        │
│  IP encapsula el segmento TCP                                        │
│  IP origen: 192.168.1.x (equipo del cliente)                        │
│  IP destino: 192.168.1.y (equipo del servidor)                      │
├──────────────────────────────────────────────────────────────────────┤
│  CAPA 2 — ENLACE DE DATOS                                            │
│  Ethernet/Wi-Fi encapsula el paquete IP en tramas                   │
│  MAC origen y MAC destino del gateway o equipo destino              │
├──────────────────────────────────────────────────────────────────────┤
│  CAPA 1 — FÍSICA                                                     │
│  Bits transmitidos por cable Ethernet (eléctrico) o Wi-Fi (radio)  │
└──────────────────────────────────────────────────────────────────────┘
```

**Lo que MiniNet controla directamente:** solo Capa 7 (el texto del protocolo MNP).
**Lo que el SO y la JVM manejan:** Capas 4-6 (TCP, puertos, sesión, codificación).
**Lo que la red física maneja:** Capas 1-3 (IP, Ethernet, bits).

---

## Relación con el Modelo TCP/IP

```
┌───────────────────────────────────────────────────────────┐
│  CAPA DE APLICACIÓN (Application Layer)                   │
│  Protocolo MNP — mensajes tipo "MENSAJE|bob|Hola\n"      │
│  Implementado en: Server.java, Client.java               │
├───────────────────────────────────────────────────────────┤
│  CAPA DE TRANSPORTE (Transport Layer)                     │
│  TCP — garantía de entrega, control de flujo             │
│  Puertos: servidor=9090, cliente=dinámico                │
│  Manejado por: java.net.Socket / ServerSocket            │
├───────────────────────────────────────────────────────────┤
│  CAPA DE INTERNET (Internet Layer)                        │
│  IPv4 — direccionamiento y enrutamiento                  │
│  Manejado por: sistema operativo                         │
├───────────────────────────────────────────────────────────┤
│  CAPA DE ACCESO A RED (Network Access Layer)              │
│  Ethernet / Wi-Fi — transmisión física de tramas         │
│  Manejado por: driver de red y hardware NIC              │
└───────────────────────────────────────────────────────────┘
```

**Distinción clave:**
- MiniNet *genera* datos (capa de aplicación)
- El SO *transporta* esos datos (capas inferiores)
- Wireshark *observa* el tráfico real en las capas 2-4

---

## Captura con Wireshark

Para capturar el tráfico de MiniNet:

1. Abrir Wireshark → seleccionar interfaz de red activa (Ethernet o Wi-Fi)
2. Filtro de captura: `tcp port 9090`
3. Iniciar captura → correr servidor y clientes → realizar intercambios
4. Detener captura

**Qué se puede identificar:**
- IP origen/destino en cada paquete
- Puerto 9090 como destino en mensajes al servidor
- Handshake TCP de 3 vías al conectar (SYN → SYN-ACK → ACK)
- FIN/RST al desconectar un cliente
- El contenido del protocolo MNP en texto plano en la pestaña "Data"
- Tiempo entre paquetes = latencia observable

**Correlación log ↔ Wireshark:**
El `server.log` registra `HH:mm:ss` de cada evento. En Wireshark el tiempo
relativo de los paquetes permite identificar el mismo intercambio.

---

## Registro de Eventos (`server.log`)

Formato de cada línea:
```
HH:mm:ss  ORIGEN       -> DESTINO       TIPO               RESULTADO
10:34:20  alice        -> SERVIDOR      REGISTRO           OK  IP=127.0.0.1
10:34:25  alice        -> bob           MENSAJE            OK 2ms
10:34:27  bob          -> alice         MENSAJE            OK 1ms
10:34:34  alice        -> TODOS         BROADCAST          OK [2 destinatarios]
10:34:40  alice        -> SERVIDOR      PING               DISPONIBLE
10:34:55  bob          -> SERVIDOR      SALIR              OK [1 restantes]
```

El log se escribe en `server.log` en la carpeta donde se ejecuta el servidor.

---

## Comandos del Cliente (consola)

| Comando | Efecto |
|---------|--------|
| `L` | Listar clientes activos |
| `M <id> <texto>` | Unicast a cliente |
| `B <texto>` | Broadcast a todos |
| `GC <grupo> <m1,m2>` | Crear grupo con miembros |
| `G <grupo> <texto>` | Enviar a grupo (multicast lógico) |
| `P <id>` | Ping: consultar disponibilidad |
| `Q` | Desconectarse del servidor |
| `?` | Mostrar ayuda |

---

## Estructura de Archivos

```
mininet/
├── Server.java        ← código fuente del servidor
├── Client.java        ← código fuente del cliente
├── compilar.bat       ← compila ambos archivos
├── servidor.bat       ← inicia el servidor en puerto 9090
├── cliente.bat        ← inicia un cliente (pide host/puerto/id)
├── Server.class       ← generado al compilar
├── Server$ClientHandler.class
├── Client.class       ← generado al compilar
└── server.log         ← generado al ejecutar el servidor
```

---

## Restricciones Respetadas

- Todo el desarrollo en **Java puro** (sin frameworks externos)
- Comunicación real por **sockets TCP** (`java.net.Socket`)
- Servidor y clientes son **procesos independientes**
- No hay simulación artificial de capas OSI en el código
- No hay base de datos ni autenticación
- Interfaz de **consola** únicamente
- El broadcast y multicast se implementan a **nivel de aplicación**, no IP

---

## Verificación del Sistema (Prueba Paso a Paso)

### Paso 1 — Compilar
```
cd mininet
compilar.bat
```
Sin errores en consola = OK.

### Paso 2 — Arrancar servidor (Terminal 1)
```
servidor.bat
```
Debe mostrar:
```
╔══════════════════════════════╗
║   MiniNet Server - Puerto 9090  ║
╚══════════════════════════════╝
```

### Paso 3 — Conectar cliente A (Terminal 2)
```
java Client localhost 9090 alice
```
El servidor imprime:
```
10:00:01  alice  -> SERVIDOR  REGISTRO  OK  IP=127.0.0.1
```
El cliente muestra `[OK] REGISTRO alice` y el menú de comandos.

### Paso 4 — Conectar cliente B (Terminal 3)
```
java Client localhost 9090 bob
```
En la consola de `alice` aparece automáticamente: `[NOTIF] INGRESO bob`

### Paso 5 — Probar unicast
En la consola de `alice`:
```
M bob Hola
```
- `bob` recibe: `[MSG de alice] Hola`
- `alice` recibe: `[ACK] MENSAJE|bob|Xms`

### Paso 6 — Probar listar clientes
```
L
```
Respuesta: `[CLIENTES] alice,bob`

### Paso 7 — Probar broadcast
```
B Hola a todos
```
- `bob` recibe: `[BROADCAST de alice] Hola a todos`
- `alice` recibe: `[ACK] BROADCAST|1`

### Paso 8 — Probar grupos
En consola de `alice`:
```
GC lab alice,bob
G lab Reunion en 5 minutos
```
- `bob` recibe: `[GRUPO lab de alice] Reunion en 5 minutos`

### Paso 9 — Probar ping
```
P bob
```
Respuesta: `[PING] bob: DISPONIBLE (Xms)`

### Paso 10 — Probar desconexión
Cerrar `bob` con `Q`. En consola de `alice`:
- Aparece: `[NOTIF] SALIDA bob`
- Hacer `P bob` → responde `NO_DISPONIBLE`
- Hacer `M bob Hola` → responde `ERROR|MENSAJE|DESTINO_NO_DISPONIBLE|bob`

---

### Solución de Problemas

| Síntoma | Causa probable |
|---------|----------------|
| `[ERROR] No se puede conectar` | Servidor no está corriendo o puerto incorrecto |
| `ERROR\|REGISTRO\|ID_EN_USO` | Ya hay un cliente con ese ID conectado |
| `ERROR\|MENSAJE\|DESTINO_NO_DISPONIBLE` | El destino no está registrado o se desconectó |
| No llegan mensajes al otro cliente | Revisar que ambos usen el mismo host y puerto |
| El cliente se cierra solo | El servidor se detuvo; reiniciar servidor primero |

El archivo `server.log` guarda el registro completo de todos los eventos. Si algo falla, revisarlo para identificar en qué punto ocurrió el problema.

---

## Requisitos

- **Java 8 o superior** (JDK instalado)
- Los equipos deben estar en la **misma red** o el servidor debe ser accesible desde los clientes
- Puerto **9090 TCP** abierto en el firewall del equipo servidor