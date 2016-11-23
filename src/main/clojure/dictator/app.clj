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

(ns dictator.app
  (:require
    [dictator [ui :refer [show-app]]
              [util :refer [init-native-code]]])
  (:import
    [javax.swing SwingUtilities UIManager])
  (:gen-class))

(defn -main
  "The app entry point."
  [& args]
  (init-native-code)
  (UIManager/setLookAndFeel (UIManager/getSystemLookAndFeelClassName))
  (SwingUtilities/invokeLater show-app))
