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
  (:require [dictator [util :refer [text icon]] [audio :as audio]])
  (:import
    [java.awt Color Insets]
    [javax.swing AbstractAction DefaultComboBoxModel ImageIcon JComboBox JFrame JLabel JMenu JMenuBar JMenuItem
                 JOptionPane JPanel JSeparator JTabbedPane JToggleButton]
    java.util.EventObject
    net.miginfocom.swing.MigLayout))

(def ^:private ^DefaultComboBoxModel mixers-model (DefaultComboBoxModel.))
(def ^:private capturer (atom nil))

(defn- build-menu [^JFrame frame]
  "Builds a main menu of the app."
  (let [menu (JMenuBar.)
        file-menu (JMenu. (text ::menu.file))
        help-menu (JMenu. (text ::menu.help))
        exit-action (proxy [AbstractAction] [(text ::menu.exit)]
                      (actionPerformed [evt] (.dispose frame)))
        options-action (proxy [AbstractAction] [(text ::menu.options) (icon ::options)]
                         (actionPerformed [evt] (println evt)))
        about-action (proxy [AbstractAction] [(text ::menu.about) (icon ::about)]
                       (actionPerformed [evt]
                         (JOptionPane/showMessageDialog
                           frame (text ::app.about) (text ::app.title)
                           JOptionPane/PLAIN_MESSAGE (icon ::mic :large))))]
    (doto file-menu
      (.add (JMenuItem. options-action))
      (.addSeparator)
      (.add (JMenuItem. exit-action)))
    (doto help-menu
      (.add (JMenuItem. about-action)))
    (.add menu file-menu)
    (.add menu help-menu)
    (.setJMenuBar frame menu)))

(defn- build-input-tab []
  ""
  (doto (JPanel. (MigLayout. "wrap 2" "5[]rel[grow]5" "5[]5"))
    (.add (JLabel. (text ::input.device)))
    (.add
      (doto (JComboBox. mixers-model)
        (.setPrototypeDisplayValue ""))
      "growx")))

(defn- build-controls [^JFrame frame]
  ""
  (let [tabbed-pane (JTabbedPane. JTabbedPane/BOTTOM JTabbedPane/SCROLL_TAB_LAYOUT)
        rec-action (proxy [AbstractAction] [(text ::rec)]
                     (actionPerformed [^EventObject evt]
                       (let [^JToggleButton rec-btn (.getSource evt)
                             selected? (.isSelected rec-btn)]
                         (if selected?
                           (if-let [mixer (.getSelectedItem mixers-model)]
                             (reset! capturer (audio/capture mixer)))
                           (if-let [capturer @capturer]
                             (-> capturer :line .stop))))))
        settings-action (proxy [AbstractAction] []
                          (actionPerformed [^EventObject evt]
                            (let [^JToggleButton config-btn (.getSource evt)
                                  selected? (.isSelected config-btn)]
                              (when selected?
                                (.removeAllElements mixers-model)
                                (doseq [mixer (audio/get-mixers)]
                                  (.addElement mixers-model mixer)))
                              (.setVisible tabbed-pane selected?)
                              (.pack frame))))]
    (doto frame
      (.setLayout (MigLayout. "wrap 2" "5[]unrel[]5" "5[]5[nogrid][]5"))
      (.add (doto (JToggleButton. rec-action)
              (.setToolTipText (text ::rec.tip))
              (.setIcon (icon ::rec-off :large))
              (.setSelectedIcon  (icon ::rec-on :large))))
      (.add
        (doto (JPanel.)
          (.setBackground Color/BLACK))
        "w 200, growy")
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
          (.add (text ::tab.output) (JLabel. "Test"))
          (.add (text ::tab.misc) (JLabel. "Test")))
        "h 150, growx, span 2, hidemode 2"))))

(defn show-app []
  "Initializes a main frame and controls of the app, wires them together and shows the UI."
  (doto (JFrame. (text ::app.title))
    (build-menu)
    (build-controls)
    (.setIconImages [(.getImage (icon ::mic)) (.getImage (icon ::mic :large))])
    (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
    (.setResizable false)
    (.pack)
    (.setLocationRelativeTo nil)
    (.setVisible true)))
