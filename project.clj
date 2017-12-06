(defproject org.akvo/kfk.avro-bridge "dev"
  :description "Converting Clojure data structures to Avro-compatible Java classes back and forth."
  :url "https://github.com/konukhov/kfk-avro-bridge"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.apache.avro/avro "1.8.2"]
                 [cheshire "5.8.0"]
                 [camel-snake-kebab "0.4.0"]]
  :profiles {:dev         {:source-paths   ["dev/src"]
                           :resource-paths ["dev/resources"]}
             :set-version {:plugins [[lein-set-version "0.4.1"]]}}
  :repositories [["releases" {:url           "https://clojars.org/repo"
                              :sign-releases false
                              :username      :env/clojars_username
                              :password      :env/clojars_password}]])