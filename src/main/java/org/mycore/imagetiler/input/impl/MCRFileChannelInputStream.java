/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *  
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mycore.imagetiler.input.impl;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import javax.imageio.stream.ImageInputStreamImpl;

public class MCRFileChannelInputStream extends ImageInputStreamImpl {
    private FileChannel input;

    private long mappedPos, mappedUpperBound;

    private MappedByteBuffer mappingBuffer;

    public MCRFileChannelInputStream(FileChannel input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input is null");
        }
        if (!input.isOpen()) {
            throw new IllegalArgumentException("image FileChannel is not open");
        }
        this.input = input;
        long inputPos = input.position();
        this.streamPos = this.flushedPos = inputPos;
        long size = input.size() - inputPos;
        long mappingSize = Math.min(Integer.MAX_VALUE, size);
        this.mappedPos = 0;
        this.mappedUpperBound = mappingSize;
        this.mappingBuffer = input.map(FileChannel.MapMode.READ_ONLY, streamPos, mappingSize);
    }

    @Override
    public int read() throws IOException {
        checkClosed();
        bitOffset = 0;
        prepareRead(1);

        if (mappingBuffer.remaining() < 1) {
            return -1; //EOF
        }

        int value = mappingBuffer.get() & 0xff; //convert signed byte to int (0-255)
        streamPos++;
        return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException(
                "off=" + off + " and len=" + len + " do not work with array length: " + b.length);
        }
        if (len == 0) {
            return 0;
        }

        checkClosed();
        bitOffset = 0;

        prepareRead(len);
        int bytesLeft = mappingBuffer.remaining();

        if (bytesLeft < 1) {
            return -1; //EOF
        } else if (len > bytesLeft) {
            len = bytesLeft; //do not read over end of file
        }

        mappingBuffer.get(b, off, len);
        streamPos += len;
        return len;
    }

    /**
     * Checks if boundaries are sufficient to read len bytes.
     * Will move the mapping region if needed.
     */
    private void prepareRead(int len) throws IOException {
        if (streamPos < mappedPos || streamPos + len >= mappedUpperBound) {
            mappedPos = streamPos;
            long mappingSize = Math.min(input.size() - mappedPos, Integer.MAX_VALUE);
            mappedUpperBound = mappedPos + mappingSize;
            mappingBuffer = input.map(FileChannel.MapMode.READ_ONLY, mappedPos, mappingSize);
            mappingBuffer.order(super.getByteOrder());
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        input = null;
    }

    @Override
    public void setByteOrder(ByteOrder byteOrder) {
        super.setByteOrder(byteOrder);
        mappingBuffer.order(byteOrder);
    }

    @Override
    public long length() {
        try {
            return input.size();
        } catch (IOException e) {
            return -1;
        }
    }

    //--overwrite methods for better performance

    @Override
    public void seek(long pos) throws IOException {
        super.seek(pos);
        if (pos >= mappedPos && pos < mappedUpperBound) {
            //Seek within buffer -> change position
            mappingBuffer.position((int) (pos - mappedPos));
        } else {
            //Seek outside buffer -> prepare read
            int len = (int) Math.min(input.size() - pos, Integer.MAX_VALUE);
            prepareRead(len);
        }
    }

    @Override
    public void readFully(short[] s, int off, int len) throws IOException {
        readFully(off, len, s.length, Short.BYTES, () -> mappingBuffer.asShortBuffer().get(s, off, len));
    }

    @Override
    public void readFully(char[] c, int off, int len) throws IOException {
        readFully(off, len, c.length, Character.BYTES, () -> mappingBuffer.asCharBuffer().get(c, off, len));
    }

    @Override
    public void readFully(int[] i, int off, int len) throws IOException {
        readFully(off, len, i.length, Integer.BYTES, () -> mappingBuffer.asIntBuffer().get(i, off, len));
    }

    @Override
    public void readFully(long[] l, int off, int len) throws IOException {
        readFully(off, len, l.length, Long.BYTES, () -> mappingBuffer.asLongBuffer().get(l, off, len));
    }

    @Override
    public void readFully(float[] f, int off, int len) throws IOException {
        readFully(off, len, f.length, Float.BYTES, () -> mappingBuffer.asFloatBuffer().get(f, off, len));
    }

    @Override
    public void readFully(double[] d, int off, int len) throws IOException {
        readFully(off, len, d.length, Double.BYTES, () -> mappingBuffer.asDoubleBuffer().get(d, off, len));
    }

    private void readFully(int off, int len, int arrLength, int byteSize, ReadOperation op) throws IOException {
        if (off < 0 || len < 0 || off + len > arrLength) {
            throw new IndexOutOfBoundsException(
                "off=" + off + " and len=" + len + " do not work with array length: " + arrLength);
        } else if (len == 0) {
            return;
        }
        int byteLen = byteSize * len;
        prepareRead(byteLen);
        if (mappingBuffer.remaining() < byteLen) {
            throw new EOFException();
        }
        op.read();
        seek(streamPos + byteLen); //update position
    }

    private interface ReadOperation {
        void read() throws IOException;
    }
}
