(ns cljs-karaoke.subs
  (:require [re-frame.core :as rf :include-macros true]))


(rf/reg-sub
 ::display-lyrics?
 (fn [db _]
   (:display-lyrics? db)))

(rf/reg-sub
 ::audio
 (fn [db _]
   (:audio db)))

(rf/reg-sub
 ::lyrics
 (fn [db _]
   (:lyrics db)))

(rf/reg-sub
 ::lyrics-loaded?
 (fn [db _]
   (:lyrics-loaded? db)))

(rf/reg-sub
 ::current-frame
 (fn [db _]
   (:current-frame db)))

(rf/reg-sub
 ::current-song
 (fn [db _]
   (:current-song db)))

(rf/reg-sub
 ::player-status
 (fn [db _]
   (:player-status db)))

(rf/reg-sub
 ::highlight-status
 (fn [db _]
   (:highlight-status db)))

(rf/reg-sub
 ::lyrics-delay
 (fn [db _]
   (:lyrics-delay db)))


(rf/reg-sub
 ::song-list
 (fn [db _]
   (:song-list db)))

(rf/reg-sub
 ::song-list-page-size
 :<- [::song-list]
 (fn [song-list _]
   (:page-size song-list)))

(rf/reg-sub
 ::song-list-current-page
 :<- [::song-list]
 (fn [song-list _]
   (:current-page song-list)))

(rf/reg-sub
 ::song-list-filter
 :<- [::song-list]
 (fn [song-list _]
   (:filter song-list)))

(rf/reg-sub
 ::song-list-offset
 :<- [::song-list-current-page]
 :<- [::song-list-page-size]
 (fn [[page size] _]
   (* page size)))

(rf/reg-sub
 ::song-list-visible?
 :<- [::song-list]
 (fn [song-list _]
   (:visible? song-list)))

(rf/reg-sub
 ::clock
 (fn [db _]
   (:clock db)))

(rf/reg-sub
 ::clocked-audio
 :<- [::audio]
 :<- [::clock]
 (fn [[audio clock] _]
   (if (pos? clock)
     audio
     nil)))

(rf/reg-sub
 ::song-duration
 :<- [::clocked-audio]
 :<- [::clock]
 (fn [[audio clock] _]
   (if (pos? clock)
     (.-duration audio)
     0)))

(rf/reg-sub
 ::song-position
 :<- [::clocked-audio]
 :<- [::clock]
 (fn [[audio clock] _]
   (if (pos? clock)
     (.-currentTime audio)
     0)))

(rf/reg-sub
 ::song-paused?
 :<- [::clocked-audio]
 :<- [::clock]
 (fn [[audio clock] _]
   (if (and
        (pos? clock)
        (not (nil? audio)))
     (.-paused audio)
     true)))

(rf/reg-sub
 ::custom-song-delay
 (fn [db [_ song-name]]
   (get-in db [:custom-song-delay song-name] (:lyrics-delay db))))
