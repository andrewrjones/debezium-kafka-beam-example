package com.andrewjones;

import com.google.common.collect.ImmutableMap;
import dbserver1.inventory.customers.Envelope;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.kafka.common.serialization.StringDeserializer;

public class KafkaAvroConsumerExample {
    public static void main(String[] args) {

        PipelineOptions options = PipelineOptionsFactory.create();
        Pipeline p = Pipeline.create(options);

        p.apply(KafkaIO.<String, Object>read()
                .withBootstrapServers("kafka:9092")
                .withTopic("dbserver1.inventory.customers")
                .withKeyDeserializer(StringDeserializer.class)
                /*
                 * Using just the KafkaAvroDeserializer fails with:
                 * Caused by: java.lang.RuntimeException: Unable to automatically infer a Coder for the Kafka
                 * Deserializer class io.confluent.kafka.serializers.KafkaAvroDeserializer: no coder registered for
                 * type class java.lang.Object
                 */
                .withValueDeserializer(KafkaAvroDeserializer.class)

                /*
                 * Using this and changing to KafkaIO.<String, Envelope>read() does not compile with:
                 * Compilation failure
                 * [ERROR] /usr/src/kafka/src/main/java/com/andrewjones/KafkaAvroConsumerExample.java:[36,58]
                 * incompatible types: java.lang.Class<io.confluent.kafka.serializers.KafkaAvroDeserializer> cannot be
                 * converted to java.lang.Class<? extends org.apache.kafka.common.serialization.Deserializer<dbserver1.inventory.customers.Envelope>>
                 */
//                .withValueDeserializerAndCoder(KafkaAvroDeserializer.class, AvroCoder.of(Envelope.class))

                .updateConsumerProperties(ImmutableMap.of("auto.offset.reset", (Object)"earliest"))
                .updateConsumerProperties(ImmutableMap.of("schema.registry.url", (Object)"http://registry:8081"))
                .updateConsumerProperties(ImmutableMap.of("specific.avro.reader", (Object)"true"))

                // We're writing to a file, which does not support unbounded data sources. This line makes it bounded to
                // the first 2 records.
                // In reality, we would likely be writing to a data source that supports unbounded data, such as BigQuery.
                .withMaxNumRecords(2)

                .withoutMetadata() // PCollection<KV<Long, String>>
        )
                .apply(Values.<Object>create())
                .apply("ExtractWords", ParDo.of(new DoFn<Object, String>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        System.out.println(c.element());
//                        System.out.println(c.element().getAfter().getEmail());
                        c.output("hi");
                    }
                }))
                .apply(Count.<String>perElement())
                .apply("FormatResults", MapElements.via(new SimpleFunction<KV<String, Long>, String>() {
                    @Override
                    public String apply(KV<String, Long> input) {
                        return input.getKey() + ": " + input.getValue();
                    }
                }))
                .apply(TextIO.write().to("wordcounts"));

        p.run().waitUntilFinish();
    }
}
