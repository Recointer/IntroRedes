import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.Scanner;

public class Client {

    static String clientId;
    static PrintWriter out;
    static volatile boolean running = true;

    public static void main(String[] args) throws IOException {
        // Usage: java Client [host] [port] [id]
        String host = args.length > 0 ? args[0] : "localhost";
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : 9090;
        String id   = args.length > 2 ? args[2] : null;

        Socket socket;
        try {
            socket = new Socket(host, port);
        } catch (ConnectException e) {
            System.out.println("[ERROR] No se puede conectar a " + host + ":" + port);
            return;
        }

        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Scanner scanner   = new Scanner(System.in);

        // Get ID
        if (id == null) {
            System.out.print("ID de cliente: ");
            id = scanner.nextLine().trim();
        }
        clientId = id;

        // Receiver thread
        Thread receiver = new Thread(() -> {
            try {
                String line;
                while (running && (line = in.readLine()) != null) render(line);
            } catch (IOException ignored) {}
            System.out.println("[DESCONECTADO]");
            running = false;
        });
        receiver.setDaemon(true);
        receiver.start();

        // Register
        send("REGISTRO|" + clientId);
        printHelp();

        // Input loop
        while (running && scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) processInput(input);
        }

        send("SALIR|" + clientId);
        socket.close();
    }

    // ── Send ──────────────────────────────────────────────────────────────
    static void send(String msg) { out.println(msg); }

    // ── Render server responses ───────────────────────────────────────────
    static void render(String line) {
        String[] p = line.split("\\|", -1);
        switch (p[0]) {
            case "OK":
                System.out.println("\u001B[32m[OK]\u001B[0m " + join(p, 1));
                break;
            case "ERROR":
                System.out.println("\u001B[31m[ERROR]\u001B[0m " + join(p, 1));
                break;
            case "MSG":
                System.out.println("\u001B[36m[MSG de " + p[1] + "]\u001B[0m " + p[2]);
                break;
            case "BCAST":
                System.out.println("\u001B[35m[BROADCAST de " + p[1] + "]\u001B[0m " + p[2]);
                break;
            case "GMSG":
                System.out.println("\u001B[33m[GRUPO " + p[1] + " de " + p[2] + "]\u001B[0m " + p[3]);
                break;
            case "LISTA": {
                String lista = p.length > 1 && !p[1].isEmpty() ? p[1] : "(ninguno)";
                System.out.println("[CLIENTES] " + lista);
                break;
            }
            case "ACK":
                System.out.println("[ACK] " + join(p, 1));
                break;
            case "PONG": {
                long lat = -1;
                if (p.length > 3) {
                    try { lat = System.currentTimeMillis() - Long.parseLong(p[3]); } catch (NumberFormatException ignored) {}
                }
                System.out.printf("[PING] %s: %s (%dms)%n", p[1], p[2], lat);
                break;
            }
            case "NOTIF":
                System.out.println("\u001B[90m[NOTIF] " + p[1] + " " + p[2] + "\u001B[0m");
                break;
            default:
                System.out.println("[SERVIDOR] " + line);
        }
    }

    // ── Process console input ─────────────────────────────────────────────
    static void processInput(String input) {
        String[] tokens = input.split(" ", 2);
        String cmd = tokens[0].toUpperCase();
        String rest = tokens.length > 1 ? tokens[1] : "";

        switch (cmd) {
            case "L": case "LISTAR":
                send("LISTAR");
                break;

            case "M": case "MSG": {
                // M <dest> <texto>
                String[] mp = rest.split(" ", 2);
                if (mp.length < 2) { System.out.println("Uso: M <destino> <texto>"); break; }
                send("MENSAJE|" + mp[0] + "|" + mp[1]);
                break;
            }

            case "B": case "BCAST":
                if (rest.isEmpty()) { System.out.println("Uso: B <texto>"); break; }
                send("BROADCAST|" + rest);
                break;

            case "GC": case "GRUPO_CREAR": {
                // GC <grupoId> <m1,m2,...>
                String[] gcp = rest.split(" ", 2);
                if (gcp.length < 2) { System.out.println("Uso: GC <grupoId> <m1,m2,...>"); break; }
                send("GRUPO_CREAR|" + gcp[0] + "|" + gcp[1]);
                break;
            }

            case "G": case "GRUPO": {
                // G <grupoId> <texto>
                String[] gp = rest.split(" ", 2);
                if (gp.length < 2) { System.out.println("Uso: G <grupoId> <texto>"); break; }
                send("GRUPO|" + gp[0] + "|" + gp[1]);
                break;
            }

            case "P": case "PING":
                if (rest.isEmpty()) { System.out.println("Uso: P <destino>"); break; }
                send("PING|" + rest + "|" + System.currentTimeMillis());
                break;

            case "Q": case "SALIR":
                running = false;
                send("SALIR|" + clientId);
                System.exit(0);
                break;

            case "?": case "H": case "AYUDA":
                printHelp();
                break;

            default:
                // raw protocol
                send(input);
        }
    }

    static void printHelp() {
        System.out.println("┌─────────────────────────────────────────┐");
        System.out.println("│           MiniNet Client - " + clientId + "          ");
        System.out.println("├──────────────┬──────────────────────────┤");
        System.out.println("│  L           │ Listar clientes          │");
        System.out.println("│  M <id> <msg>│ Unicast a cliente        │");
        System.out.println("│  B <msg>     │ Broadcast a todos        │");
        System.out.println("│  GC <g> <ids>│ Crear grupo (m1,m2,...)  │");
        System.out.println("│  G <g> <msg> │ Mensaje a grupo          │");
        System.out.println("│  P <id>      │ Ping (disponibilidad)    │");
        System.out.println("│  Q           │ Salir                    │");
        System.out.println("│  ?           │ Esta ayuda               │");
        System.out.println("└──────────────┴──────────────────────────┘");
    }

    static String join(String[] arr, int from) {
        return String.join("|", Arrays.copyOfRange(arr, from, arr.length));
    }
}
