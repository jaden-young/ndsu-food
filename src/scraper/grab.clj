(ns scraper.grab
  (:gen-class)
  (:require [clj-time.format :as t-fmt]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cemerick.url :as url]))

(def base-url "https://www.ndsu.edu/dining/menu/shortmenu.asp")
(def website-date-formatter (t-fmt/formatter "M/d/yyyy"))

(def ^:const loc-nums {:wdc "02"
                       :rdc "04"
                       :udc "10"
                       :pe "25"
                       :mg "11"})
(def ^:const all-locs (vec (keys loc-nums)))

(def cache-dir
  ;; One would assume that a directory name ends in '/', but I guess Java
  ;; has it's reasons.
  (let [home (System/getProperty "user.home")
        folders [home ".cache" "ndsu-food" "html"]
        path (interpose (java.io.File/separatorChar) folders)
        s (apply str path)
        dir (str s (java.io.File/separatorChar))]
    dir))

(defn- loc-abbrev
  [loc]
  (-> loc
      (str)
      (subs 1)
      (string/upper-case)))

(defn- iso-date-fmt
  [date]
  (t-fmt/unparse (t-fmt/formatters :date) date))

(defn- menu-date-fmt
  [date]
  (t-fmt/unparse website-date-formatter date))

(defn- build-filename
  [date loc]
  (str (iso-date-fmt date)
       "-"
       (loc-abbrev loc)
       ".html"))

(defn- build-url
  [date loc]
  (let [u (url/url base-url)
        q {:dtdate (menu-date-fmt date)
           :locationNum (get loc-nums loc)}]
    (str (assoc u :query q))))

(defn- persist!
  [date loc]
  (let [fname (build-filename date loc)
        file (io/file (str cache-dir fname))]
    (when-not (.exists file)
      (io/make-parents file)
      (spit file (slurp (build-url date loc))))
    file))

(defn- locs-resources
  [date locs resource-fn]
  (into {} (map #(vector % (resource-fn date %)) locs)))

(defn grab
  "Creates a mapping from location keywords to urls of
  menus for those dates/locs on the web. If locations are not
  provided, defaults to all."
  ([date]
   (apply grab date all-locs))
  ([date & locs]
   (locs-resources date locs build-url)))

(defn grab!
  "Creates a mapping from location keywords to java.io.File objects with html
  data for the menus at that loc/date. Grabs the menu data from the web and
  stores it at ~/.cache/ndsu-food/html/. Cache is checked each time, so web
  is only hit once."
  ([date]
   (apply grab! date all-locs))
  ([date & locs]
   (locs-resources date locs persist!)))
