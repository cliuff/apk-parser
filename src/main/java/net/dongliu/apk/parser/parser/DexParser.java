package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.bean.DexClass;
import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.struct.dex.DexClassStruct;
import net.dongliu.apk.parser.struct.dex.DexHeader;
import net.dongliu.apk.parser.traverse.DexProcessor;
import net.dongliu.apk.parser.utils.Buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * parse dex file.
 * current we only get the class name.
 * see:
 * http://source.android.com/devices/tech/dalvik/dex-format.html
 * http://dexandroid.googlecode.com/svn/trunk/dalvik/libdex/DexFile.h
 *
 * @author dongliu
 */
public class DexParser extends DexProcessor {

    private static final int NO_INDEX = 0xffffffff;

    private DexHeader header;
    private DexClassStruct[] dexClassStructs;
    private String[] types;

    public DexParser(ByteBuffer buffer) {
        this.buffer = buffer.duplicate();
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public DexHeader processHeader() {
        // read magic
        String magic = new String(Buffers.readBytes(buffer, 8));
        if (!magic.startsWith("dex\n")) {
            return null;
        }
        int version = Integer.parseInt(magic.substring(4, 7));
        // now the version is 035
        if (version < 35) {
            // version 009 was used for the M3 releases of the Android platform (November–December 2007),
            // and version 013 was used for the M5 releases of the Android platform (February–March 2008)
            throw new ParserException("Dex file version: " + version + " is not supported");
        }
        // read header
        header = readDexHeader();
        header.setVersion(version);
        return header;
    }

    public void processClassTypes() {
        if (header == null && processHeader() == null) return;

        // read string pool
        long[] stringOffsets = readStringPool(header.getStringIdsOff(), header.getStringIdsSize());

        // read types
        int[] typeIds = readTypes(header.getTypeIdsOff(), header.getTypeIdsSize());

        // read classes
        DexSection classSection = new DexSection(header.getClassDefsOff(), header.getClassDefsSize());
        final DexClassStruct[] dexClassStructs = new DexClassStruct[classSection.getSize()];
        this.dexClassStructs = dexClassStructs;
        processSection(classSection, CLASS_STRUCT_PRODUCER, arrayConsumer(dexClassStructs));

        StringPool stringpool = readStrings(stringOffsets);

        String[] types = new String[typeIds.length];
        this.types = types;
        for (int i = 0; i < typeIds.length; i++) {
            types[i] = stringpool.get(typeIds[i]);
        }
    }

    public DexClass[] parse() {
        if (header == null && processHeader() == null) {
            return new DexClass[0];
        }

        processClassTypes();

        DexSection dexClassSection = new DexSection(-1, dexClassStructs.length);
        DexClass[] dexClasses = new DexClass[dexClassSection.getSize()];
        processSection(dexClassSection, CLASS_PRODUCER, arrayConsumer(dexClasses));
        return dexClasses;
    }

    public static final RecordProducer<DexClass> CLASS_PRODUCER = new RecordProducer<>() {
        private DexParser parser;
        private DexClassStruct[] dexClassStructs;
        private String[] types;

        private DexClass oneDexClass;
        private int lastIndex;

        @Override
        protected boolean preProduce(DexProcessor processor, DexSection section) {
            if (processor instanceof DexParser) {
                parser = (DexParser) processor;
                dexClassStructs = parser.dexClassStructs;
                types = parser.types;
                oneDexClass = new DexClass();
                lastIndex = section.getSize() - 1;
            }
            // dexClassStructs and types must be filled in advance
            return parser != null && dexClassStructs != null && types != null;
        }

        @Override
        protected void postProduce(DexProcessor processor) {
        }

        @Override
        protected DexClass produce(DexProcessor processor, DexSection section, int i) {
            DexClassStruct dexClassStruct = dexClassStructs[i];
            DexClass dexClass;
            try {
                dexClass = i == lastIndex ? oneDexClass : (DexClass) oneDexClass.clone();
            } catch (CloneNotSupportedException ignored) {
                dexClass = new DexClass();
            }
            int superclassIdx = dexClassStruct.getSuperclassIdx();
            dexClass.setSuperClass(superclassIdx != NO_INDEX ? types[superclassIdx] : null);
            dexClass.setClassType(types[dexClassStruct.getClassIdx()]);
            dexClass.setAccessFlags(dexClassStruct.getAccessFlags());
            return dexClass;
        }
    };

    /**
     * read class info.
     */
    public static final RecordProducer<DexClassStruct> CLASS_STRUCT_PRODUCER = new RecordProducer<>() {
        private ByteBuffer buffer;
        private DexClassStruct oneStruct;
        private int lastIndex;

        @Override
        protected boolean preProduce(DexProcessor processor, DexSection section) {
            buffer = processor.getBuffer();
            oneStruct = new DexClassStruct();
            lastIndex = section.getSize() - 1;
            return true;
        }

        @Override
        protected void postProduce(DexProcessor processor) {
        }

        @Override
        protected DexClassStruct produce(DexProcessor processor, DexSection section, int i) {
            DexClassStruct dexClassStruct;
            try {
                dexClassStruct = i == lastIndex ? oneStruct : (DexClassStruct) oneStruct.clone();
            } catch (CloneNotSupportedException ignored) {
                dexClassStruct = new DexClassStruct();
            }
            dexClassStruct.setClassIdx(buffer.getInt());

            dexClassStruct.setAccessFlags(buffer.getInt());
            dexClassStruct.setSuperclassIdx(buffer.getInt());

            dexClassStruct.setInterfacesOff(Buffers.readUInt(buffer));
            dexClassStruct.setSourceFileIdx(buffer.getInt());
            dexClassStruct.setAnnotationsOff(Buffers.readUInt(buffer));
            dexClassStruct.setClassDataOff(Buffers.readUInt(buffer));
            dexClassStruct.setStaticValuesOff(Buffers.readUInt(buffer));

            return dexClassStruct;
        }
    };

    /**
     * read types.
     */
    private int[] readTypes(long typeIdsOff, int typeIdsSize) {
        Buffers.position(buffer, typeIdsOff);
        int[] typeIds = new int[typeIdsSize];
        for (int i = 0; i < typeIdsSize; i++) {
            typeIds[i] = (int) Buffers.readUInt(buffer);
        }
        return typeIds;
    }

    /**
     * read string pool for dex file.
     * dex file string pool diff a bit with binary xml file or resource table.
     *
     * @param offsets
     * @return
     * @throws IOException
     */
    private StringPool readStrings(long[] offsets) {
        // read strings.
        // buffer some apk, the strings' offsets may not well ordered. we sort it first

        StringPoolEntry[] entries = new StringPoolEntry[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            entries[i] = new StringPoolEntry(i, offsets[i]);
        }

        String lastStr = null;
        long lastOffset = -1;
        StringPool stringpool = new StringPool(offsets.length);
        for (StringPoolEntry entry : entries) {
            if (entry.getOffset() == lastOffset) {
                stringpool.set(entry.getIdx(), lastStr);
                continue;
            }
            Buffers.position(buffer, entry.getOffset());
            lastOffset = entry.getOffset();
            String str = readString();
            lastStr = str;
            stringpool.set(entry.getIdx(), str);
        }
        return stringpool;
    }

    /*
     * read string identifiers list.
     */
    private long[] readStringPool(long stringIdsOff, int stringIdsSize) {
        Buffers.position(buffer, stringIdsOff);
        long offsets[] = new long[stringIdsSize];
        for (int i = 0; i < stringIdsSize; i++) {
            offsets[i] = Buffers.readUInt(buffer);
        }

        return offsets;
    }

    /**
     * read dex encoding string.
     */
    private String readString() {
        // the length is char len, not byte len
        int strLen = readVarInts();
        return readString(strLen);
    }

    /**
     * read Modified UTF-8 encoding str.
     *
     * @param strLen the java-utf16-char len, not strLen nor bytes len.
     */
    private String readString(int strLen) {
        char[] chars = new char[strLen];

        for (int i = 0; i < strLen; i++) {
            short a = Buffers.readUByte(buffer);
            if ((a & 0x80) == 0) {
                // ascii char
                chars[i] = (char) a;
            } else if ((a & 0xe0) == 0xc0) {
                // read one more
                short b = Buffers.readUByte(buffer);
                chars[i] = (char) (((a & 0x1F) << 6) | (b & 0x3F));
            } else if ((a & 0xf0) == 0xe0) {
                short b = Buffers.readUByte(buffer);
                short c = Buffers.readUByte(buffer);
                chars[i] = (char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F));
            } else if ((a & 0xf0) == 0xf0) {
                //throw new UTFDataFormatException();

            } else {
                //throw new UTFDataFormatException();
            }
            if (chars[i] == 0) {
                // the end of string.
            }
        }

        return new String(chars);
    }


