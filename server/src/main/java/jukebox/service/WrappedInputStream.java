package jukebox.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ListIterator;

class WrappedInputStream extends InputStream {
    private final PlayerService playerService;
    InputStream innerStream;

    WrappedInputStream(PlayerService playerService, InputStream innerStream) {
        this.playerService = playerService;
        this.innerStream = innerStream;
    }

    @Override
    public int read() throws IOException {
        return innerStream.read();
    }

    @Override
    public String toString() {
        return innerStream.toString();
    }

    @Override
    public synchronized void reset() throws IOException {
        innerStream.reset();
    }

    @Override
    public void close() throws IOException {
        innerStream.close();
    }

    @Override
    public long skip(long n) throws IOException {
        return innerStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return innerStream.available();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = innerStream.read(b, off, len);
        ListIterator<OutputStream> iterator = playerService.audioStreams.listIterator();
        while (iterator.hasNext()) {
            OutputStream stream = iterator.next();
            try {
                stream.write(b);
            }
            catch (IOException e) {
                iterator.remove();
            }
        }
        return read;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return innerStream.read(b);
    }

    @Override
    public synchronized void mark(int readlimit) {
        innerStream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return innerStream.markSupported();
    }
}
