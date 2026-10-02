import { useTranslation } from "react-i18next";
import {
  useToolOperation,
  defineMultiFileTool,
} from "@app/hooks/tools/shared/useToolOperation";
import {
  objectToFormData,
  type ToolApiParams,
  type ToolEndpoint,
} from "@app/hooks/tools/shared/toolApiMapping";
import { createStandardErrorHandler } from "@app/utils/toolErrorHandler";
import {
  InterleavedPagesParameters,
  defaultParameters,
} from "@app/hooks/tools/interleavedPages/useInterleavedPagesParameters";

const ENDPOINT = "/api/v1/general/interleaved-pages" satisfies ToolEndpoint;
type InterleavedPagesApiParams = ToolApiParams[typeof ENDPOINT];

// No special API parameters for interleaved pages; files passed in FormData only
export const buildFormData = (
  _parameters: InterleavedPagesParameters,
  files: File[]
): FormData => {
  // Files are already in desired order from UI reordering
  const formData = objectToFormData({}, { fileInput: files });
  return formData;
};

export const interleavedPagesOperationConfig = defineMultiFileTool({
  buildFormData,
  operationType: "interleavedPages",
  endpoint: ENDPOINT,
  filePrefix: "interleaved_",
  defaultParameters,
});

export const useInterleavedPagesOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<InterleavedPagesParameters>({
    ...interleavedPagesOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t(
        "interleavedPages.error.failed",
        "Failed to create interleaved PDF. Please check your images and try again."
      )
    ),
  });
};
