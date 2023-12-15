package jukebox.core;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDeviceBase;

import javax.sound.sampled.*;

public class AudioDevice extends AudioDeviceBase {

    private final Line externalLine;
    private SourceDataLine source = null;
    private AudioFormat fmt = null;
    private byte[] byteBuf = new byte[4096];

    public AudioDevice(Line line) {
        externalLine = line;
    }

    public int getPosition() {
        int pos = 0;
        if (this.source != null) {
            pos = (int) (this.source.getMicrosecondPosition() / 1000L);
        }

        return pos;
    }

    protected AudioFormat getAudioFormat() {
        if (this.fmt == null) {
            Decoder decoder = this.getDecoder();
            this.fmt = new AudioFormat(
                    (float) decoder.getOutputFrequency(),
                    16,
                    decoder.getOutputChannels(),
                    true,
                    false
            );
        }

        return this.fmt;
    }

    protected void setAudioFormat(AudioFormat fmt0) {
        this.fmt = fmt0;
    }

    protected DataLine.Info getSourceLineInfo() {
        AudioFormat fmt = this.getAudioFormat();
        return new DataLine.Info(SourceDataLine.class, fmt);
    }

    protected void openImpl() {
    }

    protected void createSource() throws JavaLayerException {
        Throwable t = null;

        try {
            Line line = AudioSystem.getLine(this.getSourceLineInfo());
            if (line instanceof SourceDataLine) {
                this.source = externalLine != null ? (SourceDataLine) externalLine : (SourceDataLine) line;
                if (!source.isOpen()) {
                    this.source.open(this.fmt);
                    this.source.start();
                }
            }
        }
        catch (RuntimeException | LinkageError | LineUnavailableException var3) {
            t = var3;
        }

        if (this.source == null) {
            throw new JavaLayerException("cannot obtain source audio line", t);
        }
    }

    protected void closeImpl() {
        if (this.source != null) {
            this.source.close();
        }

    }

    protected void writeImpl(short[] samples, int offs, int len) throws JavaLayerException {
        if (this.source == null) {
            this.createSource();
        }

        byte[] b = this.toByteArray(samples, offs, len);
        this.source.write(b, 0, len * 2);
    }

    protected byte[] getByteArray(int length) {
        if (this.byteBuf.length < length) {
            this.byteBuf = new byte[length + 1024];
        }

        return this.byteBuf;
    }

    protected byte[] toByteArray(short[] samples, int offs, int len) {
        byte[] b = this.getByteArray(len * 2);

        short s;
        for (int idx = 0; len-- > 0; b[idx++] = (byte) (s >>> 8)) {
            s = samples[offs++];
            b[idx++] = (byte) s;
        }

        return b;
    }

    protected void flushImpl() {
        if (this.source != null) {
            this.source.drain();
        }

    }
}
