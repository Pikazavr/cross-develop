/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package javaapplication2;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
/**
 *
 * @author admin
 */
public class UdpServer {
    private final int serverPort;
    private final List<ClientInfo> clients = new ArrayList<>();

    public UdpServer(int serverPort) {
        this.serverPort = serverPort;
    }

    // регистрация 
    public void listenForClients() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(serverPort)) {
                System.out.println("Server listening for clients on port " + serverPort);

                while (true) {
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength());

                    if (msg.startsWith("HELLO")) {
                        clients.add(new ClientInfo(packet.getAddress(), packet.getPort()));
                        System.out.println("Client registered: " +
                                packet.getAddress() + ":" + packet.getPort());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // распределённое вычисление
    public double calculateDistributed(double a, double b, double h) throws Exception {

        int K = clients.size();
        if (K == 0) {
            throw new Exception("Нет подключённых клиентов!");
        }

        DatagramSocket socket = new DatagramSocket();

        double interval = (b - a) / K;

        // отправляем задачи
        for (int i = 0; i < K; i++) {
            ClientInfo c = clients.get(i);

            double start = a + i * interval;
            double end = (i == K - 1) ? b : start + interval;

            String msg = "TASK;a=" + start + ";b=" + end + ";h=" + h;
            byte[] data = msg.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    c.addr,
                    c.port
            );

            System.out.println("Sending to " + c.addr + ":" + c.port + " → " + msg);
            socket.send(packet);
        }

        //  принимаем результаты
        double total = 0;

        for (int i = 0; i < K; i++) {
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            String resp = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received: " + resp);

            if (resp.startsWith("RESULT;")) {
                String[] parts = resp.split(";");
                double value = Double.parseDouble(parts[1].split("=")[1]);
                total += value;
            }
        }

        socket.close();
        return total;
    }

    // клиент
    private static class ClientInfo {
        InetAddress addr;
        int port;

        ClientInfo(InetAddress addr, int port) {
            this.addr = addr;
            this.port = port;
        }
    }
}
