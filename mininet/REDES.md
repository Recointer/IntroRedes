# MiniNet — Análisis desde la Perspectiva de Redes

**Proyecto IB · Introducción a Redes · UTA · Carrera de Software · 3B**

---

## 1. Arquitectura de Red

MiniNet implementa una arquitectura **cliente-servidor** sobre una red TCP/IP real.
No simula la red — la usa. Cada proceso (servidor y clientes) corre de forma independiente
y se comunica a través de sockets, exactamente como cualquier aplicación distribuida real.

```
         ┌────────────┐
         │  CLIENTE A │
         │  (alice)   │
         └─────┬──────┘
               │ TCP
               ▼
         ┌────────────┐       ┌────────────┐
         │  SERVIDOR  │◀─────▶│  CLIENTE B │
         │  :9090     │  TCP  │  (bob)     │
         └────────────┘       └────────────┘
               ▲
               │ TCP
         ┌─────┴──────┐
         │  CLIENTE C │
         │  (carlos)  │
         └────────────┘
```

**Topología lógica:** estrella — todos los clientes convergen en el servidor central.

**Protocolo de transporte:** TCP (orientado a conexión, confiable, ordenado).

**Comunicación:** toda comunicación entre clientes es **intermediada por el servidor**.
No existe comunicación directa peer-to-peer entre clientes.

---

## 2. Modelo OSI aplicado a MiniNet

El modelo OSI divide la comunicación en 7 capas. MiniNet opera en la **capa 7**
y delega las capas inferiores al sistema operativo y la red física.

```
┌──────┬────────────────────┬──────────────────────────────────────────────┐
│ Capa │ Nombre             │ En MiniNet                                   │
├──────┼────────────────────┼──────────────────────────────────────────────┤
│  7   │ Aplicación         │ Protocolo MNP: REGISTRO, MENSAJE, BROADCAST… │
│      │                    │ Implementado en Server.java y Client.java     │
├──────┼────────────────────┼──────────────────────────────────────────────┤
│  6   │ Presentación       │ Codificación de texto: String → bytes UTF-8  │
│      │                    │ Manejado por OutputStreamWriter de Java       │
├──────┼────────────────────┼──────────────────────────────────────────────┤
│  5   │ Sesión             │ Conexión TCP persistente por cliente          │
│      │                    │ El socket Java mantiene la sesión abierta     │
├──────┼────────────────────┼──────────────────────────────────────────────┤
│  4   │ Transporte         │ TCP — entrega ordenada y sin pérdidas        │
│      │                    │ Puerto servidor: 9090 / Clientes: dinámico   │
├──────┼────────────────────┼──────────────────────────────────────────────┤
│  3   │ Red                │ IPv4 — enrutamiento entre equipos            │
│      │                    │ IP origen (cliente) → IP destino (servidor)  │
├──────┼────────────────────┼──────────────────────────────────────────────┤
│  2   │ Enlace de datos    │ Ethernet / Wi-Fi — tramas entre nodos        │
│      │                    │ Direcciones MAC, control de acceso al medio  │
├──────┼────────────────────┼──────────────────────────────────────────────┤
│  1   │ Física             │ Bits transmitidos por cable o señal de radio │
│      │                    │ NIC, cable UTP, antena Wi-Fi                 │
└──────┴────────────────────┴──────────────────────────────────────────────┘
```

**Encapsulamiento:** cuando `alice` envía `MENSAJE|bob|Hola`:

```
[MENSAJE|bob|Hola]                       ← datos MNP (capa 7)
  └─ encapsulado en segmento TCP         ← capa 4 (puerto 9090)
       └─ encapsulado en paquete IP      ← capa 3 (IP origen/destino)
            └─ encapsulado en trama ETH  ← capa 2 (MAC origen/destino)
                 └─ transmitido en bits  ← capa 1 (señal eléctrica/radio)
```

En el destino ocurre el proceso inverso (desencapsulamiento).

**Lo que MiniNet controla:** solo capa 7 (el texto del protocolo MNP).
**Lo que el SO maneja:** capas 4, 5 y 6 (TCP, sesión, codificación).
**Lo que la red maneja:** capas 1, 2 y 3 (física, enlace, IP).

---

## 3. Modelo TCP/IP aplicado a MiniNet

El modelo TCP/IP agrupa las 7 capas OSI en 4:

```
┌─────────────────────────┬────────────────────────────────────────────┐
│ Capa TCP/IP             │ En MiniNet                                 │
├─────────────────────────┼────────────────────────────────────────────┤
│ Aplicación              │ Protocolo MNP (Server.java / Client.java)  │
├─────────────────────────┼────────────────────────────────────────────┤
│ Transporte              │ TCP — java.net.Socket / ServerSocket        │
├─────────────────────────┼────────────────────────────────────────────┤
│ Internet                │ IPv4 — Sistema Operativo                   │
├─────────────────────────┼────────────────────────────────────────────┤
│ Acceso a la red         │ Ethernet / Wi-Fi — driver NIC + hardware   │
└─────────────────────────┴────────────────────────────────────────────┘
```

