(ns thdr.kfk.avro-bridge.core
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]])
  (:import [java.nio ByteBuffer]
           [org.apache.avro.generic
            GenericData
            GenericData$Record
            GenericData$Array
            GenericData$Fixed
            GenericData$EnumSymbol]
           [org.apache.avro.util Utf8]
           [org.apache.avro Schema Schema$Type]))

(defn- bytes?
  "Compatibility with Clojure < 1.9"
  [x]
  (if (nil? x)
    false
    (-> x class .getComponentType (= Byte/TYPE))))

(defn- throw-invalid-type
  [^Schema schema obj]
  (throw (Exception. (format "Value `%s` cannot be cast to `%s` schema"
                             (str obj)
                             (.toString schema)))))

(defn parse-schema
  "A little helper for parsing schemas"
  [json]
  (Schema/parse json))

(defn ->java
  "Converts a Clojure data structure to an Avro-compatible
   Java object. Avro `Schema` must be provided."
  [schema obj]
  (condp = (and (instance? Schema schema) (.getType schema))
    Schema$Type/NULL
    (if (nil? obj)
      nil
      (throw-invalid-type schema obj))

    Schema$Type/INT
    (if (and (integer? obj) (<= Integer/MIN_VALUE obj Integer/MAX_VALUE))
      (int obj)
      (throw-invalid-type schema obj))

    Schema$Type/LONG
    (if (and (integer? obj) (<= Long/MIN_VALUE obj Long/MAX_VALUE))
      (long obj)
      (throw-invalid-type schema obj))

    Schema$Type/FLOAT
    (if (float? obj)
      (float obj)
      (throw-invalid-type schema obj))

    Schema$Type/DOUBLE
    (if (float? obj)
      (double obj)
      (throw-invalid-type schema obj))

    Schema$Type/BOOLEAN
    (if (instance? Boolean obj) ;; boolean? added only in 1.9 :(
      obj
      (throw-invalid-type schema obj))

    Schema$Type/STRING
    (if (string? obj)
      obj
      (throw-invalid-type schema obj))

    Schema$Type/BYTES
    (if (bytes? obj)
      (doto (ByteBuffer/allocate (count obj))
        (.put obj)
        (.position 0))
      (throw-invalid-type schema obj))

    Schema$Type/ARRAY ;; TODO Exception for complex type
    (let [f (partial ->java (.getElementType schema))]
      (GenericData$Array. schema (map f obj)))

    Schema$Type/FIXED
    (if (and (bytes? obj) (= (count obj) (.getFixedSize schema)))
      (GenericData$Fixed. schema obj)
      (throw-invalid-type schema obj))

    Schema$Type/ENUM
    (let [enum (name obj)
          enums (into #{} (.getEnumSymbols schema))]
      (if (contains? enums enum)
        (GenericData$EnumSymbol. schema enum)
        (throw-invalid-type schema enum)))

    Schema$Type/MAP ;; TODO Exception for complex type
    (zipmap (map (comp name ->snake_case) (keys obj))
            (map (partial ->java (.getValueType schema))
                 (vals obj)))

    Schema$Type/UNION
    (let [[val matched]
          (reduce (fn [_ schema]
                    (try
                      (reduced [(->java schema obj) true])
                      (catch Exception _
                        [nil false])))
                  [nil false]
                  (.getTypes schema))]
      (if matched
        val
        (throw-invalid-type schema obj)))

    Schema$Type/RECORD
    (reduce-kv
     (fn [record k v]
       (let [k (-> (name k) ->snake_case)
             s (some-> (.getField schema k)
                       (as-> f (.schema f)))]
         (doto record
           (.put k (->java (or s k) v)))))
     (GenericData$Record. schema)
     obj)

    (throw (Exception. (format "Field `%s` is not in schema" schema)))))

(defn ->clj
  "Parses deserialized Avro object into
   a Clojure data structure. Keys in records
   and maps + enums will get keywordized and
   kebab-cased."
  [msg]
  (condp instance? msg
    Utf8
    (str msg)

    java.nio.ByteBuffer
    (.array msg)

    GenericData$Array
    (into [] (map ->clj msg))

    GenericData$Fixed
    (.bytes msg)

    GenericData$EnumSymbol
    (keyword (str msg))

    java.util.Map
    (zipmap (map (comp keyword ->kebab-case str) (keys msg))
            (map ->clj (vals msg)))

    GenericData$Record
    ;; Transients are making this slower, I wonder why?
    (loop [fields (seq (.. msg getSchema getFields)) record {}]
      (if-let [f (first fields)]
        (let [n (.name f)
              v (->clj (.get msg n))
              k (-> n ->kebab-case keyword)]
          (recur (rest fields)
                 (assoc record k v)))
        record))

    msg))
