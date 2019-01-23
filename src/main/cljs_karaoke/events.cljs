(ns cljs-karaoke.events
  (:require [re-frame.core :as rf :include-macros true]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [ajax.core :as ajax]
            [cljs.reader :as reader]
            [cljs-karaoke.lyrics :refer [preprocess-frames]]))


(rf/reg-event-db
 ::init-db
 (fn-traced [_ _]
   {:current-frame nil
    :lyrics nil
    :lyrics-loaded? false
    :lyrics-fetching? false
    :lyrics-delay 1000
    :audio nil
    :display-lyrics? false
    :current-song nil
    :player-status nil
    :highlight-status nil
    :playing? false
    :song-list {:page-size 10
                :current-page 0
                :filter ""
                :visible? true}}))

(defn reg-set-attr [evt-name attr-name]
  (rf/reg-event-db
   evt-name
   (fn-traced [db [_ obj]]
     (assoc db attr-name obj))))

(reg-set-attr ::set-current-frame :current-frame)
(reg-set-attr ::set-audio :audio)
(reg-set-attr ::set-lyrics :lyrics)
(reg-set-attr ::set-lyrics-delay :lyrics-delay)
(reg-set-attr ::set-lyrics-loaded? :lyrics-loaded?)
(reg-set-attr ::set-display-lyrics? :display-lyrics?)

(reg-set-attr ::set-current-song :current-song)

(reg-set-attr ::set-player-status :player-status)
(reg-set-attr ::set-highlight-status :highlight-status)
;; (reg-set-attr ::set-song-filter :song-filter)
(rf/reg-event-db
 ::set-song-filter
 (fn-traced
  [db [_ filter-text]]
  (-> db
      (assoc-in [:song-list :filter] filter-text)))) 
(rf/reg-event-db
 ::set-song-list-current-page
 (fn-traced [db [_ page]]
            (-> db
                (assoc-in [:song-list :current-page] page))))

(rf/reg-event-db
 ::toggle-song-list-visible
 (fn-traced [db _]
            (-> db
                (update-in [:song-list :visible?] not))))

(rf/reg-event-fx
 ::fetch-lyrics
 (fn-traced [{:keys [db]} [_ name process]]
   {:db (-> db
            (assoc :lyrics-loaded? false)
            (assoc :lyrics-fetching? true))
    :http-xhrio {:method :get
                 :uri (str "lyrics/" name ".edn")
                 :timeout 8000
                 :response-format (ajax/text-response-format)
                 :on-success [::handle-set-lyrics-success]}}))

(rf/reg-event-db
 ::handle-set-lyrics-success
 (fn-traced [db [_ lyrics]]
            (-> db
                (assoc :lyrics (-> (reader/read-string lyrics)
                                   (preprocess-frames)))
                (assoc :lyrics-fetching? false)
                (assoc :lyrics-loaded? true))))

(rf/reg-event-fx
 ::play
 (rf/after (fn [{:keys [db]} [_ audio lyrics status]]
              (.play audio)))
 (fn-traced [{:keys [db]} [_ audio lyrics status]]
            {:dispatch-n [[::set-lyrics lyrics]
                          [::set-audio audio]
                          [::set-player-status status]]
             :db (-> db
                     (assoc :playing? true)
                     (assoc :player-status statys))}))

(defn highlight-if-same-id [id]
  (fn [evt]
    (if (= id (:id evt))
      (assoc evt :highlighted? true)
      evt)))

(rf/reg-event-db
 ::highlight-frame-part
 (fn-traced [db [_ frame-id part-id]]
            (if (and  (get db :current-frame)
                      (= frame-id (:id (get db :current-frame))))
              (-> db
                (update-in [:current-frame :events]
                           (fn [evts]
                             (mapv (highlight-if-same-id part-id) evts))))
              db)))
