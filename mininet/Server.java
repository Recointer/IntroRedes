import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Servidor MiniNet — acepta conexiones TCP de múltiples clientes
 * y aplica el Protocolo MiniNet (MNP) para enrutar mensajes entre ellos.
 *
 * Protocolo MNP — sintaxis de mensajes (separados por '|', terminan en '\n'):
 *   Cliente → Servidor:
 *     REGISTRO|<id>                    registrar cliente
 *     LISTAR                           consultar clientes activos
 *     MENSAJE|<destino>|<texto>        unicast a un cliente específico
 *     BROADCAST|<texto>                difusión a todos los clientes
 *     GRUPO_CREAR|<grupoId>|<m1,m2>   definir un grupo de participantes
 *     GRUPO|<grupoId>|<texto>          multicast lógico al grupo
 *     PING|<destino>|<timestamp_ms>    consultar disponibilidad
 *     SALIR|<id>                       desconexión ordenada
 *
 *   Servidor → Cliente:
 *     OK|<operacion>|...               confirmación exitosa
 *     ERROR|<operacion>|<razon>        error en la operación
 *     MSG|<origen>|<texto>             mensaje unicast recibido
 *     BCAST|<origen>|<texto>           broadcast recibido
 *     GMSG|<grupoId>|<origen>|<texto>  mensaje de grupo recibido
 *     PONG|<dest>|DISPONIBLE|<ts>      respuesta al PING
 *     NOTIF|INGRESO|<id>               notificación: nuevo cliente conectado
 *     NOTIF|SALIDA|<id>                notificación: cliente desconectado
 */
public class Server {

    static final int DEFAULT_PORT = 9090;
    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Registro de clientes activos: clientId → canal de escritura al cliente
    static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();

    // Registro de grupos definidos: groupId → conjunto de IDs miembros
    static final Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    // Archivo de log donde se registran todos los eventos
    static FileWriter logFile;

    public static void main(String[] args) throws IOException {
        // El puerto puede pasarse como argumento; si no, usa 9090 por defecto
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        logFile = new FileWriter("server.log", true);
        log("SERVIDOR", "SISTEMA", "INICIO", "Puerto " + port);
        System.out.println("╔══════════════════════════════╗");
        System.out.println("║   MiniNet Server - Puerto " + port + "  ║");
        System.out.println("╚══════════════════════════════╝");

        try (ServerSocket ss = new ServerSocket(port)) {
            while (true) {
                // Espera una nueva conexión TCP entrante (bloqueante)
                Socket sock = ss.accept();
                // Cada cliente recibe su propio hilo para atención simultánea
                new Thread(new ClientHandler(sock)).start();
            }
        }
    }

    // Escribe una línea en consola y en el archivo server.log
    // 'synchronized' evita que dos hilos escriban al mismo tiempo (condición de carrera)
    static synchronized void log(String from, String to, String type, String result) {
        String line = String.format("%s  %-12s -> %-12s  %-18s %s",
                LocalTime.now().format(FMT), from, to, type, result);
        System.out.println(line);
        try { logFile.write(line + "\n"); logFile.flush(); } catch (IOException ignored) {}
    }

    // Envía un mensaje a TODOS los clientes registrados excepto al indicado en 'exclude'
    // Implementa la difusión (broadcast) a nivel de aplicación — no usa broadcast IP
    static void broadcast(String from, String msg, String exclude) {
        clients.forEach((id, pw) -> {
            if (!id.equals(exclude)) pw.println(msg);
        });
    }

    // Envía un mensaje a UN cliente específico por su ID
    // Retorna true si el cliente existe y se envió, false si no está disponible
    static boolean sendTo(String dest, String msg) {
        PrintWriter pw = clients.get(dest);
        if (pw != null) { pw.println(msg); return true; }
        return false;
    }