**Distinción clave:**
- MiniNet *genera* los datos (capa de aplicación)
- El SO *transporta* esos datos (capas inferiores)
- Wireshark *observa* el tráfico generado en las capas 2-4

---

## 4. Protocolo de Aplicación MiniNet (MNP)

MNP es un protocolo de nivel de aplicación diseñado para este sistema.
Opera sobre TCP y usa texto plano con campos separados por `|`.

### 4.1 Sintaxis

Estructura general:
```
COMANDO|campo1|campo2|...\n
```

Mensajes del protocolo:

| Mensaje | Formato | Ejemplo |
|---------|---------|---------|
| Registro | `REGISTRO\|<id>` | `REGISTRO\|alice` |
| Listar | `LISTAR` | `LISTAR` |
| Unicast | `MENSAJE\|<dest>\|<texto>` | `MENSAJE\|bob\|Hola` |
| Difusión | `BROADCAST\|<texto>` | `BROADCAST\|Clase cancelada` |
| Crear grupo | `GRUPO_CREAR\|<gid>\|<m1,m2>` | `GRUPO_CREAR\|lab\|alice,bob` |
| Mensaje grupo | `GRUPO\|<gid>\|<texto>` | `GRUPO\|lab\|Reunión` |
| Ping | `PING\|<dest>\|<timestamp>` | `PING\|bob\|1727480060000` |
| Salir | `SALIR\|<id>` | `SALIR\|alice` |

### 4.2 Semántica

| Comando | Significado |
|---------|-------------|
| `REGISTRO` | Incorporar un participante al sistema |
| `LISTAR` | Consultar participantes activos |
| `MENSAJE` | Enviar información a un destinatario específico |
| `BROADCAST` | Difundir información a todos los participantes |
| `GRUPO_CREAR` | Definir un subconjunto de participantes |
| `GRUPO` | Enviar información solo a miembros del grupo |
| `PING` | Consultar disponibilidad de un participante |
| `SALIR` | Abandonar el sistema de forma ordenada |

### 4.3 Temporización y Secuencia

**Registro (handshake inicial):**
```
Cliente ──── REGISTRO|alice ──────────▶ Servidor
Cliente ◀─── OK|REGISTRO|alice ──────── Servidor
```

**Unicast:**
```
Cliente A ── MENSAJE|bob|Hola ─────────▶ Servidor
Servidor ─── MSG|alice|Hola ────────────▶ Cliente B
Cliente A ◀─ ACK|MENSAJE|bob|2ms ─────── Servidor
```

**Broadcast:**
```
Cliente A ── BROADCAST|texto ──────────▶ Servidor
Servidor ─── BCAST|alice|texto ─────────▶ Cliente B
Servidor ─── BCAST|alice|texto ─────────▶ Cliente C
Cliente A ◀─ ACK|BROADCAST|2 ─────────── Servidor
```

**Desconexión detectada:**
```
Cliente B se desconecta (cierra socket)
Servidor detecta IOException en hilo del ClientHandler
Servidor elimina bob del mapa de clientes
Servidor ─── NOTIF|SALIDA|bob ──────────▶ Cliente A, C, ...
```

**Respuesta esperada no llega (destino no disponible):**
```
Cliente A ── MENSAJE|bob|Hola ─────────▶ Servidor
Servidor verifica: bob no está en el mapa
Cliente A ◀─ ERROR|MENSAJE|DESTINO_NO_DISPONIBLE|bob
```

---

## 5. Modalidades de Comunicación

### 5.1 Unicast
Un emisor, un receptor específico.

```
alice ──▶ Servidor ──▶ bob
```

- A nivel de aplicación: `MENSAJE|bob|texto`
- A nivel de red: dos conexiones TCP unicast (alice→servidor, servidor→bob)
- Representa comunicación punto a punto

### 5.2 Broadcast (Difusión)
Un emisor, todos los receptores activos.

```
alice ──▶ Servidor ──▶ bob
                  ──▶ carlos
                  ──▶ diana
```

- A nivel de aplicación: `BROADCAST|texto`
- El servidor replica el mensaje a todos los clientes registrados
- **No usa broadcast IP** — son N conexiones TCP individuales
- Es difusión implementada a nivel de aplicación (capa 7)

### 5.3 Multicast lógico (por grupo)
Un emisor, un subconjunto de receptores.

```
alice ──▶ Servidor ──▶ bob    (miembro del grupo "lab")
                  ──▶ carlos  (miembro del grupo "lab")
                  ✗   diana   (no es miembro)
```

- A nivel de aplicación: `GRUPO|lab|texto`
- El servidor filtra y entrega solo a miembros del grupo
- **No usa multicast IP** (no requiere IGMP ni routers con soporte multicast)
- Es multicast lógico implementado a nivel de aplicación

