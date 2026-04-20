package io.github.tomusin.Voyager;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.tomusin.lodDataRecords.PointQuantizerDataRecord;
import io.github.tomusin.lodDataRecords.UniformQuantizerDataRecord;
import io.github.tomusin.lodDataRecords.CompressedVertexCoordinateArrayRecord;
import io.github.tomusin.voyager.Main;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class TopologicallyCompressedVertexRecordsTest {

    @Test
    void pointQuantizerDataOffsetsAreChained() {
        // 3 UniformQuantizerData = 3 x 9 bytes = 27 bytes
        ByteBuffer bb = ByteBuffer.allocate(27).order(ByteOrder.LITTLE_ENDIAN);
        // X: min=0.0, max=10.0, bits=8
        bb.putFloat(0, 0.0f);
        bb.putFloat(4, 10.0f);
        bb.put(8, (byte) 8);
        // Y: min=-5.0, max=5.0, bits=16
        bb.putFloat(9, -5.0f);
        bb.putFloat(13, 5.0f);
        bb.put(17, (byte) 16);
        // Z: min=1.0, max=100.0, bits=12
        bb.putFloat(18, 1.0f);
        bb.putFloat(22, 100.0f);
        bb.put(26, (byte) 12);
        
        var buffer = new io.github.tomusin.voyager.utils.BitByteBuffer(bb);
        PointQuantizerDataRecord pqd = PointQuantizerDataRecord.fromByteBuffer(buffer, 0);
        
        assertEquals(0.0f, pqd.xUniformQuantizerData().min());
        assertEquals(10.0f, pqd.xUniformQuantizerData().max());
        assertEquals(8, pqd.xUniformQuantizerData().numberOfBits());
        assertEquals(9, pqd.xUniformQuantizerData().jtEndIndex());
        
        assertEquals(-5.0f, pqd.yUniformQuantizerData().min());
        assertEquals(5.0f, pqd.yUniformQuantizerData().max());
        assertEquals(16, pqd.yUniformQuantizerData().numberOfBits());
        assertEquals(18, pqd.yUniformQuantizerData().jtEndIndex());
        
        assertEquals(1.0f, pqd.zUniformQuantizerData().min());
        assertEquals(100.0f, pqd.zUniformQuantizerData().max());
        assertEquals(12, pqd.zUniformQuantizerData().numberOfBits());
        assertEquals(27, pqd.zUniformQuantizerData().jtEndIndex());
        
        assertEquals(27, pqd.jtEndIndex());
    }
    
    @Test
    void dequantizationProducesCorrectValues() {
        // Test the dequantization formula: value = min + code * (max - min) / (2^bits - 1)
        UniformQuantizerDataRecord xq = new UniformQuantizerDataRecord(0.0f, 255.0f, 8, 'x', 9);
        UniformQuantizerDataRecord yq = new UniformQuantizerDataRecord(-10.0f, 10.0f, 8, 'y', 18);
        UniformQuantizerDataRecord zq = new UniformQuantizerDataRecord(0.0f, 1.0f, 8, 'z', 27);
        PointQuantizerDataRecord pqd = new PointQuantizerDataRecord(xq, yq, zq);
        
        // Create a CompressedVertexCoordinateArrayRecord with known quantized codes
        var xCodes = new io.github.tomusin.voyager.datastructures.VecI32(3, new int[]{0, 127, 255}, 0);
        var yCodes = new io.github.tomusin.voyager.datastructures.VecI32(3, new int[]{0, 127, 255}, 0);
        var zCodes = new io.github.tomusin.voyager.datastructures.VecI32(3, new int[]{0, 127, 255}, 0);
        
        CompressedVertexCoordinateArrayRecord rec = new CompressedVertexCoordinateArrayRecord(
            3, 3, pqd, null, new io.github.tomusin.voyager.datastructures.VecI32[]{xCodes, yCodes, zCodes}, 0, 100
        );
        
        float[][] coords = rec.dequantize();
        
        // X: min=0, max=255, code 0 -> 0, code 127 -> 127, code 255 -> 255
        assertEquals(0.0f, coords[0][0], 0.01f);
        assertEquals(127.0f, coords[0][1], 0.01f);
        assertEquals(255.0f, coords[0][2], 0.01f);
        
        // Y: min=-10, max=10, code 0 -> -10, code 127 -> ~-0.08, code 255 -> 10
        assertEquals(-10.0f, coords[1][0], 0.01f);
        assertEquals(10.0f, coords[1][2], 0.01f);
        
        // Z: min=0, max=1, code 0 -> 0, code 255 -> 1
        assertEquals(0.0f, coords[2][0], 0.01f);
        assertEquals(1.0f, coords[2][2], 0.01f);
    }

    @Test
    void fullPipelineRunsWithoutException() {
        java.util.Set<String> filePaths = new java.util.LinkedHashSet<>();
        filePaths.add("E:\\JTReaderCollection\\JTReader\\JTReader\\Voyager\\src\\main\\resources\\example_block_jt10.3.jt");
        assertDoesNotThrow(() -> Main.run(
                new io.github.tomusin.voyager.CliArgs(io.github.tomusin.voyager.CliArgs.Mode.PARSE, "INFO", false, true, null, filePaths)
        ));
    }
}
