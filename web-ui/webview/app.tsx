import { lazy, Suspense, useEffect, useState } from "react";
import { ConnectedAppsView, TimelineView, useCollections } from "../src";

const PulseView = lazy(() =>
  import("../src/components/pulse/pulse-view").then((m) => ({
    default: m.PulseView,
  })),
);
const SpacesView = lazy(() =>
  import("../src/components/spaces/spaces-view").then((m) => ({
    default: m.SpacesView,
  })),
);

type Route = "timeline" | "pulse" | "spaces" | "connections";

function readRoute(): Route {
  const name = window.location.hash.replace(/^#\/?/, "");
  return name === "pulse" || name === "spaces" || name === "connections"
    ? name
    : "timeline";
}

const LAYERS: { id: Route; label: string }[] = [
  { id: "pulse", label: "Pulse" },
  { id: "connections", label: "Connected Apps" },
  { id: "spaces", label: "Spaces" },
];

function LayerSwitch({ route }: { route: Route }) {
  return (
    <nav className="flex shrink-0 gap-1 border-b border-white/[0.06] bg-ink/80 p-2">
      {LAYERS.map((layer) => (
        <button
          key={layer.id}
          type="button"
          onClick={() => {
            window.location.hash = `/${layer.id}`;
          }}
          className={
            route === layer.id
              ? "flex-1 rounded-md bg-white/10 px-3 py-1.5 text-xs font-medium text-white"
              : "flex-1 rounded-md px-3 py-1.5 text-xs font-medium text-white/50"
          }
        >
          {layer.label}
        </button>
      ))}
    </nav>
  );
}

function TimelineScreen() {
  const { collections } = useCollections(true);
  return <TimelineView collections={collections} />;
}

export function App() {
  const [route, setRoute] = useState<Route>(readRoute);

  useEffect(() => {
    const onChange = () => setRoute(readRoute());
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);

  return (
    <div className="flex h-full w-full flex-col pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)] pl-[env(safe-area-inset-left)] pr-[env(safe-area-inset-right)]">
      {route === "timeline" ? null : <LayerSwitch route={route} />}
      <Suspense fallback={null}>
        {route === "connections" ? (
          <ConnectedAppsView />
        ) : route === "pulse" ? (
          <PulseView />
        ) : route === "spaces" ? (
          <SpacesView />
        ) : (
          <TimelineScreen />
        )}
      </Suspense>
    </div>
  );
}
