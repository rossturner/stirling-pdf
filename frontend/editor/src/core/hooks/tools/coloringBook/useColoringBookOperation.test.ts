import { describe, expect, it } from "vitest";
import {
  buildColoringBookFormData,
  coloringBookFileName,
  coloringBookResponseHandler,
  COLORING_BOOK_ENDPOINT,
} from "@app/hooks/tools/coloringBook/useColoringBookOperation";
import { defaultParameters } from "@app/hooks/tools/coloringBook/useColoringBookParameters";
import { toolAcceptsFile } from "@app/utils/toolIOCompat";

const image = (name: string) => new File(["x"], name, { type: "image/png" });

describe("buildColoringBookFormData", () => {
  it("sends every image as fileInput in workbench order", () => {
    const formData = buildColoringBookFormData(defaultParameters, [
      image("b.png"),
      image("a.png"),
      image("c.png"),
    ]);
    const names = formData
      .getAll("fileInput")
      .map((entry) => (entry as File).name);
    expect(names).toEqual(["b.png", "a.png", "c.png"]);
  });
});

describe("coloringBookFileName", () => {
  it("replaces the image extension with _coloring_book.pdf", () => {
    expect(coloringBookFileName("cat.png")).toBe("cat_coloring_book.pdf");
  });

  it("keeps dots inside the base name", () => {
    expect(coloringBookFileName("my.cat.v2.jpeg")).toBe(
      "my.cat.v2_coloring_book.pdf",
    );
  });

  it("handles a name with no extension", () => {
    expect(coloringBookFileName("scan")).toBe("scan_coloring_book.pdf");
  });
});

describe("coloringBookResponseHandler", () => {
  it("returns one PDF named after the first image", async () => {
    const blob = new Blob(["%PDF"], { type: "application/pdf" });
    const files = await coloringBookResponseHandler(blob, [
      image("cat.png"),
      image("dog.png"),
    ]);
    expect(files).toHaveLength(1);
    expect(files[0].name).toBe("cat_coloring_book.pdf");
    expect(files[0].type).toBe("application/pdf");
  });
});

describe("coloring-book input filtering", () => {
  it("accepts images, including upper-case and jpeg extensions", () => {
    for (const name of ["a.png", "PHOTO.JPG", "scan.JPEG", "frames.tiff"]) {
      expect(
        toolAcceptsFile(COLORING_BOOK_ENDPOINT, { name, type: "" }),
        name,
      ).toBe(true);
    }
  });

  it("ignores PDFs loaded alongside the images", () => {
    expect(
      toolAcceptsFile(COLORING_BOOK_ENDPOINT, {
        name: "doc.pdf",
        type: "application/pdf",
      }),
    ).toBe(false);
  });
});
