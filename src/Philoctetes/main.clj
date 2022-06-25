(ns Philoctetes.main
  (:require
   [clojure.core.async
    :refer [chan put! take! close! offer! to-chan! timeout thread
            sliding-buffer dropping-buffer
            go >! <! alt! alts! do-alts
            mult tap untap pub sub unsub mix unmix admix
            pipe pipeline pipeline-async]]
   [clojure.core.async.impl.protocols :refer [closed?]]
   [clojure.java.io]
   [clojure.string]
   [clojure.pprint]
   [clojure.repl]
   [cheshire.core]

   [datahike.api]
   [taoensso.timbre]

   [Philoctetes.seed]
   [Philoctetes.pears]
   [Philoctetes.peanuts]
   [Philoctetes.salt]
   [Philoctetes.oats]
   [Philoctetes.prunes])
  (:import
   (javax.swing JFrame WindowConstants JPanel JScrollPane JTextArea BoxLayout JEditorPane ScrollPaneConstants SwingUtilities JDialog)
   (javax.swing JMenu JMenuItem JMenuBar KeyStroke JOptionPane JToolBar JButton JToggleButton JSplitPane JLabel JTextPane JTextField JTable)
   (javax.swing DefaultListSelectionModel JCheckBox JTabbedPane)
   (javax.swing.border EmptyBorder)
   (javax.swing.table DefaultTableModel)
   (javax.swing.plaf.basic BasicTabbedPaneUI)
   (javax.swing.event DocumentListener DocumentEvent ListSelectionListener ListSelectionEvent)
   (java.awt Toolkit Dimension)
   (javax.swing.text SimpleAttributeSet StyleConstants JTextComponent)
   (java.awt.event KeyListener KeyEvent MouseListener MouseEvent ActionListener ActionEvent ComponentListener ComponentEvent)
   (java.awt.event  WindowListener WindowAdapter WindowEvent)
   (com.formdev.flatlaf FlatLaf FlatLightLaf)
   (com.formdev.flatlaf.extras FlatUIDefaultsInspector FlatDesktop FlatDesktop$QuitResponse)
   (com.formdev.flatlaf.util SystemInfo UIScale)
   (java.util.function Consumer)
   (java.util ServiceLoader)
   (net.miginfocom.swing MigLayout)
   (net.miginfocom.layout ConstraintParser LC UnitValue)
   (java.io File ByteArrayOutputStream PrintStream OutputStreamWriter PrintWriter)
   (java.lang Runnable)

   (java.util.stream Stream)
   (java.util Base64)
   (java.io BufferedReader)
   (java.nio.charset StandardCharsets))
  (:gen-class))

(do (set! *warn-on-reflection* true) (set! *unchecked-math* true))

(taoensso.timbre/merge-config! {:min-level :warn})

(defonce program-data-dirpath
  (or
   (some-> (System/getenv "PHILOCTETES_PATH")
           (.replaceFirst "^~" (System/getProperty "user.home")))
   (.getCanonicalPath ^File (clojure.java.io/file (System/getProperty "user.home") ".Philoctetes"))))

(defonce program-db-dirpath (.getCanonicalPath ^File (clojure.java.io/file program-data-dirpath "db")))

(defonce state-file-filepath (.getCanonicalPath ^File (clojure.java.io/file program-data-dirpath "Philoctetes.edn")))

(defonce stateA (atom nil))
(defonce gamesA (atom nil))
(defonce gameA (atom nil))
(defonce settingsA (atom nil))

(defonce cancel-sub| (chan 1))
(defonce cancel-pub| (chan 1))
(defonce ops| (chan 10))
(defonce tabs| (chan 1))
(defonce table| (chan (sliding-buffer 10)))
(defonce sub| (chan (sliding-buffer 10)))
(def ^:dynamic ^JFrame jframe nil)
(def ^:dynamic ^JPanel jroot-panel nil)
(def ^:dynamic raw-stream-connection-pool nil)

#_(defonce *ns (find-ns 'Philoctetes.main))

(def ^:const jframe-title "call me Phil")

