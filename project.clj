(defproject dictator "0.1.0-SNAPSHOT"
  :description "FIXME"
  :url "https://github.com/vonZeppelin/dictator"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [us.monoid.web/resty "0.3.2"]
                 [com.miglayout/miglayout-swing "5.0"]]
  :plugins [[lein-resource "16.9.1"]]
  :hooks [leiningen.resource]
  :source-paths ["src/main/clojure" "target/clj"]
  :java-source-paths ["src/main/java"]
  :resource-paths ["src/main/resources"]
  :main dictator.app
  ;:global-vars {*warn-on-reflection* true}
  :profiles {:uberjar {:aot :all
                       :uberjar-exclusions [#"cljs/|project\.clj|README\.md|LICENSE"
                                            #"META-INF/(?:maven|leiningen)"]
                       :uberjar-name "dictator-standalone.jar"}}
  :resource {:resource-paths ["src/main/resources"]
             :includes [#".+\.clj$"]
             :target-path "target/clj"})
