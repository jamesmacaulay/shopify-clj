# shopify-friend

Provides a friend workflow for authorizing Shopify apps with OAuth2.

## Usage

Something like this:

```clojure
(def shopify-api-client
  {:key (System/getenv "SHOPIFY_API_KEY")
   :secret (System/getenv "SHOPIFY_API_SECRET")
   :url "http://my-shopify-app.com"
   :scope [:read_products :read_orders]})

(def ring-app
  (-> app-routes
    (friend/authenticate {
      :allow-anon? true
      :workflows [
        (shopify.auth/workflow
          {:api-client shopify-api-client})]})
    (handler/site)))
```

## License

Copyright © 2012 Shopify

Distributed under the Eclipse Public License, the same as Clojure.
