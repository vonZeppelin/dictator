;; Copyright 2014-2019 Leonid Bogdanov
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
    [dictator
      [audio :as audio]
      [engine :as engine]
      [util :refer [icon text ->ValueHolder]]]
    [clojure.core.async :refer [<! close! go-loop]])
  (:import
    [dictator LCDPanel Native Native$Aggressiveness]
    net.miginfocom.swing.MigLayout
    [java.awt BasicStroke BorderLayout Color Insets Point Rectangle RenderingHints Toolkit]
    [java.awt.event KeyEvent MouseAdapter WindowAdapter]
    [javax.swing AbstractAction DefaultBoundedRangeModel DefaultComboBoxModel Icon JCheckBox JComboBox JFrame
                 JLabel JMenu JMenuBar JMenuItem JOptionPane JPanel JSeparator JSlider JTabbedPane JToggleButton
                 JWindow KeyStroke SwingWorker]
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
(def ^:private selected-element (atom nil))


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
          audio-format (engine/audio-format engine)
          raw-ch (audio/capture-sound mixer audio-format)
          voice-ch (audio/filter-voice raw-ch audio-format vad-mode)
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
                           (as-> frame $
                             (.getContentPane $)
                             (.getComponents $)
                             (filter #(instance? JTabbedPane %) $)
                             (first $)
                             (.setSelectedIndex $ 2))))
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
  ^JPanel []
  (doto (JPanel. (MigLayout. "ay center,wrap" "[]rel[grow]" "[center]unrel[center]"))
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
  ^JPanel [frame]
  (let [tkit (Toolkit/getDefaultToolkit)
        xhair-icon (icon ::crosshair)
        xhair-hotspot (Point.
                        (/ (.getIconWidth xhair-icon) 2)
                        (/ (.getIconHeight xhair-icon) 2))
        xhair-cursor (.createCustomCursor tkit (.getImage xhair-icon) xhair-hotspot "xhair")
        pid-label (JLabel.)
        app-label (JLabel.)
        reset-labels (fn []
                       (doseq [lbl [pid-label app-label]]
                         (.setText lbl "")))
        swapper (fn [old-elem new-elem]
                  (some-> old-elem .close)
                  new-elem)
        mouse-tracker (proxy [MouseAdapter] []
                        (mouseClicked [evt]
                          (-> evt .getSource .dispose))
                        (mouseMoved [evt]
                          (let [veil (.getSource evt)
                                cursor-pos (.getLocationOnScreen evt)]
                            (.execute (proxy [SwingWorker] []
                                        (doInBackground []
                                          (swap! selected-element swapper (Native/findElement veil cursor-pos)))
                                        (done []
                                          (if-let [elem (.get this)]
                                            (do
                                              (->> elem .-pid str (.setText pid-label))
                                              (->> elem .-appTitle str (.setText app-label)))
                                            (reset-labels))
                                            ;; OK skip tree locking when in Swing EDT
                                          (-> veil .getContentPane (.getComponent 0) .repaint)))))))
        find-action (proxy [AbstractAction] [nil (icon ::crosshair :large)]
                      (actionPerformed [evt]
                        (let [find-button (.getSource evt)]
                          (when (.isSelected find-button)
                            (reset-labels)
                            (swap! selected-element swapper nil)
                            (let [esc-key (KeyStroke/getKeyStroke KeyEvent/VK_ESCAPE 0)
                                  dash-stroke (BasicStroke.
                                                2.0
                                                BasicStroke/CAP_BUTT
                                                BasicStroke/JOIN_MITER
                                                5.0
                                                (float-array [5.0])
                                                0.0)
                                  veil (JWindow. frame)
                                  canvas (proxy [JPanel] [nil false]
                                           (paintComponent [g]
                                             (proxy-super paintComponent g)
                                             (when-let [elem @selected-element]
                                               (let [area (.-area elem)]
                                                 (doto (.create g)
                                                   (.setColor Color/RED)
                                                   (.setStroke dash-stroke)
                                                   (.setRenderingHint
                                                      RenderingHints/KEY_ANTIALIASING
                                                      RenderingHints/VALUE_ANTIALIAS_ON)
                                                   (.drawRect
                                                     (.-x area)
                                                     (.-y area)
                                                     (.-width area)
                                                     (.-height area))
                                                   (.dispose))))))
                                  window-listener (proxy [WindowAdapter] []
                                                    (windowClosed [evt]
                                                      (.setSelected find-button false)))
                                  window-closer (proxy [AbstractAction] []
                                                  (actionPerformed [evt]
                                                    (reset-labels)
                                                    (.dispose veil)))
                                  root-pane (.getRootPane veil)]
                              (-> root-pane .getInputMap (.put esc-key "close-window"))
                              (-> root-pane .getActionMap (.put "close-window" window-closer))
                              (.setOpaque canvas false)
                              (doto veil
                                (.add canvas BorderLayout/CENTER)
                                (.addMouseListener mouse-tracker)
                                (.addMouseMotionListener mouse-tracker)
                                (.addWindowListener window-listener)
                                (.setBackground (Color. 1.0 1.0 1.0 0.001))
                                (.setAlwaysOnTop (.isAlwaysOnTopSupported veil))
                                (.setBounds (-> tkit .getScreenSize Rectangle.))
                                (.setCursor xhair-cursor)
                                (.setVisible true)))))))]
    (doto (JPanel. (MigLayout. "wrap" "[]25[]rel[grow]" "[]rel[]"))
      (.add
        (doto (JToggleButton. find-action)
          (.setToolTipText (text ::find.tip))
          (.setSelectedIcon
            (reify Icon
              (getIconHeight [_] 32)
              (getIconWidth [_] 32)
              (paintIcon [_ _ _ _ _]))))
        "spany,aligny top")
      (.add (JLabel. (text ::pid)))
      (.add pid-label)
      (.add (JLabel. (text ::title)))
      (.add app-label))))

(defn- build-misc-tab
  "Builds Misc tab."
  ^JPanel [frame]
  (let [ontop-action (proxy [AbstractAction] [(text ::ontop)]
                       (actionPerformed [evt]
                         (->> evt .getSource .isSelected (.setAlwaysOnTop frame)))
                       (isEnabled []
                         (.isAlwaysOnTopSupported frame)))]
    (doto (JPanel. (MigLayout. "aligny center,wrap" "[]rel[grow]" "[center]unrel[center]"))
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
      (.setLayout (MigLayout. "wrap" "[]unrel[]" "[]5[nogrid][]"))
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
