package io.github.tomusin.voyager.utils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class BitReader {
    private final BitByteBuffer buffer;
    private int bitPosInByte;  // 0 = MSB, 7 = LSB
    private byte currentByte;
    private int remainingBytes; // bytes left in buffer

    public BitReader(BitByteBuffer buffer) {
        // Duplicate to avoid changing the caller's position
        this.buffer = buffer.duplicate();
        this.remainingBytes = buffer.remaining();
        

        if (remainingBytes > 0) {
            this.currentByte = this.buffer.get();
        }
        this.bitPosInByte = 0; // start at MSB
    }
    
    public BitReader(BitByteBuffer buffer, int startIndex) {
        // Duplicate so we don't modify the caller's buffer position
        this.buffer = buffer.duplicate();
        
        if (startIndex < 0 || startIndex >= buffer.limit()) {
            throw new IllegalArgumentException("startIndex out of range");
        }
        
        // Jump to the starting byte
        this.buffer.position(startIndex);

        // How many bytes left after start
        this.remainingBytes = buffer.limit() - startIndex;

        if (remainingBytes > 0) {
            this.currentByte = this.buffer.get();
        }
        this.bitPosInByte = 0;
    }
    
	public int nibblerGetSigned() {
	    int value = 0;
	    int shift = 0;
	    int b;
	    do {
	        b = readBits(8); // read full byte
	        value |= (b & 0x7F) << shift;
	        shift += 7;
	    } while ((b & 0x80) != 0);

	    // Interpret as signed (ZigZag decoding if needed)
	    // ZigZag: even = positive, odd = negative
	    return (value >>> 1) ^ -(value & 1);
	}
    
    /**
     * Moves the reading position n bits back from the current position.
     */
    public void rewindBits(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Cannot rewind a negative number of bits");
        }

        // Current absolute bit index from start of buffer
        int bitsFromStart = (buffer.position() - 1) * 8 + bitPosInByte;
        // When bitPosInByte == 0, current byte is the one already loaded
        if (bitPosInByte == 0) {
            bitsFromStart = (buffer.position() * 8);
        }

        int newBitIndex = bitsFromStart - n;
        if (newBitIndex < 0) {
            throw new IndexOutOfBoundsException("Rewind goes before start of buffer");
        }

        // Jump to new absolute bit position
        int byteIndex = newBitIndex / 8;
        bitPosInByte = newBitIndex % 8;

        buffer.position(byteIndex);
        remainingBytes = buffer.limit() - byteIndex;
        if (remainingBytes > 0) {
            currentByte = buffer.get();
        } else {
            currentByte = 0;
        }
    }
    
    public int readSignedBits(int n) {
        int val = readBits(n); // read unsigned bits as usual
        int signBit = 1 << (n - 1);
        if ((val & signBit) != 0) {
            // Negative number: sign extend
            val |= ~((1 << n) - 1);
        }
        return val;
    }

    /** Reads the next bit (MSB first) */
    public int readBit() {
        if (remainingBytes <= 0) {
            throw new IndexOutOfBoundsException("No more bits to read");
        }

        int bit = (currentByte >> (7 - bitPosInByte)) & 1;
//        int bit = (currentByte >> bitPosInByte) & 1; // LSB first
        bitPosInByte++;

        if (bitPosInByte == 8) {
            bitPosInByte = 0;
            remainingBytes--;
            if (remainingBytes > 0) {
                currentByte = buffer.get();
            }
        }
        return bit;
    }
    
    public int getPosition() {
        // number of *bytes* already consumed in the buffer
        int byteIndex = buffer.position() - 1;  
        if (byteIndex < 0) byteIndex = 0;

        // if bitPosInByte == 0 we are still at the beginning of the current byte
        return byteIndex * 8 + bitPosInByte;
    }

    /** Reads 'n' bits as an int */
    public int readBits(int n) {
        int value = 0;
        for (int i = 0; i < n; i++) {
            value = (value << 1) | readBit();
        }
        return value;
    }

    /** Number of bits remaining */
    public int bitsRemaining() {
        return remainingBytes * 8 - bitPosInByte;
    }
}


