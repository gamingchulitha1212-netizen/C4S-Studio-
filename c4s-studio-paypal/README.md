# C4S Studio — website with real PayPal Checkout

This is the C4S Studio site wired up to a working PayPal Checkout backend,
based on PayPal's official Standard Integration sample.

## What's in here

- `public/index.html` — the website (packages, cart, checkout, dark/light mode)
- `server.js` — Node/Express backend that talks to PayPal's Orders API
- `package.json` — backend dependencies
- `.env.example` — where your PayPal credentials go

The site is no longer a plain static file — it needs this backend running,
because PayPal's secret key can never sit in browser code.

## Setup

1. **Get PayPal credentials**
   - Sign up / log in at https://developer.paypal.com/dashboard/
   - Create a Sandbox app under **Apps & Credentials** to get a test
     **Client ID** and **Secret** (use this first — it lets you test payments
     with fake PayPal accounts before going live)

2. **Configure environment variables**
   ```bash
   cp .env.example .env
   ```
   Open `.env` and paste in your Client ID and Secret.

3. **Install dependencies**
   ```bash
   npm install
   ```

4. **Run it**
   ```bash
   npm start
   ```
   Visit **http://localhost:8080** — the site and the checkout will both work
   through this one server.

## Going live

- Once sandbox payments work end to end, create a **Live** app in the PayPal
  dashboard, swap the credentials in `.env`, and set `PAYPAL_ENV=live`.
- Deploy this whole folder to any host that runs Node.js (Render, Railway,
  a VPS, etc.) — a plain static host (like GitHub Pages) won't work since the
  backend needs to run continuously.
- Point your domain (e.g. c4sstudio.lk) at that host.

## About pricing

Packages are priced in **USD** in `server.js` (`PACKAGES` object) because
PayPal does not let Sri Lankan accounts receive payments in LKR. The LKR
prices shown on the site are for display — update the USD amounts in
`server.js` if your exchange-rate assumption changes, and keep both numbers
roughly in sync.

## Security note

The server — not the browser — decides the price of each package
(see `PACKAGES` in `server.js`). This stops someone from tampering with the
page and checking out for a different amount.
