(ns user
  (:require [mount.core :as mount]
            ndsu-food.core))

(defn start []
  (mount/start-without #'ndsu-food.core/repl-server))

(defn stop []
  (mount/stop-except #'ndsu-food.core/repl-server))

(defn restart []
  (stop)
  (start))


