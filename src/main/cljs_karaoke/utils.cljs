(ns cljs-karaoke.utils
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf :include-macros true]
            [cljs-karaoke.events :as events]
            [cljs-karaoke.subs :as s]))

(defn modal-card-dialog [{:keys [title content footer]}]
  [:div.modal.is-active
   [:div.modal-background]
   [:div.modal-card
    [:header.modal-card-head
     [:p.modal-card-title title]
     [:button.delete
      {:aria-label "close"
       :on-click #(rf/dispatch [::events/modal-pop])}]]
    [:section.modal-card-body
     content]
    (when-not (nil? footer)
      [:footer.modal-card-foot
        footer])]])

(defn modals-component []
  [:div.modals
   (for [m @(rf/subscribe [::s/modals])]
     m)])

(defn show-export-sync-info-modal []
  (let [data @(rf/subscribe [::s/custom-song-delay-for-export])
        modal (modal-card-dialog
               {:title "Export sync data"
                :content [:div.export-sync-data-content
                          [:div.field>div.control
                           [:textarea.textarea.is-primary
                            data]]]
                :footer nil})]
    (rf/dispatch [::events/modal-push modal])))
