import { createFileRoute } from "@tanstack/react-router";
import { Radio } from "lucide-react";
import OnlineSearchSection from "@/features/online/components/OnlineSearchSection";

export const Route = createFileRoute("/stream/")({
  component: StreamRouteComponent,
});

function StreamRouteComponent() {
  return (
    <div className="w-full h-full overflow-y-auto px-3 sm:px-6 pt-3 sm:pt-6 md:pt-20 pb-32 sm:pb-36 space-y-6 custom-scrollbar">
      <div className="max-w-7xl mx-auto space-y-2">
        <div className="flex items-center gap-3">
          <div className="size-10 sm:size-11 rounded-2xl bg-primary/15 flex items-center justify-center text-primary shadow-sm shadow-primary/20">
            <Radio className="size-5 sm:size-6" />
          </div>
          <div>
            <h1 className="text-xl sm:text-2xl font-bold font-heading tracking-tight flex items-center gap-2">
              Search & Browse
            </h1>
            <p className="text-xs text-muted-foreground">
              Search online streams or explore curated genres and vibes.
            </p>
          </div>
        </div>
      </div>

      <OnlineSearchSection />
    </div>
  );
}

export default StreamRouteComponent;
