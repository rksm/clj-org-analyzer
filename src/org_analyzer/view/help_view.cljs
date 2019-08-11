(ns org-analyzer.view.help-view)

(defn help-view [close-help-fn]
  [:div.help-view
   [:div.content
    [:h2 "About"]

    [:p "Org Analyzer creates an interactive visualization of org-mode
time-tracking data (via org-clock-in). It parses org files and extracts CLOCK:
timestamps. This is the underlying data displayed here."]

    [:h3 "Search"]

    [:p "The terms entered in the search panel will by default be matched with the full section names of clock entries. A space between two words means " [:i "and"] ". If groups of words are surrounded by quotation marks the exact phrase must match. Example:"
     [:pre "* some entry                                             :tag1:
** goes here                                             :tag2:
:LOGBOOK:
CLOCK: [2019-08-10 Sat 13:17]--[2019-08-10 Sat 14:09] =>  0:52
:END:
"]
     [:span "The entry above would be found if we would enter " [:i "goes"] " or " [:i "some here"] " or " [:i "\"some entry\""] "."]

     [:p "It is also possible to search for tags by prepending a search term with +. For example " [:i "+tag2"] " would match the entry above, so would " [:i "+tag1"] ". When a - is prepended an entry with such a tag will not match."]]

    [:h3 "Calendar"]
    [:p "You can select individual days in the calendar by clicking on the day boxes. When you press SHIFT and click you will add to the selection. ALT and click removes from the selection. You can also click on the top of weeks to select weeks or on month, to select them. Clicking and dragging will bring up a rectangular selection that allows you to select multiple days at once."]

    [:h3 "Shortcuts"]
    [:span [:code "[CTRL + s]"] " focus search input"]
    [:br]
    [:span [:code "[CTRL + a]"] " select all days in calendar"]

    [:h3 "Contact"]
    [:p "If you have questions, issues, or need a developer, please get in touch: " [:a {:href "mailto:robert@kra.hn"} "robert@kra.hn"] "."]

]
   [:button.close.material-button
    {:on-click close-help-fn}
    [:i.material-icons "close"]]])
