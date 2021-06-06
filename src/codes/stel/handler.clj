(ns codes.stel.functional-news.handler
  (:require [reitit.ring :refer
             [ring-handler router create-resource-handler routes redirect-trailing-slash-handler
              create-default-handler]]
            [ring.util.response :refer [redirect bad-request]]
            [cuter-news.views :as views]
            [cuter-news.state :as state]
            [cuter-news.util :refer [validate-url]]
            [muuntaja.core :as muuntaja]
            [failjure.core :as f]
            [reitit.ring.middleware.muuntaja :refer [format-middleware]]
            [reitit.ring.middleware.parameters :refer [parameters-middleware]]
            [reitit.ring.coercion :refer [coerce-exceptions-middleware coerce-request-middleware]]
            [reitit.coercion.schema]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [reitit.spec :refer [validate]]
            [reitit.dev.pretty :refer [exception]]
            [schema.core :as s]))

;; GET request handler functions

(defn hot-submissions-page-handler
  [request]
  (let [submissions (state/get-hot-submissions (:conn request))
        user-id (get-in request [:session :id])
        user (state/find-user (:conn request) user-id)]
    {:status 200, :content-type "text/html", :body (views/submission-list user submissions)}))

(defn new-submissions-page-handler
  [request]
  (let [conn (:conn request)
        submissions (state/get-new-submissions conn)
        user-id (get-in request [:session :id])
        user (state/find-user conn user-id)]
    {:status 200, :content-type "text/html", :body (views/submission-list user submissions)}))

(defn login-page-handler [request] {:status 200, :content-type "text/html", :body (views/login-page)})

(defn submit-page-handler
  [request]
  (let [conn (:conn request)
        user-id (get-in request [:session :id])
        user (state/find-user conn user-id)]
    (if (f/failed? user)
      (redirect "/login" :see-other)
      {:status 200, :content-type "text/html", :body (views/submit-page user nil)})))

(defn submission-page-handler
  [request]
  (let [conn (:conn request)
        user-id (get-in request [:session :id])
        user (state/find-user conn user-id)
        submission-id (get-in request [:parameters :path :id])
        submission (state/find-submission conn submission-id)
        comments (state/find-comments conn submission-id)]
    (if (f/failed? submission)
      {:status 400, :content-type "text/html", :body (views/not-found user)}
      {:status 200, :content-type "text/html", :body (views/submission-page user submission comments)})))

;; POST request handler functions

(defn authorize-user-handler
  [request]
  (spit "/tmp/log" request)
  (let [conn (:conn request)
        email (get-in request [:params "email"])
        password (get-in request [:params "password"])
        result (state/auth-user conn email password)
        session-body {:id (:users/id result)}]
    (if (f/failed? result)
      {:status 400,
       :content-type "text/html",
       :body (views/login-page {:login "We didn't recognize that email password combination ;-;"})}
      (assoc (redirect "/" :see-other) :session session-body))))

(defn create-comment-handler
  [request]
  (let [conn (:conn request)
        user-id (get-in request [:session :id])
        submission-id (get-in request [:parameters :form :submission-id])
        body (get-in request [:parameters :form :body])
        result (state/create-comment conn user-id submission-id body)]
    (if (f/failed? result) (redirect "/login" :see-other) (redirect (str "/submissions/" submission-id) :see-other))))


(defn create-submission-handler
  [request]
  (let [conn (:conn request)
        user-id (get-in request [:session :id])
        user (state/find-user conn user-id)
        title (get-in request [:parameters :form :title])
        url (get-in request [:parameters :form :url])
        result (f/attempt-all [_ (validate-url url) submission (state/create-submission conn title url user-id)
                               submission-id (:submissions/id submission)]
                              submission-id)]
    (if (f/failed? result)
      (bad-request (views/submit-page user (f/message result)))
      (redirect (str "/submissions/" result) :see-other))))

(defn logout-handler [_request] (assoc (redirect "/") :session nil))

(defn upvote-handler
  [request]
  (let [conn (:conn request)
        submission-id (get-in request [:parameters :path :id])
        user-id (get-in request [:session :id])
        user-result (state/find-user conn user-id)
        location (get-in request [:headers "referer"])
        upvote-result (state/create-upvote conn user-id submission-id)]
    (cond (f/failed? user-result) (redirect "/login" :see-other)
          :else (redirect location :see-other))))

(defn signup-handler
  [request]
  (let [conn (:conn request)
        email (get-in request [:parameters :form :email])
        password (get-in request [:parameters :form :password])
        result (state/create-user conn email password)
        session-body {:id (:users/id result)}]
    (if (f/failed? result)
      (bad-request (views/login-page {:signup "That email is already taken ;-;"}))
      (assoc (redirect "/" :see-other) :session session-body))))

(def db-connection-middleware
  {:name ::db-connection,
   :compile (fn [{:keys [conn], :as _route-data} _opts] (fn [handler] (fn [req] (handler (assoc req :conn conn)))))})

(def session-middleware
  {:name ::session,
   :compile (fn [_route-data _opts]
              (let [cookie-key (.getBytes (or (System/getenv "COOKIE_KEY") "abcdefghijklmnop"))]
                (fn [handler]
                  (wrap-session handler {:store (cookie-store {:key cookie-key}), :cookie-name "cuternewscookie"}))))})

(def app-routes
  [["/" {:handler hot-submissions-page-handler}] ["/assets/*" (create-resource-handler)]
   ["/new" {:handler new-submissions-page-handler}]
   ["/submit"
    {:get {:handler submit-page-handler},
     :post {:handler create-submission-handler, :parameters {:form {:title s/Str, :url s/Str}}}}]
   ["/logout" {:get {:handler logout-handler}}]
   ["/submissions/:id" {:handler submission-page-handler, :parameters {:path {:id s/Int}}}]
   ["/upvote/:id" {:get {:name ::upvote, :handler upvote-handler, :parameters {:path {:id s/Int}}}}]
   ["/login"
    {:get {:handler login-page-handler},
     :post
       {:name ::authorize-user, :handler authorize-user-handler, :parameters {:form {:email s/Str, :password s/Str}}}}]
   ["/signup" {:post {:name ::signup, :handler signup-handler, :parameters {:form {:email s/Str, :password s/Str}}}}]
   ["/comments"
    {:post {:handler create-comment-handler,
            :name ::create-comment,
            :parameters {:form {:submission-id s/Int, :body s/Str}}}}]])

(def router-options
  {:validate validate,
   :exception exception,
   :data {:conn conn,
          :coercion reitit.coercion.schema/coercion,
          :muuntaja muuntaja/instance,
          :middleware [format-middleware db-connection-middleware parameters-middleware coerce-exceptions-middleware
                       coerce-request-middleware]}})

(defn app
  [conn]
  (ring-handler (router app-routes router-options)
                (routes (redirect-trailing-slash-handler)
                        (create-default-handler {:not-found (constantly {:status 404, :body "Not found :("}),
                                                 :method-not-allowed (constantly {:status 405,
                                                                                  :body "Method not allowed :("})}))
                {:middleware [session-middleware]}))


