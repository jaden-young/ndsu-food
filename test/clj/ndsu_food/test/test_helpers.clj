(ns ndsu-food.test.test-helpers
  (:require [clojure.java.jdbc :as jdbc]
            [ndsu-food.db.core :refer [*db*] :as db]
            [ndsu-food.config :refer [env]]
            [mount.core :as mount]
            [luminus-migrations.core :as migrations]))

(defn setup-db
  [f]
  (mount/start
   #'ndsu-food.config/env
   #'ndsu-food.db.core/*db*)
  (migrations/migrate ["reset"] (select-keys env [:database-url]))
  (migrations/migrate ["migrate"] (select-keys env [:database-url]))
  (f))

(defn rollback
  [f]
  (jdbc/with-db-transaction [tx *db*]
    (jdbc/db-set-rollback-only! tx)
    (binding [*db* tx] (f))))
