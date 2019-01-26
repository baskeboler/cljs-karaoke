(ns cljs-karaoke.songs
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as str]
            [cljs-karaoke.subs :as s]
            [cljs-karaoke.events :as events]
            [re-frame.core :as rf :include-macros true]))

(def song-list
  ["14_Years"
   "Absolutely"
   "Aces_High"
   "Africa"
   "After_The_Love_Has_Gone"
   "Alanis-Crazy"
   "All That She Wants"
   "Always_Somewhere"
   "Androgyny"
   "Anytime_You_Need_A_Friend"
   "As_Long_As_You_Love_Me"
   "Baby_One_More_Time"
   "Band_On_The_Run"
   "Barbie girl"
   "Beat_Of_My_Heart"
   "Beautiful Life"
   "Beep"
   "Belive"
   "Bennie_And_The_Jets"
   "Bloody_Well_Right"
   "boogie_wonderland"
   "born_to_make_you_happy"
   "Born_To_Make_You_Happy"
   "Boys_Of_Summer"
   "Breakdown"
   "Breakfast_In_America"
   "Breaking_The_Habit"
   "Brothers in arms"
   "Brown_Girl_In_The_Ring"
   "Can^t_Seem_To_Make_You_Mine"
   "carry_my_urn_to_ukraine"
   "Cartoon_Heroes"
   "Cats_In_The_Cradle"
   "Chad-Hero"
   "Cherish"
   "Cherry_Lips"
   "Civil_War"
   "Closer_To_The_Heart"
   "Comfortably_Numb"
   "Complicated"
   "Cool"
   "Copacabana"
   "Crazy"
   "Crazy_For_You"
   "crazy_the_stop_remix"
   "crocodile_rock"
   "crucified"
   "Cryin"
   "Cup_Of_Coffee"
   "Daddy_Cool"
   "Dancing Queen"
   "Diary"
   "Diva_dana_int"
   "Doctor_Jones"
   "dont_ask_me_why"
   "dontbringmedown"
   "Dont_Let_Go"
   "Dont Turn Around"
   "Down_Under"
   "Dreadlock_Holiday"
   "Dream_On"
   "Drift_Away"
   "Drive_You_Home"
   "Enrique-Hero"
   "Estranged"
   "Eye_in_The_Sky"
   "fantasy"
   "final countdown"
   "genie_in_a_bottle"
   "Get_In_The_Ring"
   "girl"
   "give my life"
   "guess_thats_what_the_blues"
   "Hallo hallo"
   "Hammering_In_My_Head"
   "Happy Nation"
   "Heaven"
   "Heaven Is A Place On Earth"
   "Hello"
   "Hello_Hooray"
   "here_comes_the_rain_again"
   "Here_I_Go_Again"
   "Heroe"
   "Hey_Jude"
   "Hey_You"
   "Hold_On_To_The_Nights"
   "Hole_In_My_Soul"
   "Hotel California_excellent (lyrics)"
   "House_Of_The_Rising_Sun"
   "I_Don^t_Want_To_Miss_A_Thing"
   "I_Think_I^m_Paranoid"
   "its_my_life"
   "Jamming"
   "Janie^s_Got_A_Gun"
   "Knockin^_On_Heaven^s_Door"
   "Lady In Red"
   "La_Plage_De_Saint_Tropez"
   "LedyInRed1"
   "Le_Freak"
   "Life_Is_A_Flower"
   "lit de parade(XG-edit)"
   "Little_Boy"
   "Little_Deuce_Coupe"
   "Live_And_Let_Die"
   "Live_To_Tell"
   "Living In Danger"
   "Lollipop_(Candyman)"
   "Lonely_No_More"
   "Love_Of_A_Lifetime"
   "Lucky_Love"
   "Medication"
   "Milk"
   "Modanna-Holiday"
   "My_Heart_Will_Go_On"
   "My_Lover^s_Box"
   "My_Oh_My"
   "never_gonna_say_im_sorry"
   "Nobody_Loves_You"
   "Not_My_Idea"
   "Notorious"
   "November_Rain"
   "obsession"
   "onderweg"
   "Only_Happy_When_It_Rains"
   "oops_i_did_it_again"
   "Patience"
   "Pink"
   "Proud_Mary"
   "Push_It"
   "Queer"
   "quit_playing_games"
   "Roses_Are_Red"
   "Sacrifice"
   "Silence_Is_Golden"
   "Since_I_Don^t_Have_You"
   "Sleep_Together"
   "Smoke On The Water"
   "Special"
   "stand up for myself"
   "Stumblin`in"
   "Stupid_Girl"
   "Sweet Dreams"
   "Take_On_Me"
   "Temptation_Waits"
   "The_Lamb_Cuckoo_Cocoon"
   "The_Living_Daylights"
   "there_must_be_an_angel"
   "The Sign"
   "The_Trick_Is_To_Keep_Breathing"
   "The_World_Is_Not_Enough"
   "Time_APP"
   "Turn_Back_Time"
   "Untouchable"
   "Vow"
   "WalkingInMyShoes"
   "What_Goes_Up"
   "Wheel Of Fortune"
   "When_I_Grow_Up"
   "Wicked_Ways"
   "Wish_you_were_here"
   "Yesterdays"
   "You_Ain^t_The_First"
   "You_Could_Be_Mine"
   "You_Learn"
   "You_Look_So_Fine"
   "all_i_really_want"
      "Always Somewhere"
      "Anyone"
      "Beautiful_Stranger"
      "Beautiful_Things"
      "Bohemian_Rhapsody"
      "Brain_Damage"
      "bye_bye_bye"
      "Californication"
      "Call_of_The_Wild"
      "cinnamon_girl"
      "Cooper"
      "Crash_Boom_Bang"
      "Dangerous"
      "Dark_Side_Of_The_Moon-LP1"
      "Dark_Side_Of_The_Moon-LP2"
      "Dontspeak_no_doubt"
      "dust_in_the_wind"
      "easy"
      "Eclipse"
      "engel"
      "Fading_Like_A_Flower"
      "From_One_Heart_To_Another"
      "frozen"
      "Goodbye_To_You"
      "Half_A_Woman__Half_A_Shadow"
      "heal_the_world"
      "High_Hopes"
      "Higher_Ground"
      "holiday"
      "How_Do_You_Do"
      "I^ll_Remember"
      "I_breathe_vacuum"
      "I_Call_Your_Name"
      "I_Don^t_Want_To_Get_Hurt"
      "I_Just_Cant_Stop_Loving_You"
      "i_think_im_in_love_with_you"
      "i_wanna_be_with_you"
      "Its_a_sin_PSB"
      "its_in_your_eyes"
      "iturntoyou"
      "Joy_of_a_Toy"
      "Joyride"
      "JulyMorning"
      "June_Afternoon"
      "La_Isla_Bonita"
      "Learning_To_Fly"
      "Like_Lovers_Do"
      "Listen_To_Your_Heart"
      "Livin La Vida Loca"
      "mirror_mirror"
      "Money"
      "music"
      "My_Life_Has_Been_Saved"
      "Mysterious times sash"
      "Neverending_Love"
      "new_years_day"
      "Nothing Else Matters"
      "one"
      "Otherside"
      "Pea"
      "Pearls_of_Passion"
      "Ray_Of_Light"
      "Road_Trippin"
      "say_you_say_me"
      "Secrets_That_She_Keeps"
      "She_Doesn^t_Live_Here_Anymore"
      "sleeping_in_my_car"
      "Smooth_Operator"
      "Speak_to_Me+Breathe+On_The_Run"
      "Spending_My_Time"
      "Stars"
      "Still Loving You"
      "stuck_on_you"
      "Suck_My_Kiss"
      "Surrender"
      "take_the_long_way_home"
      "takeabow"
      "The_Big_L"
      "The_Great_Gig_in_the_Sky"
      "The_Look"
      "The_Wall_1d"
      "The_Wall_2d1"
      "The_Wall_2d2"
      "there_you_go"
      "This_Could_Be_Heaven"
      "thriller"
      "Time"
      "Too_Much_Love_Will_Kill_You"
      "Turn_To_Me"
      "uncle_john_from_jamaica"
      "Under_The_Bridge"
      "Us_And_Them"
      "Voices"
      "waterfall"
      "Wind Of Change"
      "Winter_Fall"
      "Wish_I_Could_Fly"
      "Wish_You_Were_Here-PF"
      "with_or_without_you"
      "Wonderful world"
      "wont_cry"
      "You_Dont_Fool_Me"
      "You`re in the army now"
      "You And I"
      "youllsee"])

