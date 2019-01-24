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
            [cljs-karaoke.lyrics :as l :refer [preprocess-frames]]
            [stylefy.core :as stylefy])) 

(stylefy/init)

(def wallpapers
  ["wp1.jpeg"
   "Dolphin.jpg"
   "wp2.jpg"
   "wp3.jpeg"
   "wp4.png"])

(def parent-style
  {:transition "background-image 5s ease-out"
   :background-size "cover"
   :background-image (str "url(\"images/" (first wallpapers) "\")")})

(def bg-style (atom parent-style))

(go-loop [wps (cycle wallpapers)
          _ (<! (timeout (* 60 1000)))]
  (let [wp (first wps)
        wps-2 (rest wps)
        image-path (str "images/" wp)
        new-style {:background-image (str "url(\"" image-path "\")")}]
    (println "setting wp " wp)
    (reset! bg-style new-style)
    (recur wps-2 (<! (timeout (* 60 1000))))))

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
    (.play audio)
    (.pause audio)
    ;; (set! (.-volume audio) 0)
    (rf/dispatch [::events/set-audio audio])
    (rf/dispatch [::events/fetch-lyrics name preprocess-frames])
    (rf/dispatch [::events/toggle-song-list-visible])))

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

(defn song-progress []
  (let [dur (rf/subscribe [::s/song-duration])
        cur (rf/subscribe [::s/song-position])]
    (fn []
      [:progress.progress.is-small.is-primary.song-progress
        {:max (if (number? @dur) @dur 0)
         :value (if (number? @cur) @cur 0)}
       (str (if (pos? @dur)
               (long (* 100 (/ @cur @dur)))
               0) "%")])))

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
        song (rf/subscribe [::s/current-song])
        song-delay (rf/subscribe [::s/custom-song-delay @song])
        frames-tos (mapv #(return-after-timeout % (+ @song-delay (:offset %))) frames)]
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
        song (rf/subscribe [::s/current-song])
        delay (rf/subscribe [::s/custom-song-delay song])]
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


(defn delay-select []
  (let [delay (rf/subscribe [::s/lyrics-delay])]
    [:div.tile.is-child.is-12.field
     [:label "Text delay (ms)"]
     [:div.control
       [:div.select.delay-select
         [:select {:value @delay
                   :on-change #(rf/dispatch [::events/set-lyrics-delay (-> % .-target .-value (long))])}
          (for [v (vec (range -10000 10001 250))]
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
  [:div.tile.is-child.is-vertical
   (for [frame (vec lyrics)]
     [:div [frame-text frame]])])

(defn current-frame-display []
  (when @(rf/subscribe [::s/current-frame])
    [:div.current-frame
     [frame-text @(rf/subscribe [::s/current-frame])]]))
   
(defn play []
  (let [audio (rf/subscribe [::s/audio])
        lyrics (rf/subscribe [::s/lyrics])]
    (rf/dispatch-sync [::events/set-player-status
                       (play-lyrics-2 @lyrics)])
    (set! (.-currentTime @audio) 0)
    (.play @audio)))


(defn stop []
  (let [audio (rf/subscribe [::s/audio])
        highlight-status (rf/subscribe [::s/highlight-status])
        player-status (rf/subscribe [::s/player-status])]
    (.pause @audio)
    (set! (.-currentTime @audio) 0)
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
    [:button.button.is-small
     {:class (concat []
                   (if @visible?
                     ["is-selected"
                      "is-success"]
                     ["is-danger"]))
      :on-click #(rf/dispatch [::events/toggle-song-list-visible])}
     (if @visible?
       "Hide songs"
       "Show song list")]))
(defn save-custom-delay-btn []
  (let [selected (rf/subscribe [::s/current-song])
        delay (rf/subscribe [::s/lyrics-delay])]
    [:button.button.is-primary.is-small
     {:disabled (nil? @selected)
      :on-click #(when-not (nil? @selected)
                   (rf/dispatch [::events/set-custom-song-delay @selected @delay]))}
     "remember song delay"]))
(defn control-panel []
  (let [lyrics (rf/subscribe [::s/lyrics])
        display-lyrics? (rf/subscribe [::s/display-lyrics?])
        current-song (rf/subscribe [::s/current-song])
        lyrics-loaded? (rf/subscribe [::s/lyrics-loaded?])
        songs-visible? (rf/subscribe [::s/song-list-visible?])]
    
    [:div.control-panel.tile.is-ancestor
       {:class (if @(rf/subscribe [::s/song-paused?])
                 ["song-paused"]
                 ["song-playing"])}
     [:div.tile.is-vertical.is-parent (stylefy/use-style {:background-color "rgba(1,1,1, .3)"})
       [toggle-display-lyrics-link]
       [delay-select]]
     [:div.tile.is-parent.is-vertical
       [:p (str "current: " @current-song)]
       [:p (str " paused? " (if @(rf/subscribe [::s/song-paused?]) "yes" "no"))]
       (when (and
              @lyrics
              @display-lyrics?)
         [lyrics-view @lyrics])
       [:p
        (str "lyrics loaded? ")
        (if @lyrics-loaded?
          [:span.tag.is-success "loaded"]
          [:span.tag.is-danger "not loaded"])]
      [:div.tile.is-child
       [:div.buttons.is-small
        [:button.button.is-primary.is-small {:on-click #(load-song @current-song)}
         [:span.icon
          [:i.fas.fa-folder-open]]]
        [:button.button.is-info.is-small {:on-click play}
         [:span.icon
          [:i.fas.fa-play]]]
        [:button.button.is-warning.is-small.stop-btn {:on-click stop}
         [:span.icon
          [:i.fas.fa-stop]]]
        [save-custom-delay-btn]
        [toggle-song-list-btn]]]]
     (when @songs-visible?
       [:div.tile.is-vertical.is-child
        [songs/song-table-component {:select-fn
                                      (fn [s]
                                        (rf/dispatch-sync [::events/set-current-song s])
                                        (load-song @current-song))}]])]))

(defn app []
  [:div.container.app
   [:div.app-bg (stylefy/use-style (merge parent-style @bg-style))]
   [current-frame-display]
   [control-panel]
   [:button.button.is-danger.edge-stop-btn
    {:class (if @(rf/subscribe [::s/song-paused?])
              []
              ["song-playing"])
     :on-click stop}
    [:span.icon
     [:i.fas.fa-stop]]]
   (when-not @(rf/subscribe [::s/song-paused?])
     [:div.edge-progress-bar
      [song-progress]])])
     


(defn mount-components! []
  (reagent/render
   [app]
   (. js/document (getElementById "root"))))

(defn init! []
  (println "init!")
  (rf/dispatch-sync [::events/init-db])
  (mount-components!))

