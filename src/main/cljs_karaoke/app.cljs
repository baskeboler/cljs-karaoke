(ns cljs-karaoke.app
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as rf :include-macros true]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [cljs-karaoke.events :as events]
            [cljs-karaoke.subs :as s]
            [cljs-karaoke.utils :as utils :refer [show-export-sync-info-modal]]
            [cljs-karaoke.songs :as songs :refer [song-table-component]]
            [cljs-karaoke.lyrics :as l :refer [preprocess-frames]]
            [cljs.reader :as reader]
            [cljs.core.async :as async :refer [go go-loop chan <! >! timeout alts!]]
            [stylefy.core :as stylefy]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.events :as gevents]
            [goog.history.EventType :as EventType]
            [keybind.core :as key])
  (:import goog.History))
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

(def bg-style (rf/subscribe [::s/bg-style]))

(comment
  (go-loop [wps (cycle wallpapers)]
    _ (<! (timeout (* 60 1000)))
    (let [wp (first wps)
          wps-2 (rest wps)
          image-path (str "images/" wp)
          new-style {:background-image (str "url(\"" image-path "\")")}]
      (println "setting wp " wp)
     ;; (reset! bg-style new-style)
      (rf/dispatch [::events/generate-bg-css wp])
      (recur wps-2 (<! (timeout (* 60 1000)))))))

(defn toggle-display-lyrics []
  (rf/dispatch [::events/toggle-display-lyrics]))

(defn toggle-display-lyrics-link []
  [:div.field>div.control
   [:a.button.is-info
    {:href "#"
     :on-click toggle-display-lyrics}
    (if @(rf/subscribe [::s/display-lyrics?])
      "hide lyrics"
      "show lyrics")]])

(defn load-song
  ([name]
   (rf/dispatch [::events/set-can-play? false])
   (let [audio-path (str "mp3/" name ".mp3")
         lyrics-path (str "lyrics/" name ".edn")
         audio (js/Audio. audio-path)]
     (.. audio (addEventListener
                "canplaythrough"
                (fn []
                  (println "media is ready!")
                  (rf/dispatch [::events/set-can-play? true]))))
     (.play audio)
     (.pause audio)
     ;; (set! (.-volume audio) 0)
     (rf/dispatch [::events/set-current-view :playback])
     (rf/dispatch-sync [::events/set-current-song name])
     (rf/dispatch-sync [::events/set-audio audio])
     (rf/dispatch-sync [::events/fetch-lyrics name preprocess-frames])
     (rf/dispatch-sync [::events/toggle-song-list-visible])))
  ([]
   (let [song (rand-nth songs/song-list)]
     (load-song song))))

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
    [:div.field
     ;; [:label "Text delay (ms)"]
     [:div.control
      [:div.select.is-primary.is-fullwidth.delay-select
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
        current (rf/subscribe [::s/current-song])
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
    (rf/dispatch-sync [::events/set-player-status nil])
    ;; reload the song so we can play it again
    (load-song @current)))

(defn toggle-song-list-btn []
  (let [visible? (rf/subscribe [::s/song-list-visible?])]
    [:button.button.is-fullwidth
     {:class (concat []
                     (if @visible?
                       ["is-selected"
                        "is-success"]
                       ["is-danger"]))
      :on-click #(rf/dispatch [::events/toggle-song-list-visible])}
     [:span.icon
      (if @visible?
        [:i.fas.fa-eye-slash];"Hide songs"
        [:i.fas.fa-eye])]])) ;"Show song list")]]))
(defn save-custom-delay-btn []
  (let [selected (rf/subscribe [::s/current-song])
        delay (rf/subscribe [::s/lyrics-delay])]
    [:button.button.is-primary
     {:disabled (nil? @selected)
      :on-click #(when-not (nil? @selected)
                   (rf/dispatch [::events/set-custom-song-delay @selected @delay]))}
     "remember song delay"]))

(defn export-sync-data-btn []
  [:button.button.is-info
   {:on-click (fn [_]
                (utils/show-export-sync-info-modal))}
   [:span.icon
    [:i.fas.fa-file-export]]])
    ;; "export sync data"]])

(defn info-table []
  (let [current-song (rf/subscribe [::s/current-song])
        lyrics-loaded? (rf/subscribe [::s/lyrics-loaded?])]
    [:table.table.is-fullwidth
     [:tbody
      [:tr
       [:td "current"] [:td
                        (if-not (nil? @current-song)
                          [:span.tag.is-info.is-normal
                           @current-song]
                          [:span.tag.is-danger.is-normal
                           "no song selected"])]]
      [:tr
       [:td "is paused?"]
       [:td (if @(rf/subscribe [::s/song-paused?]) "yes" "no")]]
      [:tr
       [:td "lyrics loaded?"]
       [:td (if @lyrics-loaded?
              [:span.tag.is-success "loaded"]
              [:span.tag.is-danger "not loaded"])]]]]))

