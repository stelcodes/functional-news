(ns codes.stel.functional-news.state
  (:require [next.jdbc.sql :refer [query]]
            [next.jdbc :refer [execute! execute-one!]]
            [failjure.core :as f]
            [cuter-news.util :refer [generate-username]]))

(defn find-user
  [conn id]
  (let [result (query conn ["SELECT * FROM users WHERE id = ?" id])]
    (if (empty? result) (f/fail "User not found") (first result))))

(defn find-username
  [conn username]
  (let [result (query conn ["SELECT * FROM users WHERE name = ?" username])]
    (if (empty? result) (f/fail "User not found") (first result))))

(defn create-user
  [conn email password]
  (let [username (loop [username (generate-username)]
                   (if (f/failed? (find-username conn username)) username (recur (generate-username))))]
    (f/try*
      (-> (execute! conn
                    ["INSERT INTO users (name, email, password) VALUES (?, ?, crypt(?, gen_salt('bf', 8))) RETURNING *"
                     username email password])
          (first)))))

(defn auth-user
  [conn email password]
  (let [result (query conn ["SELECT * FROM users WHERE email = ? AND password = crypt(?, password)" email password])]
    (if (empty? result) (f/fail "Email or password incorrect") (first result))))

(defn create-submission
  [conn title url user-id]
  (f/try* (let [submission (execute-one! conn
                                         ["INSERT INTO submissions (title, url, userid) VALUES (?, ?, ?) RETURNING *"
                                          title url user-id])
                submission-id (:submissions/id submission)
                upvote (execute! conn
                                 ["INSERT INTO upvotes (userid, submissionid) VALUES (?, ?) RETURNING *" user-id
                                  submission-id])]
            submission)))

(defn get-new-submissions
  [conn]
  (query
    conn
    ["SELECT submissions.id, submissions.title, submissions.url, submissions.created, minutes_age(submissions.created) AS age, users.name, c.commentcount, u.upvotecount FROM submissions JOIN users ON submissions.userid = users.id LEFT JOIN (SELECT comments.submissionid, COUNT(comments.id) AS commentcount FROM comments GROUP BY comments.submissionid) AS c ON c.submissionid = submissions.id LEFT JOIN (SELECT upvotes.submissionid, COUNT(upvotes.id) AS upvotecount FROM upvotes GROUP BY upvotes.submissionid) AS u ON u.submissionid = submissions.id ORDER BY submissions.created DESC LIMIT 30"]))

(defn get-hot-submissions
  [conn]
  (query
    conn
    ["SELECT submissions.id, submissions.title, submissions.url, submissions.created, minutes_age(submissions.created) AS age, users.name, c.commentcount, u.upvotecount FROM submissions JOIN users ON submissions.userid = users.id LEFT JOIN (SELECT comments.submissionid, COUNT(comments.id) AS commentcount FROM comments GROUP BY comments.submissionid) AS c ON c.submissionid = submissions.id LEFT JOIN (SELECT upvotes.submissionid, COUNT(upvotes.id) AS upvotecount FROM upvotes GROUP BY upvotes.submissionid) AS u ON u.submissionid = submissions.id ORDER BY hotness(u.upvotecount, minutes_age(submissions.created)) DESC LIMIT 30;"]))

(defn find-submission
  [conn id]
  (let
    [result
       (query
         conn
         ["SELECT submissions.id, submissions.title, submissions.url, submissions.created, minutes_age(submissions.created) AS age, users.name, u.upvotecount FROM submissions JOIN users ON submissions.userid = users.id LEFT JOIN (SELECT upvotes.submissionid, COUNT(upvotes.id) AS upvotecount FROM upvotes GROUP BY upvotes.submissionid) AS u ON u.submissionid = submissions.id WHERE submissions.id = ?"
          id])]
    (if (empty? result) (f/fail "Submission not found") (first result))))

(defn create-comment
  [conn user-id submission-id body]
  (f/try* (execute! conn
                    ["INSERT INTO comments (userid, submissionid, body) VALUES (?, ?, ?)" user-id submission-id body])))

(defn find-comments
  [conn submission-id]
  (query
    conn
    ["SELECT comments.body, users.name, CAST (minutes_age(comments.created) AS INTEGER) AS age FROM comments JOIN users ON comments.userid = users.id WHERE comments.submissionid = ? ORDER BY comments.created DESC"
     submission-id]))

(defn create-upvote
  [conn user-id submission-id]
  (f/try* (execute! conn ["INSERT INTO upvotes (userid, submissionid) VALUES (?, ?)" user-id submission-id])))
