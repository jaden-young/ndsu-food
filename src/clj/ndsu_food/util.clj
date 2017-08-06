(ns ndsu-food.util
  (:require [clj-time.format :as t-fmt]
            [clj-time.core :as t]))

(defn iso-date-fmt
  [date]
  (t-fmt/unparse (t-fmt/formatters :date) date))

(defn today-str
  []
  (t-fmt/unparse-local-date (t-fmt/formatters :date) (t/today)))

(defn parse-date-add-default-zone
  "Parses a 'yyyy-mm-dd' string into a clj-time.DateTime object, adding
  the time zone, or default to server's time zone.

  When given a string 'yyyy-mm-dd' clj-time parses it into a DateTime with
  zeroed out UTC time. This causes the parsed date to be one day behind/ahead
  depending on the timezone. It would be a better idea to figure out how to
  standardize on UTC instead of converting these time zones, but that's a job
  for another day."
  ([date-str]
   (parse-date-add-default-zone date-str (t/default-time-zone)))
  ([date-str zone]
   (let [fmt (t-fmt/with-zone (t-fmt/formatter :date) zone)]
     (t-fmt/parse fmt date-str))))
