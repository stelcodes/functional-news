(ns codes.stel.functional-news.handler-test
  (:require [clojure.test :refer :all]
            [codes.stel.functional-news.handler :as handler]))

(deftest hot-submissions-page-handler
  (let [response (handler/hot-submissions-page-handler {})]
    (testing "Do we get a map back" (is (map? response)))
    (testing "Do we get a 200 status" (is (= 200 (:status response))))))

(deftest new-submissions-page-handler
  (let [response (handler/new-submissions-page-handler {})]
    (testing "Do we get a map back" (is (map? response)))
    (testing "Do we get a 200 status" (is (= 200 (:status response))))))

(deftest login-page-handler
  (let [response (handler/login-page-handler {})]
    (testing "Do we get a map back" (is (map? response)))
    (testing "Do we get a 200 status" (is (= 200 (:status response))))))

(comment
  (run-tests))
