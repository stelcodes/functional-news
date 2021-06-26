(ns codes.stel.functional-news.core
  (:require [codes.stel.functional-news.http :refer [start-server]]
            [taoensso.timbre :refer [spy debug log warn error]]))

(defn -main [& _] (try (start-server) (catch Exception e (error e) (System/exit 1))))