    // ── Hilo por cliente — maneja toda la comunicación con un cliente conectado ──
    static class ClientHandler implements Runnable {
        private final Socket socket; // conexión TCP con este cliente
        private String clientId;     // ID registrado mediante REGISTRO|<id>
        private PrintWriter out;     // canal para enviar mensajes al cliente

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                // Preparar canales de entrada (leer) y salida (escribir) sobre el socket TCP
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                // Leer mensajes línea a línea hasta que el cliente se desconecte
                while ((line = in.readLine()) != null) handle(line.trim());
            } catch (IOException ignored) {
                // IOException ocurre cuando el cliente cierra la conexión abruptamente
            } finally {
                disconnect(); // asegurar limpieza aunque haya excepción
            }
        }

        // ── PROTOCOLO MNP — punto de entrada del parser ──────────────────────
        // Aquí se aplica el Protocolo MiniNet (MNP):
        // cada línea recibida se divide por '|' y el primer campo determina el comando
        void handle(String line) {
            if (line.isEmpty()) return;

            // MNP — parseo: dividir el mensaje en campos usando '|' como separador
            // Ejemplo: "MENSAJE|alice|Hola" → p[0]="MENSAJE", p[1]="alice", p[2]="Hola"
            String[] p = line.split("\\|", -1);

            // MNP — despacho: el primer campo (p[0]) identifica el tipo de mensaje
            switch (p[0]) {
                case "REGISTRO":    doRegistro(p);    break; // incorporar cliente al sistema
                case "LISTAR":      doListar();        break; // consultar participantes activos
                case "MENSAJE":     doMensaje(p);      break; // unicast a un cliente específico
                case "BROADCAST":   doBroadcast(p);    break; // difusión a todos
                case "GRUPO_CREAR": doGrupoCrear(p);   break; // definir grupo de participantes
                case "GRUPO":       doGrupo(p);        break; // multicast lógico al grupo
                case "PING":        doPing(p);         break; // consultar disponibilidad
                case "SALIR":       disconnect();      break; // desconexión ordenada
                default: out.println("ERROR|COMANDO_DESCONOCIDO|" + p[0]); // MNP — error
            }
        }

        // MNP — REGISTRO|<id>
        // El cliente se incorpora al sistema con un identificador único
        // Secuencia: Cliente → REGISTRO|alice → Servidor → OK|REGISTRO|alice → Cliente
        void doRegistro(String[] p) {
            if (p.length < 2 || p[1].isBlank()) { out.println("ERROR|REGISTRO|SINTAXIS"); return; }
            String id = p[1].trim();
            // Verificar que el ID no esté ya en uso por otro cliente activo
            if (clients.containsKey(id)) {
                out.println("ERROR|REGISTRO|ID_EN_USO");
                log("?", "SERVIDOR", "REGISTRO", "ERROR ID_EN_USO: " + id); return;
            }
            clientId = id;
            clients.put(id, out); // registrar cliente en el mapa de activos
            out.println("OK|REGISTRO|" + id); // confirmar al cliente
            broadcast("SERVIDOR", "NOTIF|INGRESO|" + id, id); // notificar a los demás
            log(id, "SERVIDOR", "REGISTRO", "OK  IP=" + socket.getInetAddress().getHostAddress());
        }

        // MNP — LISTAR
        // Devuelve la lista de IDs de clientes activos separados por coma
        void doListar() {
            String lista = String.join(",", clients.keySet());
            out.println("LISTA|" + lista);
            log(who(), "SERVIDOR", "LISTAR", "OK [" + clients.size() + " activos]");
        }

        // MNP — MENSAJE|<destino>|<texto>
        // Unicast: el servidor reenvía el texto a un único destinatario específico
        // Mide la latencia de reenvío y la incluye en el ACK
        void doMensaje(String[] p) {
            if (p.length < 3) { out.println("ERROR|MENSAJE|SINTAXIS"); return; }
            String dest = p[1], txt = p[2];
            long t0 = System.currentTimeMillis(); // marca de tiempo para medir latencia
            if (sendTo(dest, "MSG|" + who() + "|" + txt)) {
                long lat = System.currentTimeMillis() - t0; // latencia de reenvío en ms
                out.println("ACK|MENSAJE|" + dest + "|" + lat + "ms");
                log(who(), dest, "MENSAJE", "OK " + lat + "ms");
            } else {
                // El destino no está registrado o se desconectó
                out.println("ERROR|MENSAJE|DESTINO_NO_DISPONIBLE|" + dest);
                log(who(), dest, "MENSAJE", "ERROR DESTINO_NO_DISPONIBLE");
            }
        }

        // MNP — BROADCAST|<texto>
        // Difusión a nivel de aplicación: el servidor replica el mensaje a todos los clientes
        // No usa broadcast IP — son conexiones TCP individuales a cada cliente
        void doBroadcast(String[] p) {
            if (p.length < 2) { out.println("ERROR|BROADCAST|SINTAXIS"); return; }
            broadcast(who(), "BCAST|" + who() + "|" + p[1], who()); // replicar a todos menos al emisor
            int n = clients.size() - 1;
            out.println("ACK|BROADCAST|" + n);
            log(who(), "TODOS", "BROADCAST", "OK [" + n + " destinatarios]");
        }

        // MNP — GRUPO_CREAR|<grupoId>|<m1,m2,...>
        // Define un grupo de participantes para comunicación multicast lógica
        void doGrupoCrear(String[] p) {
            if (p.length < 3) { out.println("ERROR|GRUPO_CREAR|SINTAXIS"); return; }
            String gid = p[1];
            Set<String> miembros = new HashSet<>(Arrays.asList(p[2].split(",")));
            groups.put(gid, miembros); // guardar grupo en el registro
            out.println("OK|GRUPO_CREAR|" + gid + "|" + miembros.size());
            log(who(), gid, "GRUPO_CREAR", "OK [" + miembros.size() + " miembros]");
        }

        // MNP — GRUPO|<grupoId>|<texto>
        // Multicast lógico: el servidor entrega el mensaje solo a los miembros del grupo
        // No usa multicast IP — el servidor filtra y replica por conexiones TCP individuales
        void doGrupo(String[] p) {
            if (p.length < 3) { out.println("ERROR|GRUPO|SINTAXIS"); return; }
            String gid = p[1], txt = p[2];
            Set<String> miembros = groups.get(gid);
            if (miembros == null) { out.println("ERROR|GRUPO|GRUPO_NO_EXISTE|" + gid); return; }
            int enviados = 0;
            for (String m : miembros)
                // Enviar solo a miembros activos, excluyendo al propio emisor
                if (!m.equals(who()) && sendTo(m, "GMSG|" + gid + "|" + who() + "|" + txt)) enviados++;
            out.println("ACK|GRUPO|" + gid + "|" + enviados);
            log(who(), gid, "GRUPO_MSG", "OK [" + enviados + " entregados]");
        }

        // MNP — PING|<destino>|<timestamp_ms>
        // Consulta si un cliente está disponible; el timestamp permite calcular RTT en el cliente
        void doPing(String[] p) {
            if (p.length < 2) { out.println("ERROR|PING|SINTAXIS"); return; }
            String dest = p[1];
            String ts = p.length > 2 ? p[2] : String.valueOf(System.currentTimeMillis());
            boolean avail = clients.containsKey(dest); // verificar si el destino está registrado
            out.println("PONG|" + dest + "|" + (avail ? "DISPONIBLE" : "NO_DISPONIBLE") + "|" + ts);
            log(who(), dest, "PING", avail ? "DISPONIBLE" : "NO_DISPONIBLE");
        }

        // Desconexión: elimina al cliente del sistema y notifica a los demás
        // Se llama tanto en desconexión ordenada (SALIR) como abrupta (IOException)
        void disconnect() {
            if (clientId != null) {
                clients.remove(clientId); // eliminar del mapa de activos
                broadcast("SERVIDOR", "NOTIF|SALIDA|" + clientId, clientId); // notificar a todos
                log(clientId, "SERVIDOR", "SALIR", "OK [" + clients.size() + " restantes]");
                clientId = null;
            }
            try { socket.close(); } catch (IOException ignored) {}
        }

        // Retorna el ID del cliente actual o "?" si aún no se ha registrado
        String who() { return clientId != null ? clientId : "?"; }
    }
}
