package kurs.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {

    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    // Игровое состояние — враги на сервере
    private final ServerGameState gameState = new ServerGameState();

    // Игровой цикл сервера: тикает 20 раз в секунду (каждые 50мс)
    private final ScheduledExecutorService gameTick = Executors.newSingleThreadScheduledExecutor();

    public GameServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("GameServer started on port " + port);

            // Запускаем игровой тик ~60 раз в секунду
            gameTick.scheduleAtFixedRate(this::tick, 0, 16, TimeUnit.MILLISECONDS);

            while (running) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clientPool.submit(handler);
            }
        } catch (IOException e) {
            if (running) System.err.println("Server error: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private volatile boolean paused = false;

    //Игровой тик: обновляем врагов, рассылаем STATE всем клиентам. 
    private void tick() {
        try {
            if (paused) return;
            gameState.update();
            String state = gameState.serialize();
            broadcast("STATE;" + state);
        } catch (Exception e) {
            System.err.println("Tick error: " + e.getMessage());
        }
    }

    public void setPaused(boolean p) { this.paused = p; }

    public void stop() {
        running = false;
        gameTick.shutdownNow();
        try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); }
        catch (IOException ignored) {}
        clientPool.shutdownNow();
        System.out.println("GameServer stopped");
    }

    public void registerClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
        broadcast("SERVER;JOINED;" + clientId);
        // Сразу шлём новому клиенту текущее состояние
        handler.send("STATE;" + gameState.serialize());
        System.out.println("Client join: " + clientId + " (total in server: " + clients.size() + ")");
    }

    public void unregisterClient(String clientId) {
        if (clientId == null) return;
        clients.remove(clientId);
        broadcast("SERVER;LEFT;" + clientId);
        System.out.println("Client left: " + clientId + " (total in server: " + clients.size() + ")");
    }

        public void handlePlayerAction(String clientId, String msg) {
        String[] parts = msg.split(";");
        if (parts.length < 1) return;

        switch (parts[0]) {
            case "MOVE" -> {
                if (parts.length >= 3) {
                    try {
                        int px = Integer.parseInt(parts[1]);
                        int py = Integer.parseInt(parts[2]);
                        gameState.updatePlayerPos(clientId, px, py);
                    } catch (NumberFormatException ignored) {}
                }
            }
            case "SHOOT" -> {
                if (parts.length >= 4) {
                    try {
                        int bx  = Integer.parseInt(parts[1]);
                        int by  = Integer.parseInt(parts[2]);
                        int dir = Integer.parseInt(parts[3]);
                        List<String> events = gameState.playerShoot(bx, by, dir);
                        // Шлём клиенту результаты его выстрела
                        ClientHandler handler = clients.get(clientId);
                        if (handler != null) {
                            for (String ev : events) handler.send(ev);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            case "PING" -> {
                ClientHandler handler = clients.get(clientId);
                if (handler != null) handler.send("SERVER;PONG");
            }
        }
    }

    public void broadcast(String message) {
        for (ClientHandler h : clients.values()) h.send(message);
    }

    public void broadcastExcept(String message, String exceptId) {
        for (Map.Entry<String, ClientHandler> e : clients.entrySet())
            if (!e.getKey().equals(exceptId)) e.getValue().send(message);
    }

    public ServerGameState getGameState() { return gameState; }

    public static void main(String[] args) {
        new GameServer(5000).start();
    }
}