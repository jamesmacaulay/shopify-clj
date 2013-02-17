(ns shopify.util-test
  (:use clojure.test
        shopify.util))

(deftest name-str-test
  (testing "(name-str :keyword) returns the keyword's `name`."
    (is (= "foo"
           (name-str :foo))))
  (testing "(name-str \"string\") returns the string."
    (is (= "foo"
           (name-str "foo"))))
  (testing "name-str returns the `str` value of the argument by default."
    (is (= "42"
           (name-str 42)))))

(deftest partition-keys-test
  (testing "partition-keys takes a map and a predicate which is applied to the keys to split the map in two"
    (is (= [{:a 1 :c 3}
            {:b 2}]
           (partition-keys {:a 1 :b 2 :c 3}
                           #{:a :c})))))