    /**
     * read varints.
     *
     * @return
     * @throws IOException
     */
    private int readVarInts() {
        int i = 0;
        int count = 0;
        short s;
        do {
            if (count > 4) {
                throw new ParserException("read varints error.");
            }
            s = Buffers.readUByte(buffer);
            i |= (s & 0x7f) << (count * 7);
            count++;
        } while ((s & 0x80) != 0);

        return i;
    }

    private DexHeader readDexHeader() {

        // check sum. skip
        buffer.getInt();

        // signature skip
        Buffers.readBytes(buffer, DexHeader.kSHA1DigestLen);

        DexHeader header = new DexHeader();
        header.setFileSize(Buffers.readUInt(buffer));
        header.setHeaderSize(Buffers.readUInt(buffer));

        // skip?
        Buffers.readUInt(buffer);

        // static link data
        header.setLinkSize(Buffers.readUInt(buffer));
        header.setLinkOff(Buffers.readUInt(buffer));

        // the map data is just the same as dex header.
        header.setMapOff(Buffers.readUInt(buffer));

        header.setStringIdsSize(buffer.getInt());
        header.setStringIdsOff(Buffers.readUInt(buffer));

        header.setTypeIdsSize(buffer.getInt());
        header.setTypeIdsOff(Buffers.readUInt(buffer));

        header.setProtoIdsSize(buffer.getInt());
        header.setProtoIdsOff(Buffers.readUInt(buffer));

        header.setFieldIdsSize(buffer.getInt());
        header.setFieldIdsOff(Buffers.readUInt(buffer));

        header.setMethodIdsSize(buffer.getInt());
        header.setMethodIdsOff(Buffers.readUInt(buffer));

        header.setClassDefsSize(buffer.getInt());
        header.setClassDefsOff(Buffers.readUInt(buffer));

        header.setDataSize(buffer.getInt());
        header.setDataOff(Buffers.readUInt(buffer));

        Buffers.position(buffer, header.getHeaderSize());

        return header;
    }

}

