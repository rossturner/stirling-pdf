import { describe, expect, test } from "vitest";
import { coloringBookOperationConfig } from "@app/hooks/tools/coloringBook/useColoringBookOperation";

describe("coloring book operation", () => {
  test("submits every selected file under fileInput in selection order", () => {
    const firstFile = new File(["first"], "first.png");
    const secondFile = new File(["second"], "second.png");

    const formData = coloringBookOperationConfig.buildFormData({}, [
      firstFile,
      secondFile,
    ]);

    expect(formData.getAll("fileInput")).toEqual([firstFile, secondFile]);
  });

  test("targets the coloring book conversion endpoint", () => {
    expect(coloringBookOperationConfig.endpoint).toBe(
      "/api/v1/convert/coloring-book",
    );
  });
});
