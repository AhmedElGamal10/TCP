package main.client;

import main.util.AckPacket;
import main.util.DataPacket;

import javax.xml.crypto.Data;
import java.io.*;

import java.net.*;
import java.util.concurrent.*;

public class Client {

    public static final int MAX_SIZE = 2048;

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

        FileOutputStream fileStream = null;
        String sentence = "GET dummy.pdf";
        byte[] sendData = sentence.getBytes();
        byte[] receiveData = new byte[MAX_SIZE];
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            clientSocket.send(new DatagramPacket(sendData, sendData.length, IPAddress, 9876));

            System.out.println("Sent get request!");

            File file = new File("tempDummy.pdf");
            DatagramSocket finalClientSocket = clientSocket;
            fileStream = new FileOutputStream(file);

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                Future future = executor.submit(() -> {
                    try {
                        finalClientSocket.receive(receivePacket);
                    } catch (IOException e) { }
                });
                future.get(1, TimeUnit.SECONDS);

                DataPacket packet = DataPacket.deserialize(receivePacket.getData(), receivePacket.getLength());
                fileStream.write(packet.getData(), 0, packet.getLength());

                sendData = AckPacket.serialize(new AckPacket(packet.getSeqNum() + packet.getLength()));
                clientSocket.send(new DatagramPacket(sendData, sendData.length, IPAddress, 9876));
            }

        } catch (IOException | InterruptedException | ExecutionException e) {
            System.err.println("Failure: " + e.getMessage());
        } catch (TimeoutException e) {
            System.out.println("Finished!");
        }

        try {
            fileStream.close();
        } catch (IOException e) {
            System.out.println("No file to close!");
        }
        clientSocket.close();
        executor.shutdown();
    }

}