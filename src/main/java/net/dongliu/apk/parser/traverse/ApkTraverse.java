/*
 * Copyright 2021 Clifford Liu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dongliu.apk.parser.traverse;

import net.dongliu.apk.parser.AbstractApkFile;
import net.dongliu.apk.parser.bean.DexClass;
import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.parser.DexParser;
import net.dongliu.apk.parser.struct.AndroidConstants;
import net.dongliu.apk.parser.struct.dex.DexHeader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

public class ApkTraverse {

    // Use Java built-in Function as parameter to reduce coupling
    public static void traverseDexFiles(
            AbstractApkFile apk, final BiPredicate<Integer, DexClass> traverse
    ) throws IOException {
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
            AbstractApkFile apk, String path,
            DexProcessor.RecordConsumer<DexClass> consumer
    ) throws IOException {
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
}
