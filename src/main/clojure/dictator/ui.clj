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
    [clojure.core.async :refer [<! close! go-loop thread]]
    [dictator [audio :as audio]
              [engine :as engine]
              [util :refer [icon text ->ValueHolder]]])
  (:import
    net.miginfocom.swing.MigLayout
    [dictator LCDPanel Native Native$Aggressiveness]
    [java.awt Color Insets Point Rectangle Toolkit]
    [java.awt.event KeyEvent MouseAdapter WindowFocusListener]
    [javax.swing AbstractAction DefaultBoundedRangeModel DefaultComboBoxModel Icon JCheckBox JComboBox JFrame
                 JLabel JMenu JMenuBar JMenuItem JOptionPane JPanel JSeparator JSlider JTabbedPane JToggleButton
                 JWindow KeyStroke]
    [javax.swing.event ListDataListener]))

(def ^:private mixers-model (DefaultComboBoxModel.))
(def ^:private langs-model (DefaultComboBoxModel.))
(def ^:private pause-model (DefaultBoundedRangeModel. 500 0 500 2500))
(def ^:private engine-model (DefaultComboBoxModel. (to-array engine/engine-factories)))
(def ^:private quality-model (DefaultComboBoxModel.
                               (to-array [(->ValueHolder (text ::q.normal) :qnormal)
                                          (->ValueHolder (text ::q.low) :qlow)
                                          (->ValueHolder (text ::q.high) :qhigh)])))
(def ^:private vad-mode-model (DefaultComboBoxModel.
                                (to-array [(->ValueHolder (text ::v.normal) Native$Aggressiveness/NORMAL)
                                           (->ValueHolder (text ::v.lbrate) Native$Aggressiveness/LOW_BITRATE)
                                           (->ValueHolder (text ::v.agg) Native$Aggressiveness/AGGRESSIVE)
                                           (->ValueHolder (text ::v.vagg) Native$Aggressiveness/VERY_AGGRESSIVE)])))
(def ^:private dictation-channel (atom nil))


(defn- stop-dictation []
  (when-let [ch @dictation-channel]
    (close! ch)))

(defn- start-dictation []
  (when-let [mixer (.getSelectedItem mixers-model)]
    (let [lang (-> langs-model .getSelectedItem .value)
          pause (.getValue pause-model)
          vad-mode (-> vad-mode-model .getSelectedItem .value)
          quality (-> quality-model .getSelectedItem .value)
          engine (-> engine-model .getSelectedItem (engine/create quality lang))
          srate (engine/sample-rate engine)
          raw-ch (audio/capture-sound mixer srate)
          voice-ch (audio/filter-voice raw-ch srate vad-mode)
          chunks-ch (audio/aggregate-chunks voice-ch pause)
          text-ch (engine/recognize-speech chunks-ch engine)]
      (reset! dictation-channel raw-ch)
      (go-loop []
        (when-let [text (<! text-ch)]
          (println text)
          (recur))))))

