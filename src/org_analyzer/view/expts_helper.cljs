(ns org-analyzer.view.expts-helper
  (:require [clojure.string :as s]
            [reagent.ratom :refer [atom] :rename {atom ratom}])
  (:require-macros [org-analyzer.view.expts-helper-macros]))

(def expt-registry (ratom (sorted-map)))

(def visible-expts
  (let [state
        (let [queries (keep not-empty (-> js/document.location.search
                                          (s/replace #"^\?" "")
                                          (s/split #"&")))]
          (into {} (map vector (map keyword (seq queries)) (repeat true))))]
    (ratom state)))

(add-watch visible-expts :hist-update
           (fn [_ _ _ new-state]
             (js/history.pushState
              (-> visible-expts deref clj->js)
              "expt visibility"
              (let [opened (keys (filter (comp true? second) new-state))
                    new-path js/window.location.pathname
                    new-path (if (seq opened)
                               (str new-path "?" (s/join "&" (clj->js opened)))
                               new-path)]
                (str new-path)))))

(defn expts-toggle [key component]
  (let [comp-name (name key)
        toggle (fn [bool] (swap! visible-expts assoc key bool))]
    (if-not (key @visible-expts)
      [:div.expt-toggle.collapsed
       [:button.collapsible {:on-click #(toggle true)} (str "+ " comp-name)]]
      [:div.expt-toggle.uncollapsed
       [:button.collapsible {:on-click #(toggle false)} (str "- " comp-name)]
       [component]])))

(defn purge-expt! [expt-id]
  (swap! expt-registry dissoc expt-id))


(defn expts []
  [:div.expts
   (doall (for [[key component] @expt-registry]
            ^{:key key} [expts-toggle key component]))])
