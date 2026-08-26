import { resolve } from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const adminPages = [
  "console/index.html",
  "console/login.html",
  "console/merchant.html",
  "console/categories.html",
  "console/items.html",
  "console/packages.html",
  "console/orders.html",
  "console/reviews.html",
  "console/export-jobs.html",
  "console/users.html",
  "console/employees.html",
  "console/operation-logs.html"
];

const clientPages = [
  "client/index.html",
  "client/login.html",
  "client/history.html",
  "client/favorites.html",
  "client/my-orders.html"
];

const backendTarget = "http://localhost:8080";

function entries(pages) {
  return Object.fromEntries(
    pages.map((page) => [page.replace(".html", ""), resolve(__dirname, page)])
  );
}

export default defineConfig({
  base: "./",
  plugins: [react()],
  server: {
    proxy: {
      "/admin": backendTarget,
      "/user": backendTarget
    }
  },
  build: {
    outDir: resolve(__dirname, "../src/main/resources/static"),
    emptyOutDir: true,
    assetsDir: "assets/app",
    rollupOptions: {
      input: {
        ...entries(adminPages),
        ...entries(clientPages)
      }
    }
  }
});
