import { useTranslation } from "react-i18next";
import { createToolFlow } from "@app/components/tools/shared/createToolFlow";
import { useColoringBookParameters } from "@app/hooks/tools/coloringBook/useColoringBookParameters";
import { useColoringBookOperation } from "@app/hooks/tools/coloringBook/useColoringBookOperation";
import { useBaseTool } from "@app/hooks/tools/shared/useBaseTool";
import { BaseToolProps, ToolComponent } from "@app/types/tool";

const ColoringBook = (props: BaseToolProps) => {
  const { t } = useTranslation();
  const base = useBaseTool(
    "coloringBook",
    useColoringBookParameters,
    useColoringBookOperation,
    props,
  );

  return createToolFlow({
    title: {
      title: t("home.coloringBook.title", "Coloring Book"),
      description: t(
        "coloringBook.filePrompt",
        "Select images to create coloring book pages.",
      ),
    },
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
    },
    steps: [],
    executeButton: {
      text: t("coloringBook.submit", "Create Coloring Book"),
      isVisible: !base.hasResults,
      loadingText: t("loading"),
      onClick: base.handleExecute,
      endpointEnabled: base.endpointEnabled,
      paramsValid: base.params.validateParameters(),
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t("coloringBook.title", "Coloring Book Results"),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

ColoringBook.tool = () => useColoringBookOperation;

export default ColoringBook as ToolComponent;
