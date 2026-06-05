package kurs.server;

import java.io.*;
import java.net.*;
import java.util.UUID;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final GameServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String clientId;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            clientId = UUID.randomUUID().toString();

            socket.setSoTimeout(10_000);
            String line = in.readLine();
            socket.setSoTimeout(0);

            if (line != null && line.startsWith("JOIN;")) {
                String[] parts = line.split(";", 2);
                String name = parts.length > 1 ? parts[1] : clientId;
                clientId = name + "-" + clientId.substring(0, 8);
            }

            server.registerClient(clientId, this);
            send("SERVER;WELCOME;" + clientId);

            while (!socket.isClosed()) {
                line = in.readLine();
                if (line == null) break;
                handleMessage(line.trim());
            }
        } catch (SocketTimeoutException e) {
            send("SERVER;ERROR;Timeout");
        } catch (IOException ignored) {
        } finally {
            cleanup();
        }
    }

    private void handleMessage(String msg) {
        if (msg.isEmpty()) return;
        System.out.println("From " + clientId + ": " + msg);

        if (msg.equals("PAUSE")) {
            server.setPaused(true);
        } else if (msg.equals("RESUME")) {
            server.setPaused(false);
        } else if (msg.equals("RESTART")) {
            server.setPaused(false);
            server.getGameState().reset();
        } else if (msg.startsWith("MOVE;") || msg.startsWith("SHOOT;") || msg.startsWith("PING")) {
            server.handlePlayerAction(clientId, msg);
        } else {
            send("SERVER;ERROR;UnknownCommand");
        }
    }

    public synchronized void send(String message) {
        if (out != null) out.println(message);
    }

    private void cleanup() {
        try { if (socket != null && !socket.isClosed()) socket.close(); }
        catch (IOException ignored) {}
        if (clientId != null) server.unregisterClient(clientId);
    }
}