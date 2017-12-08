package main.server;

import java.io.IOException;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class Server {

    private static final int MAX_SIZE = 2048;
    private static final int THREADS_NUMBER = 128;

    public static void main(String[] args) {
        DatagramSocket serverSocket = null;

        try {
            serverSocket = new DatagramSocket(9876);
        } catch (SocketException e) {
            System.err.println("Failure: " + e.getMessage());
            return;
        }

        DatagramPacket receivePacket;
        byte[] receiveData = new byte[MAX_SIZE];
        Map<String, RequestHandler> handlers = new HashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS_NUMBER);

        while (true) {
            receivePacket = new DatagramPacket(receiveData, receiveData.length);

            try {
                serverSocket.receive(receivePacket);
            } catch (IOException e) {
                System.err.println("Failure: " + e.getMessage());
                continue;
            }

            //TODO: parallelize.
            int port = receivePacket.getPort();
            InetAddress IPAddress = receivePacket.getAddress();

            String key = IPAddress.getHostAddress() + ":" + port;

            handlers.computeIfPresent(key, (k, v) -> v.isDone() ? null : v);

            handlers.computeIfAbsent(
                    key, (k) -> {
                        RequestHandler handler = new RequestHandler(IPAddress, port, true); //TODO: set Boolean.
                        executor.execute(() -> handler.handleRequest());
                        return handler;
                    });

            handlers.get(key).addRequest(receivePacket.getData(), receivePacket.getLength());
        }
    }

}