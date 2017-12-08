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

import java.util.*;
import java.util.concurrent.*;

public class RequestHandler {

    private int port;
    private boolean done;
    private boolean single;
    private InetAddress ip;
    private Set<String> requests;
    private DatagramSocket socket;
    private CountDownLatch received;
    private Map<Integer, CountDownLatch> sentPackets;

    private static final int MAX_SIZE = 1024;
    private static final int NUM_PACKETS = 8;

    RequestHandler(InetAddress ip, int port, boolean single) {
        this.ip = ip;
        this.port = port;
        this.done = false;
        this.single = single;
        this.received = new CountDownLatch(1);
        this.sentPackets = new ConcurrentHashMap<>();
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
            received = new CountDownLatch(1);
            byte[] serializedData = DataPacket.serialize(new DataPacket(seqNum, content, length));

            while (!receivedPacket) {
                send(serializedData, serializedData.length);
                receivedPacket = received.await(100, TimeUnit.MILLISECONDS);

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

    private void sendPacket(byte[] serializedData, int ackNum) {
        boolean receivedPacket = false;

        while (!receivedPacket) {
            send(serializedData, serializedData.length);
            CountDownLatch acked = new CountDownLatch(1);
            sentPackets.put(ackNum, acked);

            try {
                receivedPacket = acked.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                System.err.println("Failure: " + e.getMessage());
            }
        }
    }
    
    private void selectiveRepeat(FileInputStream fileStream) throws InterruptedException, IOException {
        byte[] content = new byte[MAX_SIZE];
        int count = 0, length = 0, seqNum = 0;
        Queue<Integer> waitedAcks = new LinkedList<>();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_PACKETS + 1);

        executor.submit(() -> {
            while (true) {
                received.await(); // TODO: شيل أمها

                for (String request : requests) {
                    requests.remove(request);

                    AckPacket ack = AckPacket.deserialize(request.getBytes());
                    sentPackets.get(ack.getAckNum()).countDown();
                }

                received = new CountDownLatch(1);
            }
        });

        while ((length = fileStream.read(content)) != -1) {
            byte[] serializedData = DataPacket.serialize(new DataPacket(seqNum, content, length));

            count++;
            seqNum += length;
            final int ackNum = seqNum;
            waitedAcks.add(ackNum);
            executor.submit(() -> sendPacket(serializedData, ackNum));

            while (!sentPackets.containsKey(waitedAcks.peek()) && sentPackets.size() == NUM_PACKETS) {
                received.await(1, TimeUnit.SECONDS);
            }

            while (!waitedAcks.isEmpty() && sentPackets.containsKey(waitedAcks.peek())) {
                sentPackets.remove(waitedAcks.poll());
            }
        }

        System.out.println("Sent chunks count = " + count);
        executor.shutdown();
    }

    public void handleRequest() {
        try {
            received.await();
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
            received.countDown();
        }
    }
}