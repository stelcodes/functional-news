(ns codes.stel.functional-news.views
  (:require [hiccup.page :as hp]
            [hiccup2.core :refer [html]]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [cemerick.url :as cu]))

(defn layout
  [title & content]
  (-> (html {:lang "en"}
            [:head [:title title] [:meta {:charset "utf-8"}] [:meta {:http-equiv "X-UA-Compatible", :content "IE=edge"}]
             [:meta {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
             [:link {:rel "shortcut icon", :href "/assets/favicon.ico", :type "image/x-icon"}]
             (hp/include-css "/assets/minireset.css") (hp/include-css "/assets/main.css")
             (when (= (System/getenv "PROD") "true")
               [:script
                {:src "https://plausible.io/js/plausible.js",
                 :data-domain "functional-news.stel.codes",
                 :defer "defer",
                 :async "async"}])]
            [:body content])
      (str)))

(defn nav
  ([] (nav nil))
  ([user]
   [:nav
    (he/unordered-list [(he/link-to "/" "hot") (he/link-to "/new" "new") (he/link-to "/submit" "submit")
                        (when user (he/link-to "/login" "login") (he/link-to "/logout" "logout"))])]))

(defn header
  ([] (header nil))
  ([user]
   (let [username (:users/name user)
         username-text (when username (str "logged in as " username " ^-^"))]
     [:header
      (he/link-to {:class "logo-text"}
                  "/"
                  (he/image {:class "logo"} "/assets/functional-news-logo.svg" "functional news")) (nav user)
      (when username-text [:div.username-text username-text])])))

(defn upvote-panel
  [submission-id upvote-count]
  [:div.love-panel (he/link-to (str "/upvote/" submission-id) (he/image "/assets/love.svg"))
   (when upvote-count [:span (str upvote-count)])])

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
     [:div.submission-header (he/link-to {:class "submission-title"} url title) [:p.submission-host host]
      [:p (str "by " username " " (created-string age) " | ")
       (he/link-to submission-url (comment-string comment-count))]]]))

(defn comment-item
  [comment]
  [:div.comment-item [:p.comment-header (str (:users/name comment) " " (created-string (:age comment)))]
   [:p (:comments/body comment)]])

(defn footer [] [:footer [:p "made with clojure in michigan 💖"]])

(defn submission-page
  [user submission comments]
  (let [id (:submissions/id submission)
        title (:submissions/title submission)
        url (:submissions/url submission)
        host (:host (cu/url url))
        username (:users/name submission)
        age (:age submission)
        upvotecount (:upvotecount submission)]
    (layout (str title " | Cuter News")
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
  (layout "Submissions | Cuter News"
          (header user)
          [:main [:div.submission-list (map submission-list-item submissions)]]
          (footer)))

(defn login-page
  ([] (login-page {}))
  ([message]
   "message is a map with optional :login and :signup keys"
   (let [login-message (or (:login message) "Log in to submit links, comment, and up-paw! ^-^*")
         signup-message (:signup message)]
     (layout "Login | Cuter News"
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
  (layout "Submit | Cuter News"
          (header user)
          [:main (when message [:p message])
           (hf/form-to {:class "submit-form"}
                       [:post "/submit"]
                       (hf/label "title" "title")
                       (hf/text-field {:placeholder "Is it 2010 again?"} "title")
                       (hf/label "url" "link")
                       (hf/text-field {:placeholder "https://nyan.cat"} "url")
                       (hf/submit-button {:class "submit-button"} "submit"))]))

(defn not-found [user] (layout "Not Found | Cuter News" (header user) [:main [:h1 "Page not found :("]] (footer)))

