import { Stack, Text } from "@mantine/core";
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
    { ignoreViewerScope: true },
  );

  return createToolFlow({
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
    belowExecuteButton: !base.hasResults ? (
      <Stack align="center" gap={4} mx="md" mt={4}>
        <Text size="xs" c="dimmed" ta="center">
          {t(
            "coloringBook.fixedLayout",
            "Each image is centered on a portrait A4 page at its original aspect ratio.",
          )}
        </Text>
        <Text size="xs" c="dimmed" ta="center">
          {t(
            "coloringBook.blankPageRule",
            "A blank portrait A4 page is added after every generated page for duplex printing.",
          )}
        </Text>
        <Text size="xs" c="dimmed" ta="center">
          {t(
            "coloringBook.orderHint",
            "Files are processed in the order shown in your selected-file list.",
          )}
        </Text>
      </Stack>
    ) : undefined,
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t("coloringBook.results.title", "Coloring Book Results"),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

ColoringBook.tool = () => useColoringBookOperation;

export default ColoringBook as ToolComponent;
