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



(comment
  (def audio (atom nil))
  (def lyrics (atom nil))
  (def lyrics-loaded? (atom false))
  (def display-lyrics? (atom false))
  (def current-frame (atom nil))
  (def current-song (atom (first songs/song-list)))
  (def player-status (atom nil)))

(defn toggle-display-lyrics []
  (rf/dispatch [::events/set-display-lyrics? (not @(rf/subscribe [::s/display-lyrics?]))]))

(defn toggle-display-lyrics-link []
  [:a {:href "#"
       :on-click toggle-display-lyrics}
   (if @(rf/subscribe [::s/display-lyrics?]) "hide lyrics" "show lyrics")])

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
;; (def lyrics-delay 0)

(defn return-after-timeout [obj delay]
  (let [ret-chan (chan)]
    (go
      (<! (timeout delay))
      (>! ret-chan obj))
    ret-chan))

(defn highlight-parts-2 [frame]
  (let [part-chan (chan)
        part-tos (->> (:events frame)
                      (map (fn [evt]
                             (return-after-timeout evt (:offset evt)))))]
    (go
      (doseq [_ (vec (range (count part-tos)))]
        (let [[v ch] (async/alts! part-tos)]
          (println "highlight-2" (:id v))
          (rf/dispatch-sync [::events/highlight-frame-part (:id frame) (:id v)]))))))
          
      


(defn highlight-parts [frame]
  (let [part-chan (chan)
        evts (:events frame)
        current-frame (rf/subscribe [::s/current-frame])]
    (doseq [v (vec evts)]
      (go
        (<! (timeout (long (:offset v))))
        (>! part-chan (:id v))))
    (go-loop [h-id (<! part-chan)
              status @(rf/subscribe [::s/highlight-status])]
      (when (and
             @current-frame
             status)
        (println "highlight " h-id)
        (rf/dispatch-sync
         [::events/highlight-frame-part (:id frame) h-id])
        (recur (<! part-chan) @(rf/subscribe [::s/highlight-status]))))
    (rf/dispatch [::events/set-highlight-status part-chan])))

(defn play-lyrics-2 [frames]
  (let [frame-chan (chan 1000)
        part-chan (chan 10)
        delay (rf/subscribe [::s/lyrics-delay])
        frames-tos (mapv #(return-after-timeout % (:offset %)) frames)]
    (go
      (doseq [_ (vec (range (count frames)))]
        (let [[v ch] (async/alts! (concat frames-tos [frame-chan]))]
          (case ch
            frame-chan (doseq [c (vec frames-tos)]
                         (async/close! c))
            (when-not (nil? v)
              (rf/dispatch-sync [::events/set-current-frame v])
              (highlight-parts-2 v))))))
    frame-chan))
            
      
(defn play-lyrics [frames]
  (let [frame-chan (chan 1000)
        ;; part-chan (chan 10)
        delay (rf/subscribe [::s/lyrics-delay])]
        ;; frames-tos (map #(return-after-timeout % (:offset %)) frames)]
    (doseq [frame (vec frames)
            :when (not= nil (:ticks frame))]
      (go
        (<! (timeout (+ (long (:offset frame)) @delay)))
        (println "after timeout" frame)
        (async/put! frame-chan frame)
        (highlight-parts-2 frame)))
    (go-loop [fr (<! frame-chan)]
      (when-not (nil? fr)
        (rf/dispatch-sync [::events/set-current-frame fr])
        (recur (<! frame-chan))))
    frame-chan))

;; (comment
  ;; (ajax/GET
   ;; "lyrics/Africa.edn"
   ;; {:handler #(rf/dispatch [::events/set-lyrics (reader/read-string %)])}))

(defn delay-select []
  (let [delay (rf/subscribe [::s/lyrics-delay])]
    [:div.field
     [:label "Text delay (ms)"]
     [:div.control
       [:div.select.delay-select
         [:select {:value @delay
                   :on-change #(rf/dispatch [::events/set-lyrics-delay (-> % .-target .-value (long))])}
          
          (for [v (vec (range -5000 5001 250))]
             [:option {:key (str "opt_" v)
                       :value v}
              v])]]]])) 
   
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
                       (play-lyrics-2 @lyrics)])
    (.play @audio)))

    ;; (rf/dispatch [::events/play @audio @lyrics (play-lyrics @lyrics)])))

(defn stop []
  (let [audio (rf/subscribe [::s/audio])
        highlight-status (rf/subscribe [::s/highlight-status])
        player-status (rf/subscribe [::s/player-status])]
    (.pause @audio)
    (.load @audio)
    (rf/dispatch-sync [::events/set-current-frame nil]) 
    (rf/dispatch-sync [::events/set-lyrics nil])
    (rf/dispatch-sync [::events/set-lyrics-loaded? false])
    (when-not (nil? @player-status)
      (async/close! @player-status))
    (when-not (nil? @highlight-status)
      (async/close! @highlight-status))
    (rf/dispatch-sync [::events/set-highlight-status nil])
    (rf/dispatch-sync [::events/set-player-status nil])))

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
     
     [current-frame-display]
     [toggle-display-lyrics-link]
     [delay-select]
     [:ul
      [:li (str "current: " @current-song)]
      (when (and
             @lyrics
             @display-lyrics?)
        [:li [lyrics-view @lyrics]])
      [:li [:span (str "lyrics loaded? ") (if @lyrics-loaded? [:span.tag.is-success "loaded"] [:span.tag.is-danger "not loaded"])]]]
     [:div.buttons
      [:button.button.is-primary {:on-click #(load-song @current-song)} "Load song"]
      [:button.button.is-info {:on-click play}
          "Play song"]
      [:button.button.is-warning {:on-click stop} "Stop"]
      [toggle-song-list-btn]]
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

