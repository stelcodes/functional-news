(ns codes.stel.functional-news.http
  (:require [org.httpkit.server :refer [run-server]]
            [codes.stel.functional-news.handler :refer [app]]
            [codes.stel.functional-news.config :refer [config]]
            [taoensso.timbre :as timbre :refer [info]]))

(defn start-server
  []
  ;; The #' is useful when you want to hot-reload code
  ;; You may want to take a look: https://github.com/clojure/tools.namespace
  ;; and https://http-kit.github.io/migration.html#reload
  (let [http-opts (config :http)]
    (info (str "\n🟢 Listening for requests on port " (:port http-opts)))
    (run-server #'app http-opts)))
