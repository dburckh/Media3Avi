package com.homesoft.exo.extractor.avi;

import java.nio.ByteBuffer;

/**
 * Open DML Index Box
 */
public class IndexBox extends ResidentBox {
    public static final int INDX = 0x78646E69;
    public static final int IX00 = 0x30307869;
    public static final int IX00_MASK = 0xf0f0ffff;
    //Supported IndexType(s)
    public static final byte AVI_INDEX_OF_INDEXES = 0;
    public static final byte AVI_INDEX_OF_CHUNKS = 1;

    IndexBox(int type, int size, ByteBuffer byteBuffer) {
        super(type, size, byteBuffer);
    }
    int getLongsPerEntry() {
        return byteBuffer.getShort(0) & 0xffff;
    }
    byte getIndexType() {
        return byteBuffer.get(3);
    }
    int getEntriesInUse() {
        return byteBuffer.get(4);
    }
    int getChunkId() {
        return byteBuffer.get(8);
    }
    long getEntry(int index) {
        return byteBuffer.getInt(0x18 + index * 4) & AviExtractor.UINT_MASK;
    }
}
