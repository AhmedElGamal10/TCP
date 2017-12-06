package main.client;

import main.util.AckPacket;
import main.util.DataPacket;

import java.io.*;

import java.net.*;

import java.util.concurrent.*;

public class Client {

    private int expectedSeqNum;
    private InetAddress IPAddress;
    private DatagramSocket clientSocket;
    private static final int MAX_SIZE = 2048;

    private void stopAndWait(FileOutputStream fileStream, DatagramPacket receivePacket) throws IOException {
        DataPacket packet = DataPacket.deserialize(receivePacket.getData(), receivePacket.getLength());

        if (packet.getSeqNum() == expectedSeqNum) {
            expectedSeqNum += packet.getLength();
            fileStream.write(packet.getData(), 0, packet.getLength());
        }

        byte[] sendData = AckPacket.serialize(new AckPacket(expectedSeqNum));
        clientSocket.send(new DatagramPacket(sendData, sendData.length, IPAddress, 9876));
    }

    private void selectiveRepeat(FileOutputStream fileStream, DatagramPacket receivePacket) {

    }

    private void init() {
        try {
            clientSocket = new DatagramSocket(5554);
        } catch (SocketException e) {
            System.err.println("Failure: " + e.getMessage());
            return;
        }

        try {
            IPAddress = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            System.err.println("Failure: " + e.getMessage());
            clientSocket.close();
            return;
        }

        FileOutputStream fileStream = null;
        String sentence = "GET dummy.pdf";
        byte[] receiveData = new byte[MAX_SIZE];
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            clientSocket.send(
                    new DatagramPacket(
                            sentence.getBytes(), sentence.getBytes().length, IPAddress, 9876));

            System.out.println("Sent get request!");

            expectedSeqNum = 0;
            DatagramSocket finalClientSocket = clientSocket;
            fileStream = new FileOutputStream(new File("tempDummy.pdf"));

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                Future future = executor.submit(() -> {
                    try {
                        finalClientSocket.receive(receivePacket);
                    } catch (IOException e) { }
                });
                future.get(10, TimeUnit.SECONDS);


                //TODO: parallelize.
                if (true) { //TODO: change to case switch.
                    stopAndWait(fileStream, receivePacket);
                } else {
                    selectiveRepeat(fileStream, receivePacket);
                }
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

    public static void main(String[] args) {
        (new Client()).init();
    }

}