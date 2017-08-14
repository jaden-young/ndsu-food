(ns user
  (:require [clj-time.core :as t]
            [clj-time.format :as t-fmt]
            [migratus.core :as mig]
            [mount.core :as mount]
            [ndsu-food.config :refer [env]]
            ndsu-food.core
            [ndsu-food.db.core :as db]
            [ndsu-food.util :as util]))

(def db-map {:store :database
                 :db (env :database-url)})

(defn start []
  (mount/start-without #'ndsu-food.core/repl-server))

(defn stop []
  (mount/stop-except #'ndsu-food.core/repl-server))

(defn restart []
  (stop)
  (start))


(defn migrate [] (mig/migrate db-map))
(defn rollback [] (mig/rollback db-map))
(defn create
  [name]
  (mig/create db-map name))
