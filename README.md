# shopify-clj

A set of libraries for interacting with the [Shopify][shopify] platform:

* `shopify-friend`: provides a [friend][friend] workflow to [authenticate with Shopify shops][auth-docs] using OAuth2.
* `shopify-resources`: functions for interacting with [a shop's resources][resource-docs].
* `shopify-core`: functions used by the other libraries, probably not very useful on its own.

[shopify]: http://www.shopify.com/
[friend]: https://github.com/cemerick/friend
[auth-docs]: http://api.shopify.com/authentication.html
[resource-docs]: http://api.shopify.com/

## Installation

Add the following to your `:dependencies`:

```
[shopify "0.1.0-SNAPSHOT"]
```

Or if you just need one of the libraries, you can do that too:

```
[shopify/shopify-friend "0.1.0-SNAPSHOT"]
```

## License

Copyright © 2012 Shopify

Distributed under the Eclipse Public License, the same as Clojure.
