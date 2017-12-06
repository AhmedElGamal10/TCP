package main.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class DataPacket {

    private int seqNum;
    private int length;
    private byte[] data;

    public DataPacket(int seqNum, byte[] data, int length) {
        this.data = data;
        this.length = length;
        this.seqNum = seqNum;
    }

    public int getLength() {
        return length;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public byte[] getData() {
        return data;
    }

    public static byte[] serialize(DataPacket packet) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        try {
            outputStream.write(String.format("%010d", packet.getSeqNum()).getBytes());
            outputStream.write(Arrays.copyOfRange(packet.getData(), 0, packet.getLength()));
        } catch (IOException e) {
            System.err.println("Failure!");
        }
        return outputStream.toByteArray();
    }

    public static DataPacket deserialize(byte[] data, int length) {
        return new DataPacket(
                Integer.parseInt(new String(Arrays.copyOfRange(data, 0, 10))),
                Arrays.copyOfRange(data, 10, length),
                length - 10);
    }

}
