(ns cljs-karaoke.playlists)

(defprotocol ^:export Playlist
  (add-song [this song])
  (next-song [this])
  (clear [this])
  (empty? [this])
  (current [this])
  (has-next? [this]))

(defrecord ^:export KaraokePlaylist [id created current songs]
  Playlist
  (add-song [this song] (-> this (update :songs conj song)))
  (next-song [this] (-> this
                        (update :current inc)))
  (clear [this] (-> this
                    (assoc :current 0)
                    (assoc :songs [])))
  (empty? [this] (empty? (:songs this)))
  (current [this] (if (< (:current this) (count (:songs this)))
                    (nth
                     (:songs this)
                     (:current this))
                    nil))
  (has-next? [this] (< (inc (:current this)) (count songs))))
  

(defn build-playlist
  ([id created current songs] (->KaraokePlaylist id created current (vec songs)))
  ([created current songs] (build-playlist (str (random-uuid)) created current songs))
  ([current songs] (build-playlist (js/Date.) current songs))
  ([songs] (build-playlist 0 songs))
  ([] (build-playlist [])))
