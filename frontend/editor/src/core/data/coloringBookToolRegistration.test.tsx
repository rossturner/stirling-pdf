import { describe, expect, test, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { useTranslatedToolCatalog } from "@app/data/useTranslatedToolRegistry";
import { SubcategoryId } from "@app/data/toolsTaxonomy";
import { coloringBookOperationConfig } from "@app/hooks/tools/coloringBook/useColoringBookOperation";
import { TOOL_IO } from "@app/types/toolIO";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
    i18n: { changeLanguage: vi.fn(), language: "en-US" },
  }),
  Trans: ({ children }: { children?: unknown }) => children,
}));

const COLORING_BOOK_ENDPOINT = "/api/v1/convert/coloring-book";

describe("coloring book tool registration", () => {
  test("registers Coloring Book as a Page Formatting tool backed by the dedicated endpoint", () => {
    const { result } = renderHook(() => useTranslatedToolCatalog());
    const tool = result.current.regularTools.coloringBook;

    expect(tool).toBeDefined();
    expect(tool.subcategoryId).toBe(SubcategoryId.PAGE_FORMATTING);
    expect(tool.endpoints).toEqual(["coloring-book"]);
    expect(tool.maxFiles).toBe(-1);
    expect(tool.operationConfig).toBe(coloringBookOperationConfig);
    expect(tool.supportedFormats).toEqual(
      TOOL_IO[COLORING_BOOK_ENDPOINT]?.inputExtensions,
    );
  });
});
