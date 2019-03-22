(ns cljs-karaoke.events.songs
  (:require [re-frame.core :as rf]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [day8.re-frame.async-flow-fx]
            [cljs-karaoke.events :as events]
            [cljs-karaoke.audio :as aud]
            [cljs-karaoke.lyrics :refer [preprocess-frames]]
            [cljs.core.async :as async :refer [go go-loop <! >! chan]]))

                                        ; fetch song delay, fetch song background
(defn load-song-flow [song-name]
    {:first-dispatch [::load-song-start song-name]
     :rules [{:when :seen-all-of?
              :events [::events/handle-set-lyrics-success
                       ::events/generate-bg-css
                       ::setup-audio-complete
                       ::events/set-audio
                       ::events/set-audio-events]
              :dispatch-n [[::events/set-pageloader-active? false]]
              :halt? true}]})

(rf/reg-event-fx
 ::trigger-load-song-flow
 (fn-traced
  [{:keys [db]} [_ song-name]]
  {:db db
   :async-flow (load-song-flow song-name)}))

(rf/reg-event-fx
 ::load-song-start
 (fn-traced
  [{:keys [db]} [_ song-name]]
  {:db db
   :dispatch-n [[::events/set-pageloader-active? true]
                [::events/set-can-play? false]
                [::setup-audio-events song-name]
                [::events/set-current-song song-name]
                [::events/fetch-lyrics song-name preprocess-frames]
                [::events/set-current-view :playback]]})) 

(rf/reg-event-fx
 ::setup-audio-events
 (rf/after
  (fn [_ [_ song-name]]
    (. js/console (log "setup audio: " song-name))
    (let [audio-path (str "mp3/" song-name ".mp3")
          audio (js/Audio. audio-path)
          audio-events (aud/setup-audio-listeners audio)]
      (go-loop [e (<! audio-events)]
        (when-not (nil? e)
          (aud/process-audio-event e)
          (recur (<! audio-events))))
      (.play audio)
      (.pause audio)
      (rf/dispatch [::events/set-audio audio])
      (rf/dispatch [::events/set-audio-events audio-events]))))
 (fn-traced
  [{:keys [db]} [_ song-name]]
  {:db db
   :dispatch-later [{:ms 500
                     :dispatch [::setup-audio-complete]}]}))
     ;; :dispatch-n [[::events/set-audio-events audio-events]
                  ;; [::events/set-audio audio]
                  ;; [::audio-setup-complete]}))
(rf/reg-event-db
 ::setup-audio-complete
 (fn-traced
  [db _]
  (. js/console (log "setup audio complete!"))
  db))
