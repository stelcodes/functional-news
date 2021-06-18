(ns codes.stel.functional-news.views
  (:require [hiccup.page :as hp]
            [hiccup2.core :refer [html raw]]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [spy debug log warn error]]
            [cemerick.url :as cu]))

(defn layout
  [title & content]
  (-> (html {:lang "en"}
            [:head [:title title] [:meta {:charset "utf-8"}] [:meta {:http-equiv "X-UA-Compatible", :content "IE=edge"}]
             [:meta {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
             [:link {:rel "shortcut icon", :href "/assets/favicon.ico", :type "image/x-icon"}]
             (hp/include-css "/assets/css/main.css")
             (when (= (System/getenv "PROD") "true")
               [:script
                {:src "https://plausible.io/js/plausible.js",
                 :data-domain "functional-news.stel.codes",
                 :defer "defer",
                 :async "async"}])]
            [:body content])
      (str)))

(defn unordered-list [coll] [:ul (for [x (remove nil? coll)] [:li x])])

(defn nav
  ([] (nav nil))
  ([user]
   [:nav
    (unordered-list [(he/link-to "/" "hot") (he/link-to "/new" "new") nil nil (he/link-to "/submit" "submit")
                     (if user (he/link-to "/logout" "logout") (he/link-to "/login" "login"))])]))

(defn header
  ([] (header nil))
  ([user]
   (let [username (:users/name user)
         username-text (when username (str username "(" (:score user) ")"))]
     [:header (he/link-to {:class "logo"} "/" [:span "λn"]) (nav user)
      (when username-text [:div.username-text username-text])])))

(defn upvote-panel
  [submission-id upvote-count]
  [:div.love-panel (he/link-to (str "/upvote/" submission-id) (raw (slurp (clojure.java.io/resource "svg/love.svg"))))
   (when upvote-count [:span (str upvote-count)])])

(comment
  (slurp (clojure.java.io/resource "svg/love.svg")))

(defn created-string
  "Takes age in minutes and returns 'created _ minutes ago' OR 'created _ hours ago'"
  [age]
  (cond (>= age 2880) (format "created %.0f days ago" (/ age 1440.0))
        (>= age 1440) "created 1 day ago"
        (>= age 120) (format "created %.0f hours ago" (/ age 60.0))
        (>= age 60) "created 1 hour ago"
        (>= age 2) (str "created " age " minutes ago")
        (= age 1) "created 1 minute ago"
        :else "created just now"))

(defn comment-string
  [comment-count]
  (cond (nil? comment-count) " discuss"
        (= comment-count 1) " 1 comment"
        :else (str comment-count " comments")))

(defn submission-list-item
  [submission]
  (let [id (:submissions/id submission)
        url (:submissions/url submission)
        title (:submissions/title submission)
        host (:host (cu/url url))
        username (:users/name submission)
        age (:age submission)
        upvotecount (:upvotecount submission)
        submission-url (str "submissions/" id)
        comment-count (:commentcount submission)]
    [:div.submission-list-item (upvote-panel id upvotecount)
     [:div.submission-header (he/link-to {:class "submission-title"} url title) [:p.submission-host (str "@ " host)]
      [:p (str "by " username " " (created-string age) " | ")
       (he/link-to submission-url (comment-string comment-count))]]]))

(defn comment-item
  [comment]
  [:div.comment-item [:p.comment-header (str (:users/name comment) " " (created-string (:age comment)))]
   [:p (:comments/body comment)]])

(defn footer
  []
  [:footer (he/image {:class "avatar"} "https://s3.stel.codes/avatar.png")
   [:div.self-promotion [:p.introduction "Hi! I'm Stel Abrego, and I created this web app: λn (functional news)"]
    [:p.explanation "Thanks for checking it out! I used Clojure, SCSS, Postgres, and NixOS"]
    [:p "My Dev Blog + Resume 👉 " (he/link-to "https://stel.codes" "stel.codes")]
    [:p "My Twitter Feed 👉 " (he/link-to "https://twitter.com/stelstuff" "@stelstuff")]
    [:p "My Github Projects 👉 " (he/link-to "https://github.com/stelcodes" "@stelcodes")]]])

(defn submission-page
  [user submission comments]
  (let [id (:submissions/id submission)
        title (:submissions/title submission)
        url (:submissions/url submission)
        host (:host (cu/url url))
        username (:users/name submission)
        age (:age submission)
        upvotecount (:upvotecount submission)]
    (layout (str title " | λn")
            (header user)
            [:main
             [:div.submission-body (upvote-panel id upvotecount)
              [:div.submission-header (he/link-to {:class "submission-title"} url title) [:p.submission-host host]
               [:p (str "by " username " " (created-string age))]]
              (hf/form-to [:post "/comments"]
                          (hf/hidden-field "submission-id" id)
                          (hf/text-area "body")
                          (hf/submit-button {:class "submit-button"} "add comment"))
              [:div.submission-comments (map comment-item comments)]]]
            (footer))))

(defn submission-list
  [user submissions]
  (layout "Submissions | λn"
          (header user)
          [:main [:div.submission-list (map submission-list-item submissions)]]
          (footer)))

(defn login-page
  "message is a map with optional :login and :signup keys"
  ([] (login-page {}))
  ([message]
   (let [login-message (or (:login message) "Log in to submit links, comment, and upvote!")
         signup-message (:signup message)]
     (layout "Login | λn"
             (header)
             [:main [:p.form-message login-message]
              (hf/form-to {:class "login-form"}
                          [:post "/login"]
                          (hf/label "email" "email")
                          (hf/email-field "email")
                          (hf/label "password" "password")
                          (hf/password-field "password")
                          (hf/submit-button {:class "submit-button"} "login"))
              (when signup-message [:p.form-message signup-message])
              (hf/form-to {:class "signup-form"}
                          [:post "/signup"]
                          (hf/label "email" "email")
                          (hf/email-field "email")
                          (hf/label "password" "password")
                          (hf/password-field "password")
                          (hf/submit-button {:class "submit-button"} "signup"))]
             (footer)))))

(defn submit-page
  [user message]
  (layout "Submit | λn"
          (header user)
          [:main (when message [:p message])
           (hf/form-to {:class "submit-form"}
                       [:post "/submit"]
                       (hf/label "title" "title")
                       (hf/text-field {:placeholder "Is it 2010 again?"} "title")
                       (hf/label "url" "link")
                       (hf/text-field {:placeholder "https://nyan.cat"} "url")
                       (hf/submit-button {:class "submit-button"} "submit"))]))

(defn not-found [user] (layout "Not Found | λn" (header user) [:main [:h1 "Page not found :("]] (footer)))

