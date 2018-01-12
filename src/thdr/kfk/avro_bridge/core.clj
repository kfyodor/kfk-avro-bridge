(ns thdr.kfk.avro-bridge.core
  (:refer-clojure :exclude [bytes?])
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
  [^Schema schema path obj]
  (throw (Exception. (format "Value `%s` cannot be cast to `%s` schema at %s"
                             obj
                             schema
                             path))))

(defn parse-schema
  "A little helper for parsing schemas"
  [json]
  (Schema/parse json))

(def default-clj-field-fn (comp keyword ->kebab-case str))
(def default-java-field-fn (comp name ->snake_case))

(defn ->java
  "Converts a Clojure data structure to an Avro-compatible
   Java object. Avro `Schema` must be provided.

  It accepts an optional as third argument a map with the options:

  :java-field-fn function to apply to the Clojure's map keys when transforming them to Record fields and Map keys. Defaults to (comp name ->snake_case)
  :ignore-unknown-fields? default to false"
  ([schema obj] (->java schema obj nil))
  ([schema obj opts] (->java schema obj [] opts))
  ([schema obj path {:keys [java-field-fn ignore-unknown-fields?]
                     :or   {java-field-fn          default-java-field-fn
                            ignore-unknown-fields? false}
                     :as   opts}]
   (condp = (and (instance? Schema schema) (.getType schema))
     Schema$Type/NULL
     (if (nil? obj)
       nil
       (throw-invalid-type schema path obj))

     Schema$Type/INT
     (if (and (integer? obj) (<= Integer/MIN_VALUE obj Integer/MAX_VALUE))
       (int obj)
       (throw-invalid-type schema path obj))

     Schema$Type/LONG
     (if (and (integer? obj) (<= Long/MIN_VALUE obj Long/MAX_VALUE))
       (long obj)
       (throw-invalid-type schema path obj))

     Schema$Type/FLOAT
     (if (float? obj)
       (float obj)
       (throw-invalid-type schema path obj))

     Schema$Type/DOUBLE
     (if (float? obj)
       (double obj)
       (throw-invalid-type schema path obj))

     Schema$Type/BOOLEAN
     (if (instance? Boolean obj)                            ;; boolean? added only in 1.9 :(
       obj
       (throw-invalid-type schema path obj))

     Schema$Type/STRING
     (if (string? obj)
       obj
       (throw-invalid-type schema path obj))

     Schema$Type/BYTES
     (if (bytes? obj)
       (doto (ByteBuffer/allocate (count obj))
         (.put obj)
         (.position 0))
       (throw-invalid-type schema path obj))

     Schema$Type/ARRAY
     (if (sequential? obj)
       (let [f (fn [index o] (->java (.getElementType schema) o (conj path index) opts))]
         (GenericData$Array. schema (map-indexed f obj)))
       (throw-invalid-type schema path obj))

     Schema$Type/FIXED
     (if (and (bytes? obj) (= (count obj) (.getFixedSize schema)))
       (GenericData$Fixed. schema obj)
       (throw-invalid-type schema path obj))

     Schema$Type/ENUM
     (let [enum (name obj)
           enums (into #{} (.getEnumSymbols schema))]
       (if (contains? enums enum)
         (GenericData$EnumSymbol. schema enum)
         (throw-invalid-type schema path enum)))

     Schema$Type/MAP
     (if (map? obj)
       (into {}
             (map (fn [[k v]]
                    [(java-field-fn k)
                     (->java (.getValueType schema) v (conj path k) opts)])
                  obj))
       (throw-invalid-type schema path obj))

     Schema$Type/UNION
     (let [[val matched]
           (reduce (fn [_ schema]
                     (try
                       (reduced [(->java schema obj path opts) true])
                       (catch Exception _
                         [nil false])))
                   [nil false]
                   (.getTypes schema))]
       (if matched
         val
         (throw-invalid-type schema path obj)))

     Schema$Type/RECORD
     (if (map? obj)
       (reduce-kv
         (fn [record k v]
           (let [java-k (java-field-fn k)
                 s (some-> (.getField schema java-k) .schema)]
             (if (and (not s) ignore-unknown-fields?)
               record
               (doto record
                 (.put java-k (->java (or s java-k) v (conj path k) opts))))))
         (GenericData$Record. schema)
         obj)
       (throw-invalid-type schema path obj))

     (throw (Exception. (format "Field `%s` is not in schema %s" schema path))))))

(defn ->clj
  "Parses deserialized Avro object into
   a Clojure data structure. Enums will get keywordized and
   kebab-cased.

   It accepts an optional as second argument a map with the options:
   :clj-field-fn function to apply to the record's fields and map keys. Defaults to (comp keyword ->kebab-case str)"
  ([msg] (->clj msg nil))
  ([msg {:keys [clj-field-fn] :or {clj-field-fn default-clj-field-fn} :as opts}]
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
     (zipmap (map clj-field-fn (keys msg))
             (map #(->clj % opts) (vals msg)))

     GenericData$Record
     ;; Transients are making this slower, I wonder why?
     (loop [fields (seq (.. msg getSchema getFields)) record {}]
       (if-let [f (first fields)]
         (let [n (.name f)
               v (->clj (.get msg n) opts)
               k (-> n clj-field-fn)]
           (recur (rest fields)
                  (assoc record k v)))
         record))

     msg)))
