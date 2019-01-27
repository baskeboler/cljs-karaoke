(ns cljs-karaoke.lyrics
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [com.rpl.specter :as s :include-macros true]))
            ;; [cljs.core :as core :refer [random-uuid]]))

(def *print-length* nil)

(defn set-event-id [event]
  (if-not (:id event)
    (assoc event :id (random-uuid))
    event))

(defn to-relative-offset-events [base-offset]
  (fn [event]
    (s/transform [:offset] #(- % base-offset) event)))
    ;; (-> event (update :offset - base-offset))))

(defn to-relative-offset-events-with-id [base-offset]
  (comp set-event-id (to-relative-offset-events base-offset)))

(defn update-events-to-relative-offset [base-offset]
  (fn [events]
    (s/transform [s/ALL] (to-relative-offset-events base-offset) events)))
    ;; (mapv (to-relative-offset-events base-offset) events)))

(defn update-events-to-relative-offset-with-id [base-offset]
  (fn [events]
    (s/transform [s/ALL] (to-relative-offset-events-with-id base-offset) events)))
    ;; (mapv (to-relative-offset-events-with-id base-offset) events)))

(defn to-relative-frame-offsets [frames]
  (reduce
   (fn [res fr]
     (let [last-frame (last res)
           new-offset (if-not (nil? last-frame)
                        (- (:offset fr) (:offset last-frame))
                        (:offset fr))
           new-frame (->> fr
                          (s/setval [:relative-offset] new-offset)
                          (s/setval [:id] (random-uuid)))
           #_(-> fr
                 (assoc :relative-offset new-offset)
                 (set-event-id))]
       (conj (vec res) new-frame)))
   []
   (vec frames)))

(defn- event-text [evt]
  (str/trim (:text evt)))

(defn- partition-fn [evt]
  (or
   (str/starts-with? (event-text evt) "/")
   (str/starts-with? (event-text evt) "\\")
   (not (or
         (str/ends-with? (event-text evt) ".")
         (str/ends-with? (event-text evt) "?")
         (str/ends-with? (event-text evt) "!")))))

(defn- partition-events [events]
  (loop [res []
         events-1 events]
    (let [[new-grp rst] (split-with partition-fn events-1)
          new-grp (concat (vec new-grp) (take-while (comp not partition-fn) rst))
          new-rst (drop-while (comp not partition-fn) rst)]
      (if  (and (empty? new-grp)
                (empty? new-rst))
        res
        (recur (conj res new-grp) new-rst)))))


(def frame-text-limit 64)
(defn frame-text-string [frame]
  (let [events (:events frame)]
    (->> events
         (map :text)
         (apply str))))


(defn build-frame [grp]
  {:type :frame-event
   :id (random-uuid)
   :events (map set-event-id (vec grp))
   :ticks (reduce min js/Number.MAX_VALUE (map :ticks (vec grp)))
   :offset (reduce min js/Number.MAX_VALUE (map :offset (vec grp)))})

(defn split-frame [frame]
  (let [grps (partition-events (:events frame))
        frames (map build-frame grps)]
    frames))

(defn needs-split? [frame]
  (> (count (frame-text-string frame)) frame-text-limit))

(defn split-frames-if-necessary [frames]
  (let [frame-grps (mapv (fn [fr]
                          (if (needs-split? fr)
                            (split-frame fr)
                            [fr]))
                        frames)]
    (apply concat frame-grps)))

(defn random-uuid [] (cljs.core/random-uuid))
(defn set-ids [frames]
  #_(s/transform [s/ALL]
               (fn [fr]
                 (s/setval [:id] (rand-uuid) fr)))
  (s/transform [s/ALL :events s/ALL]
               (fn [evt]
                 (assoc evt :id (rand-uuid)))
               frames))

(defn preprocess-frames [frames]
  (let [no-dupes (map (fn [fr]
                        (let [events (->> (into #{} (:events fr))
                                          vec
                                          (sort-by :offset))]
                          (-> fr
                              (assoc :events events))))
                      frames)
        frames-2 (split-frames-if-necessary (vec no-dupes))
        with-offset
        #_(s/transform [s/ALL]
                     #(s/setval [:offset] (reduce min js/Number.MAX_VAL
                                                  (s/select [s/ALL :events s/ALL :offset] %))
                                %)
                     frames-2)
        (mapv (fn [fr]
                  (-> fr
                      (assoc :offset
                              (reduce min 1000000
                                      (map :offset (:events fr))))))
                   ;; (- 10000)
              frames-2)
        with-relative-events (mapv
                              #(-> %
                                   (update :events
                                           (update-events-to-relative-offset-with-id (:offset %)))) with-offset)]
    ;; frames-2
    (-> with-relative-events
        (to-relative-frame-offsets))))
