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

    public interface RecordTransformer<T, R> extends RecordConsumer<R> {
        R transform(int index, T data);
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

    public <T, R> void processSection(DexSection section, RecordProducer<T> producer, RecordTransformer<T, R> transformer) {
        if (section.getPosition() > -1) {
            Buffers.position(buffer, section.getPosition());
        }
        if (producer.preProduce(this, section)) {
            for (int i = 0; i < section.getSize(); i++) {
                T product = producer.produce(this, section, i);
                R transformed = transformer.transform(i, product);
                boolean terminate = !transformer.consume(i, transformed);
                if (terminate) break;
            }
        }
        producer.postProduce(this);
    }
}
