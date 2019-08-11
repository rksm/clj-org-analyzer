(ns org-analyzer.view.search-view
  (:require [clojure.string :as s]
            [clojure.set :as set]
            [org-analyzer.view.util :as util]
            [reagent.core :as rg]
            [reagent.ratom :refer [atom] :rename {atom ratom}]))

(defn tokenize-search-input [input]
  (filter not-empty
          (loop [[c & rest] (seq input)
                 quoted? false
                 current-word ""
                 words []]
            (cond
              (not c)                      (conj words current-word)
              (= c \")                     (recur rest (not quoted?) current-word words)
              (and (not quoted?) (= c \ )) (recur rest quoted? "" (conj words current-word))
              :else                        (recur rest quoted? (str current-word c) words)))))

(defn parse-search-input [input]
  (let [tokens (tokenize-search-input input)]
    (loop [[token & tokens] tokens
           result {:words #{} :included-tags #{} :excluded-tags #{}}]
      (if-not token
        result
        (case (first token)
          \+ (recur tokens (update result :included-tags #(conj % (subs token 1))))
          \- (recur tokens (update result :excluded-tags #(conj % (subs token 1))))
          (recur tokens (update result :words #(conj % token))))))))

(defn apply-search-input! [app-state input]
  (swap! app-state
         assoc :clocks-by-day-filtered
         (if (empty? input)
           (:clocks-by-day @app-state)
           (let [{:keys [words included-tags excluded-tags]} (parse-search-input input)]
             (prn [words included-tags excluded-tags])
             (into (sorted-map-by <)
                   (for [[date clocks] (-> @app-state :clocks-by-day)]
                     [date (for [{:keys [path tags name] :as clock} clocks
                                 :let [strings (concat [name] tags path)]
                                 :when (and
                                        (every? (fn [w] (some (fn [s] (s/includes? s w)) strings)) words)
                                        (set/subset? included-tags tags)
                                        (empty? (set/intersection excluded-tags tags)))]
                             clock)])))))

  ;; FIXME!!!
  (swap! app-state
         assoc :clock-minute-intervals-by-day-filtered
         (util/clock-minute-intervals-by-day (:clocks-by-day-filtered @app-state))))




(defn search-bar [app-state]
  (rg/with-let [loading? (ratom false)
                apply-search-input-debounced! (util/debounce #(do (apply-search-input! %1 %2)
                                                                  (reset! loading? false)) 500)]
    (let [search-input (-> @app-state :search-input)
          focused? (-> @app-state :search-focused?)]
      [:div.elev-2.search-bar.panel
       [:i.material-icons "search"]
       [:input.search-input
        {:type "search"
         :value search-input
         :ref #(when (and % focused?) (.focus %) (swap! app-state assoc :search-focused? false))
         :on-change (fn [evt] (let [input (-> evt .-target .-value)]
                                (reset! loading? true)
                                (swap! app-state assoc :search-input input)
                                (apply-search-input-debounced! app-state input)))}]
       [:div.loading {:class (if @loading? "visible" "")}]
       [:button.help.material-button
        {:on-click #(swap! app-state assoc :show-help? true)}
        [:i.material-icons "help"]]])))


(comment
 (parse-search-input "test hello")
 (parse-search-input "test \"hello world\" +foo bar   ")



 (clojure.set/subset? #{"hello" "world"} (set (s/split "hello foo  world" #" ")))
 ((set (s/split "hello foo world" #" ")) #{"hello" "world"}))
