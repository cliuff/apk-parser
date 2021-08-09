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

import net.dongliu.apk.parser.utils.Buffers;

import java.nio.ByteBuffer;

public abstract class DexProcessor {

    public static class DexSection {
        private final long position;
        private final int size;

        public DexSection(long position, int size) {
            this.position = position;
            this.size = size;
        }

        public long getPosition() {
            return position;
        }

        public int getSize() {
            return size;
        }
    }

    public abstract static class RecordProducer<T> {
        protected abstract boolean preProduce(DexProcessor processor, DexSection section);
        protected abstract T produce(DexProcessor processor, DexSection section, int i);
        protected abstract void postProduce(DexProcessor processor);
    }

    public interface RecordConsumer<T> {
        boolean consume(int index, T data);
    }

    public static <T> RecordConsumer<T> arrayConsumer(final T[] array) {
        return (index, data) -> {
            array[index] = data;
            return true;
        };
    }

    protected ByteBuffer buffer;

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public <T> void processSection(DexSection section, RecordProducer<T> producer, RecordConsumer<T> consumer) {
        if (section.getPosition() > -1) {
            Buffers.position(buffer, section.getPosition());
        }
        if (producer.preProduce(this, section)) {
            for (int i = 0; i < section.getSize(); i++) {
                T product = producer.produce(this, section, i);
                boolean terminate = !consumer.consume(i, product);
                if (terminate) break;
            }
        }
        producer.postProduce(this);
    }
}
