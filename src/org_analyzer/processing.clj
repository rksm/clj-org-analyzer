(ns org-analyzer.processing
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [java-time :as time :refer [duration]]
            [taoensso.tufte :refer [p defnp]])
  (:import [java.time LocalDate LocalDateTime]
           java.time.format.DateTimeFormatter
           java.util.Locale))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; CLOCK parsing

(defrecord Clock [start end duration clock-string sections])

(def clock-re #"(?i)^\s*CLOCK:\s*((?:\[|<)[0-9a-z :-]+(?:\]|>))(?:--((?:\[|<)[0-9a-z :-]+(?:\]|>)))?(?:\s*=>\s*([0-9:]+))?\s*$")
(def brackets-re #"^(\[|<)|(\]|>)$")
(def colon-re #":")

(def date-time-patterns [{:parse #(p ::localdatetime-parse-timestamp (LocalDateTime/parse %1 (DateTimeFormatter/ofPattern %2 %3)))
                          :pattern "y-M-d[ ][cccc][ccc][ ]H:m"}
                         {:parse #(p ::localdate-parse-timestamp (. (LocalDate/parse %1 (DateTimeFormatter/ofPattern %2 %3)) (atTime 0 0)))
                          :pattern "y-M-d[ ][cccc][ccc]"}])

(def locales [Locale/ENGLISH Locale/GERMAN])

(defnp parse-timestamp
  "`string` like \"[2019-06-19 Wed 14:11]\". Returns LocalDateTime."
  [string]
  (let [sanitized (s/replace string brackets-re "")]
    (first
     (filter some?
             (for [locale locales
                   {:keys [parse pattern]} date-time-patterns]
               (try (parse sanitized pattern locale) (catch Exception e nil)))))))

(defnp parse-duration
  "`duration-string` like \"3:22\"."
  [duration-string]
  (as-> duration-string it
    (s/replace it colon-re "H")
    (str "PT" it "M")
    (duration it)))

(defnp parse-clock [clock-string]
  (let [[_ start end duration] (re-find clock-re clock-string)]
    {:start (parse-timestamp start)
     :end (when end (parse-timestamp end))
     :duration (when duration (parse-duration duration))}))

(declare parent-entries all-tags-for)

(defnp find-clocks [org-data]
  (for [{:keys [type text] :as entry} org-data
        :when (= type :clock)]
    (merge {:clock-string text
            :sections (p ::parent-entries (parent-entries entry org-data))
            :tags (p ::all-tags-for (all-tags-for entry org-data))}
           (parse-clock text))))

(defn clocks-by-section [clocks]
  (group-by (comp last :sections) clocks))


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; org-parsing

(def headline-re #"^(\*+)\s*(.*)$")
(def file-props-re #"^\s*#\+([0-9A-Za-z_\-]+):\s*(.*)")
(def metadata-re #"^\s*(CLOCK|DEADLINE|START|CLOSED|SCHEDULED|CREATED):.*")
(def simple-clock-re #"^\s*(CLOCK):.*")
(def section-keyword-re #"^(TODO|NEXT|MAYBE|WAIT|CANCELLED|DONE|REVIEW)\s*(.*)")

(defn parse-section-text-and-tags [text]
  (let [[_ keyword text-no-kw] (re-find section-keyword-re text)
        text (or text-no-kw text)
        [_ text-no-tags raw-tags] (re-find #"(.*)\s+:([^ ]+):$" text)
        text (or text-no-tags text)
        result {:name text}]
    (merge {:name text}
           (when keyword {:keyword keyword})
           (when raw-tags {:tags (keep not-empty (s/split raw-tags #":"))}))))

(defn read-org-lines [lines]
  (for [[i line] (map-indexed vector lines)
        :let [[_ stars text] (re-find headline-re line)
              [metadata] (re-find metadata-re line)
              clock (when (and metadata (s/starts-with? metadata "CLOCK:")) metadata)
              [_ file-prop-name prop-value] (re-find file-props-re line)
              line-no (inc i)]
        :when (or stars metadata clock file-prop-name)]
    (cond
      stars (merge {:line line-no
                    :type :section
                    :depth (count stars)}
                   (parse-section-text-and-tags text))
      clock {:line line-no :type :clock :text clock}
      metadata {:line line-no :type :metadata :text metadata}
      file-prop-name {:line line-no
                      :type :file-prop
                      :prop file-prop-name
                      :value prop-value})))

(defn read-file-props [file-name parsed]
  (let [props (->> parsed
                   (take-while #(not (= :section (:type %))))
                   (filter #(= (:type %) :file-prop)))
        tags (->> props
                  (keep #(when (= (:prop %) "FILETAGS")
                           (->> #":"
                                (s/split (:value %))
                                (keep not-empty))))
                  (apply concat))]
    {:type :file :name file-name :depth 0 :index 0 :props props :tags tags}))


(defn read-sections
  "Takes the parsed lines filters out sections and clocks and adds parent
  information to them. Parent relationship is definend through an index to a
  previous item."
  [file-name parsed]
  (let [parent-cache (let [size (count parsed)
                           arr (int-array 10 0)]
                       (aset arr 0 0)
                       (transient {:size size :max-depth 0 :cache arr}))]
    (loop [[current & rest] parsed
           index 0
           result (list (read-file-props file-name parsed))]
      (if-not current
        (reverse result)
        (let [{:keys [type depth]} current
              depth (or depth ##Inf)]
          (if-not (#{:clock :section :metadata} type)
            (recur rest index result)
            (let [
                  index (inc index)
                  parent (let [{:keys [max-depth cache size]} parent-cache
                               section? (= type :section)
                               parent-at-or-before (if section? (min max-depth (dec depth)) max-depth)
                               parent (loop [i parent-at-or-before] (or (and (= i 0) 0) (let [n (aget cache i)] (and (> n 0) n)) (recur (dec i))))]
                           (when section?
                             (when
                               ;; need to "resize" cache to depth
                               (> max-depth depth) (loop [i depth]
                                                     (when (<= i max-depth) (aset cache i 0)
                                                           (recur (inc i)))))
                             (assoc! parent-cache :max-depth depth)
                             (aset cache depth index))
                           parent)
                  entry (case type
                          (:clock :metadata) (assoc current :parent parent :index index)
                          :section (assoc current :parent parent :index index))]
              (recur rest index (conj result entry)))))))))



(defn parent-entries [entry org-data]
  (loop [index (:parent entry) result []]
    (if-let [next (and index (nth org-data index))]
      (recur (:parent next) (conj result next))
      result)))

(defn all-tags-for [entry org-data]
  (let [with-parents (conj (parent-entries entry org-data) entry)]
    (set (apply concat (keep :tags with-parents)))))

(defnp parse-org-file
  "The main function to turn org-content into a flat sequence of entry maps, each having at least :type, :line, and :index fields."
  ([org-file]
   (let [org-file (if (string? org-file) (io/file org-file) org-file)]
     (parse-org-file (.getName org-file) org-file)))
  ([name org-file]
   (let [lines (p ::parse-org-file-lines (line-seq (io/reader org-file)))
         parsed (p ::parse-org-file-read-org-lines (read-org-lines lines))
         org-data (p ::parse-org-file-read-sections (read-sections name parsed))]
     org-data)))
