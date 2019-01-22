(ns cljs-karaoke.app
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-karaoke.events :as events]
            [cljs-karaoke.subs :as s]
            [re-frame.core :as rf :include-macros true]
            [ajax.core :as ajax]
            [cljs.reader :as reader]
            [cljs.core.async :as async :refer [go go-loop chan <! >! timeout alts!]]
            ["midi.js"]
            [day8.re-frame.http-fx]
            [cljs-karaoke.songs :as songs :refer [song-table-component]]
            [cljs-karaoke.lyrics :as l :refer [preprocess-frames]]))


#_(defn player [midi-path]
    [:audio {:src midi-path
             :controls  true
             :auto-play true
             :preload true
             :height "100%"
             :width "100%"}])


(def audio (atom nil))
(def lyrics (atom nil))
(def lyrics-loaded? (atom false))
(def display-lyrics? (atom false))
(def current-frame (atom nil))
(def current-song (atom (first songs/song-list)))
(def player-status (atom nil))

(defn toggle-display-lyrics []
  (rf/dispatch [::events/set-display-lyrics? (not @(rf/subscribe [::s/display-lyrics?]))]))

(defn toggle-display-lyrics-link []
  [:a {:href "#"
       :on-click toggle-display-lyrics}
   (if @display-lyrics? "hide lyrics" "show lyrics")])

(defn load-song [name]
  (let [audio-path (str "mp3/" name ".mid.mp3")
        lyrics-path (str "lyrics/" name ".edn")
        audio (js/Audio. audio-path)]
    (rf/dispatch [::events/set-audio audio])
    ;; (rf/dispatch [::events/set-lyrics-loaded? false])
    ;; (reset! lyrics-loaded? false)
    (rf/dispatch [::events/fetch-lyrics name preprocess-frames])
    (rf/dispatch [::events/toggle-song-list-visible])
    #_(ajax/GET lyrics-path
              {:handler #(do
                           (rf/dispatch [::events/set-lyrics
                                         (-> (reader/read-string %)
                                             (preprocess-frames))])
                           (rf/dispatch [::events/set-lyrics-loaded? true]))})))
(def lyrics-delay 1000)
(defn highlight-parts [frame]
  (let [part-chan (chan)
        evts (:events frame)]
    (doseq [v (vec evts)]
      (go
        (<! (timeout (+ lyrics-delay (long (:offset v)))))
        (>! part-chan (:id v))))
    (go-loop [h-id (<! part-chan)]
      (when h-id
        (println "highlight " h-id)
        (rf/dispatch
         [::events/highlight-frame-part h-id])
        (recur (<! part-chan))))))
(defn play-lyrics [frames]
  (let [frame-chan (chan 1000)
        part-chan (chan)]
    (doseq [frame (vec frames)
            :when (not= nil (:ticks frame))]
      (go
        (<! (timeout (- (long (:offset frame)) lyrics-delay)))
        (println "after timeout" frame)
        (>! frame-chan frame)
        (highlight-parts frame))
      nil)
    (go-loop [fr (<! frame-chan)]
      (when-not (nil? fr)
        (rf/dispatch-sync [::events/set-current-frame fr])
        (recur (<! frame-chan))))
    frame-chan))

