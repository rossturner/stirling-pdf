import { describe, expect, test } from "vitest";
import {
  buildColoringBookFormData,
  coloringBookOperationConfig,
} from "@app/hooks/tools/coloringBook/useColoringBookOperation";
import { defaultParameters } from "@app/hooks/tools/coloringBook/useColoringBookParameters";

describe("buildColoringBookFormData", () => {
  test("appends every selected file under fileInput in order", () => {
    const files = [
      new File(["first"], "first.png", { type: "image/png" }),
      new File(["second"], "second.jpg", { type: "image/jpeg" }),
      new File(["third"], "third.tiff", { type: "image/tiff" }),
    ];

    const formData = buildColoringBookFormData(defaultParameters, files);

    expect(formData.getAll("fileInput")).toEqual(files);
  });
});

describe("coloringBookOperationConfig", () => {
  test("targets the dedicated multi-file endpoint", () => {
    expect(coloringBookOperationConfig.operationType).toBe("coloringBook");
    expect(coloringBookOperationConfig.endpoint).toBe(
      "/api/v1/convert/coloring-book",
    );
    expect(coloringBookOperationConfig.filePrefix).toBe("coloring-book_");
    expect(coloringBookOperationConfig.defaultParameters).toEqual(
      defaultParameters,
    );
  });
});
