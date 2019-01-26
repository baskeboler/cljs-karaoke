(ns cljs-karaoke.events
  (:require [re-frame.core :as rf :include-macros true]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [ajax.core :as ajax]
            [cljs.reader :as reader]
            [clojure.string :refer [replace]]
            [cljs-karaoke.lyrics :refer [preprocess-frames]]
            [cljs-karaoke.search :as search]))
           

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
                     :filter-verified? false
                     :visible? true}
         :modals []}
    :dispatch-n [[::clock-event]
                 [::fetch-custom-delays]]}))
(rf/reg-event-fx
 ::http-fetch-fail
 (fn-traced
  [db [_ err dispatch-n-vec]]
  (println "fetch failed" err)
  {:db db
   :dispatch-n dispatch-n-vec}))

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
                :on-failure [::http-fetch-fail [[::init-song-delays]]]}}))

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

(rf/reg-event-db
 ::toggle-display-lyrics
 (fn-traced
  [db _]
  (-> db
      (update :display-lyrics? not))))

;; (reg-set-attr ::set-current-song :current-song)

(rf/reg-event-fx
 ::set-current-song
 (fn-traced
  [{:keys [db]} [_ song-name]]
  {:db (-> db
           (assoc :current-song song-name))
   :dispatch-n [[::fetch-bg (replace song-name #"-|_" " ")] 
                [::set-lyrics-delay (get-in db [:custom-song-delay song-name] (get db :lyrics-delay))]]}))

(reg-set-attr ::set-player-status :player-status)
(reg-set-attr ::set-highlight-status :highlight-status)
;; (reg-set-attr ::set-song-filter :song-filter)

(rf/reg-event-fx
 ::set-song-filter
 (fn-traced
  [{:keys [db]} [_ filter-text]]
  {:db (-> db
           (assoc-in [:song-list :filter] filter-text))
   :dispatch [::set-song-list-current-page 0]})) 

(rf/reg-event-db
 ::set-song-list-current-page
 (fn-traced [db [_ page]]
            (-> db
                (assoc-in [:song-list :current-page] page))))

(rf/reg-event-db
 ::toggle-song-list-visible
 (fn-traced
  [db _]
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
 (fn-traced
  [db [_ lyrics]]
  (let [l (-> lyrics
              (reader/read-string)
              (preprocess-frames))]
    
     (-> db
         (assoc :lyrics l)
         (assoc :lyrics-fetching? false)
         (assoc :lyrics-loaded? true)))))

(rf/reg-event-fx
 ::play
 (rf/after (fn [{:keys [db]} [_ audio lyrics status]]
              (.play audio)))
 (fn-traced
  [{:keys [db]} [_ audio lyrics status]]
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
 ::toggle-filter-verified-songs
 (fn-traced
  [db _]
  (-> db
      (update-in [:song-list :filter-verified?] not))))

(rf/reg-event-fx
 ::modal-push
 (fn-traced
  [{:keys [db]} [_ modal]]
  {:db (-> db
           (update :modals conj modal))
   :dispatch [::modal-activate]}))


(rf/reg-event-db
 ::modal-activate
 (fn-traced
  [db _] db))
(rf/reg-event-db
 ::modal-pop
 (fn-traced
  [db _]
  (-> db
      (update :modals pop))))

(rf/reg-event-fx
 ::search-images
 (fn-traced
  [{:keys [db]} [_ q callback-event]]
  {:db db
   :http-xhrio {:method :get
                :timeout 8000
                :uri (str search/base-url
                          "?cx="  search/ctx-id
                          "&key=" search/api-key
                          "&q=" q)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success callback-event
                :on-failure [::print-arg]}}))          

(rf/reg-event-fx
 ::print-arg
 (fn-traced
  [{:keys [db]} [_ & opts]]
  (cljs.pprint/pprint opts)
  {:db db}))

(def fetch-bg-from-web-enabled? true)

(rf/reg-event-fx
 ::fetch-bg
 (fn-traced
  [{:keys [db]} [_ title]]
  (merge
   {:db db}
   (if fetch-bg-from-web-enabled?
     {:dispatch [::search-images title [::handle-fetch-bg]]}
     {}))))

(rf/reg-event-fx
 ::handle-fetch-bg
 (fn-traced
  [{:keys [db]} [_ res]]
  (let [candidate-image (search/extract-candidate-image res)]
    {:db (if-not (nil? candidate-image)
            (-> db
                (assoc :bg-image (:url candidate-image)))
            db)
     :dispatch [::generate-bg-css (:url candidate-image)]})))
             
              
(rf/reg-event-db
 ::generate-bg-css
 (fn-traced
  [db [_ url]]
  (-> db
      (assoc :bg-style {:background-image (str "url(\"" url "\")")
                        :background-size "cover"
                        :transition "background-image 5s ease-out"}))))

