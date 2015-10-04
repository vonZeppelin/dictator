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

(ns dictator.ui
  (:require
    [clojure.core.async :refer [<! go close!]]
    [clojure.java.io :as io]
    [dictator [util :refer [icon text ->ValueHolder]]
              [audio :as audio]])
  (:import
    net.miginfocom.swing.MigLayout
    [dictator Native Native$Aggressiveness]
    [java.awt Color Insets]
    [java.util EventObject Hashtable]
    [javax.swing AbstractAction DefaultBoundedRangeModel DefaultComboBoxModel ImageIcon JCheckBox JComboBox JFrame
                 JLabel JMenu JMenuBar JMenuItem JOptionPane JPanel JSeparator JSlider JTabbedPane JToggleButton]))

(def ^:private mixers-model (DefaultComboBoxModel.))
(def ^:private sample-rates-model (DefaultBoundedRangeModel. 0 0 0 2))
(def ^:private vad-mode-model (DefaultComboBoxModel.
                                (into-array [(->ValueHolder (text ::vad.norm) Native$Aggressiveness/NORMAL)
                                             (->ValueHolder (text ::vad.lb) Native$Aggressiveness/LOW_BITRATE)
                                             (->ValueHolder (text ::vad.agg) Native$Aggressiveness/AGGRESSIVE)
                                             (->ValueHolder (text ::vad.vagg) Native$Aggressiveness/VERY_AGGRESSIVE)])))
(def ^:private dictation-channel (atom nil))


(defn- stop-dictation []
  (if-let [ch @dictation-channel]
    (close! ch)))

(defn- start-dictation []
  (if-let [mixer (.getSelectedItem mixers-model)]
    (let [sample-rates [8000 16000 32000] ;; TODO Refactor?
          sample-rate (-> sample-rates-model .getValue sample-rates)
          vad-mode (-> vad-mode-model .getSelectedItem .value)
          ch (audio/capture mixer sample-rate)]
      (audio/filter-voice ch sample-rate vad-mode)
      (reset! dictation-channel ch))))

(defn- build-menu [frame]
  "Builds a main menu of the app."
  (let [menu (JMenuBar.)
        file-menu (JMenu. (text ::menu.file))
        help-menu (JMenu. (text ::menu.help))
        exit-action (proxy [AbstractAction] [(text ::menu.exit)]
                      (actionPerformed [evt]
                        (.dispose frame)))
        options-action (proxy [AbstractAction] [(text ::menu.options) (icon ::options)]
                         (actionPerformed [evt]
                           (println evt)))
        about-action (proxy [AbstractAction] [(text ::menu.about) (icon ::about)]
                       (actionPerformed [evt]
                         (JOptionPane/showMessageDialog
                           frame
                           (text ::app.about)
                           (text ::app.title)
                           JOptionPane/PLAIN_MESSAGE
                           (icon ::darth :large))))]
    (doto file-menu
      (.add (JMenuItem. options-action))
      (.addSeparator)
      (.add (JMenuItem. exit-action)))
    (doto help-menu
      (.add (JMenuItem. about-action)))
    (doto menu
      (.add file-menu)
      (.add help-menu)
      (.setVisible false))
    (.setJMenuBar frame menu)))

(defn- build-input-tab []
  ""
  (doto (JPanel. (MigLayout. "wrap 2" "[]rel[grow]" "[center]unrel[center]"))
    (.add (JLabel. (text ::input.device)))
    (.add
      (doto (JComboBox. mixers-model)
        (.setPrototypeDisplayValue ""))
      "growx")
    (.add (JLabel. (text ::sample.rate)))
    (.add
      (doto (JSlider. sample-rates-model)
        (.setMajorTickSpacing 1)
        (.setSnapToTicks true)
        (.setPaintLabels true)
        (.setPaintTicks true)
        (.setLabelTable (let [khz (text ::khz)
                              labels {(int 0) (JLabel. (str 8 khz))
                                      (int 1) (JLabel. (str 16 khz))
                                      (int 2) (JLabel. (str 32 khz))}]
                          (Hashtable. labels))))
      "growx")))

(defn- build-misc-tab [frame]
  ""
  (let [ontop-action (proxy [AbstractAction] [(text ::always.ontop)]
                             (actionPerformed [evt]
                               (->> evt .getSource .isSelected (.setAlwaysOnTop frame)))
                             (isEnabled []
                               (.isAlwaysOnTopSupported frame)))]
    (doto (JPanel. (MigLayout. "wrap 2" "[]rel[grow]" "[center]unrel[center]"))
      (.add (JCheckBox. ontop-action) "spanx 2")
      (.add (JLabel. (text ::vad)))
      (.add
        (doto (JComboBox. vad-mode-model)
          (.setPrototypeDisplayValue ""))
        "growx"))))

(defn- build-controls [frame]
  ""
  (let [tabbed-pane (JTabbedPane. JTabbedPane/BOTTOM JTabbedPane/SCROLL_TAB_LAYOUT)
        rec-action (proxy [AbstractAction] [(text ::rec)]
                     (actionPerformed [evt]
                       (if (-> evt .getSource .isSelected)
                         (start-dictation)
                         (stop-dictation))))
        settings-action (proxy [AbstractAction] []
                          (actionPerformed [evt]
                            (let [config-btn (.getSource evt)
                                  selected? (.isSelected config-btn)]
                              (when selected?
                                (.removeAllElements mixers-model)
                                (doseq [mixer (audio/get-mixers)]
                                  (.addElement mixers-model mixer)))
                              (.setVisible tabbed-pane selected?)
                              (-> frame .getJMenuBar (.setVisible selected?))
                              (.pack frame))))]
    (doto frame
      (.setLayout (MigLayout. "wrap 2" "[]unrel[]" "[]5[nogrid][]"))
      (.add (doto (JToggleButton. rec-action)
              (.setToolTipText (text ::rec.tip))
              (.setIcon (icon ::rec-off :large))
              (.setSelectedIcon  (icon ::rec-on :large))))
      (.add
        (doto (JPanel.)
          (.setBackground Color/BLACK))
        "wmin 200,grow")
      (.add (JLabel. (text ::settings)))
      (.add (JSeparator.) "growx")
      (.add
        (doto (JToggleButton. settings-action)
          (.setMargin (Insets. 0 0 0 0))
          (.setToolTipText (text ::settings.tip))
          (.setIcon (icon ::left))
          (.setSelectedIcon (icon ::down))
          (.setBorderPainted false)
          (.setFocusPainted false)
          (.setContentAreaFilled false))
        "wrap")
      (.add
        (doto tabbed-pane
          (.setVisible false)
          (.add (text ::tab.input) (build-input-tab))
          (.add (text ::tab.output) (JLabel. "TBD"))
          (.add (text ::tab.misc) (build-misc-tab frame)))
        "growx,span 2,hidemode 2"))))

(defn show-app []
  "Initializes a main frame and controls of the app, wires them together and shows the UI."
  (doto (JFrame. (text ::app.title))
    (build-menu)
    (build-controls)
    (.setIconImages [(.getImage (icon ::darth)) (.getImage (icon ::darth :large))])
    (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
    (.setResizable false)
    (.pack)
    (.setLocationRelativeTo nil)
    (.setVisible true)))
