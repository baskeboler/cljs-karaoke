(ns cljs-karaoke.app
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :as ajax]
            [cljs.reader :as reader]
            [cljs.core.async :as async :refer [go go-loop chan <! >! timeout]]
            ["midi.js"]
            [cljs-karaoke.songs :as songs :refer [song-table-component]]))


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
  (swap! display-lyrics? not))

(defn toggle-display-lyrics-link []
  [:a {:href "#"
       :on-click toggle-display-lyrics}
   (if @display-lyrics? "hide lyrics" "show lyrics")])

(defn to-relative-offset-events [base-offset]
  (fn [event]
    (-> event (update :offset - base-offset))))

(defn update-events-to-relative-offset [base-offset]
  (fn [events]
    (mapv (to-relative-offset-events base-offset) events)))

(defn to-relative-frame-offsets [frames]
  (reduce (fn [res fr]
            (let [last-frame (last res)
                  new-offset (if-not (nil? last-frame)
                               (- (:offset fr) (:offset last-frame))
                               (:offset fr))
                  new-frame (assoc fr :relative-offset new-offset)]
              (conj res new-frame)))
          []
          frames))
(defn preprocess-frames [frames]
  (let [with-offset (mapv #(-> % (assoc :offset (-> (map :offset (:events %))
                                                    sort
                                                    first)))
                                                    ;; (- 10000))))
                           frames)
        with-relative-events (mapv #(-> % (update :events (update-events-to-relative-offset (:offset %)))) with-offset)]
    with-offset))

(defn load-song [name]
  (let [audio-path (str "mp3/" name ".mid.mp3")
        lyrics-path (str "lyrics/" name ".edn")]
    (reset! audio (js/Audio. audio-path))
    (reset! lyrics-loaded? false)
    (ajax/GET lyrics-path
              {:handler #(do
                           (reset! lyrics (-> (reader/read-string %)))
                                              ;; (preprocess-frames)))
                           (reset! lyrics-loaded? true))})))

(defn play-lyrics [frames]
  (let [frame-chan (chan 1000)]
    (doseq [frame (vec frames)
            :when (not= nil (:ticks frame))]
      #_(js/setTimeout
         (fn [& opt]))
      (go
        (<! (timeout (- (long (:offset frame)) 3000)))
        (println "after timeout" frame)
        (>! frame-chan frame))
       ;; (:offset frame))
      nil)
    (go-loop [fr (<! frame-chan)]
      (when-not (nil? fr)
        (reset! current-frame fr)
        (recur (<! frame-chan))))
    frame-chan))

(comment
  (ajax/GET
   "lyrics/Africa.edn"
   {:handler #(reset! lyrics (reader/read-string %))}))


   
(defn frame-text [frame]
  [:div.frame-text
   (for [e (vec (:events frame))]
     [:span {:key (str "frame" (:ticks frame) "_ticks" (:ticks e))}
      (:text e)])])



(defn lyrics-view [lyrics]
  [:ul
   (for [frame (vec lyrics)]
     [:li [frame-text frame]])])

(defn current-frame-display []
  (when @current-frame
    [:div.current-frame
     [frame-text @current-frame]]))
   


(defn app []
  [:div.app
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
    [:li (str "lyrics loaded? " @lyrics-loaded?)]]
   [:button {:on-click #(load-song @current-song)} "Load song"]
   [:button {:on-click #(do
                          (.play @audio)
                          (play-lyrics @lyrics))}
        "Play song"]
   [:button {:on-click #(.pause @audio)} "Stop"]
   [songs/song-table-component {:select-fn (fn [s] (reset! current-song s))}]])
             

(defn mount-components! []
  (reagent/render
   [app]
   (. js/document (getElementById "root"))))

(defn init! []
  (println "init!")
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
