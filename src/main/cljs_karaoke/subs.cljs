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
