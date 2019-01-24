(ns cljs-karaoke.events
  (:require [re-frame.core :as rf :include-macros true]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [ajax.core :as ajax]
            [cljs.reader :as reader]
            [cljs-karaoke.lyrics :refer [preprocess-frames]]))


(rf/reg-event-fx
 ::init-db
 (fn-traced [_ _]
   {:db {:current-frame nil
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
         :clock 0
         :custom-song-delay {}
         :song-list {:page-size 10
                     :current-page 0
                     :filter ""
                     :visible? true}
         :modals []}
    :dispatch-n [[::clock-event]
                 [::fetch-custom-delays]]}))
(rf/reg-event-db
 ::http-fetch-fail
 (fn-traced
  [db _]
  (println "fetch failed")
  db))

(rf/reg-event-fx
 ::clock-event
 (fn-traced
  [{:keys [db]} _]
  {:db (-> db
           (update :clock inc))
   :dispatch-later [{:ms 500 :dispatch [::clock-event]}]}))
            

(rf/reg-event-fx
 ::fetch-custom-delays
 (fn-traced
  [{:keys [db]} _]
  {:db db
   :http-xhrio {:method :get
                :uri "lyrics/delays.edn"
                :timeout 8000
                :response-format (ajax/text-response-format)
                :on-success [::handle-fetch-delays-success]
                :on-failure [::http-fetch-fail]}}))

(rf/reg-event-fx
 ::handle-fetch-delays-success
 (fn-traced
  [{:keys [db]} [_ delays-resp]]
  {:db (-> db
           (assoc :custom-song-delay (reader/read-string delays-resp)))
   :dispatch [::init-song-delays]}))

(defn reg-set-attr [evt-name attr-name]
  (rf/reg-event-db
   evt-name
   (fn-traced [db [_ obj]]
     (assoc db attr-name obj))))

(defn save-custom-delays-to-localstore [delays]
  (. js/localStorage (setItem "custom-song-delays" (js/JSON.stringify (clj->js delays)))))

(defn get-custom-delays-from-localstorage []
  (-> (. js/localStorage (getItem "custom-song-delays"))
      (js/JSON.parse)
      (js->clj)))

(rf/reg-event-db
 ::init-song-delays
 (fn-traced
  [db _]
  (let [delays (get-custom-delays-from-localstorage)]
    (if-not (nil? delays)
      (-> db
          (assoc :custom-song-delay (merge (if-not (nil? (:custom-song-delay db))
                                             (:custom-song-delay db)
                                             {})
                                           delays)))
      db))))

(reg-set-attr ::set-current-frame :current-frame)
(reg-set-attr ::set-audio :audio)
(reg-set-attr ::set-lyrics :lyrics)
(reg-set-attr ::set-lyrics-delay :lyrics-delay)
(reg-set-attr ::set-lyrics-loaded? :lyrics-loaded?)
(reg-set-attr ::set-display-lyrics? :display-lyrics?)

;; (reg-set-attr ::set-current-song :current-song)

(rf/reg-event-fx
 ::set-current-song
 (fn-traced
  [{:keys [db]} [_ song-name]]
  {:db (-> db
           (assoc :current-song song-name))
   :dispatch [::set-lyrics-delay (get-in db [:custom-song-delay song-name] (get db :lyrics-delay))]}))

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
                     (assoc :player-status status))}))

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

(rf/reg-event-db
 ::save-custom-song-delays-to-localstorage
 (fn-traced [db _]
            (save-custom-delays-to-localstore (:custom-song-delay db))
            db))

(rf/reg-event-fx
 ::set-custom-song-delay
 (fn-traced
  [{:keys [db]} [_ song-name delay]]
  {:db (-> db
           (assoc-in [:custom-song-delay song-name] delay))
   :dispatch [::save-custom-song-delays-to-localstorage]}))


(rf/reg-event-db
 ::modal-push
 (fn-traced
  [db [_ modal]]
  (-> db
      (update :modals conj modal))))

(rf/reg-event-db
 ::modal-pop
 (fn-traced
  [db _]
  (-> db
      (update :modals pop))))
