import React from "react";
import { createRoot } from "react-dom/client";
import "../../styles/app.css";
import { ClientApp } from "./ClientApp.jsx";

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ClientApp />
  </React.StrictMode>
);
