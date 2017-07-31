(ns ndsu-food.test.db.core
  (:require [ndsu-food.db.core :refer [*db*] :as db]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [ndsu-food.config :refer [env]]
            [mount.core :as mount]
            [conman.core :refer [with-transaction]]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'ndsu-food.config/env
     #'ndsu-food.db.core/*db*)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest test-food-item
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (is (= {:id 1} (db/create-food-item!
                    t-conn
                    {:name "test_item"
                     :vegetarian true
                     :gluten_free false
                     :nuts true})))
    (is (= {:id 1
            :name "test_item"
            :vegetarian true
            :gluten_free false
            :nuts true}
           (db/get-food-item t-conn {:id 1})))))
