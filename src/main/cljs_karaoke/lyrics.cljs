(ns cljs-karaoke.lyrics
  (:require [re-frame.core :as rf]))



(defn set-event-id [event]
  (if-not (:id event)
    (assoc event :id (random-uuid))
    event))

(defn to-relative-offset-events [base-offset]
  (fn [event]
    (-> event (update :offset - base-offset))))

(defn to-relative-offset-events-with-id [base-offset]
  (comp set-event-id (to-relative-offset-events base-offset)))

(defn update-events-to-relative-offset [base-offset]
  (fn [events]
    (mapv (to-relative-offset-events base-offset) events)))

(defn update-events-to-relative-offset-with-id [base-offset]
  (fn [events]
    (mapv (to-relative-offset-events-with-id base-offset) events)))

(defn to-relative-frame-offsets [frames]
  (reduce (fn [res fr]
            (let [last-frame (last res)
                  new-offset (if-not (nil? last-frame)
                               (- (:offset fr) (:offset last-frame))
                               (:offset fr))
                  new-frame (-> fr
                                (assoc :relative-offset new-offset)
                                (set-event-id))]
              (conj res new-frame)))
          []
          frames))
(defn preprocess-frames [frames]
  (let [with-offset (mapv #(-> % (assoc :offset (-> (map :offset (:events %))
                                                    (sort)
                                                    (first))))
                                                    ;; (- 10000))))
                           frames)
        with-relative-events (mapv
                              #(-> %
                                   (update :events
                                           (update-events-to-relative-offset-with-id (:offset %)))) with-offset)]
    (-> with-relative-events
        to-relative-frame-offsets)))
