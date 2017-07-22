(ns scraper.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.java.io :refer [reader writer]]))

(def pages-dir "resources/scraped-pages/")
(def test-page
  (reader (str pages-dir "2017-07-20-WDC.html")))
(def menu-table-selector
  [:body :> :table (html/nth-child 1) :> :tbody (html/nth-child 1) :> :tr (html/nth-child 2) :> :td (html/nth-child 2) :> :table (html/nth-child 5) :> :tbody (html/nth-child 1) :> :tr (html/nth-child 1)])

(defn menu-table
  "Closest ancestor that all actual menu data has in common"
  [page]
  (map html/text (html/select  (html/html-resource page)
                               [:body])))
(defn select
  "Closest ancestor that all actual menu data has in common"
  [selection]
  (html/text (html/select (html/html-resource (with-open test-page) test-page)
                               selection)))
(defn asdf
  []
  (println (select [:body :> :table (html/nth-child 1) :> :tbody (html/nth-child 1) :> :tr (html/nth-child 2) :> :td (html/nth-child 2) :> :table (html/nth-child 5) :> :tbody (html/nth-child 1) :> :tr (html/nth-child 1)])))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println (select [:body :> :table (html/nth-child 1) :> :tbody (html/nth-child 1) :> :tr (html/nth-child 2) :> :td (html/nth-child 2) :> :table (html/nth-child 5) :> :tbody (html/nth-child 1) :> :tr (html/nth-child 1)])))