(defn reload
  []
  (require
   '[Philoctetes.seed]
   '[Philoctetes.pears]
   '[Philoctetes.peanuts]
   '[Philoctetes.salt]
   '[Philoctetes.oats]
   '[Philoctetes.prunes]
   '[Philoctetes.main]
   :reload))

(defn menubar-process
  [{:keys [^JMenuBar jmenubar
           ^JFrame jframe
           menubar|]
    :as opts}]
  (let [on-menubar-item (fn [f]
                          (reify ActionListener
                            (actionPerformed [_ event]
                              (SwingUtilities/invokeLater
                               (reify Runnable
                                 (run [_]
                                   (f _ event)))))))

        on-menu-item-show-dialog (on-menubar-item (fn [_ event] (JOptionPane/showMessageDialog jframe (.getActionCommand ^ActionEvent event) "menu bar item" JOptionPane/PLAIN_MESSAGE)))]
    (doto jmenubar
      (.add (doto (JMenu.)
              (.setText "program")
              (.setMnemonic \F)
              
              (.add (doto (JMenuItem.)
                      (.setText "pears")
                      (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_F (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                      (.setMnemonic \F)
                      (.addActionListener
                       (on-menubar-item (fn [_ event]
                                          (put! tabs| {:op :tab :tab-name :pears})
                                          #_(put! menubar| {:op :game}))))))
              
              (.add (doto (JMenuItem.)
                      (.setText "peanuts")
                      (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_P (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                      (.setMnemonic \P)
                      (.addActionListener
                       (on-menubar-item (fn [_ event]
                                          (put! tabs| {:op :tab :tab-name :peanuts})
                                          #_(put! menubar| {:op :game}))))))
              
              (.add (doto (JMenuItem.)
                      (.setText "salt")
                      (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_S (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                      (.setMnemonic \S)
                      (.addActionListener
                       (on-menubar-item (fn [_ event]
                                          (put! tabs| {:op :tab :tab-name :salt})
                                          #_(put! menubar| {:op :game}))))))
              #_(.add (doto (JMenuItem.)
                        (.setText "join")
                        (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_J (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                        (.setMnemonic \J)
                        (.addActionListener on-menu-item-show-dialog)))
              #_(.add (doto (JMenuItem.)
                        (.setText "observe")
                        (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_O (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                        (.setMnemonic \O)
                        (.addActionListener on-menu-item-show-dialog)))
              (.add (doto (JMenuItem.)
                      (.setText "oats")
                      (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_O (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                      (.setMnemonic \O)
                      (.addActionListener
                       (on-menubar-item (fn [_ event]
                                          (put! tabs| {:op :tab :tab-name :oats})
                                          #_(put! menubar| {:op :discover}))))))
              
              (.add (doto (JMenuItem.)
                      (.setText "prunes")
                      (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_W (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                      (.setMnemonic \W)
                      (.addActionListener
                       (on-menubar-item (fn [_ event]
                                          (put! tabs| {:op :tab :tab-name :prunes})
                                          #_(put! menubar| {:op :game}))))))

              (.add (doto (JMenuItem.)
                      (.setText "exit")
                      (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_Q (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                      (.setMnemonic \Q)
                      (.addActionListener (on-menubar-item (fn [_ event]
                                                             (.dispose jframe))))))))

      #_(.add (doto (JMenu.)
                (.setText "edit")
                (.setMnemonic \E)
                (.add (doto (JMenuItem.)
                        (.setText "undo")
                        (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_Z (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                        (.setMnemonic \U)
                        (.addActionListener on-menu-item-show-dialog)))
                (.add (doto (JMenuItem.)
                        (.setText "redo")
                        (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_Y (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                        (.setMnemonic \R)
                        (.addActionListener on-menu-item-show-dialog)))
                (.addSeparator)
                (.add (doto (JMenuItem.)
                        (.setText "cut")
                        (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_X (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                        (.setMnemonic \C)
                        (.addActionListener on-menu-item-show-dialog)))
                (.add (doto (JMenuItem.)
                        (.setText "copy")
                        (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_C (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                        (.setMnemonic \O)
                        (.addActionListener on-menu-item-show-dialog)))
                (.add (doto (JMenuItem.)
                        (.setText "paste")
                        (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_V (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))))
                        (.setMnemonic \P)
                        (.addActionListener on-menu-item-show-dialog)))
                (.addSeparator)
                (.add (doto (JMenuItem.)
                        (.setText "delete")
                        (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_DELETE 0))
                        (.setMnemonic \D)
                        (.addActionListener on-menu-item-show-dialog)))))))
  nil)

(defn host-game-process
  [{:keys [^JFrame root-jframe
           ^JFrame jframe
           ^String frequency
           yes|]
    :or {frequency (str (java.util.UUID/randomUUID))}
    :as opts}]
  (let [root-panel (JPanel.)
        jfrequency-text-field (JTextField. frequency 40)
        jbutton-yes (JButton. "yes")]

    (doto jframe
      (.add root-panel))

    (doto jbutton-yes
      (.addActionListener
       (reify ActionListener
         (actionPerformed [_ event]
           (put! yes| {:op :host-yes
                       :frequency (.getText jfrequency-text-field)})
           (.dispose jframe)))))

    (doto root-panel
      (.setLayout (MigLayout. "insets 10"))
      (.add (JLabel. "frequency") "cell 0 0")
      (.add jfrequency-text-field "cell 1 0")
      (.add jbutton-yes "cell 0 1"))

    (.setPreferredSize jframe (Dimension. (* 0.8 (.getWidth root-jframe))
                                          (* 0.8 (.getHeight root-jframe))))

    (doto jframe
      (.setDefaultCloseOperation WindowConstants/DISPOSE_ON_CLOSE #_WindowConstants/EXIT_ON_CLOSE)
      (.pack)
      (.setLocationRelativeTo root-jframe)
      (.setVisible true)))
  nil)

(defn discover-process
  [{:keys [^JPanel jpanel-tab
           ops|
           gamesA
           gameA
           stateA]
    :or {}
    :as opts}]
  (let [jtable (JTable.)
        jscroll-pane (JScrollPane.)

        jbutton-host (JButton. "host")
        jbutton-join (JButton. "join")
        jbutton-observe (JButton. "observe")
        jbutton-leave (JButton. "leave")
        jbutton-open (JButton. "open")

        jtext-field-frequency (JTextField. (str (java.util.UUID/randomUUID)) 40)

        column-names (into-array ^Object ["frequency" "host"])
        table-model (DefaultTableModel.) #_(DefaultTableModel.
                                            ^"[[Ljava.lang.Object;"
                                            (to-array-2d
                                             [[(str (java.util.UUID/randomUUID)) 10]
                                              [(str (java.util.UUID/randomUUID)) 10]])
                                            ^"[Ljava.lang.Object;"
                                            (into-array ^Object ["frequency" "guests"])
                                            #_(object-array
                                               [(object-array)
                                                (object-array
                                                 [(str (java.util.UUID/randomUUID)) 10])]))]

    (doto jtable
      (.setModel table-model)
      (.setRowSelectionAllowed true)
      (.setSelectionModel (doto (DefaultListSelectionModel.)
                            (.addListSelectionListener
                             (reify ListSelectionListener
                               (valueChanged [_ event]
                                 (when (not= -1 (.getSelectedRow jtable))
                                   (SwingUtilities/invokeLater
                                    (reify Runnable
                                      (run [_]
                                        (.setText jtext-field-frequency (.getValueAt jtable (.getSelectedRow jtable) 0)))))))))))
      #_(.setAutoCreateRowSorter true))

    (doto jscroll-pane
      (.setViewportView jtable)
      (.setHorizontalScrollBarPolicy ScrollPaneConstants/HORIZONTAL_SCROLLBAR_NEVER))

    (doto jbutton-host
      (.addActionListener
       (reify ActionListener
         (actionPerformed [_ event]
           (put! ops| {:op :game
                       :role :host
                       :frequency (.getText jtext-field-frequency)})
           #_(.dispose jframe)))))

    (doto jbutton-join
      (.addActionListener
       (reify ActionListener
         (actionPerformed [_ event]
           (put! ops| {:op :game
                       :role :player
                       :frequency (.getText jtext-field-frequency)})
           #_(.dispose jframe)))))

    (doto jbutton-leave
      (.addActionListener
       (reify ActionListener
         (actionPerformed [_ event]
           (put! ops| {:op :leave})
           #_(.dispose jframe)))))

    (doto jpanel-tab
      (.setLayout (MigLayout. "insets 10"))
      (.add jscroll-pane "cell 0 0 3 1, width 100%")
      (.add jtext-field-frequency "cell 0 1")
      (.add jbutton-host "cell 0 2")
      (.add jbutton-join "cell 0 2")
      (.add jbutton-open "cell 0 2")
      (.add jbutton-leave "cell 0 2"))

    (remove-watch gameA :discover-process)
    (add-watch gameA :discover-process
               (fn [ref wathc-key old-state new-state]
                 #_(println  :gameA old-state new-state)
                 #_(when (not= old-state new-state))
                 (SwingUtilities/invokeLater
                  (reify Runnable
                    (run [_]
                      (let [we-host? (= (:host-id new-state) (:peer-id @stateA))
                            in-game? (not (empty? new-state))]
                        (.setEnabled jbutton-open in-game?)
                        (.setEnabled jbutton-leave in-game?)
                        (.setEnabled jbutton-host (not in-game?))
                        (.setEnabled jbutton-join (not in-game?))))))))

    (remove-watch gamesA :discover-process)
    (add-watch gamesA :discover-process
               (fn [ref wathc-key old-state new-state]
                 (when (not= old-state new-state)
                   #_(println new-state)
                   (SwingUtilities/invokeLater
                    (reify Runnable
                      (run [_]
                        (let [selected-frequency (when (not= -1 (.getSelectedRow jtable))
                                                   (.getValueAt jtable (.getSelectedRow jtable) 0))
                              data (map (fn [[frequency {:keys [frequency host-peer-id]}]]
                                          [frequency host-peer-id]) new-state)]
                          (.setDataVector table-model
                                          ^"[[Ljava.lang.Object;"
                                          #_(to-array-2d
                                             [[(str (java.util.UUID/randomUUID)) 10]
                                              [(str (java.util.UUID/randomUUID)) 10]])
                                          (to-array-2d data)
                                          ^"[Ljava.lang.Object;"
                                          column-names)
                          (when selected-frequency
                            (let [^int new-index (->>
                                                  data
                                                  (into []
                                                        (comp
                                                         (map first)
                                                         (map-indexed vector)
                                                         (keep (fn [[index frequency]] (when (= frequency selected-frequency) index)))))
                                                  (first))]
                              (when new-index
                                (.setRowSelectionInterval jtable new-index new-index)))))))))))

    #_(go
        (loop []
          (when-let [value (<! table|)]
            (SwingUtilities/invokeLater
             (reify Runnable
               (run [_]
                 (.setDataVector table-model
                                 ^"[[Ljava.lang.Object;"
                                 (to-array-2d
                                  value)
                                 ^"[Ljava.lang.Object;"
                                 column-names))))
            (recur)))))
  nil)

(defn settings-process
  [{:keys [^JPanel jpanel-tab
           ops|
           settingsA]
    :or {}
    :as opts}]
  (let [jscroll-pane (JScrollPane.)

        jcheckbox-apricotseed (JCheckBox.)]

    #_(doto jscroll-pane
        (.setViewportView jpanel-tab)
        (.setHorizontalScrollBarPolicy ScrollPaneConstants/HORIZONTAL_SCROLLBAR_NEVER))

    (doto jpanel-tab
      (.setLayout (MigLayout. "insets 10"))
      (.add (JLabel. ":apricotseed?") "cell 0 0")
      #_(.add jcheckbox-apricotseed "cell 0 0"))

    (.addActionListener jcheckbox-apricotseed
                        (reify ActionListener
                          (actionPerformed [_ event]
                            (SwingUtilities/invokeLater
                             (reify Runnable
                               (run [_]
                                 (put! ops| {:op :settings-value
                                             :_ (.isSelected jcheckbox-apricotseed)})))))))

    (remove-watch settingsA :settings-process)
    (add-watch settingsA :settings-process
               (fn [ref wathc-key old-state new-state]
                 (SwingUtilities/invokeLater
                  (reify Runnable
                    (run [_]
                      (.setSelected jcheckbox-apricotseed (:apricotseed? new-state))))))))
  nil)

(defn -main
  [& args]
  (println "call me Phil")
  (println "i dont want my next job")
  (println "Kuiil has spoken")

  #_(alter-var-root #'*ns* (constantly (find-ns 'Philoctetes.main)))

  (when SystemInfo/isMacOS
    (System/setProperty "apple.laf.useScreenMenuBar" "true")
    (System/setProperty "apple.awt.application.name" jframe-title)
    (System/setProperty "apple.awt.application.appearance" "system"))

  (when SystemInfo/isLinux
    (JFrame/setDefaultLookAndFeelDecorated true)
    (JDialog/setDefaultLookAndFeelDecorated true))

  (when (and
         (not SystemInfo/isJava_9_orLater)
         (= (System/getProperty "flatlaf.uiScale") nil))
    (System/setProperty "flatlaf.uiScale" "2x"))

  (FlatLightLaf/setup)

  (FlatDesktop/setQuitHandler (reify Consumer
                                (accept [_ response]
                                  (.performQuit ^FlatDesktop$QuitResponse response))
                                (andThen [_ after] after)))

  (let [screenshotsMode? (Boolean/parseBoolean (System/getProperty "flatlaf.demo.screenshotsMode"))

        jframe (JFrame. jframe-title)
        jmenubar (JMenuBar.)
        jroot-panel (JPanel.)]

    (clojure.java.io/make-parents program-data-dirpath)
    (reset! stateA {:Philoctetes-row 1
                    :Philoctetes-col 1})
    (reset! gamesA {})
    (reset! gameA {})
    (reset! settingsA {:apricotseed? true})

    (clojure.java.io/make-parents program-db-dirpath)
    (let [config {:store {:backend :file :path program-db-dirpath}
                  :keep-history? true
                  :name "main"}
          _ (when-not (datahike.api/database-exists? config)
              (datahike.api/create-database config))
          conn (datahike.api/connect config)]

      (datahike.api/transact
       conn
       [{:db/cardinality :db.cardinality/one
         :db/ident :id
         :db/unique :db.unique/identity
         :db/valueType :db.type/uuid}
        {:db/ident :name
         :db/valueType :db.type/string
         :db/cardinality :db.cardinality/one}])

      (datahike.api/transact
       conn
       [{:id #uuid "3e7c14ce-5f00-4ac2-9822-68f7d5a60952"
         :name  "datahike"}
        {:id #uuid "f82dc4f3-59c1-492a-8578-6f01986cc4c2"
         :name  "Wichita"}
        {:id #uuid "5358b384-3568-47f9-9a40-a9a306d75b12"
         :name  "Little-Rock"}])

      (->>
       (datahike.api/q '[:find ?e ?n
                         :where
                         [?e :name ?n]]
                       @conn)
       (println))

      (->>
       (datahike.api/q '[:find [?ident ...]
                         :where [_ :db/ident ?ident]]
                       @conn)
       (sort)
       (println)))

    (SwingUtilities/invokeLater
     (reify Runnable
       (run [_]

         (doto jframe
           (.add jroot-panel)
           (.addComponentListener (let []
                                    (reify ComponentListener
                                      (componentHidden [_ event])
                                      (componentMoved [_ event])
                                      (componentResized [_ event])
                                      (componentShown [_ event]))))
           (.addWindowListener (proxy [WindowAdapter] []
                                 (windowClosing [event]
                                   (let [event ^WindowEvent event]
                                     #_(println :window-closing)
                                     (-> event (.getWindow) (.dispose)))))))

         (doto jroot-panel
           #_(.setLayout (BoxLayout. jroot-panel BoxLayout/Y_AXIS))
           (.setLayout (MigLayout. "insets 10"
                                   "[grow,shrink,fill]"
                                   "[grow,shrink,fill]")))

         
         (let [jtabbed-pane (JTabbedPane.)
               tabs {:pears (JPanel.)
                     :peanuts (JPanel.)
                     :salt (JPanel.)
                     :oats (JPanel.)
                     :prunes (JPanel.)}]

           (doto jtabbed-pane
             (.setTabLayoutPolicy JTabbedPane/SCROLL_TAB_LAYOUT)
             (.setUI (proxy [BasicTabbedPaneUI] []
                       (calculateTabAreaHeight [tab-placement run-count max-tab-height]
                         (int 0))))
             (.addTab "pears" (:pears tabs))
             (.addTab "peanuts" (:peanuts tabs))
             (.addTab "salt" (:salt tabs))
             (.addTab "oats" (:oats tabs))
             (.addTab "prunes" (:prunes tabs))
             (.setSelectedComponent (:oats tabs)))

           (go
             (loop []
               (when-let [value (<! tabs|)]
                 (SwingUtilities/invokeLater
                  (reify Runnable
                    (run [_]
                      (.setSelectedComponent jtabbed-pane ^JPanel ((:tab-name value) tabs)))))
                 (recur))))

           (Philoctetes.oats/process {:jpanel-tab (:oats tabs)})

           (settings-process {:jpanel-tab (:prunes tabs)
                              :ops| ops|
                              :settingsA settingsA})

           (discover-process
            {:jpanel-tab (:pears tabs)
             :ops| ops|
             :gamesA gamesA
             :gameA gameA
             :stateA stateA})

           (doto ^JPanel (:oats tabs)
             (.setLayout (MigLayout. "insets 10"
                                     "[grow,shrink,fill]"
                                     "[grow,shrink,fill]")))

           (.add ^JPanel (:oats tabs) (JTextField. "Word") "id Word, pos 50%-Word.w 50%-Word.h" #_"dock center,width 100 :100%:100%")

           (.add jroot-panel jtabbed-pane))
         
         

         (menubar-process
          {:jmenubar jmenubar
           :jframe jframe
           :menubar| ops|})
         (.setJMenuBar jframe jmenubar)

         (.setPreferredSize jframe
                            (let [size (-> (Toolkit/getDefaultToolkit) (.getScreenSize))]
                              (Dimension. (* 0.7 (.getWidth size)) (* 0.7 (.getHeight size)))
                              #_(Dimension. (UIScale/scale 1024) (UIScale/scale 576)))
                            #_(if SystemInfo/isJava_9_orLater
                                (Dimension. 830 440)
                                (Dimension. 1660 880)))

         #_(doto jframe
             (.setDefaultCloseOperation WindowConstants/DISPOSE_ON_CLOSE #_WindowConstants/EXIT_ON_CLOSE)
             (.setSize 2400 1600)
             (.setLocation 1300 200)
             #_(.add panel)
             (.setVisible true))

         (doto jframe
           (.setDefaultCloseOperation WindowConstants/DISPOSE_ON_CLOSE #_WindowConstants/EXIT_ON_CLOSE)
           (.pack)
           (.setLocationRelativeTo nil)
           (.setVisible true))

         (alter-var-root #'Philoctetes.main/jframe (constantly jframe))
         (alter-var-root #'Philoctetes.main/jroot-panel (constantly jroot-panel))

         (remove-watch stateA :watch-fn)
         (add-watch stateA :watch-fn
                    (fn [ref wathc-key old-state new-state]

                      (when (not= old-state new-state))))

         (remove-watch settingsA :main)
         (add-watch settingsA :main
                    (fn [ref wathc-key old-state new-state]
                      (SwingUtilities/invokeLater
                       (reify Runnable
                         (run [_]
                           (if (:apricotseed? @settingsA)
                             (do nil)
                             (do nil)))))))
         (reset! settingsA @settingsA))))


    (go
      (loop []
        (when-let [value (<! ops|)]
          (condp = (:op value)

            :discover
            (let [discover-jframe (JFrame. "discover")]
              (discover-process
               {:jframe discover-jframe
                :root-jframe jframe
                :ops| ops|
                :gamesA gamesA
                :gameA gameA
                :stateA stateA})
              (reset! gameA @gameA))

            :settings
            (let [settings-jframe (JFrame. "settings")]
              (settings-process
               {:jframe settings-jframe
                :root-jframe jframe
                :ops| ops|
                :settingsA settingsA})
              (reset! settingsA @settingsA))

            :settings-value
            (let []
              (swap! settingsA merge value)))

          (recur))))))


(comment

  (.getName (class (make-array Object 1 1)))

  (.getName (class (make-array String 1)))

  ;
  )