(ns shopify.auth-test
  (:use clojure.test
        shopify.auth))

(deftest verify-params-test
  (testing "valid params"
    (let [secret "dc2e817cb95adce7164db4767a13a53f"
          params {
            :shop       "xerxes.myshopify.com"
            :code       "78415eb05dd9fc31283063c71952303c"
            :timestamp  "1349400031"
            :signature  "1dfa3f92d9500dbccf3aa5671bb8e78c"}]
    (is (= true
      (verify-params secret params)))))
  (testing "invalid params"
    (let [secret "dc2e817cb95adce7164db4767a13a53f"
          params {
            :shop       "another-shop.myshopify.com"
            :code       "78415eb05dd9fc31283063c71952303c"
            :timestamp  "1349400031"
            :signature  "1dfa3f92d9500dbccf3aa5671bb8e78c"}]
    (is (= false
      (verify-params secret params))))))
