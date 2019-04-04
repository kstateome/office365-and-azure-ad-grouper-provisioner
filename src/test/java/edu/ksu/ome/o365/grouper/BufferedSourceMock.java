package edu.ksu.ome.o365.grouper;

import okio.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class BufferedSourceMock implements BufferedSource {
    @Override
    public long read(Buffer buffer, long l) throws IOException {
        return 0;
    }

    @Override
    public Timeout timeout() {
        return null;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public Buffer buffer() {
        return null;
    }

    @Override
    public boolean exhausted() throws IOException {
        return false;
    }

    @Override
    public void require(long l) throws IOException {

    }

    @Override
    public boolean request(long l) throws IOException {
        return false;
    }

    @Override
    public byte readByte() throws IOException {
        return 0;
    }

    @Override
    public short readShort() throws IOException {
        return 0;
    }

    @Override
    public short readShortLe() throws IOException {
        return 0;
    }

    @Override
    public int readInt() throws IOException {
        return 0;
    }

    @Override
    public int readIntLe() throws IOException {
        return 0;
    }

    @Override
    public long readLong() throws IOException {
        return 0;
    }

    @Override
    public long readLongLe() throws IOException {
        return 0;
    }

    @Override
    public long readDecimalLong() throws IOException {
        return 0;
    }

    @Override
    public long readHexadecimalUnsignedLong() throws IOException {
        return 0;
    }

    @Override
    public void skip(long l) throws IOException {

    }

    @Override
    public ByteString readByteString() throws IOException {
        return null;
    }

    @Override
    public ByteString readByteString(long l) throws IOException {
        return null;
    }

    @Override
    public byte[] readByteArray() throws IOException {
        return "error".getBytes();
    }

    @Override
    public byte[] readByteArray(long l) throws IOException {
        return new byte[0];
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        return 0;
    }

    @Override
    public void readFully(byte[] bytes) throws IOException {

    }

    @Override
    public int read(byte[] bytes, int i, int i1) throws IOException {
        return 0;
    }

    @Override
    public void readFully(Buffer buffer, long l) throws IOException {

    }

    @Override
    public long readAll(Sink sink) throws IOException {
        return 0;
    }

    @Override
    public String readUtf8() throws IOException {
        return null;
    }

    @Override
    public String readUtf8(long l) throws IOException {
        return null;
    }

    @Override
    public String readUtf8Line() throws IOException {
        return null;
    }

    @Override
    public String readUtf8LineStrict() throws IOException {
        return null;
    }

    @Override
    public int readUtf8CodePoint() throws IOException {
        return 0;
    }

    @Override
    public String readString(Charset charset) throws IOException {
        return null;
    }

    @Override
    public String readString(long l, Charset charset) throws IOException {
        return null;
    }

    @Override
    public long indexOf(byte b) throws IOException {
        return 0;
    }

    @Override
    public long indexOf(byte b, long l) throws IOException {
        return 0;
    }

    @Override
    public long indexOf(ByteString byteString) throws IOException {
        return 0;
    }

    @Override
    public long indexOf(ByteString byteString, long l) throws IOException {
        return 0;
    }

    @Override
    public long indexOfElement(ByteString byteString) throws IOException {
        return 0;
    }

    @Override
    public long indexOfElement(ByteString byteString, long l) throws IOException {
        return 0;
    }

    @Override
    public InputStream inputStream() {
        return null;
    }
}
