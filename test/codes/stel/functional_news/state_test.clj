(ns codes.stel.functional-news.state-test
  (:require [clojure.test :refer :all]
            [codes.stel.functional-news.state :as state]
            [malli.core :as m]))

(comment
  (run-tests))

(def valid-submission? (m/validator [:map [:submissions/id int?] [:submissions/title string?] [:submissions/url string?]]))

(deftest find-user
  (testing "An invalid id will throw error" (is (thrown? clojure.lang.ExceptionInfo (state/find-user 2343242034))))
  (testing "An string id will throw error" (is (thrown? clojure.lang.ExceptionInfo (state/find-user "test")))))

(deftest get-new-submissions
  (let [results (state/get-new-submissions)
        first-submission (first results)]
    (testing "First item is a valid submission" (is (valid-submission? first-submission)))
    (testing "Will return a vector" (is (vector? results)))))