(defn control-panel []
  (let [lyrics (rf/subscribe [::s/lyrics])
        display-lyrics? (rf/subscribe [::s/display-lyrics?])
        current-song (rf/subscribe [::s/current-song])
        lyrics-loaded? (rf/subscribe [::s/lyrics-loaded?])
        song-list-visible? (rf/subscribe [::s/song-list-visible?])
        can-play? (rf/subscribe [::s/can-play?])]
    [:div.control-panel.columns
     {:class (if @(rf/subscribe [::s/song-paused?])
               ["song-paused"]
               ["song-playing"])}
     [:div.column (stylefy/use-style {:background-color "rgba(1,1,1, .3)"})
      [toggle-display-lyrics-link]
      [delay-select]
      [info-table]
      [:div.columns>div.column.is-12
       [:div.field.has-addons
        [:div.control
         [:button.button.is-primary {:on-click #(load-song @current-song)}
          [:span.icon
           [:i.fas.fa-folder-open]]]]
        [:div.control
         [:button.button.is-info
          (if @can-play?
           {:on-click play}
           {:disabled true})
          [:span.icon
           [:i.fas.fa-play]]]]
        [:div.control
         [:button.button.is-warning.stop-btn
          {:on-click stop}
          [:span.icon
           [:i.fas.fa-stop]]]]
        [:div.control
         [export-sync-data-btn]]
        [:div.control
         [toggle-song-list-btn]]]
       [:div.field
        [:div.control
         [save-custom-delay-btn]]]]]
     (when @display-lyrics?
       [:div.column (stylefy/use-style {:background-color "rgba(1,1,1, .3)"})
        [lyrics-view @lyrics]])
     (when @song-list-visible?
       [:div.column
        [song-table-component]])]))

(def centered {:position :fixed
               :display :block
               :top "50%"
               :left "50%"
               :transform "translate(-50%, -50%)"})
(def top-left {:position :fixed
               :display :block
               :top 0
               :left 0
               :margin "2em 2em"})

(defn playback-view []
  [:div.container.app
   [utils/modals-component]
   [:div.app-bg (stylefy/use-style (merge parent-style @bg-style))]
   [current-frame-display]
   ;; [control-panel]
   (when (and
          @(rf/subscribe [::s/song-paused?])
          @(rf/subscribe [::s/can-play?]))
     [:div
      [:a
       (stylefy/use-style
        top-left
        {:on-click #(rf/dispatch [::events/set-current-view :home])})
       [:span.icon
        [:i.fas.fa-cog.fa-3x]]]
      [:a
        (stylefy/use-style
         centered
         {:on-click play})
        [:span.icon
         [:i.fas.fa-play.fa-5x]]]])
   (when-not @(rf/subscribe [::s/can-play?])
     [:a
      (stylefy/use-style
       centered
       {:on-click
        #(if-let [song @(rf/subscribe [::s/current-song])]
           (load-song song)
           (load-song))})
      [:span.icon
       [:i.fas.fa-sync.fa-5x]]])
      
      
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

(defn default-view []
  [:div.container.app
   [utils/modals-component]
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

(defn app []
  (condp = @(rf/subscribe [::s/current-view])
    :home [default-view]
    :playback [playback-view]))

(defn init-routing! []
  (secretary/set-config! :prefix "#")
  (defroute "/" []
    (println "home path")
    (load-song))
  (defroute "/songs/:song"
    [song query-params]
    (println "song: " song)
    (println "query params: " query-params)
    (load-song song)
    (when-some [offset (:offset query-params)]
      (rf/dispatch [::events/set-lyrics-delay (long offset)])
      (rf/dispatch [::events/set-custom-song-delay song (long offset)])))
  ;; Quick and dirty history configuration.
  (let [h (History.)]
    (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
    (doto h (.setEnabled true))))

(defn init-keybindings! []
  (key/bind! "ctrl-space"
             ::ctrl-space-kb
             (fn []
               (println "ctrl-s pressed!")
               false))
  (key/bind! "esc"
             ::esc-kb
             (fn []
               (println "esc pressed!")
               (when-not (nil? @(rf/subscribe [::s/player-status]))
                 (stop))))
  (key/bind! "l r" ::l-r-kb #(load-song)))

(defn mount-components! []
  (reagent/render
   [app]
   (. js/document (getElementById "root"))))

(defn init! []
  (println "init!")
  (rf/dispatch-sync [::events/init-db])
  (rf/dispatch-sync [::events/init-song-delays])
  (rf/dispatch-sync [::events/init-song-bg-cache])
  (mount-components!)
  (init-routing!)
  (init-keybindings!))

