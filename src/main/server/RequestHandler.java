package main.server;

import main.util.AckPacket;
import main.util.DataPacket;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import java.util.Set;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;

public class RequestHandler {

    private int port;
    private boolean done;
    private boolean single;
    private InetAddress ip;
    private CountDownLatch latch;
    private Set<String> requests;
    private DatagramSocket socket;

    private static final int MAX_SIZE = 1024;

    RequestHandler(InetAddress ip, int port, boolean single) {
        this.ip = ip;
        this.port = port;
        this.done = false;
        this.single = single;
        this.latch = new CountDownLatch(1);
        this.requests = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        try {
            this.socket = new DatagramSocket();
        } catch (SocketException e) {
            System.err.println("Failure: " + e.getMessage());
            return;
        }

        System.out.println("Connection to: " + ip.getHostAddress() + ":" + port + " is initialized.");
    }

    private void stopAndWait(FileInputStream fileStream) throws InterruptedException, IOException {
        boolean receivedPacket;
        byte[] content = new byte[MAX_SIZE];
        int count = 0, length = 0, seqNum = 0;

        while ((length = fileStream.read(content)) != -1) {
            receivedPacket = false;
            latch = new CountDownLatch(1);
            byte[] serializedData = DataPacket.serialize(new DataPacket(seqNum, content, length));

            while (!receivedPacket) {
                send(serializedData, serializedData.length);
                receivedPacket = latch.await(100, TimeUnit.MILLISECONDS);

                if (receivedPacket) {
                    for (String request : requests) {
                        requests.remove(request);

                        AckPacket ack = AckPacket.deserialize(request.getBytes());
                        if (ack.getAckNum() != seqNum + length) {
                            receivedPacket = false;
                        }
                    }
                }
            }

            count++;
            seqNum += length;
        }

        System.out.println("Sent chunks count = " + count);
    }

    private void selectiveRepeat(FileInputStream fileStream) {

    }

    public void handleRequest() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            System.err.println("Failure: " + e.getMessage());
            return;
        }

        for (String request : requests) {
            requests.remove(request);
            System.out.println(request);

            File file = new File(request.split(" ")[1]);

            try {
                FileInputStream fileStream = new FileInputStream(file);

                if (single) {
                    stopAndWait(fileStream);
                } else {
                    selectiveRepeat(fileStream);
                }

                done = true;
                fileStream.close();
            } catch (IOException | InterruptedException e) {
                System.err.println("Failure: " + e.getMessage());
            }

            return;
        }
    }

    private void send(byte[] data, int length) {
        DatagramPacket sendPacket = new DatagramPacket(data, length, ip, port);
        try {
            socket.send(sendPacket);
        } catch (IOException e) {
            System.err.println("Failure: " + e.getMessage());
            return;
        }
    }

    public boolean isDone() {
        return done;
    }

    public void addRequest(byte[] data, int size) {
        if (!done) {
            requests.add(new String(data).substring(0, size));
            latch.countDown();
        }
    }
}