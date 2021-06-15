(ns codes.stel.functional-news.handler
  (:require
    [reitit.ring :refer
     [create-resource-handler routes redirect-trailing-slash-handler create-default-handler]]
    [reitit.http :as http]
    [taoensso.timbre :refer [spy debug log warn error]]
    [reitit.http.coercion :refer [coerce-request-interceptor coerce-response-interceptor coerce-exceptions-interceptor]]
    [reitit.coercion.malli]
    [ring.util.response :refer [redirect bad-request]]
    [codes.stel.functional-news.views :as views]
    [codes.stel.functional-news.state :as state]
    [codes.stel.functional-news.util :refer [validate-url]]
    [slingshot.slingshot :refer [try+ throw+]]
    [ring.middleware.session :refer [session-request session-response]]
    [ring.middleware.session.cookie :refer [cookie-store]]
    [reitit.http.interceptors.muuntaja :refer [format-interceptor]]
    [reitit.spec :as rspec]
    [reitit.dev.pretty :as pretty]
    [reitit.interceptor.sieppari :as sieppari]))

;; Helper functions

(defn cookie-user
  [request]
  (when-let [user-id (get-in request [:session :id])]
    (try+ (state/find-user user-id) (throw+ {:type :handler/invalid-cookie}))))

(defn good-html-response [body] {:status 200, :content-type "text/html", :body body})

(defn bad-html-response [body] {:status 400, :content-type "text/html", :body body})

;; GET request handler functions

(defn hot-submissions-page-handler
  [{:keys [user], :as request}]
  (let [submissions (state/get-hot-submissions)] (good-html-response (views/submission-list user submissions))))

(defn new-submissions-page-handler
  [{:keys [user], :as request}]
  (let [submissions (state/get-new-submissions)] (good-html-response (views/submission-list user submissions))))

(defn login-page-handler [_request] (good-html-response (views/login-page)))

(defn submit-page-handler
  [{:keys [user], :as request}]
  (if user (good-html-response (views/submit-page user nil)) (redirect "/login" :see-other)))

(defn submission-page-handler
  "TODO: let cookie-user exception bubble up"
  [{:keys [user], :as request}]
  (try+ (let [submission-id (get-in request [:parameters :path :id])
              submission (state/find-submission submission-id)
              comments (state/find-comments submission-id)]
          (good-html-response (views/submission-page user submission comments)))
        (catch Object _ (bad-html-response (views/not-found user)))))

;; POST request handler functions

(defn authorize-user-handler
  [request]
  (try+ (let [email (get-in request [:params "email"])
              password (get-in request [:params "password"])
              user (state/auth-user email password)
              session-body {:id (:users/id user)}]
          (assoc (redirect "/" :see-other) :session session-body))
        (catch Object _
          (bad-html-response (views/login-page {:login "We didn't recognize that email password combination ;-;"})))))

(defn create-comment-handler
  [request]
  (try+ (let [user-id (get-in request [:session :id])
              submission-id (get-in request [:parameters :form :submission-id])
              body (get-in request [:parameters :form :body])
              _result (state/create-comment user-id submission-id body)]
          (redirect (str "/submissions/" submission-id) :see-other))
        (catch Object _ (redirect "/login" :see-other))))


(defn create-submission-handler
  [{:keys [user], :as request}]
  (try (let [user-id (get-in request [:session :id])
             title (get-in request [:parameters :form :title])
             url (get-in request [:parameters :form :url])
             _ (validate-url url)
             submission (state/create-submission title url user-id)
             submission-id (:submissions/id submission)]
         (redirect (str "/submissions/" submission-id) :see-other))
       (catch Exception e (bad-request (views/submit-page user (.getMessage e))))))

(defn logout-handler [_request] (assoc (redirect "/") :session nil))

(defn upvote-handler
  [request]
  (try+ (let [submission-id (get-in request [:parameters :path :id])
              user-id (get-in request [:session :id])
              _ (state/find-user user-id)
              location (get-in request [:headers "referer"])
              _ (state/create-upvote user-id submission-id)]
          (redirect location :see-other))
        (catch Object _ (redirect "/login" :see-other))))

(defn signup-handler
  [request]
  (try+ (let [email (get-in request [:parameters :form :email])
              password (get-in request [:parameters :form :password])
              result (state/create-user email password)
              session-body {:id (:users/id result)}]
          (assoc (redirect "/" :see-other) :session session-body))
        (catch Object _ (bad-request (views/login-page {:signup "That email is already taken ;-;"})))))

; (def session-middleware
;   {:name ::session,
;    :compile (fn [_route-data _opts]
;               (let [cookie-key (.getBytes (or (System/getenv "COOKIE_KEY") "abcdefghijklmnop"))]
;                 (fn [handler]
;                   (wrap-session handler {:store (cookie-store {:key cookie-key}), :cookie-name
;                   "cuternewscookie"}))))})

; (def cookie-key (.getBytes (or (System/getenv "COOKIE_KEY") "abcdefghijklmnop")))

(def session-interceptor
  (let [cookie-key (.getBytes (or (System/getenv "COOKIE_KEY") "abcdefghijklmnop"))
        options {:store (cookie-store {:key cookie-key}), :cookie-name "functional-news-cookie"}]
    {:enter (fn [{:keys [request], :as context}]
              (let [new-request (session-request request options)
                    user-id (get-in new-request [:session :body :id])
                    user (when user-id (try (state/find-user user-id) (catch Object _ nil)))
                    new-request (assoc new-request :user user)]
                (assoc context :request new-request))),
     :leave (fn [{:keys [response], :as context}] (assoc context :response (session-response response options)))}))

(def app-routes
  [["/" {:handler hot-submissions-page-handler}] ["/assets/*" {:handler (create-resource-handler)}]
   ["/new" {:handler new-submissions-page-handler}]
   ["/submit"
    {:get {:handler submit-page-handler},
     :post {:handler create-submission-handler, :parameters {:form [:map [:title string? :url string?]]}}}]
   ["/logout" {:get {:handler logout-handler}}]
   ["/submissions/:id" {:handler submission-page-handler, :parameters {:path [:map [:id int?]]}}]
   ["/upvote/:id" {:get {:name ::upvote, :handler upvote-handler, :parameters {:path [:map [:id int?]]}}}]
   ["/login"
    {:get {:handler login-page-handler},
     :post {:name ::authorize-user,
            :handler authorize-user-handler,
            :parameters {:form [:map [:email string? :password string?]]}}}]
   ["/signup"
    {:post {:name ::signup, :handler signup-handler, :parameters {:form [:map [:email string? :password string?]]}}}]
   ["/comments"
    {:post {:handler create-comment-handler,
            :name ::create-comment,
            :parameters {:form [:map [:submission-id int? :body string?]]}}}]])

(def router-options
  {:validate rspec/validate, :exception pretty/exception, :coercion reitit.coercion.malli/coercion, :data {}})
;; :middleware [format-middleware parameters-middleware coerce-exceptions-middleware
;;             coerce-request-middleware]

(def app
  (http/ring-handler
    (http/router app-routes router-options)
    (routes (redirect-trailing-slash-handler)
            (create-default-handler {:not-found (constantly {:status 404, :body "Not found :("}),
                                     :method-not-allowed (constantly {:status 405, :body "Method not allowed :("})}))
    {:interceptors [ (format-interceptor) (coerce-request-interceptor) session-interceptor (coerce-response-interceptor)
                    (coerce-exceptions-interceptor)],
     :executor sieppari/executor}))


