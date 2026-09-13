import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = (__ENV.BASE_URL || "").replace(/\/$/, "");
const customerToken = __ENV.CUSTOMER_TOKEN || "";
const sellerToken = __ENV.SELLER_TOKEN || "";
const storeId = __ENV.STORE_ID || "";
const city = encodeURIComponent(__ENV.CITY || "Ankara");
const district = encodeURIComponent(__ENV.DISTRICT || "Yenimahalle");

if (!baseUrl.startsWith("https://")) {
  throw new Error("BASE_URL internetten erişilebilir bir HTTPS adresi olmalı.");
}
if ((sellerToken && !storeId) || (!sellerToken && storeId)) {
  throw new Error("SELLER_TOKEN ve STORE_ID birlikte verilmelidir.");
}

const scenarios = {
  public_discovery: {
    executor: "constant-vus",
    exec: "publicDiscovery",
    vus: Number(__ENV.PUBLIC_VUS || 10),
    duration: __ENV.DURATION || "2m",
  },
};

if (customerToken) {
  scenarios.customer_reads = {
    executor: "constant-vus",
    exec: "customerReads",
    vus: Number(__ENV.CUSTOMER_VUS || 5),
    duration: __ENV.DURATION || "2m",
  };
}

if (sellerToken && storeId) {
  scenarios.seller_reads = {
    executor: "constant-vus",
    exec: "sellerReads",
    vus: Number(__ENV.SELLER_VUS || 5),
    duration: __ENV.DURATION || "2m",
  };
}

export const options = {
  scenarios,
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500", "p(99)<1000"],
  },
  discardResponseBodies: true,
};

function get(path, params, name) {
  const response = http.get(`${baseUrl}${path}`, {
    ...params,
    tags: { endpoint: name },
    timeout: "10s",
  });
  check(response, { [`${name} HTTP 200`]: (result) => result.status === 200 });
}

function authParams(token) {
  return { headers: { Authorization: `Bearer ${token}` } };
}

export function publicDiscovery() {
  get("/api/actuator/health/readiness", {}, "readiness");
  get(
    `/api/v1/stores?city=${city}&district=${district}&page=0&size=20`,
    {},
    "store-discovery",
  );
  get("/api/v1/stores/discovery-metadata", {}, "discovery-metadata");
  sleep(1);
}

export function customerReads() {
  const params = authParams(customerToken);
  get("/api/v1/subscriptions?page=0&size=20", params, "customer-subscriptions");
  get("/api/v1/notifications?page=0&size=20", params, "customer-notifications");
  get("/api/v1/payments/history", params, "customer-payments");
  sleep(1);
}

export function sellerReads() {
  const params = authParams(sellerToken);
  get(
    `/api/v1/seller/subscriptions/stores/${storeId}?page=0&size=20`,
    params,
    "seller-subscriptions",
  );
  get(
    `/api/v1/seller/subscriptions/stores/${storeId}/extension-requests?page=0&size=20`,
    params,
    "seller-extension-requests",
  );
  get(
    `/api/v1/seller/subscriptions/stores/${storeId}/delivery-change-requests?page=0&size=20`,
    params,
    "seller-delivery-change-requests",
  );
  sleep(1);
}
