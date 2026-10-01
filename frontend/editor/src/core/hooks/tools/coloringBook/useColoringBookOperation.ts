import { useTranslation } from "react-i18next";
import {
  useToolOperation,
  defineMultiFileTool,
} from "@app/hooks/tools/shared/useToolOperation";
import { type ToolEndpoint } from "@app/hooks/tools/shared/toolApiMapping";
import { createStandardErrorHandler } from "@app/utils/toolErrorHandler";
import type { ResponseHandler } from "@app/utils/toolResponseProcessor";
import {
  ColoringBookParameters,
  defaultParameters,
} from "@app/hooks/tools/coloringBook/useColoringBookParameters";

export const COLORING_BOOK_ENDPOINT =
  "/api/v1/misc/coloring-book" satisfies ToolEndpoint;

export const buildColoringBookFormData = (
  _parameters: ColoringBookParameters,
  files: File[],
): FormData => {
  const formData = new FormData();
  files.forEach((file) => formData.append("fileInput", file));
  return formData;
};

export const coloringBookFileName = (firstImageName: string): string => {
  const dot = firstImageName.lastIndexOf(".");
  const baseName = dot > 0 ? firstImageName.slice(0, dot) : firstImageName;
  return `${baseName}_coloring_book.pdf`;
};

// Without this the multi-file path names the PDF after the first input as-is (`…cat.png`).
export const coloringBookResponseHandler: ResponseHandler = (
  blob,
  originalFiles,
) => [
  new File([blob], coloringBookFileName(originalFiles[0]?.name ?? "images"), {
    type: "application/pdf",
  }),
];

export const coloringBookOperationConfig = defineMultiFileTool({
  buildFormData: buildColoringBookFormData,
  operationType: "coloringBook",
  endpoint: COLORING_BOOK_ENDPOINT,
  filePrefix: "coloring_book_",
  responseHandler: coloringBookResponseHandler,
  defaultParameters,
});

export const useColoringBookOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<ColoringBookParameters>({
    ...coloringBookOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t(
        "coloringBook.error.failed",
        "An error occurred while creating the coloring book.",
      ),
    ),
  });
};
