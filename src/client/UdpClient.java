/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 *
 * @author admin
 */
public class UdpClient {

    public static void main(String[] args) throws Exception {

        int listenPort = 5002; // порт клиента
        DatagramSocket socket = new DatagramSocket(listenPort);
        
        // ---------- регистрация клиента на сервере ----------
        String hello = "HELLO";
        DatagramPacket helloPacket = new DatagramPacket(
                hello.getBytes(),
                hello.length(),
                InetAddress.getByName("127.0.0.1"), // адрес сервера
                6000                                 // порт сервера
        );
        socket.send(helloPacket);

        System.out.println("Client registered on server");
        
        System.out.println("UDP Client started, waiting for tasks...");

        while (true) {

            // ---------- получение задачи ----------
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            String msg = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received task: " + msg);

            if (!msg.startsWith("TASK;"))
                continue;

            double a = 0, b = 0, h = 0;

            String[] parts = msg.split(";");
            for (String p : parts) {
                if (p.startsWith("a=")) a = Double.parseDouble(p.substring(2));
                if (p.startsWith("b=")) b = Double.parseDouble(p.substring(2));
                if (p.startsWith("h=")) h = Double.parseDouble(p.substring(2));
            }

            // ---------- вычисление ----------
            double result = calculateIntegralMultithread(a, b, h);

            // ---------- отправка результата ----------
            String resp = "RESULT;value=" + result;
            byte[] out = resp.getBytes();

            DatagramPacket respPacket = new DatagramPacket(
                    out,
                    out.length,
                    packet.getAddress(),   // адрес отправителя TASK
                    packet.getPort()       // порт отправителя TASK
            );

            socket.send(respPacket);
        }
    }

    private static double calculateIntegralMultithread(double a, double b, double h) {
        int threadsCount = 2;
        IntegralThread[] threads = new IntegralThread[threadsCount];

        double interval = (b - a) / threadsCount;

        for (int i = 0; i < threadsCount; i++) {
            double start = a + i * interval;
            double end = (i == threadsCount - 1) ? b : start + interval;

            threads[i] = new IntegralThread(start, end, h);
            threads[i].start();
        }

        for (IntegralThread t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignored) {}
        }

        double total = 0;
        for (IntegralThread t : threads) {
            total += t.getResult();
        }

        return total;
    }

    
}
