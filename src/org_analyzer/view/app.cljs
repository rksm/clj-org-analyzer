(ns org-analyzer.view.app
  (:require [reagent.core :as r]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! >! close! chan go]]
            [org-analyzer.view.info :as info]
            [org-analyzer.view.file-chooser :as file-chooser]
            [org-analyzer.view.calendar :as calendar]
            [org-analyzer.view.util :as util]
            [org-analyzer.view.selected-day :as selected-day]
            [org-analyzer.view.selection :as sel]
            [clojure.set :refer [difference]]
            [clojure.string :as s]
            [org-analyzer.view.tooltip :as tooltip]
            [org-analyzer.view.search-view :as search-view]
            [cljs.reader :as reader]
            [org-analyzer.view.help-view :as help-view]
            [clojure.pprint :as pp]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; data

(defn empty-app-state
  ([]
   (empty-app-state nil))
  ([stored-known-org-files]
   (ratom {:calendar nil
           :force-choosing-files false
           :known-org-files nil
           :stored-known-org-files stored-known-org-files
           :non-existing-org-files nil
           :info {:clock-count 0
                  :org-files []}
           :clocks-by-day {}
           :clocks-by-day-filtered {}
           :clock-minute-intervals-by-day {}
           :hovered-over-date nil
           :selected-days #{}
           :selected-days-preview #{}
           :selecting? false
           :calendar-collapsed? false
           :clocks-collapsed? false
           :by-minute-collapsed? false
           :clock-details-collapsed? false
           :highlighted-calendar-dates #{}
           :highlighted-entries #{} ;; uses clock :locations for id'ing
           :search-input ""
           :search-focused? false
           :loading? true
           :show-help? false})))

(defn empty-dom-state []
  (atom {:sel-rect (atom sel/empty-rectangle-selection-state)
         :keys {:shift-down? false
                :alt-down? false}
         :day-bounding-boxes {}}))

;; (go (prn (<! (http/get "/known-org-files" {:headers {"Cache-Control" "no-cache"}}))))

(defn fetch-org-files! [result-atom]
  (go (let [response (<! (http/get "/known-org-files"
                                   {:headers {"Cache-Control" "no-cache"}}))
            known-org-files (cljs.reader/read-string (:body response))]
        (reset! result-atom (cond
                              (empty? known-org-files) nil
                              (string? known-org-files) (vector known-org-files)
                              :else known-org-files)))))

(defn post-org-files [org-files-and-dirs]
  (let [result-chan (chan 1)]
    (pr {:form-params {:files (pr-str org-files-and-dirs)}})
    (go (let [response (<! (http/post "/known-org-files"
                                      {:form-params {:files (pr-str org-files-and-dirs)}}))
              files (cljs.reader/read-string (:body response))]
          (>! result-chan files)))
    result-chan))

(defn prepare-fetched-clocks [info clocks calendar]
  (let [clocks-by-day (group-by #(-> % :start (s/split #" ") first) clocks)
        clocks-by-day (merge
                       (into (sorted-map-by <) (map #(vector % []) (difference
                                                                    (set (keys calendar))
                                                                    (set (keys clocks-by-day)))))
                       clocks-by-day)]
    {:calendar calendar
     :info info
     :clocks-by-day clocks-by-day
     :clock-minute-intervals-by-day (util/clock-minute-intervals-by-day clocks-by-day)
     :clocks-by-day-filtered clocks-by-day
     :clock-minute-intervals-by-day-filtered (util/clock-minute-intervals-by-day clocks-by-day)}))

(defn fetch-clocks
  [& {:keys [from to]
      :or {from (js/Date. "1900-01-01")
           to (js/Date.)}}]
  (let [result-chan (chan 1)
        from (util/format-date-time from)
        to (util/format-date-time to)]
    (go (let [response (<! (http/get "/clocks"
                                     {:query-params {:from from :to to :by-day? true}
                                      :headers {"Cache-Control" "no-cache"}}))
              {:keys [clocks info]} (reader/read-string (:body response))]
          (println "got clocks")
          (let [from (:start (first clocks))
                response (<! (http/get "/calendar" {:query-params {:from from :to to}}))
                calendar (into (sorted-map-by <) (map (juxt :date identity) (reader/read-string (:body response))))]
            (println "got calendar")
            (>! result-chan (prepare-fetched-clocks info clocks calendar))
            (close! result-chan))))
    result-chan))

(defn fetch-and-update! [app-state]
  (swap! app-state assoc :loading? true)
  (go (swap! app-state merge (<! (fetch-clocks)) {:loading? false})))

(defn send-cancel-kill-server-request! []
  (println "sending server cancel kill request")
  (http/post "/cancel-kill"))