(defn song-title [name]
  (-> name
      (str/replace #"_" " ")
      (str/replace #"-" " ")))

(def song-titles
  (map song-title song-list))

(def song-map
  (->> (map vector song-list song-titles)
       (into {})))

(defn song-table-pagination []
  (let [song-count (count song-list)
        current-page (rf/subscribe [::s/song-list-current-page])
        page-size (rf/subscribe [::s/song-list-page-size])
        filter-text (rf/subscribe [::s/song-list-filter])
        page-offset (rf/subscribe [::s/song-list-offset])
        next-fn #(rf/dispatch [::events/set-song-list-current-page (inc @current-page)])
        prev-fn #(rf/dispatch [::events/set-song-list-current-page (dec @current-page)])]
    (fn []
      [:nav.pagination {:role :navigation}
        [:a.pagination-previous {:on-click #(when (pos? @current-page) (prev-fn))
                                 :disabled (if-not (pos? @current-page) true false)}
              "Previous"]
       [:a.pagination-next {:on-click #(when (> (- song-count @page-offset)
                                                @page-size)
                                         (next-fn))
                            :disabled (if-not (> (- song-count @page-offset)
                                                 @page-size)
                                        true
                                        false)}
              "Next"]])))
(defn song-filter-component []
  (let [filt (rf/subscribe [::s/song-list-filter])]
    [:div.field>div.control.has-icon
     [:input.input.is-primary
      {:value @filt
       :on-change #(rf/dispatch [::events/set-song-filter
                                 (-> % .-target .-value)])}]
     [:span.icon
      [:i.fas.fa-search]]]))
(defn song-table-component
  [{:keys [select-fn]}]
  (let [song-count (count song-list)
        current-page (rf/subscribe [::s/song-list-current-page])
        page-size (rf/subscribe [::s/song-list-page-size])
        filter-text (rf/subscribe [::s/song-list-filter])
        page-offset (rf/subscribe [::s/song-list-offset])]
    [:div.card.song-table-component
     [:div.card-header]
     [:div.card-content
      [song-filter-component]
      [song-table-pagination]
      [:table.table.is-fullwidth.song-table
        [:thead
         [:tr
          [:th "Song"]
          [:th]]]
        [:tbody
         (for [name (->> (keys song-map)
                         (filter #(clojure.string/includes?
                                   (clojure.string/lower-case %)
                                   (clojure.string/lower-case @filter-text)))
                         (sort)
                         (vec)
                         (drop @page-offset)
                         (take @page-size)) ;(vec (sort (keys song-map)))
               :let [title (get song-map name)]]
           [:tr {:key name}
            [:td title]
            [:td [:a
                  {:href (str "#/songs/" name)}
                   ;; :on-click #(select-fn name)}
                  "Load song"]]])]]]]))

