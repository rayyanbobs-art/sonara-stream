import { useRef } from "react";
import { ChevronLeft } from "lucide-react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { platform } from "@tauri-apps/plugin-os";
import { Button } from "@/components/ui/button";
import { useCanGoBack, useRouter } from "@tanstack/react-router";
import SearchDialog from "@/features/search/components/SearchDialog";
import ImportButton from "@/features/import/components/ImportButton";

const AppHeader = () => {
  const router = useRouter();
  const canGoBack = useCanGoBack();
  const containerRef = useRef<HTMLDivElement>(null);
  const appWindow = getCurrentWindow();

  const handleBack = () => {
    if (canGoBack) {
      router.history.back();
    } else {
      router.navigate({ to: "/" });
    }
  };

  const currentPlatform = platform();
  const isMacOS = currentPlatform === "macos";

  const handler = async (e: React.MouseEvent<HTMLDivElement, MouseEvent>) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.buttons === 1) {
      if (e.detail === 2) {
        await appWindow.toggleMaximize();
      } else {
        await appWindow.startDragging();
      }
    }
  };

  return (
    <header
      ref={containerRef}
      onMouseDown={isMacOS ? handler : undefined}
      data-tauri-drag-region={isMacOS}
      style={{
        top: "max(0.5rem, env(safe-area-inset-top, 0px))",
      }}
      className="h-14 px-2.5 sm:px-3 py-2 fixed right-2 left-2 md:left-64 rounded-2xl md:rounded-3xl shadow-md border border-muted-foreground/30 bg-muted/90 md:bg-muted/50 dark:bg-sidebar/90 md:dark:bg-sidebar/50 backdrop-blur-lg z-20 flex items-center justify-between gap-2 overflow-hidden"
    >
      <div className="flex items-center gap-2 sm:gap-3 flex-1 min-w-0">
        <Button
          variant="outline"
          className="border border-muted-foreground/30 shrink-0 size-9 rounded-xl"
          size="icon"
          onClick={handleBack}
        >
          <ChevronLeft className="size-4" />
        </Button>
        <SearchDialog />
      </div>
      <div className="shrink-0 flex items-center">
        <ImportButton />
      </div>
    </header>
  );
};
export default AppHeader;
