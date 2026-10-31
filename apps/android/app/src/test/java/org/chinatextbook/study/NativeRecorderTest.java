package org.chinatextbook.study;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class NativeRecorderTest {
    @Test public void wavHeaderDescribes16BitMonoPcm() {
        byte[] samples = {1, 2, 3, 4};
        byte[] wav = NativeRecorder.wav(samples, 16000);
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(40, b.getInt(4));
        assertEquals("WAVEfmt ", new String(wav, 8, 8, StandardCharsets.US_ASCII));
        assertEquals(1, b.getShort(20));
        assertEquals(1, b.getShort(22));
        assertEquals(16000, b.getInt(24));
        assertEquals(32000, b.getInt(28));
        assertEquals(16, b.getShort(34));
        assertEquals("data", new String(wav, 36, 4, StandardCharsets.US_ASCII));
        assertEquals(4, b.getInt(40));
        byte[] tail = new byte[4];
        System.arraycopy(wav, 44, tail, 0, 4);
        assertArrayEquals(samples, tail);
    }

    @Test public void bluetoothInputsOutrankWiredAndBuiltIn() {
        assertTrue(NativeRecorder.inputRank(26) > NativeRecorder.inputRank(7));
        assertTrue(NativeRecorder.inputRank(7) > NativeRecorder.inputRank(3));
        assertEquals(0, NativeRecorder.inputRank(15));
        assertEquals(0, NativeRecorder.inputRank(25));
    }
}
