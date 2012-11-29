(ns shopify.test.util.auth
  (:use clojure.test
        shopify.util.auth))

(def default-api-client
  {:url "http://shopify-test.heroku.com"
   :key "01abfc750a0c942167651c40d088531d"
   :secret "dc2e817cb95adce7164db4767a13a53f"
   :scope [:read_orders, :write_content]})

(deftest verify-params-test
  (testing "valid params"
    (let [params {
            :shop       "xerxes.myshopify.com"
            :code       "78415eb05dd9fc31283063c71952303c"
            :timestamp  "1349400031"
            :signature  "1dfa3f92d9500dbccf3aa5671bb8e78c"}]
    (is (= true
      (verify-params default-api-client params)))))
  (testing "invalid params"
    (let [params {
            :shop       "another-shop.myshopify.com"
            :code       "78415eb05dd9fc31283063c71952303c"
            :timestamp  "1349400031"
            :signature  "1dfa3f92d9500dbccf3aa5671bb8e78c"}]
    (is (= false
      (verify-params default-api-client params))))))
