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

(deftest dashes->underscores-test
  (testing "dashes->underscores replaces all dashes"
    (is (= "foo_bar_baz"
           (dashes->underscores "foo-bar-baz"))))
  (testing "dashes->underscores works on keywords and returns a string"
    (is (= "foo_bar_baz"
           (dashes->underscores :foo-bar-baz)))))

(deftest underscores->dashes-test
  (testing "underscores->dashes replaces all dashes"
    (is (= "foo-bar-baz"
           (underscores->dashes "foo_bar_baz"))))
  (testing "underscores->dashes works on keywords and returns a string"
    (is (= "foo-bar-baz"
           (underscores->dashes :foo_bar_baz)))))

(deftest partition-keys-test
  (testing "partition-keys takes a map and a predicate which is applied to the keys to split the map in two"
    (is (= [{:a 1 :c 3}
            {:b 2}]
           (partition-keys {:a 1 :b 2 :c 3}
                           #{:a :c})))))
