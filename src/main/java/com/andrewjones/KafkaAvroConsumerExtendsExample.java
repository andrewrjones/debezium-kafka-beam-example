package com.andrewjones;

import com.google.common.collect.ImmutableMap;
import dbserver1.inventory.customers.Envelope;
import dbserver1.inventory.customers.Value;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

public class KafkaAvroConsumerExtendsExample {
    public static void main(String[] args) {

        PipelineOptions options = PipelineOptionsFactory.create();
        Pipeline p = Pipeline.create(options);

        p.apply(KafkaIO.<byte[], Envelope>read()
                        .withBootstrapServers("kafka:9092")
                        .withTopic("dbserver1.inventory.customers")
                        .withKeyDeserializer(ByteArrayDeserializer.class)
                        .withValueDeserializer(EnvelopeKafkaAvroDeserializer.class)

                        .updateConsumerProperties(ImmutableMap.of("auto.offset.reset", (Object)"earliest"))
                        .updateConsumerProperties(ImmutableMap.of("schema.registry.url", (Object)"http://registry:8081"))
                        .updateConsumerProperties(ImmutableMap.of("specific.avro.reader", (Object)"true"))

                        // We're writing to a file, which does not support unbounded data sources. This line makes it bounded to
                        // the first 2 records.
                        // In reality, we would likely be writing to a data source that supports unbounded data, such as BigQuery.
                        .withMaxNumRecords(2)

                        .withoutMetadata()
        )
                .apply(Values.<Envelope>create())
                .apply("ExtractWords", ParDo.of(new DoFn<Envelope, String>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        Value customer = c.element().getAfter();
                        c.output(customer.getEmail().toString());
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
