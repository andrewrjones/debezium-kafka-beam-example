package com.andrewjones;

import com.google.common.collect.ImmutableMap;
import dbserver1.inventory.customers.Envelope;
import dbserver1.inventory.customers.Value;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDecoder;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import kafka.utils.VerifiableProperties;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Properties;

/**
 * This works, although we are parsing the Avro ourselves, which is a little ugly...
 * Would rather have KafkaAvroExample work!
 */
public class KafkaAvroConsumerBytesExample {
    // This static initialization block creates an instance of the decoder per JVM
    private static KafkaAvroDecoder avroDecoder;
    static {
        final Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://registry:8081");
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        VerifiableProperties vProps = new VerifiableProperties(props);
        avroDecoder = new KafkaAvroDecoder(vProps);
    }

    public static void main(String[] args) {

        PipelineOptions options = PipelineOptionsFactory.create();
        Pipeline p = Pipeline.create(options);

        p.apply(KafkaIO.<byte[], byte[]>read()
                .withBootstrapServers("kafka:9092")
                .withTopic("dbserver1.inventory.customers")
                .withKeyDeserializer(ByteArrayDeserializer.class)
                .withValueDeserializer(ByteArrayDeserializer.class)

                .updateConsumerProperties(ImmutableMap.of("auto.offset.reset", (Object)"earliest"))

                // We're writing to a file, which does not support unbounded data sources. This line makes it bounded to
                // the first 2 records.
                // In reality, we would likely be writing to a data source that supports unbounded data, such as BigQuery.
                .withMaxNumRecords(2)

                .withoutMetadata() // PCollection<KV<Long, String>>
        )
                .apply(Values.<byte[]>create())
                .apply("ParseAvro", ParDo.of(new DoFn<byte[], Envelope>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        Envelope data = (Envelope) avroDecoder.fromBytes(c.element());
                        c.output(data);
                    }
                }))
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