;; (comment
  ;; (ajax/GET
   ;; "lyrics/Africa.edn"
   ;; {:handler #(rf/dispatch [::events/set-lyrics (reader/read-string %)])}))


   
(defn frame-text [frame]
  [:div.frame-text
   (for [e (vec (:events frame))]
     [:span {:key (str "evt_" (:id e))
             :class (if (:highlighted? e) ["highlighted"] [])}
      (:text e)])])



(defn lyrics-view [lyrics]
  [:ul
   (for [frame (vec lyrics)]
     [:li [frame-text frame]])])

(defn current-frame-display []
  (when @(rf/subscribe [::s/current-frame])
    [:div.current-frame
     [frame-text @(rf/subscribe [::s/current-frame])]]))
   
(defn play []
  (let [audio (rf/subscribe [::s/audio])
        lyrics (rf/subscribe [::s/lyrics])]
    (rf/dispatch-sync [::events/set-player-status
                       (play-lyrics @lyrics)])
    ;; (.play @audio)

    (rf/dispatch [::events/play @audio @lyrics (play-lyrics @lyrics)])))

(defn stop []
  (let [audio (rf/subscribe [::s/audio])
        player-status (rf/subscribe [::s/player-status])]
    (.pause @audio)
    (.load @audio)
    (async/close! @player-status)
    (rf/dispatch [::events/set-player-status nil])
    (rf/dispatch [::events/set-current-frame nil])
    (rf/dispatch [::events/set-lyrics nil])
    (rf/dispatch [::events/set-lyrics-loaded? false])))

(defn toggle-song-list-btn []
  (let [visible? (rf/subscribe [::s/song-list-visible?])]
    [:button.button
     {:class (concat []
                   (if @visible?
                     ["is-selected"
                      "is-success"]
                     ["is-danger"]))
      :on-click #(rf/dispatch [::events/toggle-song-list-visible])}
     (if @visible?
       "Hide songs"
       "Show song list")]))
(defn app []
  (let [lyrics (rf/subscribe [::s/lyrics])
        display-lyrics? (rf/subscribe [::s/display-lyrics?])
        current-song (rf/subscribe [::s/current-song])
        lyrics-loaded? (rf/subscribe [::s/lyrics-loaded?])
        songs-visible? (rf/subscribe [::s/song-list-visible?])]
    [:div.container.app
     [:div.app-bg]
     [:h1 "karaoke"]
     [:h2 [current-frame-display]]
     [toggle-display-lyrics-link]
     [:ul
      [:li (str "current: " @current-song)]
      (when (and
             @lyrics
             @display-lyrics?)
        [:li [lyrics-view @lyrics]])
      [:li [:span (str "lyrics loaded? ") (if @lyrics-loaded? [:span.tag.is-success "loaded"] [:span.tag.is-danger "not loaded"])]]]
     [:button.button {:on-click #(load-song @current-song)} "Load song"]
     [:button.button {:on-click play}
          "Play song"]
     [:button.button {:on-click stop} "Stop"]
     [toggle-song-list-btn]
     (when @songs-visible?
       [songs/song-table-component {:select-fn
                                    (fn [s]
                                      (rf/dispatch-sync [::events/set-current-song s])
                                      (load-song @current-song))}])]))


(defn mount-components! []
  (reagent/render
   [app]
   (. js/document (getElementById "root"))))

(defn init! []
  (println "init!")
  (rf/dispatch-sync [::events/init-db])
  (mount-components!))


(def instrument-prefix "http://gleitz.github.io/midi-js-soundfonts/FluidR3_GM/")
(defn instr [name] (str name))
(def plugin-data {;:instrument "rock_organ_1"
                  :instruments
                  [ "accordion",
                   "acoustic_bass",
                   "acoustic_grand_piano",
                   ;; "acoustic_guitar_nylon",
                   "acoustic_guitar_steel",
                   ;; "agogo",
                   "alto_sax",
                   ;; "applause",
                   "baritone_sax",
                   ;; "bassoon",
                   ;; "brass_section",
                   "bright_acoustic_piano",
                   ;; "celesta",
                   "cello",
                   "church_organ",
                   "clarinet",
                   "distortion_guitar",
                   ;; "drawbar_organ",
                   ;; "dulcimer",
                   "electric_bass_pick",
                   "electric_grand_piano",
                   ;; "electric_piano_1",
                   ;; "electric_piano_2",
                   ;; "english_horn",
                   ;; "fiddle",
                   "flute",
                   "french_horn",
                   "fretless_bass",
                   "guitar_fret_noise",
                   "guitar_harmonics",
                   "harmonica",
                   "harpsichord",
                   ;; "helicopter",
                   ;; "honkytonk_piano",
                   ;; "kalimba",
                   ;; "koto",
                   "lead_1_square",
                   "lead_2_sawtooth",
                   "lead_3_calliope",
                   "lead_4_chiff",
                   "lead_5_charang",
                   "lead_6_voice",
                   "lead_7_fifths",
                   "lead_8_bass__lead",
                   "marimba",
                   "melodic_tom",
                   ;; "music_box",
                   ;; "muted_trumpet",
                   "oboe",
                   "ocarina",
                   "orchestra_hit",
                   "orchestral_harp",
                   "overdriven_guitar",
                   ;; "percussion",
                   ;; "percussive_organ",
                   ;; "piccolo",
                   ;; "pizzicato_strings",
                   ;; "reed_organ",
                   ;; "reverse_cymbal",
                   "rock_organ",
                   "slap_bass_1",
                   ;; "slap_bass_2",
                   "soprano_sax",
                   "string_ensemble_1",
                   ;; "string_ensemble_2",
                   "synth_bass_1",
                   ;; "synth_bass_2",
                   "synth_brass_1",
                   ;; "synth_brass_2",
                   "synth_choir",
                   "synth_drum",
                   "synth_strings_1",
                   ;; "synth_strings_2",
                   ;; "tenor_sax",
                   ;; "trombone",
                   "trumpet",
                   "tuba",
                   "vibraphone",
                   ;; "viola",
                   "violin",
                   ;; "voice_oohs",
                   ;; "whistle",
                   "xylophone"]
                  :onsuccess (fn [] (println "plugin load success"))})
