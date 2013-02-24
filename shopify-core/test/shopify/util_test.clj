(ns shopify.util-test
  (:use clojure.test
        shopify.util))

(deftest as-namespaced-str-test
  (testing "(as-namespaced-str :keyword) returns the keyword's `name`."
    (is (= "foo"
           (as-namespaced-str :foo))))
  (testing "(as-namespaced-str \"string\") returns the string."
    (is (= "foo"
           (as-namespaced-str "foo"))))
  (testing "as-namespaced-str returns the `str` value of the argument by default."
    (is (= "42"
           (as-namespaced-str 42))))
  (testing "as-namespaced-str returns the namespace and name of a namespaced keyword"
    (is (= "foo/bar"
           (as-namespaced-str :foo/bar)))))

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
