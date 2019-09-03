(ns org-analyzer.view.file-chooser
  (:require [reagent.core :as r]))

(defn file-chooser [title files non-existing-files on-confirm]
  (r/with-let [files (r/atom files)
               adding (r/atom nil)]
   [:div.file-chooser
    [:h3 title]

    (doall (for [f @files]
             ^{:key f} [:div.file
                        [:span.path f]
                        [:button.material-button.remove
                         {:on-click (fn [evt] (swap! files #(filter (partial not= f) %)))}
                         [:i.material-icons "cancel"]]]))

    (when @adding
      [:input {:type "text"
               :placeholder "/path/to/file.org"
               :value @adding
               :on-change #(reset! adding (-> % .-target .-value))
               :on-blur #(let [val (-> % .-target .-value)]
                           (when (and (not-empty val) (not-any? (partial = val) @files))
                             (swap! files conj (-> % .-target .-value)))
                           (reset! adding nil))
               :ref #(when % (.focus %))}])

    [:button.material-button.add
     {:on-click #(reset! adding "")}
     [:i.material-icons "add"] "add"]

    (when (not-empty non-existing-files)
      [:div.warning
       "The following files do not exist:"
       (doall (for [f non-existing-files]
                ^{:key f} [:div.file f]))])

    [:button.material-button.confirm
     {:on-click #(on-confirm @files)}
     [:i.material-icons "done"]
     "Confirm"]]))