(defn- build-menu
  "Builds a main menu of the app."
  [frame]
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

(defn- build-input-tab
  "Builds Input tab."
  []
  (doto (JPanel. (MigLayout. "aligny center,wrap 2" "[]rel[grow]" "[center]unrel[center]"))
    (.add (JLabel. (text ::device)))
    (.add
      (doto (JComboBox. mixers-model)
        (.setPrototypeDisplayValue ""))
      "growx")
    (.add (JLabel. (text ::lang)))
    (.add
      (doto (JComboBox. langs-model)
        (.setPrototypeDisplayValue ""))
      "growx")
    (.add (JLabel. (text ::pause)))
    (.add
      (doto (JSlider. pause-model)
        (.setLabelTable (java.util.Hashtable. {(int 500) (JLabel. (text ::sec 0.5) JLabel/CENTER)
                                               (int 1500) (JLabel. (text ::sec 1.5) JLabel/CENTER)
                                               (int 2500) (JLabel. (text ::sec 2.5) JLabel/CENTER)}))
        (.setMinorTickSpacing 500)
        (.setMajorTickSpacing 1000)
        (.setSnapToTicks true)
        (.setPaintLabels true)
        (.setPaintTicks true))
      "growx")))

;; TODO Handle multimonitor envs
(defn- build-output-tab
  "Builds Output tab."
  [frame]
  (let [tkit (Toolkit/getDefaultToolkit)
        xhair-icon (icon ::crosshair)
        xhair-hotspot (Point.
                        (quot (.getIconWidth xhair-icon) 2)
                        (quot (.getIconHeight xhair-icon) 2))
        xhair-cursor (.createCustomCursor tkit (.getImage xhair-icon) xhair-hotspot "xhair")
        mouse-tracker (proxy [MouseAdapter] []
                        (mouseClicked [evt]
                          (println evt))
                        (mouseMoved [evt]
                          (let [veil (.getSource evt)
                                cursor-pos (.getLocationOnScreen evt)
                                cursor-x (.-x cursor-pos)
                                cursor-y (.-y cursor-pos)]
                          (thread
                            (when-let [elem (Native/findElement veil cursor-x cursor-y)]
                              (println elem)
                              (.close elem))))))
        find-action (proxy [AbstractAction] [nil (icon ::crosshair :large)]
                      (actionPerformed [evt]
                        (let [find-button (.getSource evt)]
                          (when (.isSelected find-button)
                            (let [veil (JWindow. frame)
                                  root-pane (.getRootPane veil)
                                  esc-key (KeyStroke/getKeyStroke KeyEvent/VK_ESCAPE 0)
                                  window-closer (proxy [AbstractAction WindowFocusListener] []
                                                  (windowGainedFocus [_])
                                                  (windowLostFocus [evt]
                                                    (.setSelected find-button false)
                                                    (.dispose veil))
                                                  (actionPerformed [evt]
                                                    (.setSelected find-button false)
                                                    (.dispose veil)))]
                              (-> root-pane .getInputMap (.put esc-key "close-window"))
                              (-> root-pane .getActionMap (.put "close-window" window-closer))
                              (doto veil
                                (.addMouseListener mouse-tracker)
                                (.addMouseMotionListener mouse-tracker)
                                (.addWindowFocusListener window-closer)
                                (.setBackground (Color. 1.0 1.0 1.0 0.003))
                                (.setAlwaysOnTop (.isAlwaysOnTopSupported veil))
                                (.setBounds (-> tkit .getScreenSize Rectangle.))
                                (.setCursor xhair-cursor)
                                (.setVisible true)))))))]
    (doto (JPanel. (MigLayout. "" "[]rel[grow]" "[]"))
      (.add
        (doto (JToggleButton. find-action)
          (.setToolTipText (text ::find.tip))
          (.setSelectedIcon
            (reify Icon
              (getIconHeight [_] 32)
              (getIconWidth [_] 32)
              (paintIcon [_ _ _ _ _]))))))))

(defn- build-misc-tab
  "Builds Misc tab."
  [frame]
  (let [ontop-action (proxy [AbstractAction] [(text ::always.ontop)]
                       (actionPerformed [evt]
                         (->> evt .getSource .isSelected (.setAlwaysOnTop frame)))
                       (isEnabled []
                         (.isAlwaysOnTopSupported frame)))]
    (doto (JPanel. (MigLayout. "aligny center,wrap 2" "[]rel[grow]" "[center]unrel[center]"))
      (.add (JCheckBox. ontop-action) "spanx 2")
      (.add (JLabel. (text ::engine)))
      (.add
        (doto (JComboBox. engine-model)
          (.setPrototypeDisplayValue ""))
        "growx")
      (.add (JLabel. (text ::vad)))
      (.add
        (doto (JComboBox. vad-mode-model)
          (.setPrototypeDisplayValue ""))
        "growx")
      (.add (JLabel. (text ::quality)))
      (.add
        (doto (JComboBox. quality-model)
          (.setPrototypeDisplayValue ""))
        "growx"))))

(defn- build-controls
  "Builds UI controls of the main panel."
  [frame]
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
              (.setSelectedIcon (icon ::rec-on :large))))
      (.add (LCDPanel.) "wmin 200,grow")
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
          (.add (text ::tab.output) (build-output-tab frame))
          (.add (text ::tab.misc) (build-misc-tab frame)))
        "growx,span 2,hidemode 2"))))

(defn show-app
  "Initializes a main frame and controls of the app, wires them together and shows the UI."
  []
  (letfn [(populate-langs []
            (.removeAllElements langs-model)
            (doseq [lang (-> engine-model .getSelectedItem .supports)]
              (.addElement langs-model (->ValueHolder (.getDisplayLanguage lang) lang))))]
    (.addListDataListener
      engine-model
      (reify ListDataListener
        (intervalAdded [_ _])
        (intervalRemoved [_ _])
        (contentsChanged [_ _]
          (populate-langs))))
    (populate-langs)
    (doto (JFrame. (text ::app.title))
      (build-menu)
      (build-controls)
      (.setIconImages [(.getImage (icon ::darth)) (.getImage (icon ::darth :large))])
      (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
      (.setResizable false)
      (.pack)
      (.setLocationRelativeTo nil)
      (.setVisible true))))
