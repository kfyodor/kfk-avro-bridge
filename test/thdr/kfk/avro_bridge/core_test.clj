(ns thdr.kfk.avro-bridge.core-test
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [thdr.kfk.avro-bridge.core :refer [->java ->clj] :as core]
            [camel-snake-kebab.core :as csk])
  (:import [org.apache.avro Schema]))

(defn- make-schema [m]
  (json/generate-string m))

(defn- test-roundtrip [schema data & [opts]]
  (let [schema (Schema/parse (make-schema schema))
        obj (->java schema data opts)]
    (if (#'core/bytes? data)
      (is (= (seq data) (seq (->clj data opts))))
      (is (= data (->clj obj opts))))))

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
  (test-roundtrip {:type   "record"
                   :name   "Test"
                   :fields [{:name "a" :type "string"}
                            {:name "b" :type "int"}]}
                  {:a "1" :b 2})
  (test-roundtrip ["null" "string"] nil)
  (test-roundtrip ["null" "string"] "test")
  (test-roundtrip {:name "test" :type "enum" :symbols ["TEST" "ME"]} :TEST)
  (test-roundtrip {:name "test" :type "enum" :symbols ["TEST" "ME"]} :ME)
  (test-roundtrip {:name "uuid" :type "fixed" :size 36}
                  (byte-array (map byte (str (java.util.UUID/randomUUID))))))

(deftest casing-fields

  (test-roundtrip {:type   "record"
                   :name   "DefaultSnakeCase"
                   :fields [{:name "a_snake_case" :type "string"}
                            {:name "b" :type "int"}]}
                  {:a-snake-case "1" :b 2})

  (test-roundtrip {:type   "record"
                   :name   "CamelCase"
                   :fields [{:name "aCamelCasedField" :type "string"}]}
                  {:a-camel-cased-field "any value"}
                  {:java-field-fn (comp name csk/->camelCase)})

  (test-roundtrip {:type   "record"
                   :name   "CamelCaseInnerRecord"
                   :fields [{:type {:type   "record"
                                    :name   "InnerRecord"
                                    :fields [{:name "aCamelCasedField" :type "string"}]}
                             :name "innerRecord"}]}
                  {:inner-record {:a-camel-cased-field "any value"}}
                  {:java-field-fn (comp name csk/->camelCase)})

  (test-roundtrip {:type   "record"
                   :name   "DoNothingWithTheKeys"
                   :fields [{:name "aCamelCasedField" :type "string"}]}
                  {"aCamelCasedField" "any value"}
                  {:java-field-fn identity
                   :clj-field-fn  identity})

  (test-roundtrip {:type "map" :values "string"}
                  {:a_snake "1" :b "2"}
                  {:clj-field-fn (comp keyword csk/->snake_case)}))

(deftest optionally-ignore-unkown-fields

  (let [schema (Schema/parse (make-schema {:type   "record"
                                           :name   "DefaultSnakeCase"
                                           :fields [{:name "just_this_one" :type "string"}]}))]
    (is (thrown? Exception
                 (->java schema
                         {:just-this-one            "1"
                          :but-this-is-an-extra-one "2"})))

    (is (= {:just-this-one "1"}
           (->clj
             (->java schema
                     {:just-this-one            "1"
                      :but-this-is-an-extra-one "2"}
                     {:ignore-unknown-field? true}))))))