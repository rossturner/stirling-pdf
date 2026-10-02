import { useTranslation } from "react-i18next";
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
  const base = useBaseTool(
    "interleavedPages",
    useInterleavedPagesParameters,
    useInterleavedPagesOperation,
    props
  );

  return createToolFlow({
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
      placeholder: t(
        "interleavedPages.files.placeholder",
        "Drag images here or click to select. They'll appear on odd pages with blank pages on the back."
      ),
    },
    steps: [
      {
        title: t("interleavedPages.steps.settings", "Reorder Images"),
        tooltip: tips,
        content: (
          <InterleavedPagesSettings
            files={base.selectedFiles}
            onFilesChange={(files) => base.setSelectedFiles(files)}
            onRemoveFile={(index) => {
              const newFiles = base.selectedFiles.filter((_, i) => i !== index);
              base.setSelectedFiles(newFiles);
            }}
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