(defn send-kill-server-request! []
  (println "sending server kill request")
  (http/post "/kill"))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn event-handlers [app-state dom-state]
  (letfn [(set-key-down! [evt key field down?]
            (when (= key (.-key evt)) (swap! dom-state assoc-in [:keys field] down?)))

          (on-key-down-global [evt]
            (cond
              ;; select all
              (and (= "a" (.-key evt)) (.-ctrlKey evt))
              (let [all-days (-> @app-state :calendar keys set)]
                (swap! app-state update  :selected-days #(if (= % all-days) #{} all-days))
                (.preventDefault evt))

              ;; focus search
              (and (= "s" (.-key evt)) (.-ctrlKey evt))
              (do (swap! app-state assoc :search-focused? true)
                  (.preventDefault evt)))

            (set-key-down! evt "Alt" :alt-down? true)
            (set-key-down! evt "Shift" :shift-down? true))

          (on-key-up-global [evt]
            (set-key-down! evt "Alt" :alt-down? false)
            (set-key-down! evt "Shift" :shift-down? false))

          (on-window-resize [_evt] nil)

          (on-exit [_evt] (send-kill-server-request!) nil)]

    (set! (.-onbeforeunload js/window) on-exit)
    (.addEventListener js/document "keydown" on-key-down-global)
    (.addEventListener js/document "keyup" on-key-up-global)
    (.addEventListener js/window "resize" on-window-resize)

    (println "global event handlers registered")

    (merge (calendar/event-handlers app-state dom-state)
           {:on-key-down-global on-key-down-global
            :on-key-up-global on-key-up-global
            :on-window-resize on-window-resize})))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn controls [app-state]
  [:div.controls
   [:button.material-button
    {:on-click #(swap! app-state assoc :force-choosing-files true)
     :title "select org files"}
    [:i.material-icons "insert_drive_file"]]
   [:button.material-button
    {:on-click #(println "print")
     :title "export to pdf"}
    [:i.material-icons "print"]]
   [:button.material-button
    {:on-click #(swap! app-state assoc :show-info? true)
     :title (pp/cl-format nil "~a clock~:*~P" (-> @app-state :info :clock-count))}
    [:i.material-icons "info"]]
   [:button.material-button
    {:on-click #(swap! app-state assoc :show-help? true)
     :title "show help"}
    [:i.material-icons "help"]]])

(defn collapsible* [title _key collapsed-atom comp-fn]
  (let [collapsed? @collapsed-atom]
    [:div.panel.elev-2
     [:button.material-button
      {:on-click #(reset! collapsed-atom (not collapsed?))}
      title
      [:i.material-icons (if collapsed? "expand_less" "expand_more")]]
     (when-not collapsed?
       (comp-fn))]))

(defn app [app-state dom-state event-handlers]

  (if (or (empty? (:known-org-files @app-state))
          (:force-choosing-files @app-state))

    [file-chooser/file-chooser
     [:div {:style {:text-align "center"}}
      [:span (if (empty? (:known-org-files @app-state)) "Currently no org files or directories are known." "")]
      [:br]
      [:span "Please add or remove org files and directories below, then click the confirm button."]]

     (distinct (map #(clojure.string/replace % #"/$" "") (concat
                                                          (:known-org-files @app-state)
                                                          (:stored-known-org-files @app-state))))
     (:non-existing-org-files @app-state)
     (fn [files] (go (let [files (distinct files)
                           {:keys [existing non-existing]} (<! (post-org-files files))]
                       (swap! app-state assoc
                              :known-org-files existing
                              :non-existing-org-files non-existing)
                       (when (not-empty existing)
                         (js/localStorage.setItem "org-analyzer-files" (pr-str existing))
                         (swap! app-state assoc
                                :force-choosing-files false
                                :stored-known-org-files nil)
                         (fetch-and-update! app-state)))))]

    (let [{:keys [hovered-over-date
                  selected-days
                  clocks-by-day-filtered
                  clock-minute-intervals-by-day-filtered
                  calendar]} @app-state
          n-selected (count selected-days)
          selected-days (cond
                          (> n-selected 0) (vals (select-keys calendar selected-days))
                          hovered-over-date [(get calendar hovered-over-date)]
                          :else nil)

          highlighted-entries-cursor (r/cursor app-state [:highlighted-entries])]

      [:div.app

       (when (:loading? @app-state)
         [:div.loading-indicator
          [:div.loading-spinner
           [:div] [:div] [:div] [:div]
           [:div] [:div] [:div] [:div]
           [:div] [:div] [:div] [:div]]])

       (when (:show-help? @app-state)
         [help-view/help-view
          #(swap! app-state assoc :show-help? false)])

       (when (:show-info? @app-state)
         [info/info-view
          app-state
          #(swap! app-state assoc :show-info? false)])

       [controls app-state]

       [search-view/search-bar app-state]

       ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
       ;; calendar
       (collapsible* "Calendar" :calendar-collapsed? (r/cursor app-state [:calendar-collapsed?])
                     (fn [] [calendar/calendar-view app-state dom-state event-handlers]))

       ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
       ;; clocks
       (collapsible* "Clocks" :clocks-collapsed? (r/cursor app-state [:clocks-collapsed?])
                     (fn [] (when selected-days
                              [selected-day/selected-days-view
                               selected-days
                               clocks-by-day-filtered
                               calendar
                               highlighted-entries-cursor])))

       ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
       ;; by-minute
       (collapsible* "timeline" :by-minute-collapsed? (r/cursor app-state [:by-minute-collapsed?])
                     (fn [] (r/with-let [tooltip (ratom nil)]
                              (tooltip/with-tooltip-following-mouse tooltip
                                [:div.by-minute
                                 (let [dates (map :date selected-days)
                                       clock-minute-intervals-by-day-filtered (into (sorted-map-by <) (select-keys clock-minute-intervals-by-day-filtered dates))]
                                   (when (> (count dates) 0)
                                     [org-analyzer.view.timeline/activities-by-minute-view
                                      clock-minute-intervals-by-day-filtered
                                      highlighted-entries-cursor
                                      tooltip
                                      {:width (- js/document.documentElement.clientWidth 60)}]))]))))

       ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
       ;; clock
       #_(collapsible* "Clock" :clock-details-collapsed? (r/cursor app-state [:clock-details-collapsed?])
                       (fn [] "details"))])))
