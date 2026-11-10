package dev.adrian.chestindex.paper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class Wire {
    private Wire() {}

    static final class Reader {
        private final byte[] data;
        private int at;

        Reader(byte[] data) {
            this.data = data;
        }

        private byte next() {
            if (at >= data.length) throw new IllegalArgumentException("ChestIndex payload ended early");
            return data[at++];
        }

        int varInt() {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if (shift >= 35) throw new IllegalArgumentException("VarInt too long");
                b = next();
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return value;
        }

        boolean bool() {
            return next() != 0;
        }

        long int64() {
            long value = 0;
            for (int i = 0; i < 8; i++) value = (value << 8) | (next() & 0xFFL);
            return value;
        }

        double float64() {
            return Double.longBitsToDouble(int64());
        }

        String utf(int maxChars) {
            int length = varInt();
            if (length < 0 || length > maxChars * 3) {
                throw new IllegalArgumentException("ChestIndex string of " + length + " bytes");
            }
            if (at + length > data.length) throw new IllegalArgumentException("ChestIndex payload ended early");
            String value = new String(data, at, length, StandardCharsets.UTF_8);
            at += length;
            if (value.length() > maxChars) throw new IllegalArgumentException("ChestIndex string too long");
            return value;
        }
    }

    static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);

        Writer varInt(int value) {
            while ((value & ~0x7F) != 0) {
                out.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            out.write(value);
            return this;
        }

        Writer bool(boolean value) {
            out.write(value ? 1 : 0);
            return this;
        }

        Writer int64(long value) {
            for (int shift = 56; shift >= 0; shift -= 8) out.write((int) (value >>> shift) & 0xFF);
            return this;
        }

        Writer float64(double value) {
            return int64(Double.doubleToRawLongBits(value));
        }

        Writer utf(String value, int maxChars) {
            String text = value == null ? "" : value;
            if (text.length() > maxChars) text = text.substring(0, maxChars);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            varInt(bytes.length);
            out.write(bytes, 0, bytes.length);
            return this;
        }

        Writer bytes(byte[] bytes) {
            out.write(bytes, 0, bytes.length);
            return this;
        }

        int size() {
            return out.size();
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
