package main.client;

import java.io.*;

import java.net.*;
import java.util.concurrent.*;

public class Client {

    public static final int MAX_SIZE = 1024;

    public static void main(String[] args) {
        DatagramSocket clientSocket = null;

        try {
            clientSocket = new DatagramSocket(5554);
        } catch (SocketException e) {
            System.err.println("Failure: " + e.getMessage());
            return;
        }

        InetAddress IPAddress = null;
        try {
            IPAddress = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            System.err.println("Failure: " + e.getMessage());
            clientSocket.close();
            return;
        }

        String sentence = "GET dummy.pdf";
        byte[] sendData = sentence.getBytes();
        byte[] receiveData = new byte[MAX_SIZE];
        ExecutorService executor = Executors.newSingleThreadExecutor();

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9876);
        try {
            clientSocket.send(sendPacket);

            File file = new File("tempDummy.pdf");
            DatagramSocket finalClientSocket = clientSocket;
            FileOutputStream fileStream = new FileOutputStream(file);

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                Future future = executor.submit(() -> {
                    try {
                        finalClientSocket.receive(receivePacket);
                    } catch (IOException e) { }
                });
                future.get(1, TimeUnit.SECONDS);
                fileStream.write(receivePacket.getData(), 0, receivePacket.getLength());
            }

        } catch (IOException | InterruptedException | ExecutionException e) {
            System.err.println("Failure: " + e.getMessage());
        } catch (TimeoutException e) {
            System.out.println("Finished!");
        }

        clientSocket.close();
        executor.shutdown();
    }

}