import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Server {

    static final int DEFAULT_PORT = 9090;
    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // clientId -> writer
    static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
    // groupId -> set of clientIds
    static final Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    static FileWriter logFile;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        logFile = new FileWriter("server.log", true);
        log("SERVIDOR", "SISTEMA", "INICIO", "Puerto " + port);
        System.out.println("╔══════════════════════════════╗");
        System.out.println("║   MiniNet Server - Puerto " + port + "  ║");
        System.out.println("╚══════════════════════════════╝");

        try (ServerSocket ss = new ServerSocket(port)) {
            while (true) {
                Socket sock = ss.accept();
                new Thread(new ClientHandler(sock)).start();
            }
        }
    }

    static synchronized void log(String from, String to, String type, String result) {
        String line = String.format("%s  %-12s -> %-12s  %-18s %s",
                LocalTime.now().format(FMT), from, to, type, result);
        System.out.println(line);
        try { logFile.write(line + "\n"); logFile.flush(); } catch (IOException ignored) {}
    }

    static void broadcast(String from, String msg, String exclude) {
        clients.forEach((id, pw) -> {
            if (!id.equals(exclude)) pw.println(msg);
        });
    }

    static boolean sendTo(String dest, String msg) {
        PrintWriter pw = clients.get(dest);
        if (pw != null) { pw.println(msg); return true; }
        return false;
    }

    // ── Handler ─────────────────────────────────────────────────────────────
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private String clientId;
        private PrintWriter out;

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) handle(line.trim());
            } catch (IOException ignored) {
            } finally { disconnect(); }
        }

        void handle(String line) {
            if (line.isEmpty()) return;
            String[] p = line.split("\\|", -1);
            switch (p[0]) {
                case "REGISTRO":    doRegistro(p);    break;
                case "LISTAR":      doListar();        break;
                case "MENSAJE":     doMensaje(p);      break;
                case "BROADCAST":   doBroadcast(p);    break;
                case "GRUPO_CREAR": doGrupoCrear(p);   break;
                case "GRUPO":       doGrupo(p);        break;
                case "PING":        doPing(p);         break;
                case "SALIR":       disconnect();      break;
                default: out.println("ERROR|COMANDO_DESCONOCIDO|" + p[0]);
            }
        }

        // REGISTRO|<id>
        void doRegistro(String[] p) {
            if (p.length < 2 || p[1].isBlank()) { out.println("ERROR|REGISTRO|SINTAXIS"); return; }
            String id = p[1].trim();
            if (clients.containsKey(id)) {
                out.println("ERROR|REGISTRO|ID_EN_USO");
                log("?", "SERVIDOR", "REGISTRO", "ERROR ID_EN_USO: " + id); return;
            }
            clientId = id;
            clients.put(id, out);
            out.println("OK|REGISTRO|" + id);
            broadcast("SERVIDOR", "NOTIF|INGRESO|" + id, id);
            log(id, "SERVIDOR", "REGISTRO", "OK  IP=" + socket.getInetAddress().getHostAddress());
        }

        // LISTAR
        void doListar() {
            String lista = String.join(",", clients.keySet());
            out.println("LISTA|" + lista);
            log(who(), "SERVIDOR", "LISTAR", "OK [" + clients.size() + " activos]");
        }

        // MENSAJE|<dest>|<texto>
        void doMensaje(String[] p) {
            if (p.length < 3) { out.println("ERROR|MENSAJE|SINTAXIS"); return; }
            String dest = p[1], txt = p[2];
            long t0 = System.currentTimeMillis();
            if (sendTo(dest, "MSG|" + who() + "|" + txt)) {
                long lat = System.currentTimeMillis() - t0;
                out.println("ACK|MENSAJE|" + dest + "|" + lat + "ms");
                log(who(), dest, "MENSAJE", "OK " + lat + "ms");
            } else {
                out.println("ERROR|MENSAJE|DESTINO_NO_DISPONIBLE|" + dest);
                log(who(), dest, "MENSAJE", "ERROR DESTINO_NO_DISPONIBLE");
            }
        }

        // BROADCAST|<texto>
        void doBroadcast(String[] p) {
            if (p.length < 2) { out.println("ERROR|BROADCAST|SINTAXIS"); return; }
            broadcast(who(), "BCAST|" + who() + "|" + p[1], who());
            int n = clients.size() - 1;
            out.println("ACK|BROADCAST|" + n);
            log(who(), "TODOS", "BROADCAST", "OK [" + n + " destinatarios]");
        }

        // GRUPO_CREAR|<grupoId>|<m1,m2,...>
        void doGrupoCrear(String[] p) {
            if (p.length < 3) { out.println("ERROR|GRUPO_CREAR|SINTAXIS"); return; }
            String gid = p[1];
            Set<String> miembros = new HashSet<>(Arrays.asList(p[2].split(",")));
            groups.put(gid, miembros);
            out.println("OK|GRUPO_CREAR|" + gid + "|" + miembros.size());
            log(who(), gid, "GRUPO_CREAR", "OK [" + miembros.size() + " miembros]");
        }

        // GRUPO|<grupoId>|<texto>
        void doGrupo(String[] p) {
            if (p.length < 3) { out.println("ERROR|GRUPO|SINTAXIS"); return; }
            String gid = p[1], txt = p[2];
            Set<String> miembros = groups.get(gid);
            if (miembros == null) { out.println("ERROR|GRUPO|GRUPO_NO_EXISTE|" + gid); return; }
            int enviados = 0;
            for (String m : miembros)
                if (!m.equals(who()) && sendTo(m, "GMSG|" + gid + "|" + who() + "|" + txt)) enviados++;
            out.println("ACK|GRUPO|" + gid + "|" + enviados);
            log(who(), gid, "GRUPO_MSG", "OK [" + enviados + " entregados]");
        }

        // PING|<dest>|<ts>
        void doPing(String[] p) {
            if (p.length < 2) { out.println("ERROR|PING|SINTAXIS"); return; }
            String dest = p[1];
            String ts = p.length > 2 ? p[2] : String.valueOf(System.currentTimeMillis());
            boolean avail = clients.containsKey(dest);
            out.println("PONG|" + dest + "|" + (avail ? "DISPONIBLE" : "NO_DISPONIBLE") + "|" + ts);
            log(who(), dest, "PING", avail ? "DISPONIBLE" : "NO_DISPONIBLE");
        }

        void disconnect() {
            if (clientId != null) {
                clients.remove(clientId);
                broadcast("SERVIDOR", "NOTIF|SALIDA|" + clientId, clientId);
                log(clientId, "SERVIDOR", "SALIR", "OK [" + clients.size() + " restantes]");
                clientId = null;
            }
            try { socket.close(); } catch (IOException ignored) {}
        }

        String who() { return clientId != null ? clientId : "?"; }
    }
}
