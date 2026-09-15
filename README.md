# C4S Studio — website with real PayPal Checkout (Java backend)

This is the C4S Studio site wired up to a working PayPal Checkout backend,
built with Spring Boot, based on PayPal's official Java Standard Integration
sample.

## What's in here

- `src/main/resources/static/index.html` — the website (packages, cart,
  checkout, dark/light mode). Spring Boot serves this automatically.
- `src/main/java/.../SampleAppApplication.java` — the backend that talks to
  PayPal's Orders API
- `pom.xml` — Maven dependencies
- `src/main/resources/application.properties` — reads your PayPal
  credentials from environment variables

## Requirements

- **Java 21** and **Maven** installed (check with `java -version` and
  `mvn -version`)

## Setup

1. **Get PayPal credentials**
   - Sign up / log in at https://developer.paypal.com/dashboard/
   - Create a Sandbox app under **Apps & Credentials** to get a test
     **Client ID** and **Secret** (test with fake PayPal accounts before
     going live)

2. **Set your credentials as environment variables**

   - **Linux / macOS**
     ```bash
     export PAYPAL_CLIENT_ID="your_sandbox_client_id"
     export PAYPAL_CLIENT_SECRET="your_sandbox_client_secret"
     ```

   - **Windows (PowerShell)**
     ```powershell
     $env:PAYPAL_CLIENT_ID = "your_sandbox_client_id"
     $env:PAYPAL_CLIENT_SECRET = "your_sandbox_client_secret"
     ```

3. **Build the server**
   ```bash
   mvn clean install
   ```

4. **Run the server**
   ```bash
   mvn spring-boot:run
   ```

5. Visit **http://localhost:8080** — the site and the checkout both run
   through this one server.

## Going live

- Once sandbox payments work end to end, create a **Live** app in the PayPal
  dashboard, swap the environment variables for your live credentials, and
  set `PAYPAL_ENV=live` before starting the server.
- Deploy the built jar (`mvn clean package` → `target/*.jar`, run with
  `java -jar target/*.jar`) to any host that runs Java — a plain static host
  won't work since the backend needs to run continuously.
- Point your domain (e.g. c4sstudio.lk) at that host.

## About pricing

Packages are priced in **USD** in `SampleAppApplication.java` (the
`PACKAGES` map) because PayPal does not let Sri Lankan accounts receive
payments in LKR. The LKR prices shown on the site are for display — update
the USD amounts in that file if your exchange-rate assumption changes.

## Security note

The server — not the browser — decides the price of each package
(see `PACKAGES` and `calculateTotal` in `SampleAppApplication.java`). This
stops someone from tampering with the page and checking out for a
different amount.
