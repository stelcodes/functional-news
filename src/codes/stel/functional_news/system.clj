(ns codes.stel.functional-news.system
  (:require [integrant.core :refer [init-key]]
            [next.jdbc :refer [get-datasource]]
            [codes.stel.functional-news.handler :refer [app]]
            [codes.stel.functional-news.util :refer [parse-int]]))

; (def db-connection (get-datasource {:dbtype "postgresql", :dbname "functional_news", :host "127.0.0.1", :port 5432}))
; (def port
;   (if-let [env-port (System/getenv "PORT")]
;     (parse-int env-port)
;     3000))

; (def config {:adapter/jetty {:handler (ig/ref :handler/run-app), :port port}})

; (defmethod init-key :adapter/jetty [_ _] (run-jetty (app) {:port port, :join? false}))
