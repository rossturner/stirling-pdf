import { useTranslation } from "react-i18next";
import {
  useToolOperation,
  defineMultiFileTool,
} from "@app/hooks/tools/shared/useToolOperation";
import {
  fileOnlyMapping,
  objectToFormData,
  type ToolEndpoint,
} from "@app/hooks/tools/shared/toolApiMapping";
import { createStandardErrorHandler } from "@app/utils/toolErrorHandler";
import {
  defaultParameters,
  ColoringBookParameters,
} from "@app/hooks/tools/coloringBook/useColoringBookParameters";

const ENDPOINT = "/api/v1/convert/coloring-book" satisfies ToolEndpoint;
const { toApiParams, fromApiParams } = fileOnlyMapping();

const buildFormData = (
  _parameters: ColoringBookParameters,
  files: File[],
): FormData => objectToFormData(toApiParams(), { fileInput: files });

export const coloringBookOperationConfig = defineMultiFileTool({
  buildFormData,
  toApiParams,
  fromApiParams,
  operationType: "coloringBook",
  endpoint: ENDPOINT,
  filePrefix: "coloring_book_",
  defaultParameters,
});

export const useColoringBookOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<ColoringBookParameters>({
    ...coloringBookOperationConfig,
    filePrefix: `${t("coloringBook.filenamePrefix", "coloring_book")}_`,
    getErrorMessage: createStandardErrorHandler(
      t(
        "coloringBook.error.failed",
        "An error occurred while creating the coloring book.",
      ),
    ),
  });
};
