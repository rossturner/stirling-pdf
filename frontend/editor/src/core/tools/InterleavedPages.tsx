import { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useAllFiles, useFileManagement } from "@app/contexts/FileContext";
import { StirlingFile } from "@app/types/fileContext";
import { createToolFlow } from "@app/components/tools/shared/createToolFlow";
import { useInterleavedPagesParameters } from "@app/hooks/tools/interleavedPages/useInterleavedPagesParameters";
import { useInterleavedPagesOperation } from "@app/hooks/tools/interleavedPages/useInterleavedPagesOperation";
import { useBaseTool } from "@app/hooks/tools/shared/useBaseTool";
import { BaseToolProps, type ToolComponent } from "@app/types/tool";
import InterleavedPagesSettings from "@app/components/tools/interleavedPages/InterleavedPagesSettings";
import { useInterleavedPagesTips } from "@app/components/tooltips/useInterleavedPagesTips";

const InterleavedPages = (props: BaseToolProps) => {
  const { t } = useTranslation();
  const tips = useInterleavedPagesTips();
  const { fileIds } = useAllFiles();
  const { reorderFiles, removeFiles } = useFileManagement();
  const base = useBaseTool(
    "interleavedPages",
    useInterleavedPagesParameters,
    useInterleavedPagesOperation,
    props
  );

  const handleFilesChange = useCallback(
    (files: File[]) => {
      const orderedIds = (files as StirlingFile[]).map((f) => f.fileId);
      const remainingIds = fileIds.filter((id) => !orderedIds.includes(id));
      reorderFiles([...orderedIds, ...remainingIds]);
    },
    [fileIds, reorderFiles],
  );

  const handleRemoveFile = useCallback(
    (index: number) => {
      const file = base.selectedFiles[index];
      if (file) void removeFiles([file.fileId]);
    },
    [base.selectedFiles, removeFiles],
  );

  return createToolFlow({
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
    },
    steps: [
      {
        title: t("interleavedPages.steps.settings", "Reorder Images"),
        tooltip: tips,
        content: (
          <InterleavedPagesSettings
            files={base.selectedFiles}
            onFilesChange={handleFilesChange}
            onRemoveFile={handleRemoveFile}
          />
        ),
      },
    ],
    executeButton: {
      text: t("interleavedPages.submit", "Create PDF"),
      isVisible: !base.hasResults,
      loadingText: t("loading"),
      onClick: base.handleExecute,
      disabled:
        !base.params.validateParameters() || !base.hasFiles || !base.endpointEnabled,
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t("interleavedPages.results.title", "Your PDF is Ready"),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

InterleavedPages.tool = () => useInterleavedPagesOperation;
export default InterleavedPages as ToolComponent;
