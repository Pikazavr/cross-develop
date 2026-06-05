package kurs;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
public class GameClient {

    public interface MessageListener {
        void onMessage(String message);
    }

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;
    private final MessageListener listener;

    public GameClient(String host, int port, MessageListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    // Подключение (блокирующее). Можно вызывать в отдельном потоке.
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        running = true;

        // Запускаем поток чтения
        executor.submit(this::readLoop);
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                if (listener != null) {
                    listener.onMessage(line);
                }
            }
        } catch (IOException ignored) {
        } finally {
            close();
        }
    }

    // Безопасная отправка сообщения
    public synchronized void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // Закрытие клиента
    public synchronized void close() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
        if (out != null) out.close();
        executor.shutdownNow();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}
