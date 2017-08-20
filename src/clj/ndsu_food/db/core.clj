(ns ndsu-food.db.core
  (:require
   [cheshire.core :refer [generate-string parse-string]]
   [clj-time.jdbc]
   [clojure.java.jdbc :as jdbc]
   [conman.core :as conman]
   [ndsu-food.config :refer [env]]
   [mount.core :refer [defstate]]
   [clj-time.coerce :refer [from-sql-date to-sql-date]]
   [camel-snake-kebab.core :refer [->kebab-case-keyword]]
   [camel-snake-kebab.extras :refer [transform-keys]]
   [clojure.string :as str]
   [ndsu-food.util :as util]
   [clj-time.jdbc]
   [clojure.tools.logging :as log])
  (:import org.postgresql.util.PGobject
           java.sql.Array
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           [java.sql
            BatchUpdateException
            PreparedStatement]))

(defstate ^:dynamic *db*
  :start (conman/connect! {:jdbc-url (env :database-url)})
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(extend-protocol jdbc/IResultSetReadColumn
  Array
  (result-set-read-column [v _ _] (vec (.getArray v)))

  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (parse-string value true)
        "jsonb" (parse-string value true)
        "citext" (str value)
        value)))

  org.joda.time.DateTime
  (result-set-read-column [value metadata index]
    (from-sql-date value)))

(defn to-pg-json [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (generate-string value))))

(extend-type clojure.lang.IPersistentVector
  jdbc/ISQLParameter
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long idx]
    (let [conn      (.getConnection stmt)
          meta      (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt idx (to-pg-json v))))))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))

(defn result-one-snake->kebab
  [this result options]
  (->> (hugsql.adapter/result-one this result options)
       (transform-keys ->kebab-case-keyword)))

(defn result-many-snake->kebab
  [this result options]
  (->> (hugsql.adapter/result-many this result options)
       (map #(transform-keys ->kebab-case-keyword %))))

(defmethod hugsql.core/hugsql-result-fn :1 [sym]
  'ndsu-food.db.core/result-one-snake->kebab)

(defmethod hugsql.core/hugsql-result-fn :one [sym]
  'ndsu-food.db.core/result-one-snake->kebab)

(defmethod hugsql.core/hugsql-result-fn :* [sym]
  'ndsu-food.db.core/result-many-snake->kebab)

(defmethod hugsql.core/hugsql-result-fn :many [sym]
  'ndsu-food.db.core/result-many-snake->kebab)

(defn- mmap
  [f s]
  (if (map? (first s))
    (f s)
    (map f s)))

(defn- extract-food-item
  [row]
  (-> row
      (select-keys [:food-item-name :vegetarian :gluten-free :nuts])
      (clojure.set/rename-keys {:food-item-name :name})))

(defn- by-category
  [rows]
  (->> rows
       (sort-by :category)
       (partition-by :category)
       (map #(hash-map :name (:category (first %))
                       :items (vec (map extract-food-item %))))))
(defn- by-restaurant
  [rows]
  (->> rows
       (sort-by :restaurant-id)
       (partition-by :restaurant-id)
       (map #(hash-map :name (:restaurant-name (first %))
                       :categories (vec (mmap by-category %))))))

(defn- by-meal
  [rows]
  (->> rows
       (sort-by :meal)
       (partition-by :meal)
       (map #(hash-map :name (:meal (first %))
                       :restaurants (vec (mmap by-restaurant %))))))
(defn- by-date
  [rows]
  (->> rows
       (sort-by :date)
       (partition-by :date)
       (map #(hash-map :date (util/iso-date-fmt (:date (first %)))
                       :meals (vec (mmap by-meal %))))))

(defn hierarchize-menu-on-date
  [rows]
  (first (by-date rows)))

(defn get-formatted-menu-on-date
  [date-str]
  (->> date-str
       (util/parse-date-add-default-zone)
       (assoc {} :date)
       (get-menu-on-date)
       (hierarchize-menu-on-date)))
