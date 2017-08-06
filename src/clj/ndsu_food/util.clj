(ns ndsu-food.util
  (:require [clj-time.format :as t-fmt]))

(defn iso-date-fmt
  [date]
  (t-fmt/unparse (t-fmt/formatters :date) date))