**Diferencia con multicast IP real:**
El multicast IP opera en capa 3 usando direcciones del rango `224.0.0.0/4`
y el router se encarga de la distribución. En MiniNet, el servidor replica
manualmente el mensaje en capa 7 — funcionalmente equivalente para este contexto.

---

## 6. Métricas de Red

MiniNet registra y expone las siguientes métricas:

### Latencia
Tiempo entre el envío del mensaje y la recepción del ACK.
```
t0 = System.currentTimeMillis()   // antes de enviar
t1 = System.currentTimeMillis()   // al recibir ACK
latencia = t1 - t0  (ms)
```
Visible en el log: `MENSAJE OK 2ms`

### Throughput
Mensajes procesados por unidad de tiempo — observable en el log del servidor
contando eventos por segundo durante una sesión de prueba.

### Disponibilidad
Estado de un participante consultable via `PING`:
- `DISPONIBLE` → cliente registrado y activo
- `NO_DISPONIBLE` → no registrado o desconectado

### Pérdida simulada
Al desconectar un cliente abruptamente (cerrar terminal sin `Q`),
los mensajes dirigidos a él retornan `ERROR|MENSAJE|DESTINO_NO_DISPONIBLE`.
Esto simula pérdida a nivel de aplicación.

---

## 7. Comunicación Extremo a Extremo

En MiniNet, la comunicación extremo a extremo (end-to-end) entre `alice` y `bob`
ocurre lógicamente aunque físicamente pase por el servidor:

```
alice (proceso)  ──TCP──  Servidor  ──TCP──  bob (proceso)
     ↑                                            ↑
     └──────── comunicación lógica E2E ───────────┘
```

El principio extremo a extremo de TCP garantiza que:
- Los datos llegan en orden
- Los datos llegan completos (retransmisión automática si hay pérdida)
- El receptor confirma la recepción (ACK a nivel TCP)

MiniNet agrega su propia capa de confirmación (ACK de aplicación) sobre esto,
lo que permite medir latencia a nivel de la aplicación y no solo de la red.

---

## 8. Concurrencia en el Servidor

El servidor usa el modelo **hilo por cliente** (`Thread per connection`):

```
ServerSocket.accept() ──▶ nuevo Socket
                              └──▶ new Thread(new ClientHandler(socket)).start()
```

Esto permite atender múltiples clientes simultáneamente. Los datos compartidos
(`clients`, `groups`) usan `ConcurrentHashMap` para evitar condiciones de carrera
en accesos concurrentes desde múltiples hilos.

---

## 9. Captura con Wireshark

Wireshark permite observar el tráfico real generado por MiniNet en las capas 2-4.

**Configuración:**
1. Abrir Wireshark → seleccionar la interfaz de red activa
2. Filtro: `tcp port 9090`
3. Iniciar captura → ejecutar MiniNet → realizar intercambios → detener

**Qué se observa:**

| Evento en MiniNet | Tráfico en Wireshark |
|---|---|
| Cliente conecta | Handshake TCP: SYN → SYN-ACK → ACK |
| `REGISTRO\|alice` | Paquete TCP con payload de texto |
| `MENSAJE\|bob\|Hola` | Paquete TCP con el mensaje MNP en claro |
| Cliente desconecta con `Q` | FIN → FIN-ACK → ACK |
| Cliente cierra terminal | RST o FIN abrupto |

**Correlación log ↔ Wireshark:**
El `server.log` registra `HH:mm:ss` de cada evento. En Wireshark, la columna
`Time` (relativo al inicio de captura) permite identificar el mismo intercambio.

**Identificación de capas en Wireshark:**
- `Frame` → capa 1/2 (tamaño en bytes, interfaz)
- `Ethernet II` → capa 2 (MACs origen/destino)
- `Internet Protocol` → capa 3 (IPs origen/destino)
- `Transmission Control Protocol` → capa 4 (puertos, flags, seq/ack)
- `Data` → capa 7 (texto del protocolo MNP)

---

## 10. Experimentos Sugeridos

### Experimento 1 — Latencia local vs red
Medir el tiempo de respuesta (`PING`) con clientes en `localhost` vs en otro equipo
conectado por Wi-Fi. La latencia local será ~0ms; por red será mayor.

### Experimento 2 — Desconexión abrupta
Cerrar la terminal de un cliente sin usar `Q`. Observar:
- El servidor detecta la desconexión vía `IOException`
- Los demás clientes reciben `NOTIF|SALIDA|<id>`
- Los mensajes enviados a ese cliente retornan error

### Experimento 3 — Múltiples clientes simultáneos
Conectar 3+ clientes y realizar broadcast. Verificar en el log que el servidor
entrega a todos en el mismo instante (misma marca de tiempo).

### Experimento 4 — Captura Wireshark de sesión completa
Capturar desde que un cliente conecta hasta que se desconecta. Identificar
el handshake TCP inicial, los mensajes MNP y el cierre de conexión.
