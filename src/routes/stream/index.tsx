import { createFileRoute } from "@tanstack/react-router";
import { Radio } from "lucide-react";
import OnlineSearchSection from "@/features/online/components/OnlineSearchSection";

export const Route = createFileRoute("/stream/")({
  component: StreamRouteComponent,
});

function StreamRouteComponent() {
  return (
    <div className="w-full h-full overflow-y-auto px-6 pt-20 pb-36 space-y-6">
      <div className="max-w-4xl mx-auto space-y-2">
        <div className="flex items-center gap-2.5">
          <div className="size-9 rounded-xl bg-primary/15 flex items-center justify-center text-primary">
            <Radio className="size-5" />
          </div>
          <div>
            <h1 className="text-2xl font-bold font-heading tracking-tight flex items-center gap-2">
              Online Music & Streams
            </h1>
            <p className="text-xs text-muted-foreground">
              Search YouTube music or paste Spotify links to stream online music alongside your offline library.
            </p>
          </div>
        </div>
      </div>

      <OnlineSearchSection />
    </div>
  );
}

export default StreamRouteComponent;
