package main.server;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import java.util.Set;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;

public class RequestHandler {

    private int port;
    private InetAddress ip;
    private CountDownLatch latch;
    private Set<String> requests;
    private DatagramSocket clientSocket;

    public static final int MAX_SIZE = 1024;

    RequestHandler(InetAddress ip, int port) {
        this.ip = ip;
        this.port = port;
        this.latch = new CountDownLatch(1);
        this.requests = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        try {
            this.clientSocket = new DatagramSocket();
        } catch (SocketException e) {
            System.err.println("Failure: " + e.getMessage());
            return;
        }

        System.out.println("Connection to: " + ip.getHostAddress() + ":" + port + " is initialized.");
    }

    public void handleRequest() {
        while (true) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                System.err.println("Failure: " + e.getMessage());
                return;
            }

            latch = new CountDownLatch(1);

            for (String request : requests) {
                requests.remove(request);
                System.out.println(request);
                File file = new File(request.split(" ")[1]);

                try {
                    byte[] content = new byte[MAX_SIZE];
                    FileInputStream fileStream = new FileInputStream(file);
                    System.out.println("File size = " + file.length());

                    int count = 0, length = 0;
                    while ((length = fileStream.read(content)) != -1) {
                        send(content, length);
                        Thread.sleep(25);
                        count++;
                    }

                    System.out.println("Count = " + count);
                } catch (IOException e) {
                    System.err.println("Failure: " + e.getMessage());
                    continue;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void send(byte[] data, int length) {
        DatagramPacket sendPacket = new DatagramPacket(data, length, ip, port);
        try {
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            System.err.println("Failure: " + e.getMessage());
            return;
        }
    }

    public void addRequest(byte[] data, int size) {
        requests.add(new String(data).substring(0, size));
        latch.countDown();
    }
}