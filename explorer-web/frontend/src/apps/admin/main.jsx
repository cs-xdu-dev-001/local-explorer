import React from "react";
import { createRoot } from "react-dom/client";
import "../../styles/app.css";
import { AdminApp } from "./AdminApp.jsx";

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <AdminApp />
  </React.StrictMode>
);
