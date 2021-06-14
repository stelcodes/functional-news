(ns codes.stel.functional-news.state
  (:require [next.jdbc.sql :refer [query]]
            [next.jdbc :refer [get-datasource execute! execute-one!]]
            [taoensso.timbre :refer [log warn error]]
            [codes.stel.functional-news.util :refer [generate-username]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def datasource (get-datasource {:dbtype "postgresql", :dbname "functional_news", :host "127.0.0.1", :port 5432}))

(defn try-db
  [db-fn & args]
  (try+ (db-fn datasource (vec args))
        ;; I could add more catch clauses here for different types of postgres
        ;; errors
        ;; See
        ;; https://mariapaktiti.com/handling-postgres-exceptions-with-clojure
        (catch java.sql.SQLException e (throw+ {:type :state/sql-exception}))
        (catch Object _ ((error "Cannot connect to database") (throw+ {:type :state/db-connection})))))

(defn find-user
  [id]
  (let [result (try-db query "SELECT * FROM users WHERE id = ?" id)]
    (if (empty? result) (throw+ {:type :state/empty-result, :id id}) (first result))))

(defn find-username
  [username]
  (let [result (try-db query "SELECT * FROM users WHERE name = ?" username)]
    (if (empty? result) (throw+ {:type :state/empty-result, :username username}) (first result))))

(defn create-user
  [email password]
  (let [username (loop [username (generate-username)]
                   (let [status (try ((find-username username) :taken) (catch Exception _ :not-taken))]
                     if
                     (= status :not-taken)
                     username
                     (recur (generate-username))))
        result (try-db
                 execute!
                 "INSERT INTO users (name, email, password) VALUES (?, ?, crypt(?, gen_salt('bf', 8))) RETURNING *"
                 username
                 email
                 password)]
    (first result)))

(defn auth-user
  [email password]
  (let [result (try-db query "SELECT * FROM users WHERE email = ? AND password = crypt(?, password)" email password)]
    (if (empty? result) (throw+ {:type :state/auth-failure}) (first result))))

(defn create-submission
  [title url user-id]
  (let [submission (try-db execute-one!
                           "INSERT INTO submissions (title, url, userid) VALUES (?, ?, ?) RETURNING *"
                           title
                           url
                           user-id)
        submission-id (:submissions/id submission)
        _upvote (try-db execute!
                        "INSERT INTO upvotes (userid, submissionid) VALUES (?, ?) RETURNING *"
                        user-id
                        submission-id)]
    submission))

(defn get-new-submissions
  []
  (try-db
    query
    "SELECT submissions.id, submissions.title, submissions.url, submissions.created, minutes_age(submissions.created) AS age, users.name, c.commentcount, u.upvotecount FROM submissions JOIN users ON submissions.userid = users.id LEFT JOIN (SELECT comments.submissionid, COUNT(comments.id) AS commentcount FROM comments GROUP BY comments.submissionid) AS c ON c.submissionid = submissions.id LEFT JOIN (SELECT upvotes.submissionid, COUNT(upvotes.id) AS upvotecount FROM upvotes GROUP BY upvotes.submissionid) AS u ON u.submissionid = submissions.id ORDER BY submissions.created DESC LIMIT 30"))

(defn get-hot-submissions
  []
  (try-db
    query
    "SELECT submissions.id, submissions.title, submissions.url, submissions.created, minutes_age(submissions.created) AS age, users.name, c.commentcount, u.upvotecount FROM submissions JOIN users ON submissions.userid = users.id LEFT JOIN (SELECT comments.submissionid, COUNT(comments.id) AS commentcount FROM comments GROUP BY comments.submissionid) AS c ON c.submissionid = submissions.id LEFT JOIN (SELECT upvotes.submissionid, COUNT(upvotes.id) AS upvotecount FROM upvotes GROUP BY upvotes.submissionid) AS u ON u.submissionid = submissions.id ORDER BY hotness(u.upvotecount, minutes_age(submissions.created)) DESC LIMIT 30;"))

(defn find-submission
  [id]
  (let
    [result
       (try-db
         query
         "SELECT submissions.id, submissions.title, submissions.url, submissions.created, minutes_age(submissions.created) AS age, users.name, u.upvotecount FROM submissions JOIN users ON submissions.userid = users.id LEFT JOIN (SELECT upvotes.submissionid, COUNT(upvotes.id) AS upvotecount FROM upvotes GROUP BY upvotes.submissionid) AS u ON u.submissionid = submissions.id WHERE submissions.id = ?"
         id)]
    (if (empty? result) (throw+ {:type :state/empty-result}) (first result))))

(defn create-comment
  [user-id submission-id body]
  (try-db execute! "INSERT INTO comments (userid, submissionid, body) VALUES (?, ?, ?)" user-id submission-id body))

(defn find-comments
  [submission-id]
  (try-db
    query
    "SELECT comments.body, users.name, CAST (minutes_age(comments.created) AS INTEGER) AS age FROM comments JOIN users ON comments.userid = users.id WHERE comments.submissionid = ? ORDER BY comments.created DESC"
    submission-id))

(defn create-upvote
  [user-id submission-id]
  (try-db execute! "INSERT INTO upvotes (userid, submissionid) VALUES (?, ?)" user-id submission-id))
