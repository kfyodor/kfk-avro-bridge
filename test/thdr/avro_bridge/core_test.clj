(ns thdr.avro-bridge.core-test
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [thdr.avro-bridge.core :refer [->java ->clj] :as core])
  (:import [org.apache.avro Schema]))

(defn- make-schema [m]
  (json/generate-string m))

(defn- test-roundtrip [schema data]
  (let [schema (Schema/parse (make-schema schema))
        obj (->java schema data)]
    (if (#'core/bytes? data)
      (is (= (seq data) (seq (->clj data))))
      (is (= data (->clj obj))))))

(deftest avro-bridge-roundtrip-test
  (test-roundtrip "null" nil)
  (test-roundtrip "string" "test")
  (test-roundtrip "int" 1)
  (test-roundtrip "long" 1)
  (test-roundtrip "float" 1.0)
  (test-roundtrip "double" 1.0)
  (test-roundtrip "bytes" (bytes (byte-array 0)))
  (test-roundtrip {:type "array" :items "int"} [1 2])
  (test-roundtrip {:type "array" :items "string"} ["1" "2"])
  (test-roundtrip {:type "map" :values "string"} {:a "1" :b "2"})
  (test-roundtrip {:type "record"
                   :name "Test"
                   :fields [{:name "a" :type "string"}
                            {:name "b" :type "int"}]}
                  {:a "1" :b 2})
  (test-roundtrip ["null" "string"] nil)
  (test-roundtrip ["null" "string"] "test")
  (test-roundtrip {:name "test" :type "enum" :symbols ["TEST" "ME"]} :TEST)
  (test-roundtrip {:name "test" :type "enum" :symbols ["TEST" "ME"]} :ME)
  (test-roundtrip {:name "uuid" :type "fixed" :size 36}
                  (byte-array (map byte (str (java.util.UUID/randomUUID))))))
