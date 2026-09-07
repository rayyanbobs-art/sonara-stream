import { Plus } from "lucide-react";
import { open } from "@tauri-apps/plugin-dialog";
import { Button } from "@/components/ui/button";
import useImportFilesMutation from "@/features/import/api/useImportFilesMutation";
import LoadingOverlay from "@/components/custom/LoadingOverlay";

const ImportButton = () => {
  const mutation = useImportFilesMutation();

  const handleFolderSelection = async () => {
    // Implementation for folder selection
    const path = await open({
      multiple: false,
      directory: true,
    });
    if (path) {
      mutation.mutate(path);
    }
  };
  return (
    <>
      <Button
        variant="outline"
        className="border border-muted-foreground/30 h-9 px-2.5 sm:px-3.5 flex items-center gap-1.5 shrink-0 rounded-xl"
        onClick={handleFolderSelection}
        title="Import music folders"
      >
        <Plus className="size-4 shrink-0" />
        <span className="hidden sm:inline text-xs font-heading font-medium text-foreground">Import</span>
      </Button>
      <LoadingOverlay open={mutation.isPending} />
    </>
  );
};
export default ImportButton;
