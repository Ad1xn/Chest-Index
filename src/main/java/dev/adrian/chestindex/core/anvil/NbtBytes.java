package dev.adrian.chestindex.core.anvil;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class NbtBytes implements DataInput {
    private final byte[] data;
    private final int end;
    private int at;

    NbtBytes(byte[] data, int length) {
        this.data = data;
        this.end = length;
    }

    private void need(int bytes) throws EOFException {
        if (end - at < bytes) throw new EOFException("NBT ends " + (bytes - (end - at)) + " byte(s) early");
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        need(len);
        System.arraycopy(data, at, b, off, len);
        at += len;
    }

    @Override
    public int skipBytes(int n) {
        int skipped = Math.max(0, Math.min(n, end - at));
        at += skipped;
        return skipped;
    }

    @Override
    public boolean readBoolean() throws IOException {
        return readByte() != 0;
    }

    @Override
    public byte readByte() throws IOException {
        need(1);
        return data[at++];
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return readByte() & 0xFF;
    }

    @Override
    public short readShort() throws IOException {
        return (short) readUnsignedShort();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        need(2);
        int value = ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
        at += 2;
        return value;
    }

    @Override
    public char readChar() throws IOException {
        return (char) readUnsignedShort();
    }

    @Override
    public int readInt() throws IOException {
        need(4);
        int value = ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16)
                | ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
        at += 4;
        return value;
    }

    @Override
    public long readLong() throws IOException {
        return ((long) readInt() << 32) | (readInt() & 0xFFFFFFFFL);
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public String readLine() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String readUTF() throws IOException {
        int length = readUnsignedShort();
        need(length);
        boolean ascii = true;
        for (int i = at; i < at + length; i++) {
            if (data[i] < 0) {
                ascii = false;
                break;
            }
        }
        if (ascii) {
            String value = new String(data, at, length, StandardCharsets.ISO_8859_1);
            at += length;
            return value;
        }
        at -= 2;
        return DataInputStream.readUTF(this);
    }
}
