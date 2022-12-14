package net.dongliu.apk.parser.traverse;

import net.dongliu.apk.parser.AbstractApkFile;
import net.dongliu.apk.parser.bean.DexClass;
import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.parser.DexParser;
import net.dongliu.apk.parser.struct.AndroidConstants;
import net.dongliu.apk.parser.struct.dex.DexHeader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public class ApkTraverse {

    // Use Java built-in Function as parameter to reduce coupling
    public static void traverseDexFiles(
            AbstractApkFile apk, final BiPredicate<Integer, DexClass> traverse) throws IOException {
        final AtomicBoolean consumed = new AtomicBoolean(false);
        DexProcessor.RecordConsumer<DexClass> consumer = (index, data) -> {
            boolean isContinue = traverse.test(index, data);
            consumed.set(!isContinue);
            return isContinue;
        };
        traverseDexClasses(apk, AndroidConstants.DEX_FILE, consumer);
        if (consumed.get()) return;
        for (int i = 2; i < 1000; i++) {
            String path = String.format(Locale.US, AndroidConstants.DEX_ADDITIONAL, i);
            try {
                traverseDexClasses(apk, path, consumer);
                if (consumed.get()) break;
            } catch (ParserException e) {
                break;
            }
        }
    }

    private static void traverseDexClasses(
            AbstractApkFile apk, String path, DexProcessor.RecordConsumer<DexClass> consumer) throws IOException {
        byte[] data = apk.getFileData(path);
        if (data == null) {
            String msg = String.format("Dex file %s not found", path);
            throw new ParserException(msg);
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        DexParser dexParser = new DexParser(buffer);
        DexHeader header = dexParser.processHeader();
        dexParser.processClassTypes();
        DexProcessor.DexSection section = new DexProcessor.DexSection(-1, header.getClassDefsSize());
        dexParser.processSection(section, DexParser.CLASS_PRODUCER, consumer);
    }

    // Use Java built-in Function as parameter to reduce coupling
    public static <T> void transformDexFiles(
            AbstractApkFile apk,
            Function<DexClass, T> transform,
            Consumer<List<T>> collect) throws IOException {
        collect.accept(transformDexClasses(apk, AndroidConstants.DEX_FILE, transform));
        for (int i = 2; i < 1000; i++) {
            String path = String.format(Locale.US, AndroidConstants.DEX_ADDITIONAL, i);
            try {
                collect.accept(transformDexClasses(apk, path, transform));
            } catch (ParserException e) {
                break;
            }
        }
    }

    private static <T> List<T> transformDexClasses(
            AbstractApkFile apk, String path, Function<DexClass, T> transform) throws IOException {
        byte[] data = apk.getFileData(path);
        if (data == null) {
            String msg = String.format("Dex file %s not found", path);
            throw new ParserException(msg);
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        DexParser dexParser = new DexParser(buffer);
        return dexParser.transform(transform);
    }
}
