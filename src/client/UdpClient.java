/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 *
 * @author admin
 */
public class UdpClient {

    public static void main(String[] args) throws Exception {

        int listenPort = 5001; // порт клиента
        DatagramSocket socket = new DatagramSocket(listenPort);
        
        // регистрация клиента на сервере
        String hello = "HELLO";
        DatagramPacket helloPacket = new DatagramPacket(
                hello.getBytes(),
                hello.length(),
                InetAddress.getByName("127.0.0.1"), 
                6000
        );
        socket.send(helloPacket);

        System.out.println("Client registered on server");
        
        System.out.println("UDP Client started, waiting for tasks...");

        while (true) {

            // получение задачи 
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

            // вычисление с обработкой ошибок
            try {
                double result = calculateIntegralMultithread(a, b, h);
                String resp = "RESULT;value=" + result;
                byte[] out = resp.getBytes();
                DatagramPacket respPacket = new DatagramPacket(
                        out,
                        out.length,
                        packet.getAddress(),
                        packet.getPort()
                );
                socket.send(respPacket);
                System.out.println("Sent result: " + result);
            } catch (Exception e) {
                e.printStackTrace();
                String resp = "RESULT;error=" + e.getMessage();
                byte[] out = resp.getBytes();
                DatagramPacket respPacket = new DatagramPacket(
                        out,
                        out.length,
                        packet.getAddress(),
                        packet.getPort()
                );
                socket.send(respPacket);
                System.out.println("Sent error: " + e.getMessage());
            }
        }
    }

    private static double calculateIntegralMultithread(double a, double b, double h) throws Exception {
        int threadsCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        Future<Double>[] futures = new Future[threadsCount];

        double interval = (b - a) / threadsCount;

        for (int i = 0; i < threadsCount; i++) {
            double start = a + i * interval;
            double end = (i == threadsCount - 1) ? b : start + interval;

            IntegralTask task = new IntegralTask(start, end, h);
            futures[i] = executor.submit(task);
        }

        double total = 0;
        for (Future<Double> future : futures) {
            total += future.get(); // ждём результат
        }

        executor.shutdown();
        return total;
    }
    
}
