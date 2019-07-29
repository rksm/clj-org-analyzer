(ns org-analyzer.browser
  (:require [reagent.core :as r]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :refer [cl-format]]
            [clojure.string :refer [split lower-case join replace]]
            [org-analyzer.dom-helpers :as dom])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:import [goog.async Debouncer]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(enable-console-print!)

(println "running")

(defn date-string [^js/Date date]
  (first (split (.toISOString date) \T)))

(defn- weeks [days]
  (loop [week [] weeks [] days days]
    (if (empty? days)
      (if (empty? week) weeks (conj weeks week))
      (let [[day & days] days
            week (conj week day)
            sunday? (= 7 (:dow day))
            weeks (if sunday? (conj weeks week) weeks)]
        (recur (if sunday? [] week) weeks days)))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn sum-clocks-mins [clocks]
  (reduce + (for [{:keys [duration]} clocks]
              (let [[hours mins] (map #(js/Number. %) (split duration ":"))
                    result (+ (* 60 hours) mins)]
                (if (js/isNaN result) 0 result)))))

(defn sum-clocks-count [clocks]
  (count clocks))

#_(reset! (:sum-clocks-fn state) sum-clocks-mins)
#_(reset! (:sum-clocks-fn state) sum-clocks-count)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def event (atom nil))

(def state {:sum-clocks-fn (ratom sum-clocks-mins)
            :calendar (ratom nil)
            :clocks (ratom [])
            :hovered-over-day (ratom nil)
            :selected-days (ratom #{})
            :selected-days-preview (ratom #{})})

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn fetch-data []
  (let [from (pr-str (js/Date. "2000-01-01"))
        to (pr-str (js/Date.))]

    (go (let [response (<! (http/get "/clocks" {:query-params {:from from :to to :by-day? true}}))
              body (cljs.reader/read-string {:readers {'inst #(js/Date. %)}} (:body response))]
          (println "got clocks")
          (reset! (:clocks state) body)
          (let [from (pr-str (:start (first @(:clocks state))))]
            (go (let [response (<! (http/get "/calendar" {:query-params {:from from :to to}}))
                      body (cljs.reader/read-string (:body response))]
                  (println "got calendar")
                  (reset! (:calendar state) body))))))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def org-link-re #"(.*)\[\[([^\]]+)\]\[([^\]]+)\]\](.*)")

(defn parse-org-link [string i]
  (if-let [[_ before link link-title after]
           (re-find org-link-re string)]
    [[:span {:key i} before]
     [:a {:key (+ 1 i) :href link :target "_blank"} link-title]
     [:span {:key (+ 2 i)} after]]
    nil))

(defn parse-all-org-links [string]
  (loop [[[_ attrs string] & rest] [[:span {:key 0} string]]
         i 1]
    (if-let [[& parsed] (parse-org-link string i)]
      (recur (concat parsed rest) (+ i (count parsed)))
      (concat [[:span attrs string]] rest))))


(defn print-duration-mins [mins]
  (let [hours (quot mins 60)
        mins (- mins (* hours 60))]
    (cl-format nil "~d:~2,'0d" hours mins)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- emph-css-class [count max-count]
  (-> count
      (/ max-count)
      (* 10)
      js/Math.round
      ((partial str "emph-"))))


(defn on-mouse-over-day [{:keys [day clocks] :as evt}]
  (reset! (:hovered-over-day state) evt))


(defn on-mouse-out-day [{:keys [day clocks] :as evt}]
  (reset! (:hovered-over-day state) nil)
  ;(reset! event evt)
  )

;; (.- @event)

(defn on-click-day [evt day]
  (reset! event evt)
  (let [add-selection? (.-shiftKey evt)]
    (swap! (:selected-days state) (fn [selected-days]
                                    (cond
                                      (and add-selection? (selected-days day)) (disj selected-days day)
                                      add-selection? (conj selected-days day)
                                      :else #{day})))))


(defn day-view [{:keys [date] :as day} {:keys [clocks-by-day selected-days sum-clocks-fn max-weight] :as calendar-state}]
  (let [clocks (get clocks-by-day date)
        selected? (selected-days day)]
    [:div.day {:key date
               :id date
               :class [(emph-css-class
                        (sum-clocks-fn clocks)
                        max-weight) (if selected? "selected")]
               :on-mouse-over #(on-mouse-over-day {:day day :clocks clocks})
               :on-mouse-out #(on-mouse-out-day {:day day :clocks clocks})
               :on-click #(on-click-day % day)}]))

(defn week-view [week calendar-state]
  (let [week-date (:date (first week))]
    [:div.week {:key week-date}
     (map #(day-view % calendar-state) week)]))


(defn month-view [[date days-in-month] calendar-state]
  [:div.month {:key date
               :class (lower-case (:month (first days-in-month)))}
   date
   [:div.weeks (map #(week-view % calendar-state) (weeks days-in-month))]])

(comment
  
  {:left 1065, :top 19, :width 72, :height 126}
 {:left 1105.5, :top 131.875, :right 1120.5, :bottom 146.875, :width 15, :height 15}
 (-> state :calendar deref first :date)

 (-> (js/document.querySelector "#calendar") .-offsetLeft)
 (-> (js/document.querySelector "#calendar") .-offsetTop)
 (-> (js/document.querySelector "#calendar") dom/el-bounds)
 (-> (js/document.querySelector ".day") .-id)
 (-> (js/document.querySelector ".day") dom/el-bounds)

 )


;; (defn debounce [f interval]
;;   (let [dbnc (Debouncer. f interval)]
;;     ;; We use apply here to support functions of various arities
;;     (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))

;; (def deb (debounce #(reset! (-> %1 :selected-days-preview) (set %2)) 10))


(defn mark-days-as-potentially-selected [drag-state]
  (let [{:keys [days]} drag-state
        {:keys [left top width height]} (dom/selection-rectangle drag-state)
        offset-parent (js/document.querySelector "#calendar")
        offset-x (.-offsetLeft offset-parent)
        offset-y (.-offsetTop offset-parent)
        left (+ left offset-x)
        top (+ top offset-y)
        contained (apply concat (for [el (js/Array.from (js/document.querySelectorAll ".day"))
                                      :let [{l :left t :top r :right b :bottom} (dom/el-bounds el)]
                                      :when (and (< left l)
                                                 (< top t)
                                                 (> (+ left width) r)
                                                 (> (+ top height) b))]
                                  (do
                                        ;(println (filter (comp #{(.-id el)} :date) (-> state :calendar deref)))
                                    (filter (comp #{(.-id el)} :date) (-> state :calendar deref))
                                    )
                                        ;(prn [left top width height] [l t w h] clocks-by-day)
                                        ;(js/console.log el)
                                  ))]

    (reset! (-> state :selected-days-preview) (set contained))

    ))

(def empty-drag-state (assoc dom/empty-drag-state
                             :on-selection-change mark-days-as-potentially-selected))


(def drag-state (ratom empty-drag-state))

(def last-evt (atom nil))

(defn calendar-view [clocks calendar]
  (let [clocks-by-day (group-by (comp date-string :start) clocks)
        sum-clocks-fn @(:sum-clocks-fn state)
        max-weight (reduce max (map (comp sum-clocks-fn second) clocks-by-day))
        calendar-state {:max-weight max-weight
                        :sum-clocks-fn sum-clocks-fn
                        :clocks-by-day clocks-by-day
                        :selected-days
                        (clojure.set/union @(:selected-days state)
                                           @(:selected-days-preview state))
                        }]


    (let [by-month (into (sorted-map) (group-by
                                       (comp
                                        (partial join "-")
                                        (partial take 2)
                                        #(split % "-")
                                        :date)
                                       calendar))]
      (swap! drag-state assoc :days calendar)
      [:div.calendar
       (dom/drag-mouse-handlers "calendar" drag-state)
       (when (:mousedown? @drag-state)
         [:div.selection {:style (dom/selection-rectangle @drag-state)}])
       (map #(month-view % calendar-state) by-month)])))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn current-day [{:keys [day clocks]}]
  (if (empty? clocks)
    nil
    (let [location-durations (reverse
                              (sort-by second
                                       (map (fn [[a b]] [a (sum-clocks-mins b)])
                                            (group-by :location clocks))))]
      [:div.day-detail
       [:div.date (str (:date day))]
       [:div.hours (print-duration-mins (apply + (map second location-durations)))]
       [:div.clock-list
        (for [[location duration] location-durations]
          [:div.activity {:key location}
           [:span.duration (print-duration-mins duration)]
           (parse-all-org-links location)])]])))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn reload-button []
  [:input {:type "button" :value "reload" :on-click fetch-data}])


(defn app []
  [:div.app.noselect
   [reload-button]
   [:div [calendar-view @(:clocks state) @(:calendar state)]]
   [:div [current-day @(:hovered-over-day state)]]
   ;[:div [current-day (or @(:hovered-over-day state) @(:selected-days state))]]
   ])

(defn start []
  (r/render [app]
            (js/document.querySelector "#app"))
  (fetch-data))

(start)



;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

;; (def dragged (atom nil))

;; (cljs.core/add-watch dragged :drag-watcher (fn [key ref old-state new-state] (println "dragged changed")))

;; (defn on-drag [evt]
;;   (println "on-drag"))
;; (defn on-drag-start [evt]
;;   (println "on-drag-start")
;;   (reset! dragged (.-target evt))
;;   (set! (.. evt -target -style -opacity) .5))
;; (defn on-drag-end [evt]
;;   (println "on-drag-end")
;;   (set! (.. evt -target -style -opacity) "")
;;   (reset! dragged nil)
;;   )
;; (defn on-drag-over [evt]
;;   (println "on-drag-over"))
;; (defn on-drag-enter [evt]
;;   (println "on-drag-enter"))
;; (defn on-drag-leave [evt]
;;   (println "on-drag-leave"))
;; (defn on-drop [evt]
;;   (println "on-drop"))


;; (.addEventListener js/document.body "drag"      on-drag)
;; (.addEventListener js/document.body "dragstart" on-drag-start)
;; (.addEventListener js/document.body "dragend"   on-drag-end)
;; (.addEventListener js/document.body "dragover"  on-drag-over)
;; (.addEventListener js/document.body "dragenter" on-drag-enter)
;; (.addEventListener js/document.body "dragleave" on-drag-leave)
;; (.addEventListener js/document.body "drop"      on-drop)




  ;; /* Event wird vom ge-drag-ten Element ausgelöst */
  ;; document.addEventListener("drag", function( event ) {

  ;; }, false);

  ;; document.addEventListener("dragstart", function( event ) {
  ;;     // Speichern einer ref auf das drag-bare Element
  ;;     dragged = event.target;
  ;;     // Element halb-transparent machen
  ;;     event.target.style.opacity = .5;
  ;; }, false);

  ;; document.addEventListener("dragend", function( event ) {
  ;;     // Transparenz zurücksetzen
  ;;     event.target.style.opacity = "";
  ;; }, false);

  ;; /* events fired on the drop targets */
  ;; document.addEventListener("dragover", function( event ) {
  ;;     // Standard-Aktion verhindern um das drop-Event zu erlauben
  ;;     event.preventDefault();
  ;; }, false);

  ;; document.addEventListener("dragenter", function( event ) {
  ;;     // Hintergrund des möglichen Drop-Zeils anfärben, wenn das drag-bare Element auf das Ziel gezogen wird
  ;;     if ( event.target.className == "dropzone" ) {
  ;;         event.target.style.background = "purple";
  ;;     }

  ;; }, false);

  ;; document.addEventListener("dragleave", function( event ) {
  ;;     // Hintergrund des möglichen Drop-Ziels, wenn das drag-bare Element vom Ziel wieder weggezogen wird / verlässt
  ;;     if ( event.target.className == "dropzone" ) {
  ;;         event.target.style.background = "";
  ;;     }
  ;; }, false);

  ;; document.addEventListener("drop", function( event ) {
  ;;     // Standard-Aktion verhindern (Bei einigen Elementen wäre das das Öffnen als Link)
  ;;     event.preventDefault();
  ;;     // move dragged elem to the selected drop target
  ;;     if ( event.target.className == "dropzone" ) {
  ;;         event.target.style.background = "";
  ;;         dragged.parentNode.removeChild( dragged );
  ;;         event.target.appendChild( dragged );
  ;;     }
    
  ;; }, false);
