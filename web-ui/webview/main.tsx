import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserPlatform, installPlatform } from "../src";
import { App } from "./app";
import { hostSession } from "./host";
import "./index.css";

const nativePlatform = createBrowserPlatform(hostSession);
nativePlatform.browser = {
  openExternal: async (url) => {
    const destination = new URL(url);
    if (
      destination.protocol !== "https:" ||
      destination.hostname !== "accounts.google.com" ||
      destination.pathname !== "/o/oauth2/v2/auth" ||
      destination.username !== "" || destination.password !== "" || destination.port !== ""
    )
      throw new Error("Unsupported authorization destination");
    if (!window.VoxHost) throw new Error("Vox host unavailable");
    window.VoxHost.openExternal(url);
  },
};
installPlatform(nativePlatform);

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
