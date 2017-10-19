package com.andrewjones;

import dbserver1.inventory.customers.Envelope;
import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class EnvelopeKafkaAvroDeserializer extends AbstractKafkaAvroDeserializer implements Deserializer<Envelope> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        configure(new KafkaAvroDeserializerConfig(configs));
    }

    @Override
    public Envelope deserialize(String s, byte[] bytes) {
        return (Envelope) this.deserialize(bytes);
    }

    @Override
    public void close() {}
}
