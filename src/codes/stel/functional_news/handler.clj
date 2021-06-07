(ns codes.stel.functional-news.handler
  (:require [reitit.ring :refer
             [ring-handler router create-resource-handler routes
              redirect-trailing-slash-handler create-default-handler]]
            [ring.util.response :refer [redirect bad-request]]
            [codes.stel-functional-news.views :as views]
            [codes.stel-functional-news.state :as state]
            [codes.stel-functional-news.util :refer [validate-url]]
            [muuntaja.core :as muuntaja]
            [slingshot.slingshot :refer [try+ throw+]]
            [failjure.core :as f]
            [reitit.ring.middleware.muuntaja :refer [format-middleware]]
            [reitit.ring.middleware.parameters :refer [parameters-middleware]]
            [reitit.ring.coercion :refer
             [coerce-exceptions-middleware coerce-request-middleware]]
            [reitit.coercion.schema]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [reitit.spec :refer [validate]]
            [reitit.dev.pretty :refer [exception]]
            [schema.core :as s]))

;; Helper functions

(defn cookie-user
  [request]
  (when-let [user-id (get-in request [:session :id])]
    (try+ (state/find-user user-id) (throw+ {:type :handler/invalid-cookie}))))

(defn good-html-response
  [body]
  {:status 200, :content-type "text/html", :body body})

(defn bad-html-response
  [body]
  {:status 400, :content-type "text/html", :body body})

;; GET request handler functions

(defn hot-submissions-page-handler
  [request]
  (let [submissions (state/get-hot-submissions)
        user (cookie-user request)]
    (good-html-response (views/submission-list user submissions))))

(defn new-submissions-page-handler
  [request]
  (let [submissions (state/get-new-submissions)
        user (cookie-user request)]
    (good-html-response (views/submission-list user submissions))))

(defn login-page-handler [_request] (good-html-response (views/login-page)))

(defn submit-page-handler
  [request]
  (let [user (cookie-user request)]
    (if user
      (good-html-response (views/submit-page user nil))
      (redirect "/login" :see-other))))

(defn submission-page-handler
  "TODO: let cookie-user exception bubble up"
  [request]
  (try+ (let [user (cookie-user request)
              submission-id (get-in request [:parameters :path :id])
              submission (state/find-submission submission-id)
              comments (state/find-comments submission-id)]
          (good-html-response (views/submission-page user submission comments))
          (catch Object _ (bad-html-response (views/not-found user))))))

;; POST request handler functions

(defn authorize-user-handler
  [request]
  (try+ (let [email (get-in request [:params "email"])
              password (get-in request [:params "password"])
              user (state/auth-user email password)
              session-body {:id (:users/id user)}]
          (assoc (redirect "/" :see-other) :session session-body))
        (catch Object _
          (bad-html-response
            (views/login-page
              {:login
                 "We didn't recognize that email password combination ;-;"})))))

(defn create-comment-handler
  [request]
  (try+ (let [user-id (get-in request [:session :id])
              submission-id (get-in request [:parameters :form :submission-id])
              body (get-in request [:parameters :form :body])
              _result (state/create-comment user-id submission-id body)]
          (redirect (str "/submissions/" submission-id) :see-other))
        (catch Object _ (redirect "/login" :see-other))))


(defn create-submission-handler
  [request]
  (let [user-id (get-in request [:session :id])
        user (state/find-user user-id)
        title (get-in request [:parameters :form :title])
        url (get-in request [:parameters :form :url])
        result (f/attempt-all [_ (validate-url url) submission
                               (state/create-submission title url user-id)
                               submission-id (:submissions/id submission)]
                              submission-id)]
    (if (f/failed? result)
      (bad-request (views/submit-page user (f/message result)))
      (redirect (str "/submissions/" result) :see-other))))

(defn logout-handler [_request] (assoc (redirect "/") :session nil))

(defn upvote-handler
  [request]
  (let [submission-id (get-in request [:parameters :path :id])
        user-id (get-in request [:session :id])
        user-result (state/find-user user-id)
        location (get-in request [:headers "referer"])
        upvote-result (state/create-upvote user-id submission-id)]
    (cond (f/failed? user-result) (redirect "/login" :see-other)
          :else (redirect location :see-other))))

(defn signup-handler
  [request]
  (let [email (get-in request [:parameters :form :email])
        password (get-in request [:parameters :form :password])
        result (state/create-user email password)
        session-body {:id (:users/id result)}]
    (if (f/failed? result)
      (bad-request (views/login-page {:signup
                                        "That email is already taken ;-;"}))
      (assoc (redirect "/" :see-other) :session session-body))))

(def session-middleware
  {:name ::session,
   :compile (fn [_route-data _opts]
              (let [cookie-key (.getBytes (or (System/getenv "COOKIE_KEY")
                                              "abcdefghijklmnop"))]
                (fn [handler]
                  (wrap-session handler
                                {:store (cookie-store {:key cookie-key}),
                                 :cookie-name "cuternewscookie"}))))})

(def app-routes
  [["/" {:handler hot-submissions-page-handler}]
   ["/assets/*" (create-resource-handler)]
   ["/new" {:handler new-submissions-page-handler}]
   ["/submit"
    {:get {:handler submit-page-handler},
     :post {:handler create-submission-handler,
            :parameters {:form {:title s/Str, :url s/Str}}}}]
   ["/logout" {:get {:handler logout-handler}}]
   ["/submissions/:id"
    {:handler submission-page-handler, :parameters {:path {:id s/Int}}}]
   ["/upvote/:id"
    {:get {:name ::upvote,
           :handler upvote-handler,
           :parameters {:path {:id s/Int}}}}]
   ["/login"
    {:get {:handler login-page-handler},
     :post {:name ::authorize-user,
            :handler authorize-user-handler,
            :parameters {:form {:email s/Str, :password s/Str}}}}]
   ["/signup"
    {:post {:name ::signup,
            :handler signup-handler,
            :parameters {:form {:email s/Str, :password s/Str}}}}]
   ["/comments"
    {:post {:handler create-comment-handler,
            :name ::create-comment,
            :parameters {:form {:submission-id s/Int, :body s/Str}}}}]])

(def router-options
  {:validate validate,
   :exception exception,
   :data {:coercion reitit.coercion.schema/coercion,
          :muuntaja muuntaja/instance,
          :middleware [format-middleware parameters-middleware
                       coerce-exceptions-middleware
                       coerce-request-middleware]}})

(defn app
  []
  (ring-handler
    (router app-routes router-options)
    (routes (redirect-trailing-slash-handler)
            (create-default-handler
              {:not-found (constantly {:status 404, :body "Not found :("}),
               :method-not-allowed
                 (constantly {:status 405, :body "Method not allowed :("})}))
    {:middleware [session-middleware]}))


