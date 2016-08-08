# Clojure Avro Brigde for using with Kafka and Schema Registry

This library is designed specifically for converting Clojure data structures to Avro-compatible Java classes (not Avro binary format!) back and forth in order to be able to use [Schema Registry's serializers](https://github.com/confluentinc/schema-registry/blob/master/avro-serializer/src/main/java/io/confluent/kafka/serializers/KafkaAvroSerializer.java) with Kafka and Clojure. 

Note, that this library is not an Avro-wrapper for Clojure: for this purpose you might want to use [damballa/abracad](https://github.com/damballa/abracad) or [asmyczek/simple-avro](https://github.com/asmyczek/simple-avro)

## Installation

Add this to your favorite build file (`project.clj` or `build.boot`)

`[io.thdr/kfk-avro-bridge "0.1.0-SNAPSHOT"]`

## Usage

#### Notes: ####

+ All keys in Clojure maps are keywordized and kebab-cased.
+ Enums are keywordized and kebab-cased
+ When converting to Java objects, all keys and enums are snake_cased.

#### Example: ####

```clojure
(ns test-avro
  (:require [thdr.avro-bridge.core :as avro]
            [cheshire.core :as json]))

(def schema (json/generate-string {:type "record",
                                   :name "user",
								   :fields [{:name "id" :type "int"}
                                             :name "full_name" :type "string"]}))

(def obj (avro/->java (parse-schema schema) 
                      {:id 1 :full-name "John Doe"})) 
;; => <GenericRecord ...>, which can be passed to KafkaAvroSerializer, for example
  
(avro/->clj obj) 
;; => {:id 1 :full-name "John Doe"}
```

## Contributing

Feel free to open an issue or PR

## License

Copyright © 2016 Theodore Konukhov <me@thdr.io>

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

