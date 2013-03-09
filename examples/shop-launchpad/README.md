# shop-launchpad

A simple Shopify app which lets you authenticate with multiple shops.

## Prerequisites

[Leiningen](https://github.com/technomancy/leiningen) and some [Shopify API credentials](http://docs.shopify.com/api/the-basics/getting-started).

## Running

Run a server with `lein ring server`. You'll need to set environment variables for your app's API key and shared secret, which you can do inline with the server command:

    SHOPIFY_API_KEY=12345 SHOPIFY_API_SECRET=12345 lein ring server

Once the server is running, a browser window should open automatically on http://localhost:3000.

## License

Copyright © 2013 James MacAulay
