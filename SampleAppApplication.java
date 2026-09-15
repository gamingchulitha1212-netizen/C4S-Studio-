package com.paypal.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import com.paypal.sdk.controllers.OrdersController;
import com.paypal.sdk.exceptions.ApiException;
import com.paypal.sdk.http.response.ApiResponse;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.Order;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.CaptureOrderInput;
import com.paypal.sdk.models.PurchaseUnitRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.event.Level;

@SpringBootApplication
public class SampleAppApplication {

	@Value("${PAYPAL_CLIENT_ID}")
	private String PAYPAL_CLIENT_ID;

	@Value("${PAYPAL_CLIENT_SECRET}")
	private String PAYPAL_CLIENT_SECRET;

	@Value("${PAYPAL_ENV:sandbox}")
	private String PAYPAL_ENV;

	public static void main(String[] args) {
		SpringApplication.run(SampleAppApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	public PaypalServerSdkClient paypalClient() {
		return new PaypalServerSdkClient.Builder()
				.loggingConfig(builder -> builder
						.level(Level.DEBUG)
						.requestConfig(logConfigBuilder -> logConfigBuilder.body(true))
						.responseConfig(logConfigBuilder -> logConfigBuilder.headers(true)))
				.httpClientConfig(configBuilder -> configBuilder
						.timeout(0))
				.environment("live".equalsIgnoreCase(PAYPAL_ENV) ? Environment.PRODUCTION : Environment.SANDBOX)
				.clientCredentialsAuth(new ClientCredentialsAuthModel.Builder(
						PAYPAL_CLIENT_ID,
						PAYPAL_CLIENT_SECRET)
						.build())
				.build();
	}

	/**
	 * Package catalog lives on the SERVER, not the browser.
	 * Never trust a price sent from the client — always recompute it here.
	 * PayPal cannot settle in LKR for Sri Lankan accounts, so packages are
	 * priced in USD. Update these numbers (and review the LKR prices shown
	 * on the site) whenever your exchange rate assumption changes.
	 */
	static class Pkg {
		final String name;
		final BigDecimal usd;
		Pkg(String name, String usd) {
			this.name = name;
			this.usd = new BigDecimal(usd);
		}
	}

	static final Map<String, Pkg> PACKAGES = new HashMap<>();
	static {
		PACKAGES.put("portrait", new Pkg("Portrait Session", "50.00"));
		PACKAGES.put("wedding", new Pkg("Wedding Coverage", "315.00"));
		PACKAGES.put("event", new Pkg("Event & Product Shoot", "93.00"));
	}

	@RestController
	@RequestMapping("/api")
	public class CheckoutController {

		private final ObjectMapper objectMapper;
		private final PaypalServerSdkClient client;

		public CheckoutController(ObjectMapper objectMapper, PaypalServerSdkClient client) {
			this.objectMapper = objectMapper;
			this.client = client;
		}

		// Lets the frontend fetch the PUBLIC client id at runtime — the secret never leaves the server.
		@GetMapping("/config")
		public Map<String, String> config() {
			return Collections.singletonMap("clientId", PAYPAL_CLIENT_ID == null ? "" : PAYPAL_CLIENT_ID);
		}

		@PostMapping("/orders")
		public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> request) {
			try {
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> cart = (List<Map<String, Object>>) request.get("cart");
				Order response = createOrder(cart);
				return new ResponseEntity<>(response, HttpStatus.OK);
			} catch (IllegalArgumentException e) {
				return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
			} catch (Exception e) {
				e.printStackTrace();
				return new ResponseEntity<>(
						Collections.singletonMap("error", "Failed to create order."),
						HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		@PostMapping("/orders/{orderID}/capture")
		public ResponseEntity<?> captureOrder(@PathVariable String orderID) {
			try {
				Order response = captureOrders(orderID);
				return new ResponseEntity<>(response, HttpStatus.OK);
			} catch (Exception e) {
				e.printStackTrace();
				return new ResponseEntity<>(
						Collections.singletonMap("error", "Failed to capture order."),
						HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		private BigDecimal calculateTotal(List<Map<String, Object>> cart) {
			BigDecimal total = BigDecimal.ZERO;
			if (cart != null) {
				for (Map<String, Object> line : cart) {
					Object idObj = line.get("id");
					if (idObj == null) continue;
					Pkg pkg = PACKAGES.get(String.valueOf(idObj));
					if (pkg == null) continue;

					int qty = 1;
					Object qtyObj = line.get("quantity");
					if (qtyObj != null) {
						try {
							qty = Math.max(1, Integer.parseInt(String.valueOf(qtyObj)));
						} catch (NumberFormatException ignored) {
							// keep qty = 1
						}
					}
					total = total.add(pkg.usd.multiply(BigDecimal.valueOf(qty)));
				}
			}
			return total.setScale(2, RoundingMode.HALF_UP);
		}

		private Order createOrder(List<Map<String, Object>> cart) throws IOException, ApiException {
			BigDecimal total = calculateTotal(cart);
			if (total.compareTo(BigDecimal.ZERO) <= 0) {
				throw new IllegalArgumentException("Cart is empty or contains no valid packages.");
			}

			CreateOrderInput createOrderInput = new CreateOrderInput.Builder(
					null,
					new OrderRequest.Builder(
							CheckoutPaymentIntent.CAPTURE,
							Arrays.asList(
									new PurchaseUnitRequest.Builder(
											new AmountWithBreakdown.Builder(
													"USD",
													total.toPlainString())
													.build())
											.build()))
							.build())
					.build();

			OrdersController ordersController = client.getOrdersController();
			ApiResponse<Order> apiResponse = ordersController.createOrder(createOrderInput);
			return apiResponse.getResult();
		}

		private Order captureOrders(String orderID) throws IOException, ApiException {
			CaptureOrderInput ordersCaptureInput = new CaptureOrderInput.Builder(
					orderID,
					null)
					.build();
			OrdersController ordersController = client.getOrdersController();
			ApiResponse<Order> apiResponse = ordersController.captureOrder(ordersCaptureInput);
			return apiResponse.getResult();
		}
	}
}
