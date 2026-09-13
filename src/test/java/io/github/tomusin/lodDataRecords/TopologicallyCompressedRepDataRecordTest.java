package io.github.tomusin.lodDataRecords;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.utils.BitByteBuffer;

class TopologicallyCompressedRepDataRecordTest {

    @Test
    void chopperReconstructsValuesFromHighAndLowChunks() {
        ByteBuffer bytes = ByteBuffer.allocate(11 + 2 * (9 + 3 * Integer.BYTES)).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(3).put((byte) 4).put((byte) 3).putInt(100).put((byte) 7);
        putNullCdp(bytes, 0, 1, 2);
        putNullCdp(bytes, 1, 2, 3);

        VecI32 decoded = TopologicallyCompressedRepDataRecord.readInt32CDP(
                new BitByteBuffer((ByteBuffer) bytes.flip()), 0,
                TopologicallyCompressedRepDataRecord.PredictorType.PredNULL);

        assertArrayEquals(new int[] {101, 118, 135}, decoded.valueArray());
        assertEquals(bytes.limit(), decoded.jtEndIndex());
    }

    @Test
    void arithmeticDoesNotConsumeOutOfBandPacketWithoutEscapeSymbol() {
        ByteBuffer bytes = ByteBuffer.allocate(29).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(1).put((byte) 3).putInt(16).putInt(0);
        putProbabilityContext(bytes, 1, 1, 2, 10, false, 1, 3);

        VecI32 decoded = TopologicallyCompressedRepDataRecord.readInt32CDP(
                new BitByteBuffer((ByteBuffer) bytes.flip()), 0,
                TopologicallyCompressedRepDataRecord.PredictorType.PredNULL);

        assertArrayEquals(new int[] {13}, decoded.valueArray());
        assertEquals(bytes.limit(), decoded.jtEndIndex());
    }

    @Test
    void arithmeticResolvesEscapeSymbolsFromOutOfBandPacket() {
        ByteBuffer bytes = ByteBuffer.allocate(35).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(1).put((byte) 3).putInt(16).putInt(0);
        putProbabilityContext(bytes, 1, 1, 2, 0, true, 1, 0);
        putNullCdp(bytes, 42);

        VecI32 decoded = TopologicallyCompressedRepDataRecord.readInt32CDP(
                new BitByteBuffer((ByteBuffer) bytes.flip()), 0,
                TopologicallyCompressedRepDataRecord.PredictorType.PredNULL);

        assertArrayEquals(new int[] {42}, decoded.valueArray());
        assertEquals(bytes.limit(), decoded.jtEndIndex());
    }

    private static void putNullCdp(ByteBuffer bytes, int... values) {
        bytes.putInt(values.length).put((byte) 0).putInt(values.length * Integer.SIZE);
        for (int value : values) bytes.putInt(value);
    }

    private static void putProbabilityContext(ByteBuffer bytes, int entryCount, int occurrenceBits,
            int valueBits, int minValue, boolean escape, int occurrenceCount, int associatedValue) {
        String bits = String.format("%16s", Integer.toBinaryString(entryCount)).replace(' ', '0')
                + String.format("%6s", Integer.toBinaryString(occurrenceBits)).replace(' ', '0')
                + String.format("%7s", Integer.toBinaryString(valueBits)).replace(' ', '0')
                + String.format("%32s", Integer.toBinaryString(minValue)).replace(' ', '0')
                + (escape ? "1" : "0")
                + String.format("%" + occurrenceBits + "s", Integer.toBinaryString(occurrenceCount)).replace(' ', '0')
                + String.format("%" + valueBits + "s", Integer.toBinaryString(associatedValue)).replace(' ', '0');
        while (bits.length() % Byte.SIZE != 0) bits += "0";
        for (int index = 0; index < bits.length(); index += Byte.SIZE) {
            bytes.put((byte) Integer.parseInt(bits.substring(index, index + Byte.SIZE), 2));
        }
    }

}