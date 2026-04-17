/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package javaapplication2;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
/**
 *
 * @author admin
 */
public class UdpServer {

    private final InetAddress[] clientAddresses;
    private final int[] clientPorts;
    private final int serverPort;

    public UdpServer(InetAddress[] clientAddresses, int[] clientPorts, int serverPort) {
        this.clientAddresses = clientAddresses;
        this.clientPorts = clientPorts;
        this.serverPort = serverPort;
        System.out.println("Received: " + resp);
    }

    public double calculateDistributed(double a, double b, double h) throws Exception {

        int K = clientAddresses.length;
        DatagramSocket socket = new DatagramSocket(serverPort);

        double interval = (b - a) / K;

        // ---------- отправка задач клиентам ----------
        for (int i = 0; i < K; i++) {
            double start = a + i * interval;
            double end = (i == K - 1) ? b : start + interval;

            String msg = "TASK;a=" + start + ";b=" + end + ";h=" + h;
            byte[] data = msg.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    clientAddresses[i],
                    clientPorts[i]
            );

            socket.send(packet);
        }

        // ---------- приём результатов ----------
        double total = 0;

        for (int i = 0; i < K; i++) {
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            String resp = new String(packet.getData(), 0, packet.getLength());

            if (resp.startsWith("RESULT;")) {
                String[] parts = resp.split(";");
                double value = Double.parseDouble(parts[1].split("=")[1]);
                total += value;
            }
        }

        socket.close();
        return total;
    }
    
}
