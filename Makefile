TOPIC=dbserver1.inventory.customers
ZOOKEEPER=zookeeper:2181
KAFKA=kafka:9092
CONNECT=localhost:8083
REGISTRY=registry:8081

RUNNER=direct-runner

up:
	docker-compose up

upd:
	docker-compose up -d

fup:
	docker-compose up --force-recreate

down:
	docker-compose down

register:
	curl -i -X POST -H "Accept:application/json" -H  "Content-Type:application/json" http://$(CONNECT)/connectors/ -d @register-postgres.json | jq

describe:
	docker-compose exec kafka bin/kafka-topics.sh --describe --topic $(TOPIC) --zookeeper $(ZOOKEEPER)

offset:
	docker-compose exec kafka bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list $(KAFKA) --topic $(TOPIC) --time -1

dump:
	docker-compose exec registry /usr/bin/kafka-avro-console-consumer --bootstrap-server $(KAFKA) --from-beginning --property print.key=true \
	--topic dbserver1.inventory.customers --property schema.registry.url=http://$(REGISTRY)

subjects:
	curl -s http://localhost:8081/subjects | jq

schema:
	curl -s http://localhost:8081/subjects/dbserver1.inventory.customers-value/versions/latest \
	| jq '.schema | fromjson' > src/main/avro/dbserver1.inventory.customers-value.avsc

compile:
	docker-compose run beam mvn compile

consumer:
	docker-compose run beam mvn compile exec:java -Dexec.mainClass=com.andrewjones.KafkaAvroConsumerExample -P$(RUNNER)

clean: clean-docker clean-files

clean-docker:
	docker-compose rm -f

clean-files:
	rm wordcounts*
