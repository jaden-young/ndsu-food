(ns user
  (:require [mount.core :as mount]
            ndsu-food.core
            [ndsu-food.db.core :as db]
            [clj-time.core :as t]))

(defn start []
  (mount/start-without #'ndsu-food.core/repl-server))

(defn stop []
  (mount/stop-except #'ndsu-food.core/repl-server))

(defn restart []
  (stop)
  (start))
