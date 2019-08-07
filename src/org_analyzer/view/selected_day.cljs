(ns org-analyzer.view.selected-day
  (:require [org-analyzer.view.util :as util]
            [org-analyzer.view.day-by-minute-view :as day-by-minute-view]
            [org-analyzer.view.dom :as dom]
            [cljs.pprint :refer [cl-format]]
            [reagent.core :as rg]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [clojure.set :refer [intersection]]
            [clojure.string :as s]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn selected-day-view [day clocks-by-day]
  (if-not day
    nil
    (let [date (:date day)
          clocks (get clocks-by-day date)
          location-durations (reverse
                              (sort-by :duration
                                       (map (fn [[id clocks]] {:id id :clocks clocks :duration (util/sum-clocks-mins clocks)})
                                            (group-by :id clocks))))]
      [:div.day-detail
       [:div.date (str date " " (:dow-name day))]
       [:div.hours (str "Clocked time: " (util/print-duration-mins (apply + (map :duration location-durations))))]
       [:div (cl-format nil "~d activit~:*~[ies~;y~:;ies~]" (count location-durations))]
       [:div.clock-list
        (apply concat (for [{:keys [id duration] [{:keys [name tags path] :as clock}] :clocks} location-durations]
                        [^{:key (str id "-duration")} [:span.duration (util/print-duration-mins duration)]
                         ^{:key (str id "-path")} [:span.path (cl-format nil "[~{~a~^ > ~}]" (map s/trim path))]
                         ^{:key (str id "-name")} [:span.name (util/parse-all-org-links name)]
                         ^{:key (str id "-tags")} [:span.tags.md-chips
                                                   (for [tag tags]
                                                     ^{:key (str id "-" tag)}
                                                     [:span.tag.md-chip.md-chip-raised tag])]]))]])))

(defn analyze-clocks [days clocks-by-day]
  (letfn [(clocks-avg [clocks-by-sth]
            (util/print-duration-mins
             (quot (->> clocks-by-sth
                        (map (comp util/sum-clocks-mins second))
                        (apply +))
                   (count clocks-by-sth))))]
    (let [weeks (group-by #(str (:year %) "-" (:week %)) days)
          clocks-by-week (into {} (for [[week-id days] weeks
                                        :let [clocks (apply concat (map
                                                                    #(get clocks-by-day (:date %))
                                                                    days))]]
                                    [week-id clocks]))]
      {:average-day-duration (clocks-avg clocks-by-day)
       :average-week-duration (clocks-avg clocks-by-week)
       :n-weeks (count weeks)})))

(defn selected-days-view
  [days
   clocks-by-day
   clock-minute-intervals-by-day
   calendar
   scrolled-window-bounds]

  (rg/with-let [highlighted-clocks (ratom #{})
                by-minute-el (atom nil)
                by-minute-width (ratom 100)
                by-minute-height (ratom 100)
                clock-list-el (atom nil)]

    (let [dates (map :date days)
          clocks-by-day (into (sorted-map-by <) (select-keys clocks-by-day dates))
          clock-minute-intervals-by-day (into (sorted-map-by <) (select-keys clock-minute-intervals-by-day dates))
          clocks (apply concat (vals clocks-by-day))
          location-durations (reverse
                              (sort-by :duration
                                       (map (fn [[id clocks]] {:id id :clocks clocks :duration (util/sum-clocks-mins clocks)})
                                            (group-by :id clocks))))
          duration (util/sum-clocks-mins clocks)
          days (vals (select-keys calendar dates))
          {:keys [average-day-duration average-week-duration n-weeks]} (analyze-clocks days clocks-by-day)

          [_ scroll-y _ _] @scrolled-window-bounds
          clock-list-scroll-y (if-let [[_ scroll-y _ _]
                                       (and @clock-list-el
                                            (dom/screen-relative-bounds @clock-list-el))]
                                scroll-y
                                0)
          by-minute-manual-scroll? (< clock-list-scroll-y 0)]

      [:div.day-detail
       [:div.date
        (cl-format nil "~d days over ~d week~:*~P selected" (count dates) n-weeks)]
       [:div.hours (str "Clocked time: " (util/print-duration-mins duration))]
       [:div (str "Average time per day: " average-day-duration)]
       [:div (str "Average time per week: " average-week-duration)]
       [:div (cl-format nil "~d activit~:*~[ies~;y~:;ies~]" (count location-durations))]

       [:div.clocks

        [:div.clock-list
         {:ref #(do
                  (reset! clock-list-el %)
                  (when % (reset! by-minute-height (-> % (dom/screen-relative-bounds) (nth 3)))))}

         (apply concat (doall
                        (for [{:keys [id duration] [{:keys [name tags path] :as clock}] :clocks} location-durations
                              :let [as-set #{id}
                                    highlighted? (not (empty? (intersection @highlighted-clocks as-set)))]]
                          [^{:key (str id "-duration")} [:span.duration (util/print-duration-mins duration)]
                           ^{:key (str id "-path")} [:span.path (cl-format nil "[~{~a~^ > ~}]" (map s/trim path))]
                           ^{:key (str id "-name")} [:span.name (util/parse-all-org-links name)]
                           ^{:key (str id "-tags")} [:span.tags.md-chips
                                                     (for [tag tags]
                                                       ^{:key (str id "-" tag)}
                                                       [:span.tag.md-chip.md-chip-raised tag])]])))]

        ;; [:div.clock-list
        ;;  {:ref #(do
        ;;          (reset! clock-list-el %)
        ;;          (when % (reset! by-minute-height (-> % (dom/screen-relative-bounds) (nth 3)))))}
        ;;  (doall (for [[location duration] location-durations
        ;;               :let [as-set #{location}
        ;;                     highlighted? (not (empty? (intersection @highlighted-clocks as-set)))]]
        ;;           [:div.activity {:key location
        ;;                           :class (when highlighted? "highlighted")
        ;;                           :on-mouse-over (fn [evt]
        ;;                                            (reset! highlighted-clocks #{location}))
        ;;                           :on-mouse-out #(reset! highlighted-clocks #{})}
        ;;            [:span.duration (util/print-duration-mins duration)]
        ;;            (util/parse-all-org-links location)]))]


        [:div.by-minute
         {:ref #(do
                  (reset! by-minute-width
                          (cond
                            (nil? %) 100

                            (> js/document.documentElement.clientWidth 1000)
                            (- (* .65
                                  (-> % .-parentElement (dom/screen-relative-bounds) (nth 2)))
                               (* 4 10))

                            :else (-> % .-parentElement (dom/screen-relative-bounds) (nth 2) (- 20))))

                  (reset! by-minute-el %))
          :class (if by-minute-manual-scroll? "sticky" "")
          :style {:top (str (- clock-list-scroll-y) "px")}}
         [day-by-minute-view/activities-by-minute-view
          clock-minute-intervals-by-day
          highlighted-clocks
          nil
          {:width @by-minute-width
           :height @by-minute-height}]]]])))



(comment

  (def el (js/document.querySelector "div.by-minute"))

  (let [[_ y _ _] (dom/screen-relative-bounds el)]
       y)
  y
  (dom/global-bounds el)
  (dom/scrolled-window-bounds)


)
