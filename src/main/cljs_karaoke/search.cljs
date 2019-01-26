(ns cljs-karaoke.search
  (:require [ajax.core :as ajax]
            [reagent.core :as reagent :refer [atom]]))

(def api-key "AIzaSyBIBQ2KPIDLzNqTMm76UcMJQ3qLTk6iYy0")
(def base-url "https://www.googleapis.com/customsearch/v1")
(def ctx-id "007074704954011898567:vq4nmfwmtgc")

(def search-resp (atom nil))
#_(ajax/GET (str base-url
               "?cx=" ctx-id
               "&key="  api-key
               "&q=listen to your heart")
          {:handler #(reset! search-resp %)
           :response-format (ajax.json/json-response-format {:keywords? true}) })
