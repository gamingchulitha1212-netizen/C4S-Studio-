import express from "express";
import "dotenv/config";
import path from "path";
import { fileURLToPath } from "url";
import bodyParser from "body-parser";
import {
  ApiError,
  CheckoutPaymentIntent,
  Client,
  Environment,
  LogLevel,
  OrdersController,
} from "@paypal/paypal-server-sdk";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
app.use(bodyParser.json());
app.use(express.static(path.join(__dirname, "public")));

const {
  PAYPAL_CLIENT_ID,
  PAYPAL_CLIENT_SECRET,
  PAYPAL_ENV = "sandbox",
  PORT = 8080,
} = process.env;

if (!PAYPAL_CLIENT_ID || !PAYPAL_CLIENT_SECRET) {
  console.warn(
    "⚠️  PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET are not set. Copy .env.example to .env and fill them in."
  );
}

const client = new Client({
  clientCredentialsAuthCredentials: {
    oAuthClientId: PAYPAL_CLIENT_ID,
    oAuthClientSecret: PAYPAL_CLIENT_SECRET,
  },
  timeout: 0,
  environment: PAYPAL_ENV === "live" ? Environment.Production : Environment.Sandbox,
  logging: {
    logLevel: LogLevel.Info,
    logRequest: { logBody: true },
    logResponse: { logHeaders: true },
  },
});

const ordersController = new OrdersController(client);

/**
 * Package catalog lives on the SERVER, not the browser.
 * Never trust a price sent from the client — always recompute it here.
 * PayPal cannot settle in LKR for Sri Lankan accounts, so packages are
 * priced in USD. Update these numbers (and review the LKR prices shown
 * on the site) whenever your exchange rate assumption changes.
 */
const PACKAGES = {
  portrait: { name: "Portrait Session", usd: 50.0 },
  wedding: { name: "Wedding Coverage", usd: 315.0 },
  event: { name: "Event & Product Shoot", usd: 93.0 },
};

function calculateTotal(cart) {
  let total = 0;
  const lines = [];
  for (const line of cart || []) {
    const pkg = PACKAGES[line.id];
    if (!pkg) continue;
    const qty = Math.max(1, parseInt(line.quantity, 10) || 1);
    total += pkg.usd * qty;
    lines.push({ id: line.id, name: pkg.name, qty, unitUsd: pkg.usd });
  }
  return { total: Math.round(total * 100) / 100, lines };
}

// Lets the frontend fetch the PUBLIC client id at runtime — the secret never leaves the server.
app.get("/api/config", (req, res) => {
  res.json({ clientId: PAYPAL_CLIENT_ID || "" });
});

/**
 * Create an order to start the transaction.
 * @see https://developer.paypal.com/docs/api/orders/v2/#orders_create
 */
const createOrder = async (cart) => {
  const { total, lines } = calculateTotal(cart);
  if (total <= 0) {
    throw new Error("Cart is empty or contains no valid packages.");
  }

  const collect = {
    body: {
      intent: CheckoutPaymentIntent.Capture,
      purchaseUnits: [
        {
          amount: {
            currencyCode: "USD",
            value: total.toFixed(2),
          },
          description: lines.map((l) => `${l.name} x${l.qty}`).join(", ").slice(0, 127),
        },
      ],
    },
    prefer: "return=minimal",
  };

  try {
    const { body, ...httpResponse } = await ordersController.createOrder(collect);
    return {
      jsonResponse: JSON.parse(body),
      httpStatusCode: httpResponse.statusCode,
    };
  } catch (error) {
    if (error instanceof ApiError) {
      throw new Error(error.message);
    }
    throw error;
  }
};

/**
 * Capture payment for the created order to complete the transaction.
 * @see https://developer.paypal.com/docs/api/orders/v2/#orders_capture
 */
const captureOrder = async (orderID) => {
  const collect = { id: orderID, prefer: "return=minimal" };

  try {
    const { body, ...httpResponse } = await ordersController.captureOrder(collect);
    return {
      jsonResponse: JSON.parse(body),
      httpStatusCode: httpResponse.statusCode,
    };
  } catch (error) {
    if (error instanceof ApiError) {
      throw new Error(error.message);
    }
    throw error;
  }
};

app.post("/api/orders", async (req, res) => {
  try {
    const { cart } = req.body;
    const { jsonResponse, httpStatusCode } = await createOrder(cart);
    res.status(httpStatusCode).json(jsonResponse);
  } catch (error) {
    console.error("Failed to create order:", error);
    res.status(500).json({ error: error.message || "Failed to create order." });
  }
});

app.post("/api/orders/:orderID/capture", async (req, res) => {
  try {
    const { orderID } = req.params;
    const { jsonResponse, httpStatusCode } = await captureOrder(orderID);
    res.status(httpStatusCode).json(jsonResponse);
  } catch (error) {
    console.error("Failed to capture order:", error);
    res.status(500).json({ error: error.message || "Failed to capture order." });
  }
});

app.listen(PORT, () => {
  console.log(`C4S Studio server listening at http://localhost:${PORT}/`);
});
