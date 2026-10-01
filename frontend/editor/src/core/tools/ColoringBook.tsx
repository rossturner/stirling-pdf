import { useTranslation } from "react-i18next";
import { Text } from "@mantine/core";
import { createToolFlow } from "@app/components/tools/shared/createToolFlow";
import { useColoringBookParameters } from "@app/hooks/tools/coloringBook/useColoringBookParameters";
import { useColoringBookOperation } from "@app/hooks/tools/coloringBook/useColoringBookOperation";
import { useBaseTool } from "@app/hooks/tools/shared/useBaseTool";
import { useColoringBookTips } from "@app/components/tooltips/useColoringBookTips";
import { BaseToolProps, ToolComponent } from "@app/types/tool";

const ColoringBook = (props: BaseToolProps) => {
  const { t } = useTranslation();
  const tips = useColoringBookTips();

  // The viewer would otherwise narrow the input to the one image on screen.
  const base = useBaseTool(
    "coloringBook",
    useColoringBookParameters,
    useColoringBookOperation,
    props,
    { ignoreViewerScope: true },
  );

  return createToolFlow({
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
    },
    steps: [
      {
        title: t("coloringBook.info.title", "Double-sided printing"),
        isCollapsed: base.hasResults,
        tooltip: tips,
        content: (
          <Text size="sm" c="dimmed">
            {t(
              "coloringBook.info.text",
              "Each picture gets its own page followed by a blank page, ready to print double-sided.",
            )}
          </Text>
        ),
      },
    ],
    executeButton: {
      text: t("coloringBook.submit", "Create coloring book"),
      isVisible: !base.hasResults,
      loadingText: t("loading"),
      onClick: base.handleExecute,
      endpointEnabled: base.endpointEnabled,
      paramsValid: base.params.validateParameters(),
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t("coloringBook.results.title", "Coloring book"),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

ColoringBook.tool = () => useColoringBookOperation;

export default ColoringBook as ToolComponent;
