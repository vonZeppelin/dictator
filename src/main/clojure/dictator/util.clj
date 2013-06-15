;; Copyright 2014 Leonid Bogdanov
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;    http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns dictator.util
  (:require
    [clojure.string :as string]
    [clojure.java.io :as io])
  (:import
    [java.util MissingResourceException ResourceBundle]
    java.nio.file.Files
    javax.swing.ImageIcon))

;; icon size -> icon resource name templates
(def ^:private icon-sizes {:small "%s.png" :large "%s.32.png"})

(defonce platform (let [os (condp #(.startsWith %2 %1) (System/getProperty "os.name")
                             "Windows" :win
                             "Mac" :mac
                             "Darwin" :mac
                             :nix)
                        arch (or
                               (System/getProperty "sun.arch.data.model")
                               (System/getProperty "com.ibm.vm.bitmode")
                               (-> (System/getProperty "os.arch") string/trim string/lower-case))]
                     {:os os :is64 (contains? #{"64" "x86_64" "ia64" "amd64" "ppc64"} arch)}))

(defn text
  "Returns a string resource identified by a key. A ResourceBundle to query is specified
   either explicitly or calculated from the key's namespace."
  (^String [key]
    (let [bundle (-> key namespace munge ResourceBundle/getBundle)]
      (text key bundle)))
  (^String [key ^ResourceBundle bundle]
    (let [key (name key)]
      (try
        (.getString bundle key)
        (catch MissingResourceException e key)))))

(defn icon
  "Returns an ImageIcon resource of a specifed size (optional, defaults to ':small') and identified by a key.
   Currently supported sizes are ':small' and ':large'."
  (^ImageIcon [key]
    (icon key :small))
  (^ImageIcon [key size]
    (let [icon-name (format (icon-sizes size) (name key))
          icon-path (-> key namespace munge (string/replace \. \/) (str \/ icon-name))
          icon (-> icon-path io/resource ImageIcon.)]
      icon)))

(defn init-native-code
 "Unpacks the native helper lib to the temp directory and loads it."
 []
 (let [empty-attrs (make-array java.nio.file.attribute.FileAttribute 0)
       temp-dir-path (Files/createTempDirectory nil empty-attrs)
       lib-name (System/mapLibraryName "dictator")
       lib-res (if (:is64 platform)
                 (string/replace-first lib-name #"\.(?=\w+$)" "64.")
                 lib-name)
       lib-file (-> temp-dir-path (Files/createTempFile nil nil empty-attrs) .toFile)]
   (with-open [lib (-> lib-res io/resource io/input-stream)]
     (io/copy lib lib-file)
     (-> lib-file .getCanonicalPath System/load))))
