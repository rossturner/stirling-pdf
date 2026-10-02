import { useTranslation } from "react-i18next";
import {
  useToolOperation,
  defineMultiFileTool,
} from "@app/hooks/tools/shared/useToolOperation";
import {
  fileOnlyMapping,
  objectToFormData,
  type ToolApiParams,
  type ToolEndpoint,
} from "@app/hooks/tools/shared/toolApiMapping";
import { createStandardErrorHandler } from "@app/utils/toolErrorHandler";
import {
  ColoringBookParameters,
  defaultParameters,
} from "@app/hooks/tools/coloringBook/useColoringBookParameters";

const ENDPOINT = "/api/v1/convert/coloring-book" satisfies ToolEndpoint;
type ColoringBookApiParams = ToolApiParams[typeof ENDPOINT];

const { toApiParams, fromApiParams } = fileOnlyMapping();

export const buildColoringBookFormData = (
  _parameters: ColoringBookParameters,
  files: File[],
): FormData => {
  const apiParams: ColoringBookApiParams = toApiParams();
  return objectToFormData(apiParams, { fileInput: files });
};

export const coloringBookOperationConfig = defineMultiFileTool({
  buildFormData: buildColoringBookFormData,
  toApiParams,
  fromApiParams,
  operationType: "coloringBook",
  endpoint: ENDPOINT,
  filePrefix: "coloring-book_",
  defaultParameters,
});

export const useColoringBookOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<ColoringBookParameters>({
    ...coloringBookOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t(
        "coloringBook.error.failed",
        "An error occurred while creating the coloring book PDF.",
      ),
    ),
  });
};
