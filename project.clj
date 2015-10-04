(defproject dictator "0.1.0-SNAPSHOT"
  :description "FIXME"
  :url "https://github.com/vonZeppelin/dictator"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.miglayout/miglayout-swing "5.0"]]
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :resource-paths ["src/main/resources"]
  :main dictator.app
  ;:global-vars {*warn-on-reflection* true}
  :profiles {:uberjar {:aot :all
                       :uberjar-exclusions [#"project\.clj|cljs/" #"META-INF/(?:maven|leiningen)"]
                       :uberjar-name "dictator-standalone.jar"}})
